/*
 * Copyright 2018-2020 Cosgy Dev
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
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import dev.cosgy.jmusicbot.settings.RepeatMode;
import dev.cosgy.jmusicbot.slashcommands.music.RadioCmd;
import dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.io.IOException;
import java.net.URL;
import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

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
        OTHER       // Any other source
    }

    /**
     * Determines the type of the currently playing track
     * @param track The track to check
     * @return The TrackType of the track
     */
    public TrackType getTrackType(AudioTrack track) {
        if (track == null) return null;
        
        // Most reliable way: check RequestMetadata first
        RequestMetadata rm = track.getUserData(RequestMetadata.class);
        
        // Check for Spotify data in RequestMetadata
        if (rm != null && rm.hasSpotifyData()) {
            return TrackType.SPOTIFY;
        }
        
        // Check for Radio data in RequestMetadata
        if (rm != null && rm.hasRadioData()) {
            return TrackType.RADIO;
        }
        
        // Secondary checks based on track properties
        
        // Check if it's a radio station based on URL patterns
        if (track.getInfo().isStream && isRadioTrack(track)) {
            return TrackType.RADIO;
        }
        
        // Check for YouTube
        if (track instanceof YoutubeAudioTrack || 
            (track.getSourceManager() != null && 
             track.getSourceManager().getSourceName().equalsIgnoreCase("youtube"))) {
            return TrackType.YOUTUBE;
        }
        
        // Check for SoundCloud
        if (track.getSourceManager() != null && 
            track.getSourceManager().getSourceName().equalsIgnoreCase("soundcloud")) {
            return TrackType.SOUNDCLOUD;
        }
        
        // Everything else
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
        RequestMetadata rm = track.getUserData(RequestMetadata.class);
        if (rm != null && rm.hasRadioData()) {
            return true;
        }
        
        // Fallback: check RadioCmd's static maps
        String stationPath = getRadioStationPath(track);
        if (stationPath != null) {
            return true;
        }
        
        // For tracks coming from a stream source that could be a radio station
        if (track.getInfo().isStream) {
            // Check if the URL matches common radio station patterns
            String trackUrl = track.getInfo().uri;
            if (trackUrl != null && 
                (trackUrl.contains("onlineradiobox.com") || 
                 trackUrl.contains("listen.") || 
                 trackUrl.contains(".stream") ||
                 trackUrl.contains("ice") ||  
                 trackUrl.contains(".mp3") || 
                 trackUrl.contains(".aac") ||
                 trackUrl.contains("radio"))) {
                return true;
            }
        }
        
        // If we're not sure, assume it's not a radio track
        return false;
    }

    /**
     * Gets the station path from the currently playing radio track
     * @param track The current track
     * @return The station path or null if not found
     */
    public String getCurrentRadioStationPath(AudioTrack track) {
        if (track == null) return null;
        
        // Check if the RequestMetadata has radio info
        RequestMetadata rm = track.getUserData(RequestMetadata.class);
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
        String stationPath = RadioCmd.lastStationPaths.get(stringGuildId);
        if (stationPath != null) {
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
    
    /**
     * Gets the radio logo URL for the current track
     * @param track The track to check
     * @return The logo URL or null if not found
     */
    public String getRadioLogoUrl(AudioTrack track) {
        if (track == null) return null;
        
        // Check if the RequestMetadata has radio info
        RequestMetadata rm = track.getUserData(RequestMetadata.class);
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
        RequestMetadata rm = track.getUserData(RequestMetadata.class);
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
        RequestMetadata rm = track.getUserData(RequestMetadata.class);
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
        RequestMetadata rm = audioPlayer.getPlayingTrack().getUserData(RequestMetadata.class);
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
    private String getSourceIconUrl(String sourceName) {
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
        RequestMetadata rm = track.getUserData(RequestMetadata.class);
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
        queue.clear();
        defaultQueue.clear();
        audioPlayer.stopTrack();

        Guild guild = guild(manager.getBot().getJDA());
        Bot.updatePlayStatus(guild, guild.getSelfMember(), PlayStatus.STOPPED);
    }

    /**
     * Checks if music is currently playing
     * @param jda The JDA instance
     * @return True if music is playing
     */
    public boolean isMusicPlaying(JDA jda) {
        return guild(jda).getSelfMember().getVoiceState().inAudioChannel() && audioPlayer.getPlayingTrack() != null;
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
        
        RequestMetadata rm = audioPlayer.getPlayingTrack().getUserData(RequestMetadata.class);
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
        RepeatMode repeatMode = manager.getBot().getSettingsManager().getSettings(guildId).getRepeatMode();

        // Track statistics update
        updateTrackStatistics(track);
        
        // If this was a stream, stop ICY metadata monitoring
        if (track != null && track.getInfo().isStream) {
            manager.getBot().getIcyMetadataHandler().stopMonitoring(stringGuildId);
        }
        
        // Handle YouTube livestreams that ended prematurely
        if (track != null && track.getInfo().isStream && track.getInfo().uri.contains("youtube.com") 
            && (endReason == AudioTrackEndReason.FINISHED || endReason == AudioTrackEndReason.LOAD_FAILED)) {
            // For YouTube livestreams, automatically replay the same stream
            player.playTrack(track.makeClone());
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
                RequestMetadata rm = track.getUserData(RequestMetadata.class);
                
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
        // Clear votes and notify NowPlayingHandler
        votes.clear();
        
        // Debugging output to help identify track type issues
        TrackType trackType = getTrackType(track);
        
        // Record stream start time for statistics
        if (track.getInfo().isStream) {
            streamStartTime = System.currentTimeMillis();
            
            // Start monitoring ICY metadata for streams that weren't added via /radio command
            // This will automatically check if it's a stream and if it doesn't already have radio data
            manager.getBot().getIcyMetadataHandler().startMonitoring(stringGuildId, track);
        }

        // Make sure all track data is properly initialized and cleaned up from previous tracks
        handleTrackTypeData(track);
        
        // Update NowPlayingHandler with the current track
        manager.getBot().getNowplayingHandler().onTrackUpdate(guildId, track, this);

        // Update bot status
        Guild guild = guild(manager.getBot().getJDA());
        Bot.updatePlayStatus(guild, guild.getSelfMember(), PlayStatus.PLAYING);
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
            RequestMetadata rm = track.getUserData(RequestMetadata.class);
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
        
        RequestMetadata rm = track.getUserData(RequestMetadata.class);
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
        
        RequestMetadata rm = track.getUserData(RequestMetadata.class);
        return rm != null && rm.hasSpotifyData();
    }

    // Formatting
    public MessageCreateData getNowPlaying(JDA jda) throws Exception {
        if (isMusicPlaying(jda)) {
            Guild guild = guild(jda);
            AudioTrack track = audioPlayer.getPlayingTrack();
            MessageCreateBuilder mb = new MessageCreateBuilder();
            mb.addContent(FormatUtil.filter(manager.getBot().getConfig().getSuccess() + " **" + guild.getSelfMember().getVoiceState().getChannel().getAsMention() + "** is playing now..."));
            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(guild.getSelfMember().getColor());
            RequestMetadata rm = getRequestMetadata();
            
            // Double-check the track type to ensure we're displaying the correct format
            // This verification is important for when track types change in the queue
            TrackType trackType = getTrackType(track);
            
            // Set author (requester) for all track types
            setRequesterAuthor(eb, rm, guild);
            
            // Different display based on track type
            switch (trackType) {
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
                case OTHER:
                default:
                    buildDefaultEmbed(eb, track, rm);
                    break;
            }
            
            return mb.addEmbeds(eb.build()).build();
        } else return null;
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
        eb.setTitle("~ Now Playing Spotify Track ~");
        
        // Get detailed Spotify track info
        SpotifyCmd.SpotifyTrackInfo trackInfo = getSpotifyTrackInfo();
        
        if (trackInfo != null) {
            // Rich description with track details
            StringBuilder description = new StringBuilder();
            description.append("**Track:** ").append(trackInfo.trackName);
            description.append("\n**Album:** ").append(trackInfo.albumName);
            description.append("\n**Artist:** ").append(trackInfo.artistName);
            
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
     * Builds an embed for radio station tracks
     * @param eb The EmbedBuilder to populate
     * @param track The current track
     * @param rm The RequestMetadata
     */
    private void buildRadioEmbed(EmbedBuilder eb, AudioTrack track, RequestMetadata rm) {
        eb.setTitle("~ Now Playing Radio ~");
        
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
        String stationUrl = "https://onlineradiobox.com/" + stationPath;
        description.append("**Station:** [").append(stationName).append("](").append(stationUrl).append(")");
        
        // Try to get current track info from RadioCmd
        RadioCmd.TrackInfo trackInfo = getRadioTrackInfo(track);
        
        if (trackInfo != null && !trackInfo.getFormattedTitle().isEmpty() && 
            !trackInfo.getFormattedTitle().equals("Unknown")) {
            description.append("\n\n**Now playing:**\n").append(trackInfo.getFormattedTitle());
            
            // Add album art if available and npimages is enabled
            if (manager.getBot().getConfig().useNPImages() && 
                trackInfo.imageUrl != null && 
                !trackInfo.imageUrl.isEmpty()) {
                eb.setImage(trackInfo.imageUrl);
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
        eb.setFooter("Source: Online Radio Box", getSourceIconUrl("radio"));
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
        
        // Add station name
        description.append("**Station:** ").append(metadata.getStationName());
        
        // Add genre if available
        if (metadata.getStationGenre() != null && !metadata.getStationGenre().isEmpty()) {
            description.append("\n**Genre:** ").append(metadata.getStationGenre());
        }
        
        // Add current track info if available
        if (metadata.getCurrentTrack() != null && !metadata.getCurrentTrack().isEmpty()) {
            description.append("\n\n**Now playing:**\n");
            
            // If we have both artist and title
            if (!metadata.getArtist().isEmpty() && !metadata.getTitle().isEmpty()) {
                description.append("**")
                          .append(metadata.getTitle())
                          .append("**\n")
                          .append(metadata.getArtist());
            } else {
                // Just use the raw current track info
                description.append(metadata.getCurrentTrack());
            }
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
        
        // Set footer to show source
        eb.setFooter("Source: Stream with ICY Metadata", getSourceIconUrl("stream"));
    }
    
    /**
     * Builds an embed for YouTube tracks
     * @param eb The EmbedBuilder to populate
     * @param track The current track
     * @param rm The RequestMetadata
     */
    private void buildYoutubeEmbed(EmbedBuilder eb, AudioTrack track, RequestMetadata rm) {
                try {
                    eb.setTitle(track.getInfo().title, track.getInfo().uri);
                } catch (Exception e) {
                    eb.setTitle(track.getInfo().title);
                }

        // Add YouTube thumbnail
                if (manager.getBot().getConfig().useNPImages()) {
            // Extract video ID
            String videoId = extractYoutubeVideoId(track);
            if (videoId != null) {
                // Use highest quality thumbnail
                eb.setThumbnail("https://img.youtube.com/vi/" + videoId + "/maxresdefault.jpg");
            }
        }
        
        // Build description with author and progress bar
        StringBuilder description = new StringBuilder();
        if (track.getInfo().author != null && !track.getInfo().author.isEmpty()) {
            description.append("**Channel:** ").append(track.getInfo().author).append("\n\n");
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
        try {
            eb.setTitle(track.getInfo().title, track.getInfo().uri);
        } catch (Exception e) {
            eb.setTitle(track.getInfo().title);
        }
        
        // Add SoundCloud thumbnail
        if (manager.getBot().getConfig().useNPImages() && 
            track.getInfo().artworkUrl != null && 
            !track.getInfo().artworkUrl.isEmpty()) {
            eb.setThumbnail(track.getInfo().artworkUrl);
        }
        
        // Build description with author and progress bar
        StringBuilder description = new StringBuilder();
        if (track.getInfo().author != null && !track.getInfo().author.isEmpty()) {
            description.append("**Artist:** ").append(track.getInfo().author).append("\n\n");
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
        eb.setTitle("~ Now Playing Stream ~");
        
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
                try {
                    eb.setTitle(track.getInfo().title, track.getInfo().uri);
                } catch (Exception e) {
                    eb.setTitle(track.getInfo().title);
                }

        // Handle thumbnail
                if (manager.getBot().getConfig().useNPImages()) {
                    if (track.getInfo().artworkUrl != null && !track.getInfo().artworkUrl.isEmpty()) {
                        eb.setThumbnail(track.getInfo().artworkUrl);
            }
        }
        
        // Determine source platform
                String sourcePlatform = "Unknown Source";
        if (track.getSourceManager() != null) {
            sourcePlatform = track.getSourceManager().getSourceName();
            
            // Capitalize first letter and make friendly names
            if (sourcePlatform.equalsIgnoreCase("http")) {
                    sourcePlatform = "HTTP Stream";
            } else if (sourcePlatform.equalsIgnoreCase("local")) {
                    sourcePlatform = "Local File";
                } else {
                sourcePlatform = sourcePlatform.substring(0, 1).toUpperCase() + 
                                sourcePlatform.substring(1).toLowerCase();
            }
        }
        
        // Build description with author and progress bar
        StringBuilder descBuilder = new StringBuilder();
        if (track.getInfo().author != null && !track.getInfo().author.isEmpty()) {
            descBuilder.append("**Artist:** ").append(track.getInfo().author).append("\n\n");
        }
        
        appendProgressBar(descBuilder, track);
        eb.setDescription(descBuilder.toString());

                // Set footer to show source platform with favicon
        eb.setFooter("Source: " + sourcePlatform, getSourceIconUrl(track.getSourceManager().getSourceName().toLowerCase()));
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
                        
                        return "**" + stationName + " Radio** [" + (userid == 0 ? "📻" : "<@" + userid + ">") + "]" +
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
                            return "**" + stationName + "** - *" + currentTrack + "* [" + 
                                   (userid == 0 ? "📻" : "<@" + userid + ">") + "]" +
                                   "\n" + (audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI) + " " +
                                   "[LIVE] " +
                                   FormatUtil.volumeIcon(audioPlayer.getVolume());
                        } else {
                            return "**" + stationName + "** [" + (userid == 0 ? "📻" : "<@" + userid + ">") + "]" +
                                   "\n" + (audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI) + " " +
                                   "[LIVE] " +
                                   FormatUtil.volumeIcon(audioPlayer.getVolume());
                        }
                    }
                    break;
            }
            
            // Default format for all other tracks
            String title = track.getInfo().title;
            if (title == null || title.equals("Unknown title"))
                title = track.getInfo().uri;
                
            if (track.getInfo().isStream) {
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
}
