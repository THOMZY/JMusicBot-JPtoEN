/*
 * Copyright 2018-2020 Cosgy Dev
 * Edit 2025 THOMZY
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.jagrosh.jmusicbot.audio;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.JMusicBot;
import com.jagrosh.jmusicbot.PlayStatus;
import com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist;
import com.jagrosh.jmusicbot.queue.FairQueue;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.utils.YouTubeChapterExtractor;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.cosgy.jmusicbot.util.YtDlpManager.FallbackPlatform;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import dev.cosgy.jmusicbot.settings.RepeatMode;
import dev.cosgy.jmusicbot.slashcommands.music.RadioCmd;
import dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.FileUpload; // Import pour FileUpload
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import org.slf4j.LoggerFactory; // Import manquant
import dev.cosgy.jmusicbot.util.YtDlpManager.YtDlpMetadata;

import java.io.File; // Import pour java.io.File
import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.nio.file.Paths;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.TimeUnit;

/**
 * Handles audio playback and track management for a guild
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class AudioHandler extends AudioEventAdapter implements AudioSendHandler {
    private final FairQueue<QueuedTrack> queue = new FairQueue<>();
    private final List<AudioTrack> defaultQueue = new LinkedList<>();
    private final Set<String> votes = new HashSet<>();
    private final PlayerManager manager;
    private final AudioPlayer audioPlayer;
    private final long guildId;
    private final String stringGuildId;
    private AudioFrame lastFrame;
    private long streamStartTime;
    private final AtomicBoolean suppressAutoLeaveOnce = new AtomicBoolean(false);
    
    // Cache for various content sources
    private static final Map<String, String> sourceIconCache = new ConcurrentHashMap<>();
    
    /**
     * Enum representing different track types
     */
    public enum TrackType {
        SPOTIFY,    // Track played via Spotify command
        RADIO,      // Radio stream
        YOUTUBE,    // YouTube video
        SOUNDCLOUD, // SoundCloud track
        LOCAL,      // Local file uploaded through Discord
        OTHER       // Any other source
    }

    private AudioTrackInfo displayInfo(AudioTrack track) {
        return PlayerManager.getDisplayInfo(track);
    }

    private static RequestMetadata extractRequestMetadata(AudioTrack track) {
        if (track == null) return RequestMetadata.EMPTY;
        Object ud = track.getUserData();
        if (ud instanceof RequestMetadata) {
            return (RequestMetadata) ud;
        }
        if (ud instanceof PlayerManager.TrackContext) {
            PlayerManager.TrackContext tc = (PlayerManager.TrackContext) ud;
            if (tc.userData instanceof RequestMetadata) {
                return (RequestMetadata) tc.userData;
            }
        }
        return RequestMetadata.EMPTY;
    }

    /**
     * Determines the type of the currently playing track
     * @param track The track to check
     * @return The TrackType of the track
     */
    public TrackType getTrackType(AudioTrack track) {
        if (track == null) return TrackType.OTHER;
        
        RequestMetadata rm = extractRequestMetadata(track);
        AudioTrackInfo info = displayInfo(track);
        
        // Check for Spotify first - high priority
        if (isSpotifyTrack(track)) {
            return TrackType.SPOTIFY;
        }
        
        // Check for local file uploaded through Discord
        if (dev.cosgy.jmusicbot.util.LocalAudioMetadata.isDiscordUploadedFile(track)) {
            return TrackType.LOCAL;
        }
        
        // Check if it's a radio stream
        if (isRadioTrack(track)) {
            return TrackType.RADIO;
        }
        
        // Treat yt-dlp replacements as YouTube if original info matches
        if (info != null && info.uri != null) {
            String lowerUri = info.uri.toLowerCase();
            if (lowerUri.contains("youtube.com/") || lowerUri.contains("youtu.be/")) {
                return TrackType.YOUTUBE;
            }
        }
        // Otherwise use source manager name
        if (track.getSourceManager() != null &&
                track.getSourceManager().getSourceName().equalsIgnoreCase("youtube")) {
            return TrackType.YOUTUBE;
        }
        
        // Check for SoundCloud
        if (track.getSourceManager() != null && 
            track.getSourceManager().getSourceName().equalsIgnoreCase("soundcloud")) {
            return TrackType.SOUNDCLOUD;
        }

        // Infer from yt-dlp platform metadata
        FallbackPlatform platform = PlayerManager.getYtDlpPlatform(track);
        if (platform == FallbackPlatform.YOUTUBE) {
            return TrackType.YOUTUBE;
        }
        if (platform == FallbackPlatform.SOUNDCLOUD) {
            return TrackType.SOUNDCLOUD;
        }
        
        // Default to OTHER
        return TrackType.OTHER;
    }

    /**
     * Determines if a track is a radio station track
     * @param track The track to check
     * @return True if the track is a radio station
     */
    public boolean isRadioTrack(AudioTrack track) {
        if (track == null) return false;
        
        // First check the RequestMetadata for radio info
        RequestMetadata rm = extractRequestMetadata(track);
        if (rm != null && rm.hasRadioData()) {
            // If we have explicit radio data from the /radio command
            return true;
        }
        
        // Only treat as radio if the URL matches a known radio station/stream
        String trackUrl = track.getInfo() != null ? track.getInfo().uri : null;
        if (!isKnownRadioUrl(trackUrl)) {
            // Prevent stale radio metadata (ex: lastStationPaths) from mislabeling non-radio tracks
            return false;
        }

        // Fallback: check RadioCmd's static maps (tracks loaded via /radio)
        String stationPath = getRadioStationPath(track);
        return stationPath != null;
    }

    /**
     * Gets the station path from the currently playing radio track
     * @param track The current track
     * @return The station path or null if not found
     */
    public String getCurrentRadioStationPath(AudioTrack track) {
        if (track == null) return null;
        
        // Check if the RequestMetadata has radio info
        RequestMetadata rm = extractRequestMetadata(track);
        if (rm != null && rm.hasRadioData()) {
            return rm.getRadioStationPath();
        }
        
        // Fallback to the RadioCmd static maps
        return getRadioStationPath(track);
    }
    
    /**
     * Extracts the radio station path from a track URL
     * @param track The track to check
     * @return The station path or null if not found
     */
    private String getRadioStationPath(AudioTrack track) {
        if (track == null || track.getInfo().uri == null) return null;
        
        // First check if this guild has an active radio station in the RadioCmd map
        // Only use the global station path if the track is the currently playing one
        String stationPath = RadioCmd.lastStationPaths.get(stringGuildId);
        if (stationPath != null && track.equals(audioPlayer.getPlayingTrack())) {
            return stationPath;
        }
        
        // If not found in the map, try to extract it from the track URL
        String trackUrl = track.getInfo().uri;
        
        // Check if this is from OnlineRadioBox
        if (trackUrl.contains("onlineradiobox.com")) {
            // Extract the path from orb URLs like: https://onlineradiobox.com/uk/capital/
            try {
                URL url = new URL(trackUrl);
                String path = url.getPath();
                if (path.startsWith("/")) {
                    path = path.substring(1);
                }
                if (path.endsWith("/")) {
                    path = path.substring(0, path.length() - 1);
                }
                return path;
            } catch (Exception e) {
                System.out.println("Error parsing radio URL: " + e.getMessage());
            }
        } else {
            // For direct stream URLs, try to find a matching station from our stored data
            // by comparing the stream URLs
            for (Map.Entry<String, String> entry : RadioCmd.getStreamUrlMappings().entrySet()) {
                if (trackUrl.equals(entry.getValue())) {
                    return entry.getKey();
                }
            }
        }
        
        return null;
    }

    private boolean isKnownRadioUrl(String trackUrl) {
        if (trackUrl == null) {
            return false;
        }
        if (trackUrl.contains("onlineradiobox.com")) {
            return true;
        }
        return RadioCmd.getStreamUrlMappings().containsValue(trackUrl);
    }
    
    /**
     * Gets the radio logo URL for the current track
     * @param track The track to check
     * @return The logo URL or null if not found
     */
    public String getRadioLogoUrl(AudioTrack track) {
        if (track == null) return null;
        
        // Check if the RequestMetadata has radio info
        RequestMetadata rm = extractRequestMetadata(track);
        if (rm != null && rm.hasRadioData()) {
            return rm.getRadioLogoUrl();
        }
        
        // Fallback to the RadioCmd static maps
        String stationPath = getRadioStationPath(track);
        if (stationPath != null) {
            return RadioCmd.lastStationLogos.getOrDefault(stationPath, null);
        }
        
        return null;
    }
    
    /**
     * Gets the radio station name for the current track
     * @param track The track to check
     * @return The station name or null if not found
     */
    public String getRadioStationName(AudioTrack track) {
        if (track == null) return null;
        
        // Check if the RequestMetadata has radio info
        RequestMetadata rm = extractRequestMetadata(track);
        if (rm != null && rm.hasRadioData()) {
            return rm.getRadioStationName();
        }
        
        // Extract from track title if possible
        String stationTitle = track.getInfo().title;
        if (stationTitle != null) {
            if (stationTitle.contains(" | ")) {
                stationTitle = stationTitle.substring(stationTitle.lastIndexOf(" | ") + 3);
            }
            if (stationTitle.endsWith(" Radio")) {
                stationTitle = stationTitle.substring(0, stationTitle.length() - 6);
            }
            return stationTitle;
        }
        
        return "Radio Stream";
    }

    /**
     * Determines if a track was loaded through the Spotify command
     * @param track The track to check
     * @return True if the track is from Spotify
     */
    public boolean isSpotifyTrack(AudioTrack track) {
        if (track == null) return false;
        
        // Check RequestMetadata for Spotify track ID
        RequestMetadata rm = extractRequestMetadata(track);
        if (rm != null && rm.hasSpotifyData()) {
            // This track has Spotify data directly attached to it
            String spotifyTrackId = rm.getSpotifyTrackId();
            if (spotifyTrackId != null && !spotifyTrackId.isEmpty()) {
                // Make sure the SpotifyCmd knows about this track
                SpotifyCmd.lastTrackIds.put(stringGuildId, spotifyTrackId);
                return true;
            }
        }
        
        // Fallback: check if this guild has an active Spotify track in the SpotifyCmd map
        String trackId = SpotifyCmd.lastTrackIds.get(stringGuildId);
        if (trackId == null) return false;
        
        // Get the Spotify track info to compare with the current track
        SpotifyCmd.SpotifyTrackInfo trackInfo = SpotifyCmd.getTrackInfo(stringGuildId);
        if (trackInfo != null) {
            // Clean up the title for comparison (remove things like " (Official Video)")
            String cleanTitle = track.getInfo().title;
            if (cleanTitle.contains(" (")) {
                cleanTitle = cleanTitle.substring(0, cleanTitle.indexOf(" ("));
            }
            
            // Compare with the Spotify track name
            boolean titleMatch = cleanTitle.equalsIgnoreCase(trackInfo.trackName);
            
            // Additional check with artist if available
            boolean artistMatch = false;
            if (track.getInfo().author != null && !track.getInfo().author.isEmpty()) {
                artistMatch = track.getInfo().author.equalsIgnoreCase(trackInfo.artistName);
            }
            
            // If either title or artist matches, consider it a Spotify track
            return titleMatch || artistMatch;
        }
        
        return false;
    }

    /**
     * Gets detailed information about the currently playing Spotify track
     * @return SpotifyTrackInfo object or null if not a Spotify track
     */
    public SpotifyCmd.SpotifyTrackInfo getSpotifyTrackInfo() {
        if (!isSpotifyTrack(audioPlayer.getPlayingTrack())) {
            return null;
        }
        
        // If we have Spotify data in RequestMetadata, ensure it's in the SpotifyCmd maps
        RequestMetadata rm = extractRequestMetadata(audioPlayer.getPlayingTrack());
        if (rm != null && rm.hasSpotifyData()) {
            String spotifyTrackId = rm.getSpotifyTrackId();
            SpotifyCmd.lastTrackIds.put(stringGuildId, spotifyTrackId);
        }
        
        // Get the track info from SpotifyCmd
        return SpotifyCmd.getTrackInfo(stringGuildId);
    }

    /**
     * Gets detailed information about the currently playing radio station
     * @param track The track to check
     * @return RadioCmd.TrackInfo object or null if not a radio track
     */
    public RadioCmd.TrackInfo getRadioTrackInfo(AudioTrack track) {
        if (!isRadioTrack(track)) {
            return null;
        }
        
        String stationPath = getCurrentRadioStationPath(track);
        if (stationPath == null) {
            return null;
        }
        
        try {
            // Create RadioCmd instance to access non-static methods
            RadioCmd radioCmd = new RadioCmd(manager.getBot());
            
            // Get the current track info from the radio station
            return radioCmd.getDetailedTrackInfo(stationPath);
        } catch (Exception e) {
            System.out.println("Error getting radio track info: " + e.getMessage());
            return null;
        }
    }

    /**
     * Gets the icon URL for a specific source
     * @param sourceName The name of the source
     * @return URL to the source's icon
     */
    public String getSourceIconUrl(String sourceName) {
        // Check if we have this icon cached
        if (sourceIconCache.containsKey(sourceName)) {
            return sourceIconCache.get(sourceName);
        }
        
        // Otherwise, determine the icon URL based on the source
        String iconUrl;
        switch (sourceName.toLowerCase()) {
            case "youtube":
                iconUrl = "https://cdn-icons-png.flaticon.com/512/1384/1384060.png";
                break;
            case "soundcloud":
                iconUrl = "https://www.shareicon.net/data/128x128/2015/09/17/102358_soundcloud_512x512.png";
                break;
            case "bandcamp":
                iconUrl = "https://images.seeklogo.com/logo-png/52/3/bandcamp-logo-png_seeklogo-528569.png?v=1957857986033129792";
                break;
            case "spotify":
                iconUrl = "https://images.seeklogo.com/logo-png/26/3/spotify-2015-logo-png_seeklogo-266802.png";
                break;
            case "twitch":
                iconUrl = "https://seeklogo.com/images/T/twitch-new-logo-BAB7E776B9-seeklogo.com.png";
                break;
            case "vimeo":
                iconUrl = "https://pluspng.com/img-png/vimeo-logo-png-vimeo-color-icon-vimeo-video-social-png-and-vector-vimeo-logo-840x859.png";
                break;
            case "tiktok":
                iconUrl = "https://cdn-icons-png.flaticon.com/512/3046/3046121.png";
                break;
            case "twitter":
                iconUrl = "https://cdn-icons-png.flaticon.com/512/5968/5968830.png";
                break;
            case "x":
                iconUrl = "https://cdn-icons-png.flaticon.com/512/5968/5968830.png";
                break;
            case "instagram":
                iconUrl = "https://cdn-icons-png.flaticon.com/512/15713/15713420.png";
                break;
            case "http":
            case "stream":
                iconUrl = "https://cdn-icons-png.flaticon.com/128/11796/11796884.png";
                break;
            case "local":
                iconUrl = "https://www.icons101.com/icons/58/Windows_10_Filetypes_by_Smallvillerus/128/MP3.png";
                break;
            case "niconico":
                iconUrl = "http://images.shoutwiki.com/sanrio/d/d2/Niconico_logo.png";
                break;
            case "radio":
                iconUrl = "https://static.semrush.com/power-pages/media/favicons/onlineradiobox-com-favicon-7dd1a612.png";
                break;
            default:
                iconUrl = "https://cdn-icons-png.flaticon.com/512/1384/1384061.png"; // Default music icon
        }
        
        // Cache the icon URL for future use
        sourceIconCache.put(sourceName.toLowerCase(), iconUrl);
        return iconUrl;
    }

    protected AudioHandler(PlayerManager manager, Guild guild, AudioPlayer player) {
        this.manager = manager;
        this.audioPlayer = player;
        this.guildId = guild.getIdLong();
        this.stringGuildId = guild.getId();
    }

    // Skip the onTrackEnd auto-leave once (used when replacing a track via fallback)
    public void suppressAutoLeaveOnce() {
        this.suppressAutoLeaveOnce.set(true);
    }

    /**
     * Adds a track to the front of the queue, or plays it immediately if nothing is playing
     * @param qtrack The track to add
     * @return Position in the queue, or -1 if playing immediately
     */
    public int addTrackToFront(QueuedTrack qtrack) {
        if (audioPlayer.getPlayingTrack() == null) {
            audioPlayer.playTrack(qtrack.getTrack());
            return -1;
        } else {
            queue.addAt(0, qtrack);
            return 0;
        }
    }

    /**
     * Adds a track to the queue, or plays it immediately if nothing is playing
     * @param qtrack The track to add
     * @return Position in the queue, or -1 if playing immediately
     */
    public int addTrack(QueuedTrack qtrack) {
        if (audioPlayer.getPlayingTrack() == null) {
            audioPlayer.playTrack(qtrack.getTrack());
            return -1;
        } else {
            boolean toEnt = manager.getBot().getSettingsManager().getSettings(guildId).isForceToEndQue();
            return queue.add(qtrack, toEnt);
        }
    }

    /**
     * Handles adding a track to the queue if repeat mode is enabled
     * @param track The track to add if in repeat mode
     */
    public void addTrackIfRepeat(AudioTrack track) {
        RepeatMode mode = manager.getBot().getSettingsManager().getSettings(guildId).getRepeatMode();
        if (mode == RepeatMode.OFF) return;
        
        // For repeat modes, add a clone of the track to the queue
        AudioTrack clonedTrack = track.makeClone();
        
        // Preserve the RequestMetadata
        RequestMetadata rm = extractRequestMetadata(track);
        clonedTrack.setUserData(rm);
        
        // Add to queue based on settings
        boolean toEnt = manager.getBot().getSettingsManager().getSettings(guildId).isForceToEndQue();
        queue.add(new QueuedTrack(clonedTrack, rm), toEnt);
    }

    /**
     * Gets the current queue
     * @return The queue
     */
    public FairQueue<QueuedTrack> getQueue() {
        return queue;
    }

    /**
     * Stops playback, clears the queue, and disconnects from voice channel
     */
    public void stopAndClear() {
        // Get current track before stopping it
        AudioTrack currentTrack = audioPlayer.getPlayingTrack();
        
        // Stop playback
        audioPlayer.stopTrack();
        
        // Clear the queue
        queue.clear();
        defaultQueue.clear();
        votes.clear();
        
        Guild guild = guild(manager.getBot().getJDA());
        Bot.updatePlayStatus(guild, guild.getSelfMember(), PlayStatus.STOPPED);
        
        // Check if the current track was a Gensokyo Radio stream and stop the agent
        if (currentTrack != null && isGensokyoRadioTrack(currentTrack)) {
            dev.cosgy.agent.GensokyoInfoAgent.stopAgent();
            manager.getBot().getNowplayingHandler().cancelGensokyoUpdateTask(guildId);
        }
    }

    /**
     * Checks if music is currently playing
     * @param jda The JDA instance
     * @return True if music is playing
     */
    public boolean isMusicPlaying(JDA jda) {
        Guild g = guild(jda);
        if (audioPlayer.getPlayingTrack() == null) {
            return false;
        }
        if (g == null) {
            return true; // Track exists; assume playing even if guild lookup failed
        }
        GuildVoiceState vs = g.getSelfMember().getVoiceState();
        return vs != null && vs.inAudioChannel();
    }

    /**
     * Gets the current skip votes
     * @return Set of user IDs that voted to skip
     */
    public Set<String> getVotes() {
        return votes;
    }

    /**
     * Gets the player instance
     * @return The AudioPlayer
     */
    public AudioPlayer getPlayer() {
        return audioPlayer;
    }

    /**
     * Gets the request metadata for the current track
     * @return The RequestMetadata or EMPTY if no track is playing
     */
    public RequestMetadata getRequestMetadata() {
        if (audioPlayer.getPlayingTrack() == null)
            return RequestMetadata.EMPTY;
        
        RequestMetadata rm = extractRequestMetadata(audioPlayer.getPlayingTrack());
        return rm == null ? RequestMetadata.EMPTY : rm;
    }

    /**
     * Update statistics when a track is skipped
     */
    public void updateStatsOnSkip() {
        AudioTrack track = audioPlayer.getPlayingTrack();
        if (track == null) return;
        
            Settings settings = manager.getBot().getSettingsManager().getSettings(guildId);
            settings.incrementSongsPlayed();
        
        // Calculate the actual play time based on track type
            if (track.getInfo().isStream) {
            // For streams, calculate time from start to skip
                long streamDuration = System.currentTimeMillis() - streamStartTime;
                settings.addPlayTime(streamDuration);
            } else {
            // For normal tracks, add the time played before skip
                settings.addPlayTime(track.getPosition());
        }
    }

    /**
     * Plays a track from the default playlist if available
     * @return True if a track was loaded from default
     */
    public boolean playFromDefault() {
        // First check the in-memory default queue
        if (!defaultQueue.isEmpty()) {
            audioPlayer.playTrack(defaultQueue.remove(0));
            return true;
        }
        
        // Then check if we have a default playlist configured
        Settings settings = manager.getBot().getSettingsManager().getSettings(guildId);
        if (settings == null || settings.getDefaultPlaylist() == null)
            return false;

        // Load tracks from the default playlist
        Playlist pl = manager.getBot().getPlaylistLoader().getPlaylist(stringGuildId, settings.getDefaultPlaylist());
        if (pl == null || pl.getItems().isEmpty())
            return false;
        
        // Start loading tracks from the playlist
        pl.loadTracks(manager, (at) -> {
            if (audioPlayer.getPlayingTrack() == null)
                audioPlayer.playTrack(at);
            else
                defaultQueue.add(at);
        }, () -> {
            if (pl.getTracks().isEmpty() && !manager.getBot().getConfig().getStay())
                manager.getBot().closeAudioConnection(guildId);
        });
        return true;
    }

    // Audio Events
    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        if (endReason == AudioTrackEndReason.REPLACED) {
            return;
        }

        boolean suppress = suppressAutoLeaveOnce.getAndSet(false);
        // If a fallback/replacement already started another track, ignore this end event.
        // But if nothing is playing (fallback failed), continue normally so the queue can advance.
        if (suppress && player.getPlayingTrack() != null) {
            return;
        }
        RepeatMode repeatMode = manager.getBot().getSettingsManager().getSettings(guildId).getRepeatMode();

        // Track statistics update
        updateTrackStatistics(track);
        
        // If this was a stream, stop ICY metadata monitoring
        if (track != null && track.getInfo().isStream) {
            manager.getBot().getIcyMetadataHandler().stopMonitoring(stringGuildId);
            
            // If this was a Gensokyo Radio stream, stop the info agent and cancel update task
            if (isGensokyoRadioTrack(track)) {
                dev.cosgy.agent.GensokyoInfoAgent.unregisterTrack(stringGuildId);
                dev.cosgy.agent.GensokyoInfoAgent.stopAgent();
                manager.getBot().getNowplayingHandler().cancelGensokyoUpdateTask(guildId);
            }
        }
        
        // Handle YouTube livestreams that ended unexpectedly
        // Always attempt to replay YouTube streams that end with FINISHED or LOAD_FAILED
        // If the stream is truly ended, the replay attempt will fail naturally and move to next track
        if (track != null && track.getInfo().isStream && track.getInfo().uri.contains("youtube.com") 
            && (endReason == AudioTrackEndReason.FINISHED || endReason == AudioTrackEndReason.LOAD_FAILED)) {
            
            // Attempt to replay the stream - if it's truly ended, this will fail and skip to next track
            AudioTrack clonedTrack = track.makeClone();
            
            // Preserve the RequestMetadata
            RequestMetadata rm = extractRequestMetadata(track);
            if (rm != null) {
                clonedTrack.setUserData(rm);
            }
            
            player.playTrack(clonedTrack);
            return;
        }

        // Handle track repetition based on RepeatMode setting
        if (endReason == AudioTrackEndReason.FINISHED && repeatMode != RepeatMode.OFF) {
            handleTrackRepetition(track, repeatMode);
        }

        // Play next track or default playlist
        if (queue.isEmpty()) {
            if (!playFromDefault()) {
                stopPlayback(player);
            }
        } else {
            QueuedTrack qt = queue.pull();
            player.playTrack(qt.getTrack());
        }
    }

    /**
     * Updates statistics when a track finishes playing
     * @param track The track that just finished
     */
    private void updateTrackStatistics(AudioTrack track) {
        if (track == null) return;
        
                Settings settings = manager.getBot().getSettingsManager().getSettings(guildId);
                settings.incrementSongsPlayed();
        
        // Add play time based on the track type
                if (track.getInfo().isStream) {
            // For streams, calculate actual listening time
                    long streamDuration = System.currentTimeMillis() - streamStartTime;
                    settings.addPlayTime(streamDuration);
                } else {
            // For normal tracks, add the full duration
                    settings.addPlayTime(track.getDuration());
                }
            }
            
    /**
     * Handles track repetition based on the repeat mode
     * @param track The track that just finished playing
     * @param repeatMode The current repeat mode
     */
    private void handleTrackRepetition(AudioTrack track, RepeatMode repeatMode) {
        // Get the RequestMetadata from the track to preserve important information
                RequestMetadata rm = extractRequestMetadata(track);
                
                // Create a cloned track that will be added back to the queue
                AudioTrack clonedTrack = track.makeClone();
                
        // Make sure the cloned track keeps the same RequestMetadata
        clonedTrack.setUserData(rm);
        
        // Different handling based on repeat mode
                if (repeatMode == RepeatMode.ALL) {
            // Add the track to the end of the queue
            boolean toEnd = manager.getBot().getSettingsManager().getSettings(guildId).isForceToEndQue();
            queue.add(new QueuedTrack(clonedTrack, rm), toEnd);
                } else if (repeatMode == RepeatMode.SINGLE) {
            // Add the track to the front of the queue for immediate replay
                    queue.addAt(0, new QueuedTrack(clonedTrack, rm));
        }
    }
    
    /**
     * Stops playback and updates status
     * @param player The audio player
     */
    private void stopPlayback(AudioPlayer player) {
                manager.getBot().getNowplayingHandler().onTrackUpdate(guildId, null, this);
        if (!manager.getBot().getConfig().getStay()) {
            manager.getBot().closeAudioConnection(guildId);
        }

                player.setPaused(false);

                Guild guild = guild(manager.getBot().getJDA());
                Bot.updatePlayStatus(guild, guild.getSelfMember(), PlayStatus.STOPPED);
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        votes.clear();
        manager.getBot().getNowplayingHandler().onTrackUpdate(track, this);
        
        // Check for stream and update stream info
        if (track.getInfo().isStream) {
            this.streamStartTime = System.currentTimeMillis();
        }
        
        // Update guild-specific settings and process any metadata
        Guild guild = guild(manager.getBot().getJDA());
        handleTrackTypeData(track);
        
        // For Gensokyo Radio tracks, set up the Bot instance and register this track
        if (isGensokyoRadioTrack(track)) {
            dev.cosgy.agent.GensokyoInfoAgent.setBot(manager.getBot());
            dev.cosgy.agent.GensokyoInfoAgent.registerTrack(stringGuildId, track);
        }
        // Start monitoring ICY metadata for streams that weren't added via /radio command
        else if (track.getInfo().isStream && !isRadioTrack(track)) {
            manager.getBot().getIcyMetadataHandler().startMonitoring(track, guildId);
        }
        
        // Update play status (for nickname display)
        Bot.updatePlayStatus(guild, guild.getSelfMember(), PlayStatus.PLAYING);
        
        // Increment number of songs played in settings
        Settings settings = manager.getBot().getSettingsManager().getSettings(guildId);
        settings.incrementSongsPlayed();
        
        // Add the track to music history if enabled
        // Note: Gensokyo Radio tracks have special handling in the MusicHistory class
        if (manager.getBot().getConfig().isHistoryEnabled()) {
            manager.getBot().getMusicHistory().addTrack(track, this);
        }
    }
    
    @Override
    public void onTrackException(AudioPlayer player, AudioTrack track, com.sedmelluq.discord.lavaplayer.tools.FriendlyException exception) {
        // Handle exceptions during playback of YouTube streams
        // This allows for faster recovery than waiting for onTrackEnd
        if (track != null && track.getInfo().isStream && track.getInfo().uri.contains("youtube.com")) {
            // Check if the exception is recoverable (network issues, temporary failures)
            if (exception.severity != com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity.FAULT) {
                // Attempt immediate replay to minimize audio interruption
                AudioTrack clonedTrack = track.makeClone();
                
                // Preserve the RequestMetadata
                RequestMetadata rm = extractRequestMetadata(track);
                if (rm != null) {
                    clonedTrack.setUserData(rm);
                }
                
                // Replay immediately - this happens faster than waiting for onTrackEnd
                player.playTrack(clonedTrack);
                return;
            }
        }
        
        // For other cases, let the default behavior handle it (will trigger onTrackEnd)
    }
    
    /**
     * Manages track type-specific data when a track starts
     * @param track The track that started playing
     */
    private void handleTrackTypeData(AudioTrack track) {
        // First, clear any existing data from previous tracks
        clearPreviousTrackData();
        
        // Then determine the actual type of this track
        TrackType trackType = getTrackType(track);
        
        // Configure based on the specific track type
        switch (trackType) {
            case SPOTIFY:
                // Ensure Spotify data is properly initialized
                ensureSpotifyDataIsInitialized(track);
                break;
                
            case RADIO:
                // Ensure radio data is properly initialized
                ensureRadioDataIsInitialized(track);
                break;
                
            case YOUTUBE:
            case SOUNDCLOUD:
            case OTHER:
            default:
                // No special handling needed for other track types
                // The previous data has already been cleared
                break;
        }
    }
    
    /**
     * Clears all track-specific data from previous tracks
     */
    private void clearPreviousTrackData() {
        // Clear radio data
        RadioCmd.lastStationPaths.remove(stringGuildId);
        RadioCmd.lastStationLogos.remove(stringGuildId);
        
        // Clear Spotify data
        SpotifyCmd.lastTrackIds.remove(stringGuildId);
    }
    
    /**
     * Ensures that Spotify data is initialized for the track if applicable
     * @param track The track to check
     */
    private void ensureSpotifyDataIsInitialized(AudioTrack track) {
        if (hasSpotifyData(track)) {
            // Make sure the global map is updated with this track's Spotify ID
            RequestMetadata rm = extractRequestMetadata(track);
            if (rm != null && rm.hasSpotifyData()) {
                SpotifyCmd.lastTrackIds.put(stringGuildId, rm.getSpotifyTrackId());
            }
        }
    }
    
    /**
     * Ensures that radio data is initialized for the track if applicable
     * @param track The track to check
     */
    private void ensureRadioDataIsInitialized(AudioTrack track) {
        if (track == null) return;
        
        RequestMetadata rm = extractRequestMetadata(track);
        if (rm == null || !rm.hasRadioData()) return;
        
        // Store the radio info in the global maps for compatibility
        String stationPath = rm.getRadioStationPath();
        String logoUrl = rm.getRadioLogoUrl();
        
        if (stationPath != null) {
            RadioCmd.lastStationPaths.put(stringGuildId, stationPath);
            
            if (logoUrl != null && !logoUrl.isEmpty()) {
                RadioCmd.lastStationLogos.put(stringGuildId, logoUrl);
            }
        }
    }
    
    /**
     * Checks if a track has Spotify data
     * @param track The track to check
     * @return True if the track has Spotify data
     */
    private boolean hasSpotifyData(AudioTrack track) {
        if (track == null) return false;
        
        RequestMetadata rm = extractRequestMetadata(track);
        return rm != null && rm.hasSpotifyData();
    }

    // Formatting
    public MessageCreateData getNowPlaying(JDA jda) throws Exception {
        Guild guild = guild(jda);
        if (guild == null) {
            return MessageCreateData.fromContent("No music is playing.");
        }
        
        AudioTrack track = audioPlayer.getPlayingTrack();
        if (track == null) {
            return getNoMusicPlaying(jda);
        }
        
        // Get RequestMetadata
        RequestMetadata rm = getRequestMetadata();
        Bot bot = manager.getBot(); // Get bot instance
        
        // Special handling for local uploaded files
        if (getTrackType(track) == TrackType.LOCAL) {
            // Check if we don't have metadata in RequestMetadata but do have it in the local cache
            if ((rm == null || !rm.hasLocalFileData()) && 
                bot.getLocalMetadataCache().containsKey(track.getInfo().identifier)) {
                
                // Retrieve the metadata from cache
                dev.cosgy.jmusicbot.util.LocalAudioMetadata.LocalTrackInfo cachedInfo = 
                    bot.getLocalMetadataCache().get(track.getInfo().identifier);
                
                if (cachedInfo != null) {
                    // If RequestMetadata is null, create a new one
                    if (rm == null || rm == RequestMetadata.EMPTY) { // Ensure rm is not EMPTY
                        User owner = rm != null && rm.getOwner() != 0L ? jda.getUserById(rm.getOwner()) : null;
                        rm = new RequestMetadata(owner); // Recreate with user if possible
                        track.setUserData(rm);
                    }
                    
                    // Update the RequestMetadata with the cached data
                    rm.setLocalFileMetadata(
                        cachedInfo.getTitle(),
                        cachedInfo.getArtist(),
                        cachedInfo.getAlbum(),
                        cachedInfo.getYear(),
                        cachedInfo.getGenre(),
                        cachedInfo.getArtworkPath() // Added artworkHash
                    );
                    // Also update artwork path in rm if needed, though buildLocalFileEmbed will use bot.getLocalArtworkPath
                }
            }
        }
        
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(guild.getSelfMember().getColor());
        
        // Variable to track if we have artwork to attach
        boolean hasArtworkToAttach = false;
        String artworkDiskPath = null; // Full path on disk for FileUpload
        
        // Handle different track types with different embeds
        TrackType trackType = getTrackType(track);
        
        // Check for Gensokyo Radio first
        if (isGensokyoRadioTrack(track)) {
            buildGensokyoRadioEmbed(eb, track);
        }
        // Handle other track types
        else switch (trackType) {
            case SPOTIFY:
                buildSpotifyEmbed(eb, track, rm);
                break;
            case RADIO:
                buildRadioEmbed(eb, track, rm);
                break;
            case YOUTUBE:
                buildYoutubeEmbed(eb, track, rm);
                break;
            case SOUNDCLOUD:
                buildSoundCloudEmbed(eb, track, rm);
                break;
            case LOCAL:
                // For local files, check if we have artwork
                if (bot.getConfig().useNPImages()) {
                    // Get the relative path from Bot instance
                    String relativeArtworkPath = bot.getLocalArtworkPath(track.getInfo().identifier);
                    if (relativeArtworkPath != null && !relativeArtworkPath.isEmpty()) {
                        // Construct the full path on disk for FileUpload
                        // Assuming the bot runs from a directory where local_artwork is a subdirectory
                        File artworkFile = new File(relativeArtworkPath); 
                        if (artworkFile.exists() && artworkFile.isFile()) {
                            hasArtworkToAttach = true;
                            artworkDiskPath = artworkFile.getAbsolutePath(); // Use absolute path for File
                        }
                    }
                }
                buildLocalFileEmbed(eb, track, rm, hasArtworkToAttach, artworkDiskPath != null ? Paths.get(artworkDiskPath).getFileName().toString() : null);
                break;
            default:
                // Check if it's a stream with ICY metadata
                IcyMetadataHandler.StreamMetadata metadata = bot.getIcyMetadataHandler().getMetadata(guild.getId());
                if (metadata != null && !metadata.hasFailed() && metadata.getCurrentTrack() != null && !metadata.getCurrentTrack().isEmpty()) {
                    buildIcyMetadataEmbed(eb, track, metadata);
                } else if (track.getInfo().isStream) {
                    buildGenericStreamEmbed(eb, track);
                } else {
                    buildDefaultEmbed(eb, track, rm);
                }
                break;
        }
        
        // Set requester info if available
        setRequesterAuthor(eb, rm, guild);
        
        // Create the message
        MessageCreateBuilder messageBuilder = new MessageCreateBuilder();
        
        // Handle artwork for local files - Create message with file attachment first
        // so we can reference the attachment URL in the embed
        if (trackType == TrackType.LOCAL && hasArtworkToAttach && artworkDiskPath != null) {
            try {
                // Load the file and create the attachment
                File artworkFileOnDisk = new File(artworkDiskPath);
                String attachmentFilename = artworkFileOnDisk.getName(); // e.g., "hash.png"
                FileUpload fileUpload = FileUpload.fromData(artworkFileOnDisk, attachmentFilename);
                
                // Add file to the message
                messageBuilder.addFiles(fileUpload);
                
                // Set the thumbnail URL to reference the attachment
                // The URL format is "attachment://filename.ext"
                eb.setThumbnail("attachment://" + attachmentFilename);
            } catch (Exception e) {
                System.err.println("Error attaching artwork: " + e.getMessage());
                 LoggerFactory.getLogger(AudioHandler.class).error("Error attaching artwork for track {}: {}", track.getIdentifier(), e.getMessage()); // Correction ici
            }
        }
        
        // Add the embed to the message
        messageBuilder.setEmbeds(eb.build());
        
        return messageBuilder.build();
    }
    
    /**
     * Sets the author field in the embed to the user who requested the track
     * @param eb The EmbedBuilder to set author on
     * @param rm The RequestMetadata containing user info
     * @param guild The current guild
     */
    private void setRequesterAuthor(EmbedBuilder eb, RequestMetadata rm, Guild guild) {
                        if (rm.getOwner() != 0L) {
                            User u = guild.getJDA().getUserById(rm.user.id);
                            if (u == null)
                                eb.setAuthor(rm.user.username, null, rm.user.avatar);
                            else
                                eb.setAuthor(u.getName(), null, u.getEffectiveAvatarUrl());
        }
    }
    
    /**
     * Builds an embed for Spotify tracks
     * @param eb The EmbedBuilder to populate
     * @param track The current track
     * @param rm The RequestMetadata
     */
    private void buildSpotifyEmbed(EmbedBuilder eb, AudioTrack track, RequestMetadata rm) {
        eb.setTitle("~ Now playing Spotify track :");
        
        // Get detailed Spotify track info
        SpotifyCmd.SpotifyTrackInfo trackInfo = getSpotifyTrackInfo();
        
        if (trackInfo != null) {
            // Rich description with track details
            StringBuilder description = new StringBuilder();
            
            description.append("**Track:** ").append(trackInfo.trackName);
            description.append("\n**Album:** ").append(trackInfo.albumName);
            description.append("\n**Artist:** ").append(trackInfo.artistName);
            description.append("\n**Released:** ").append(trackInfo.releaseYear);
            
            // Add Spotify link using trackId
            if (trackInfo.trackId != null && !trackInfo.trackId.isEmpty()) {
                description.append("\n[Open on Spotify](https://open.spotify.com/track/").append(trackInfo.trackId).append(")");
            }

            // Add progress bar with proper spacing
            description.append("\n\n"); // Add an extra line here to ensure proper spacing
            appendProgressBar(description, track);
            
            eb.setDescription(description.toString());
            
            // Add album art as image if npimages is enabled
            if (manager.getBot().getConfig().useNPImages() && 
                trackInfo.albumImageUrl != null && 
                !trackInfo.albumImageUrl.isEmpty()) {
                eb.setImage(trackInfo.albumImageUrl);
            }
            
            // Set color and footer
            eb.setColor(trackInfo.color);
            eb.setFooter("Source: Spotify", getSourceIconUrl("spotify"));
        } else {
            // Fallback if we couldn't get Spotify info
            buildDefaultEmbed(eb, track, rm);
            eb.setFooter("Source: Spotify", getSourceIconUrl("spotify"));
        }
    }

    /**
     * Builds an embed for Gensokyo Radio streams
     * @param eb The EmbedBuilder to populate
     * @param track The current track
     */
    private void buildGensokyoRadioEmbed(EmbedBuilder eb, AudioTrack track) {
        eb.setTitle("~ Now playing Gensokyo Radio :");

        StringBuilder description = new StringBuilder();

        try {
            // Pull rich metadata from the Gensokyo agent when available
            dev.cosgy.agent.objects.ResultSet info = dev.cosgy.agent.GensokyoInfoAgent.getInfo();

            if (info != null && info.getSonginfo() != null) {
                String artist = info.getSonginfo().getArtist();
                String title = info.getSonginfo().getTitle();

                if (artist != null || title != null) {
                    description.append("**")
                               .append(artist != null ? artist : "Unknown Artist")
                               .append(" - ")
                               .append(title != null ? title : "Unknown Title")
                               .append("**");
                }

                if (info.getSonginfo().getAlbum() != null && !info.getSonginfo().getAlbum().isEmpty()) {
                    description.append("\nAlbum: ").append(info.getSonginfo().getAlbum());
                }
                if (info.getSonginfo().getCircle() != null && !info.getSonginfo().getCircle().isEmpty()) {
                    description.append("\nCircle: ").append(info.getSonginfo().getCircle());
                }

                // Attach album art if permitted and available
                if (manager.getBot().getConfig().useNPImages() &&
                    info.getMisc() != null &&
                    info.getMisc().getFullAlbumArtUrl() != null &&
                    !info.getMisc().getFullAlbumArtUrl().isEmpty()) {
                    eb.setImage(info.getMisc().getFullAlbumArtUrl());
                }
            } else {
                // Fallback text if no detailed metadata is available
                description.append("Gensokyo Radio");
            }
        } catch (Exception e) {
            // Safe fallback in case the agent fails
            description.append("Gensokyo Radio");
        }

        description.append("\n\n");
        appendProgressBar(description, track);

        eb.setDescription(description.toString());
        eb.setFooter("Source: Gensokyo Radio", "https://stream.gensokyoradio.net/images/logo.png");
    }
    
    /**
     * Builds an embed for radio station tracks
     * @param eb The EmbedBuilder to populate
     * @param track The current track
     * @param rm The RequestMetadata
     */
    private void buildRadioEmbed(EmbedBuilder eb, AudioTrack track, RequestMetadata rm) {
        eb.setTitle("~ Now playing Radio :");
        
        // First check if we have radio info in RequestMetadata - this is most reliable
        if (rm != null && rm.hasRadioData()) {
            String stationPath = rm.getRadioStationPath();
            String stationName = rm.getRadioStationName();
            String logoUrl = rm.getRadioLogoUrl();
            
            // Create radio station display
            buildRadioEmbedContent(eb, track, stationPath, stationName, logoUrl);
        }
        // Fallback to global maps if RequestMetadata doesn't have the info
        else {
            String stationPath = getCurrentRadioStationPath(track);
            if (stationPath != null) {
                // Get station info
                String stationName = getRadioStationName(track);
                String logoUrl = getRadioLogoUrl(track);
                
                // Create radio station display
                buildRadioEmbedContent(eb, track, stationPath, stationName, logoUrl);
            } else {
                // If we still can't find radio data, check if we have ICY metadata
                // This handles streams added via /play instead of /radio
                IcyMetadataHandler.StreamMetadata icyMetadata = 
                    manager.getBot().getIcyMetadataHandler().getMetadata(stringGuildId);
                
                if (icyMetadata != null) {
                    // Build the embed using ICY metadata
                    buildIcyMetadataEmbed(eb, track, icyMetadata);
                } else {
                    // If all else fails, fall back to generic stream display
                    buildGenericStreamEmbed(eb, track);
                }
            }
        }
    }
    
    /**
     * Builds the content of a radio embed with the provided station information
     * @param eb The EmbedBuilder to populate
     * @param track The current track
     * @param stationPath The station path/identifier
     * @param stationName The station name
     * @param logoUrl The station logo URL
     */
    private void buildRadioEmbedContent(EmbedBuilder eb, AudioTrack track, String stationPath, String stationName, String logoUrl) {
        // Description with station and track info
        StringBuilder description = new StringBuilder();
        
        // Only create a Online Radio Box URL if this is an actual Online Radio Box station
        String stationUrl;
        boolean isOnlineRadioBox = stationPath != null && !stationPath.equals(String.valueOf(track.getInfo().uri.hashCode()));
        
        if (isOnlineRadioBox) {
            stationUrl = "https://onlineradiobox.com/" + stationPath;
        } else {
            stationUrl = track.getInfo().uri;
        }
        
        // Try to get current track info from RadioCmd
        RadioCmd.TrackInfo trackInfo = getRadioTrackInfo(track);
        String title = "";
        
        // Set the title in the format "artist - song | RadioName"
        if (trackInfo != null && !trackInfo.getFormattedTitle().isEmpty() && 
            !trackInfo.getFormattedTitle().equals("Unknown")) {
            // Use the artist - song format for title
            eb.setTitle("~ Now playing Radio :");
            description.append("[").append(stationName).append("](").append(stationUrl).append(")\n\n");
            description.append("Now playing:\n");
            description.append(trackInfo.getFormattedTitle());
            
            // Add album art if available and npimages is enabled
            if (manager.getBot().getConfig().useNPImages() && 
                trackInfo.imageUrl != null && 
                !trackInfo.imageUrl.isEmpty()) {
                eb.setImage(trackInfo.imageUrl);
            }
        } else {
            // Check if we have ICY metadata
            IcyMetadataHandler.StreamMetadata icyData = 
                manager.getBot().getIcyMetadataHandler().getMetadata(stringGuildId);
            
            if (icyData != null && icyData.getCurrentTrack() != null && 
                !icyData.getCurrentTrack().isEmpty()) {
                // Use ICY metadata for title
                eb.setTitle("~ Now playing Radio :");
                title = icyData.getCurrentTrack() + " | " + stationName;
                description.append("**Now playing:** ").append(icyData.getCurrentTrack()).append(" | [").append(stationName).append("](").append(stationUrl).append(")");
            } else {
                // Fallback to just station name
                eb.setTitle("~ Now playing Radio :");
                description.append("**Station:** [").append(stationName).append("](").append(stationUrl).append(")");
            }
        }
        
        // Add play status for streams
        description.append("\n\n");
        description.append((audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI))
                .append(" ")
                .append(FormatUtil.progressBar(-1)) // -1 for infinite streams
                .append(" `[LIVE]` ");
        
        eb.setDescription(description.toString());
        
        // Add station logo as thumbnail if npimages is enabled
        if (manager.getBot().getConfig().useNPImages() && 
            logoUrl != null && 
            !logoUrl.isEmpty()) {
            eb.setThumbnail(logoUrl);
        }
        
        // Set footer to show source
        if (isOnlineRadioBox) {
            eb.setFooter("Source: Online Radio Box", getSourceIconUrl("radio"));
        } else {
            eb.setFooter("Source: Radio Stream", getSourceIconUrl("stream"));
        }
    }
    
    /**
     * Builds an embed using ICY metadata information
     * @param eb The EmbedBuilder to populate
     * @param track The track being played
     * @param metadata The ICY metadata
     */
    private void buildIcyMetadataEmbed(EmbedBuilder eb, AudioTrack track, IcyMetadataHandler.StreamMetadata metadata) {
        // Description with station and track info
        StringBuilder description = new StringBuilder();
        String stationName = metadata.getStationName();
        String streamUrl = track.getInfo().uri;
        
        // Set title to indicate this is a stream, not a radio
        eb.setTitle("~ Now playing Stream :");
        
        // Add current track info if available
        if (metadata.getCurrentTrack() != null && !metadata.getCurrentTrack().isEmpty()) {
            // Format the current track for display
            String currentTrack = metadata.getCurrentTrack();
            
            // Add the formatted title to the description
            description.append("**Now playing:** ").append(currentTrack);
            
            // Only add station name if different from current track
            if (!currentTrack.equals(stationName)) {
                description.append(" | [").append(stationName).append("](").append(streamUrl).append(")");
            } else {
                description.append(" | [Stream](").append(streamUrl).append(")");
            }
        } else {
            // No current track, just show stream info
            description.append("**Stream:** [").append(stationName).append("](").append(streamUrl).append(")");
        }
        
        // Add genre if available
        if (metadata.getStationGenre() != null && !metadata.getStationGenre().isEmpty()) {
            description.append("\n**Genre:** ").append(metadata.getStationGenre());
        }
        
        // Add play status for streams
        description.append("\n\n");
        description.append((audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI))
                  .append(" ")
                  .append(FormatUtil.progressBar(-1)) // -1 for infinite streams
                  .append(" `[LIVE]` ");
        
        eb.setDescription(description.toString());
        
        // Add station logo as thumbnail if available and npimages is enabled
        if (manager.getBot().getConfig().useNPImages() && 
            metadata.getStationLogo() != null && 
            !metadata.getStationLogo().isEmpty()) {
            eb.setThumbnail(metadata.getStationLogo());
        }
        
        // Add album art if available and npimages is enabled
        if (manager.getBot().getConfig().useNPImages() && 
            metadata.getAlbumArt() != null && 
            !metadata.getAlbumArt().isEmpty()) {
            eb.setImage(metadata.getAlbumArt());
        }
        
        // Set footer to show source as Stream
        eb.setFooter("Source: Stream", getSourceIconUrl("stream"));
    }
    
    /**
     * Builds an embed for YouTube tracks
     * @param eb The EmbedBuilder to populate
     * @param track The current track
     * @param rm The RequestMetadata
     */
    private void buildYoutubeEmbed(EmbedBuilder eb, AudioTrack track, RequestMetadata rm) {
        try {
            // Get the track title or filename for display
            AudioTrackInfo info = displayInfo(track);
            String title = info.title;
            if (title == null || title.isEmpty() || title.equals("Unknown title")) {
                // Extract filename from URL for local files
                String uri = info.uri;
                title = dev.cosgy.jmusicbot.util.LocalAudioMetadata.extractFilenameFromUrl(uri);
                title = dev.cosgy.jmusicbot.util.LocalAudioMetadata.cleanupFilename(title);
            }
            
            eb.setTitle(title, info.uri);
        } catch (Exception e) {
            // Get the track title or filename for display
            AudioTrackInfo info = displayInfo(track);
            String title = info.title;
            if (title == null || title.isEmpty() || title.equals("Unknown title")) {
                // Extract filename from URL for local files
                String uri = info.uri;
                title = dev.cosgy.jmusicbot.util.LocalAudioMetadata.extractFilenameFromUrl(uri);
                title = dev.cosgy.jmusicbot.util.LocalAudioMetadata.cleanupFilename(title);
            }
            
            eb.setTitle(title);
        }

        // Add YouTube thumbnail
        if (manager.getBot().getConfig().useNPImages()) {
            // Extract video ID
            String videoId = extractYoutubeVideoId(track);
            if (videoId != null) {
                // Use highest quality thumbnail
                eb.setThumbnail("https://img.youtube.com/vi/" + videoId + "/mqdefault.jpg");
            }
        }
        
        // Build description with author and progress bar
        StringBuilder description = new StringBuilder();
        AudioTrackInfo info = displayInfo(track);
        if (info.author != null && !info.author.isEmpty()) {
            description.append("**Channel:** ").append(info.author).append("\n\n");
        }
        
        // Get current chapter if available
        YouTubeChapterManager chapterManager = manager.getBot().getYoutubeChapterManager();
        com.jagrosh.jmusicbot.utils.YouTubeChapterExtractor.Chapter currentChapter = 
            chapterManager.getCurrentChapter(track, track.getPosition());
        
        if (currentChapter != null) {
            description.append("**Current Chapter:** ").append(currentChapter.getName()).append("\n\n");
            
            // List up to 5 chapters to avoid cluttering the embed
            List<com.jagrosh.jmusicbot.utils.YouTubeChapterExtractor.Chapter> chapters = 
                chapterManager.getChapters(track);
            
            if (chapters.size() > 1) {
                description.append("**Chapters:**\n");
                
                // Find the index of the current chapter
                int currentIndex = chapters.indexOf(currentChapter);
                
                // Determine which chapters to show (try to center around current chapter)
                int startIdx = Math.max(0, currentIndex - 2);
                int endIdx = Math.min(chapters.size() - 1, startIdx + 4);
                
                // Readjust start if we have less than 5 chapters at the end
                if (endIdx - startIdx < 4) {
                    startIdx = Math.max(0, endIdx - 4);
                }
                
                for (int i = startIdx; i <= endIdx; i++) {
                    com.jagrosh.jmusicbot.utils.YouTubeChapterExtractor.Chapter chapter = chapters.get(i);
                    String prefix = (chapter == currentChapter) ? "▶️ " : "⏺️ ";
                    description.append(prefix)
                        .append(FormatUtil.formatTime(chapter.getStartTimeMs()))
                        .append(" - ")
                        .append(chapter.getName())
                        .append("\n");
                }
                
                // If there are more chapters before or after, indicate that
                if (startIdx > 0) {
                    description.append("*... and ").append(startIdx).append(" more before*\n");
                }
                if (endIdx < chapters.size() - 1) {
                    description.append("*... and ").append(chapters.size() - 1 - endIdx).append(" more after*\n");
                }
                
                description.append("\n");
            }
        }
        
        appendProgressBar(description, track);
        eb.setDescription(description.toString());
        
        // Set footer
        eb.setFooter("Source: YouTube", getSourceIconUrl("youtube"));
    }
    
    /**
     * Extracts the YouTube video ID from a track
     * @param track The track to extract from
     * @return The video ID or null if not found
     */
    private String extractYoutubeVideoId(AudioTrack track) {
        // First try the identifier directly
                        String videoId = track.getIdentifier();
                        
                        // If the identifier is a full URL, extract the video ID from it
                        if (videoId.contains("?v=")) {
                            videoId = videoId.substring(videoId.indexOf("?v=") + 3);
                            if (videoId.contains("&")) {
                                videoId = videoId.substring(0, videoId.indexOf("&"));
                            }
        } else if (videoId.contains("youtu.be/")) {
            videoId = videoId.substring(videoId.indexOf("youtu.be/") + 9);
            if (videoId.contains("?")) {
                videoId = videoId.substring(0, videoId.indexOf("?"));
            }
        }
        
        return videoId;
    }
    
    /**
     * Builds an embed for SoundCloud tracks
     * @param eb The EmbedBuilder to populate
     * @param track The current track
     * @param rm The RequestMetadata
     */
    private void buildSoundCloudEmbed(EmbedBuilder eb, AudioTrack track, RequestMetadata rm) {
        AudioTrackInfo info = displayInfo(track);
        try {
            // Get the track title or filename for display
            String title = info.title;
            if (title == null || title.isEmpty() || title.equals("Unknown title")) {
                // Extract filename from URL for local files
                String uri = info.uri;
                title = dev.cosgy.jmusicbot.util.LocalAudioMetadata.extractFilenameFromUrl(uri);
                title = dev.cosgy.jmusicbot.util.LocalAudioMetadata.cleanupFilename(title);
            }
            
            eb.setTitle(title, info.uri);
        } catch (Exception e) {
            // Get the track title or filename for display
            String title = info.title;
            if (title == null || title.isEmpty() || title.equals("Unknown title")) {
                // Extract filename from URL for local files
                String uri = info.uri;
                title = dev.cosgy.jmusicbot.util.LocalAudioMetadata.extractFilenameFromUrl(uri);
                title = dev.cosgy.jmusicbot.util.LocalAudioMetadata.cleanupFilename(title);
            }
            
            eb.setTitle(title);
        }
        
        // Add SoundCloud thumbnail
        if (manager.getBot().getConfig().useNPImages() && 
            info != null && info.artworkUrl != null && 
            !info.artworkUrl.isEmpty()) {
            eb.setThumbnail(info.artworkUrl);
        }
        
        // Build description with author and progress bar
        StringBuilder description = new StringBuilder();
        if (info.author != null && !info.author.isEmpty()) {
            description.append("**Artist:** ").append(info.author).append("\n\n");
        }
        
        appendProgressBar(description, track);
        eb.setDescription(description.toString());
        
        // Set footer
        eb.setFooter("Source: SoundCloud", getSourceIconUrl("soundcloud"));
    }
    
    /**
     * Builds a generic embed for stream tracks
     * @param eb The EmbedBuilder to populate
     * @param track The current track
     */
    private void buildGenericStreamEmbed(EmbedBuilder eb, AudioTrack track) {
        eb.setTitle("~ Now playing Stream :");
        
        // Add basic stream information
        StringBuilder description = new StringBuilder();
        description.append("**Stream:** ").append(track.getInfo().title);
        
        // Add play status for streams
        description.append("\n\n");
        description.append((audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI))
                        .append(" ")
                .append(FormatUtil.progressBar(-1)) // -1 for infinite streams
                .append(" `[LIVE]` ");
                
        eb.setDescription(description.toString());
        eb.setFooter("Source: Stream", getSourceIconUrl("stream"));
    }
    
    /**
     * Builds a default embed for tracks from other sources
     * @param eb The EmbedBuilder to populate
     * @param track The current track
     * @param rm The RequestMetadata
     */
    private void buildDefaultEmbed(EmbedBuilder eb, AudioTrack track, RequestMetadata rm) {
        AudioTrackInfo info = displayInfo(track);
        if (info == null) {
            info = track.getInfo();
        }

        YtDlpMetadata ytMeta = PlayerManager.getYtDlpMetadata(track);
        FallbackPlatform ytPlatform = PlayerManager.getYtDlpPlatform(track);

        try {
            String title = info.title;
            if (title == null || title.isEmpty() || title.equals("Unknown title")) {
                String uri = info.uri;
                title = dev.cosgy.jmusicbot.util.LocalAudioMetadata.extractFilenameFromUrl(uri);
                title = dev.cosgy.jmusicbot.util.LocalAudioMetadata.cleanupFilename(title);
            }

            eb.setTitle(title, info.uri);
        } catch (Exception e) {
            String title = info.title;
            if (title == null || title.isEmpty() || title.equals("Unknown title")) {
                String uri = info.uri;
                title = dev.cosgy.jmusicbot.util.LocalAudioMetadata.extractFilenameFromUrl(uri);
                title = dev.cosgy.jmusicbot.util.LocalAudioMetadata.cleanupFilename(title);
            }

            eb.setTitle(title);
        }

        if (manager.getBot().getConfig().useNPImages()) {
            if (info.artworkUrl != null && !info.artworkUrl.isEmpty()) {
                eb.setThumbnail(info.artworkUrl);
            } else if (ytMeta != null && ytMeta.thumbnailUrl() != null && !ytMeta.thumbnailUrl().isEmpty()) {
                eb.setThumbnail(ytMeta.thumbnailUrl());
            }
        }

        TrackType trackType = getTrackType(track);
        String sourcePlatform = "Unknown Source";
        String sourceKey = null;
        String customIconUrl = null;

        if (ytPlatform != null && ytPlatform != FallbackPlatform.NONE) {
            switch (ytPlatform) {
                case INSTAGRAM -> { sourcePlatform = "Instagram"; sourceKey = "instagram"; }
                case TIKTOK -> { sourcePlatform = "TikTok"; sourceKey = "tiktok"; }
                case TWITTER -> { sourcePlatform = "Twitter"; sourceKey = "twitter"; }
                case BILIBILI -> { sourcePlatform = "Bilibili"; sourceKey = "bilibili"; }
                case VIMEO -> { sourcePlatform = "Vimeo"; sourceKey = "vimeo"; }
                case TWITCH -> { sourcePlatform = "Twitch"; sourceKey = "twitch"; }
                case SOUNDCLOUD -> { sourcePlatform = "SoundCloud"; sourceKey = "soundcloud"; }
                case YOUTUBE -> { sourcePlatform = "YouTube"; sourceKey = "youtube"; }
                default -> {
                    if (ytMeta != null && ytMeta.webpageUrl() != null) {
                        try {
                            java.net.URI uri = new java.net.URI(ytMeta.webpageUrl());
                            String host = uri.getHost();
                            if (host != null) {
                                String fullDomain = host;
                                host = host.startsWith("www.") ? host.substring(4) : host;
                                int lastDot = host.lastIndexOf('.');
                                if (lastDot > 0) {
                                    host = host.substring(0, lastDot);
                                }
                                if (!host.isEmpty()) {
                                    sourcePlatform = host.substring(0, 1).toUpperCase() + host.substring(1);
                                    sourceKey = sourcePlatform.toLowerCase();
                                    customIconUrl = "https://www.google.com/s2/favicons?domain=" + fullDomain + "&sz=64";
                                }
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }

        if (sourceKey == null) {
            switch (trackType) {
                case YOUTUBE -> { sourcePlatform = "YouTube"; sourceKey = "youtube"; }
                case SPOTIFY -> { sourcePlatform = "Spotify"; sourceKey = "spotify"; }
                case SOUNDCLOUD -> { sourcePlatform = "SoundCloud"; sourceKey = "soundcloud"; }
                case RADIO -> { sourcePlatform = "Radio"; sourceKey = "radio"; }
                case LOCAL -> { sourcePlatform = "Local File"; sourceKey = "local"; }
                default -> { }
            }
        }

        if (sourceKey == null && track.getSourceManager() != null) {
            sourceKey = track.getSourceManager().getSourceName().toLowerCase();
            sourcePlatform = track.getSourceManager().getSourceName();

            if (sourcePlatform.equalsIgnoreCase("http")) {
                sourcePlatform = "HTTP Stream";
            } else if (sourcePlatform.equalsIgnoreCase("local")) {
                sourcePlatform = "Local File";
            } else {
                sourcePlatform = sourcePlatform.substring(0, 1).toUpperCase() +
                        sourcePlatform.substring(1).toLowerCase();
            }
        }

        StringBuilder descBuilder = new StringBuilder();
        if (info.author != null && !info.author.isEmpty()) {
            descBuilder.append("**Artist:** ").append(info.author).append("\n\n");
        }

        if (ytMeta != null && ytMeta.description() != null && !ytMeta.description().isBlank()) {
            String desc = ytMeta.description().trim();
            if (desc.length() > 350) {
                desc = desc.substring(0, 347) + "...";
            }
            descBuilder.append(desc).append("\n\n");
        }

        appendProgressBar(descBuilder, track);
        eb.setDescription(descBuilder.toString());

        String finalIconUrl = customIconUrl != null ? customIconUrl : getSourceIconUrl(sourceKey != null ? sourceKey : sourcePlatform.toLowerCase());
        eb.setFooter("Source: " + sourcePlatform, finalIconUrl);
    }
    
    /**
     * Appends a progress bar to the description based on track type
     * @param description The StringBuilder to append to
     * @param track The track to get progress info from
     */
    private void appendProgressBar(StringBuilder description, AudioTrack track) {
        if (track.getInfo().isStream) {
            // For streams
            description.append((audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI))
                    .append(" ")
                    .append(FormatUtil.progressBar(-1)) // -1 for infinite streams
                    .append(" `[LIVE]` ");
        } else {
            // For normal tracks
                double progress = (double) audioPlayer.getPlayingTrack().getPosition() / track.getDuration();
            description.append((audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI))
                        .append(" ")
                        .append(FormatUtil.progressBar(progress))
                        .append(" `[")
                        .append(FormatUtil.formatTime(track.getPosition()))
                        .append("/")
                        .append(FormatUtil.formatTime(track.getDuration()))
                        .append("]` ");
            }
    }

    public MessageCreateData getNoMusicPlaying(JDA jda) {
        Guild guild = guild(jda);
        return new MessageCreateBuilder()
                .setContent(FormatUtil.filter(manager.getBot().getConfig().getSuccess() + " **No music is playing.**"))
                .setEmbeds(new EmbedBuilder()
                        .setTitle("No music is playing.")
                        .setDescription(JMusicBot.STOP_EMOJI + " " + FormatUtil.progressBar(-1) + " " + FormatUtil.volumeIcon(audioPlayer.getVolume()))
                        .setColor(guild.getSelfMember().getColor())
                        .build())
                .build();
    }

    /**
     * Gets the topic format string for channel topics
     * @param jda The JDA instance
     * @return The formatted topic string
     */
    public String getTopicFormat(JDA jda) {
        if (isMusicPlaying(jda)) {
            long userid = getRequestMetadata().getOwner();
            AudioTrack track = audioPlayer.getPlayingTrack();
            
            // Check for Gensokyo Radio first
            if (isGensokyoRadioTrack(track)) {
                try {
                    // Get current track info from GensokyoInfoAgent
                    dev.cosgy.agent.objects.ResultSet info = dev.cosgy.agent.GensokyoInfoAgent.getInfo();
                    
                    if (info != null && info.getSonginfo() != null) {
                        // Format as Artist - Title for Gensokyo Radio
                        String artistTitle = info.getSonginfo().getArtist() + " - " + info.getSonginfo().getTitle();
                        return "**" + artistTitle + " | Gensokyo Radio** [" + (userid == 0 ? "📻" : "<@" + userid + ">") + "]" +
                               "\n" + (audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI) + " " +
                               "[LIVE] " +
                               FormatUtil.volumeIcon(audioPlayer.getVolume());
                    }
                } catch (Exception e) {
                    // If there was an error, fall back to default behavior
                }
            }
            
            TrackType trackType = getTrackType(track);
            
            // Build different formats based on track type
            switch (trackType) {
                case SPOTIFY:
                    // Spotify format
                    SpotifyCmd.SpotifyTrackInfo spotifyInfo = getSpotifyTrackInfo();
                    if (spotifyInfo != null) {
                        return "**" + spotifyInfo.trackName + "** by **" + spotifyInfo.artistName + "** [" + 
                               (userid == 0 ? "🎧" : "<@" + userid + ">") + "]" +
                               "\n" + (audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI) + " " +
                               "[" + FormatUtil.formatTime(track.getDuration()) + "] " +
                               FormatUtil.volumeIcon(audioPlayer.getVolume());
                    }
                    break;
                    
                case RADIO:
                    // Radio format
                    String stationPath = getCurrentRadioStationPath(track);
                    if (stationPath != null) {
                        // Get the station name from the track title
                        String stationName = getRadioStationName(track);
                        
                        // Try to get the current track info from RadioCmd or ICY metadata
                        RadioCmd.TrackInfo radioInfo = getRadioTrackInfo(track);
                        String artistSong = "";
                        
                        if (radioInfo != null && radioInfo.getFormattedTitle() != null && 
                            !radioInfo.getFormattedTitle().isEmpty() && 
                            !radioInfo.getFormattedTitle().equals("Unknown")) {
                            // Use the formatted title from RadioTrackInfo
                            artistSong = radioInfo.getFormattedTitle() + " | ";
                        } else {
                            // Check if we have ICY metadata
                            IcyMetadataHandler.StreamMetadata icyData = 
                                manager.getBot().getIcyMetadataHandler().getMetadata(stringGuildId);
                            
                            if (icyData != null && icyData.getCurrentTrack() != null && 
                                !icyData.getCurrentTrack().isEmpty()) {
                                artistSong = icyData.getCurrentTrack() + " | ";
                            }
                        }
                        
                        return "**" + artistSong + stationName + "** [" + (userid == 0 ? "📻" : "<@" + userid + ">") + "]" +
                               "\n" + (audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI) + " " +
                               "[LIVE] " +
                               FormatUtil.volumeIcon(audioPlayer.getVolume());
                    }
                    
                    // Check if we have ICY metadata for this stream
                    IcyMetadataHandler.StreamMetadata icyMetadata = 
                        manager.getBot().getIcyMetadataHandler().getMetadata(stringGuildId);
                    
                    if (icyMetadata != null) {
                        String stationName = icyMetadata.getStationName();
                        String currentTrack = icyMetadata.getCurrentTrack();
                        
                        if (!currentTrack.isEmpty()) {
                            return "**" + currentTrack + (stationName.equals(currentTrack) ? "" : " | " + stationName) + "** [" + 
                                   (userid == 0 ? "🎧" : "<@" + userid + ">") + "]" +
                                   "\n" + (audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI) + " " +
                                   "[LIVE] " +
                                   FormatUtil.volumeIcon(audioPlayer.getVolume());
                        } else {
                            return "**" + stationName + "** [" + (userid == 0 ? "🎧" : "<@" + userid + ">") + "]" +
                                   "\n" + (audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI) + " " +
                                   "[LIVE] " +
                                   FormatUtil.volumeIcon(audioPlayer.getVolume());
                        }
                    }
                    break;
            }
            
            // Default format for all other tracks
            AudioTrackInfo info = displayInfo(track);
            String title = info.title;
            if (title == null || title.isEmpty() || title.equals("Unknown title")) {
                // Extract filename from URL for local files
                String uri = info.uri;
                title = dev.cosgy.jmusicbot.util.LocalAudioMetadata.extractFilenameFromUrl(uri);
                title = dev.cosgy.jmusicbot.util.LocalAudioMetadata.cleanupFilename(title);
            }
                
            if (info.isStream) {
                return "**" + title + "** [" + (userid == 0 ? "📻" : "<@" + userid + ">") + "]" +
                       "\n" + (audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI) + " " +
                       "[LIVE] " +
                       FormatUtil.volumeIcon(audioPlayer.getVolume());
            } else {
                return "**" + title + "** [" + (userid == 0 ? "🎵" : "<@" + userid + ">") + "]" +
                       "\n" + (audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI) + " " +
                       "[" + FormatUtil.formatTime(track.getDuration()) + "] " +
                       FormatUtil.volumeIcon(audioPlayer.getVolume());
            }
        } else {
            return "No music is playing" + JMusicBot.STOP_EMOJI + " " + FormatUtil.volumeIcon(audioPlayer.getVolume());
        }
    }

    // Audio Send Handler methods
    @Override
    public boolean canProvide() {
        lastFrame = audioPlayer.provide();
        return lastFrame != null;
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        return ByteBuffer.wrap(lastFrame.getData());
    }

    @Override
    public boolean isOpus() {
        return true;
    }

    // Private methods
    private Guild guild(JDA jda) {
        return jda.getGuildById(guildId);
    }

    /**
     * Builds an embed for local audio files uploaded through Discord
     * @param eb The EmbedBuilder to populate
     * @param track The current track
     * @param rm The RequestMetadata
     * @param hasArtworkToAttach Whether artwork will be attached (for thumbnail logic)
     * @param artworkAttachmentName The name of the attachment file (e.g., hash.png)
     */
    private void buildLocalFileEmbed(EmbedBuilder eb, AudioTrack track, RequestMetadata rm, boolean hasArtworkToAttach, String artworkAttachmentName) {
        String trackId = track.getInfo().identifier;
        Bot bot = manager.getBot(); // Get bot instance
        
        // Set title with debug info
        eb.setTitle("~ Now playing local file :");
        
        // Get the filename for display
        String trackFilename = dev.cosgy.jmusicbot.util.LocalAudioMetadata.extractFilenameFromUrl(track.getInfo().uri);
        String fileLink = "[" + trackFilename + "](" + track.getInfo().uri + ")";
        
        // Check if metadata exists in Bot.localMetadataCache
        dev.cosgy.jmusicbot.util.LocalAudioMetadata.LocalTrackInfo cachedInfo = bot.getLocalMetadataCache().get(trackId);
        boolean hasCachedInfo = (cachedInfo != null);
        
        StringBuilder description = new StringBuilder();
        description.append(fileLink).append("\n\n");

        String title = null, artist = null, album = null, year = null, genre = null;

        // Try to get metadata from RequestMetadata first
        if (rm != null && rm != RequestMetadata.EMPTY && rm.hasLocalFileData()) {
            title = rm.getLocalFileTitle();
            artist = rm.getLocalFileArtist();
            album = rm.getLocalFileAlbum();
            year = rm.getLocalFileYear();
            genre = rm.getLocalFileGenre();
        } 
        // Fallback to cached metadata if not in RequestMetadata or rm is EMPTY
        else if (hasCachedInfo) {
            title = cachedInfo.getTitle();
            artist = cachedInfo.getArtist();
            album = cachedInfo.getAlbum();
            year = cachedInfo.getYear();
            genre = cachedInfo.getGenre();
        }

        // Default title to cleaned filename if still unknown
        if (title == null || title.isEmpty() || title.equals("Unknown title") || title.equals("Unknown")) {
            title = dev.cosgy.jmusicbot.util.LocalAudioMetadata.cleanupFilename(trackFilename);
        }
        
        description.append("**Title:** ").append(title).append("\n");
        
        if (artist != null && !artist.isEmpty() && !artist.equals("Unknown Artist")) {
            description.append("**Artist:** ").append(artist).append("\n");
        }
        
        if (album != null && !album.isEmpty() && !album.equals("Unknown Album")) {
            description.append("**Album:** ").append(album).append("\n");
        }
        
        if (year != null && !year.isEmpty()) {
            description.append("**Year:** ").append(year).append("\n");
        }
        
        if (genre != null && !genre.isEmpty()) {
            description.append("**Genre:** ").append(genre).append("\n\n");
        } else {
            description.append("\n"); // Add space before progress bar
        }
        
        appendProgressBar(description, track);
        eb.setDescription(description.toString());

        // Thumbnail is set via attachment in getNowPlaying, so no direct setThumbnail here if attaching.
        // If not attaching (e.g. useNPImages is false, or no artwork found), this ensures no broken thumbnail.
        if (!hasArtworkToAttach && bot.getConfig().useNPImages()) {
             // Optionally, could set a default local file icon here if no artwork is found/attached
             // eb.setThumbnail(getSourceIconUrl("local")); 
        }
        
        eb.setFooter("Source: Local File", getSourceIconUrl("local"));
    }

    /**
     * Determines if a track is from Gensokyo Radio
     * @param track The track to check
     * @return True if the track is from Gensokyo Radio
     */
    public boolean isGensokyoRadioTrack(AudioTrack track) {
        if (track == null || track.getInfo() == null) return false;

        RequestMetadata rm = extractRequestMetadata(track);
        if (rm != null && rm.hasRadioData()) {
            String stationPath = rm.getRadioStationPath();
            if (stationPath != null && stationPath.toLowerCase().contains("gensokyo")) {
                return true;
            }
        }

        String trackUrl = track.getInfo().uri;
        if (trackUrl != null && trackUrl.contains("gensokyoradio.net")) {
            return true;
        }

        String stationPath = RadioCmd.lastStationPaths.get(stringGuildId);
        if (stationPath != null && track.equals(audioPlayer.getPlayingTrack())) {
            return stationPath.toLowerCase().contains("gensokyo");
        }

        return false;
    }

    /**
     * Format seconds to mm:ss format
     * @param seconds The seconds to format
     * @return The formatted time string
     */
    private String formatTime(Integer seconds) {
        if (seconds == null) return "0:00";
        
        int minutes = seconds / 60;
        int remainingSeconds = seconds % 60;
        
        return String.format("%d:%02d", minutes, remainingSeconds);
    }

    /**
     * Gets the ID of the guild this handler is for
     * @return The guild ID
     */
    public long getGuildId() {
        return guildId;
    }
}
