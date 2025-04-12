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
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
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

    /**
     * Determines if a track is a radio station track
     * @param track The track to check
     * @return True if the track is a radio station
     */
    private boolean isRadioTrack(AudioTrack track) {
        if (track == null) return false;
        
        // For tracks coming from a stream source that could be a radio station
        if (track.getInfo().isStream) {
            // Check if the URL matches the radio station pattern
            String trackUrl = track.getInfo().uri;
            if (trackUrl != null && 
                (trackUrl.contains("onlineradiobox.com") || 
                 trackUrl.contains("listen.") || 
                 trackUrl.contains(".stream") ||
                 trackUrl.contains("ice") ||  
                 trackUrl.contains(".mp3") || 
                 trackUrl.contains(".aac"))) {
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
    private String getCurrentRadioStationPath(AudioTrack track) {
        if (!isRadioTrack(track)) return null;
        
        // Check if this is from OnlineRadioBox
        String trackUrl = track.getInfo().uri;
        if (trackUrl.contains("onlineradiobox.com")) {
            // Extract the path from orb URLs like: https://onlineradiobox.com/uk/capital/
            try {
                java.net.URL url = new java.net.URL(trackUrl);
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
            for (Map.Entry<String, String> entry : dev.cosgy.jmusicbot.slashcommands.music.RadioCmd.getStreamUrlMappings().entrySet()) {
                if (trackUrl.equals(entry.getValue())) {
                    return entry.getKey();
                }
            }
        }
        
        return null;
    }

    /**
     * Determines if a track was loaded through the Spotify command
     * @param track The track to check
     * @return True if the track is from Spotify
     */
    private boolean isSpotifyTrack(AudioTrack track) {
        if (track == null) return false;
        
        // First check if the source manager directly indicates this is a Spotify track
        if (track.getSourceManager() != null && 
            track.getSourceManager().getSourceName().equalsIgnoreCase("spotify")) {
            return true;
        }
        
        // Fix for the /nowplaying display issue:
        // Previously, we only checked if there was ANY Spotify track in the guild's history,
        // which caused the /nowplaying command to display Spotify formatting for non-Spotify tracks.
        // Now we verify that THIS EXACT track matches the stored Spotify track info.
        
        // Get the Spotify track ID for this guild
        String trackId = dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.lastTrackIds.get(stringGuildId);
        if (trackId == null) return false;
        
        // Check if THIS track is the one stored in lastTrackIds
        // Get the track info for comparison
        dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.SpotifyTrackInfo trackInfo = 
            dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.getTrackInfo(stringGuildId);
        
        if (trackInfo != null) {
            // Compare title - this is the most reliable way to check if this is the same track
            // First remove any potential " (Official Video)" or similar suffixes
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
            
            // Return true only if this track matches the Spotify information
            return titleMatch || artistMatch;
        }
        
        return false;
    }

    protected AudioHandler(PlayerManager manager, Guild guild, AudioPlayer player) {
        this.manager = manager;
        this.audioPlayer = player;
        this.guildId = guild.getIdLong();
        this.stringGuildId = guild.getId();
    }

    public int addTrackToFront(QueuedTrack qtrack) {
        if (audioPlayer.getPlayingTrack() == null) {
            audioPlayer.playTrack(qtrack.getTrack());
            return -1;
        } else {
            queue.addAt(0, qtrack);
            return 0;
        }
    }

    public int addTrack(QueuedTrack qtrack) {
        if (audioPlayer.getPlayingTrack() == null) {
            audioPlayer.playTrack(qtrack.getTrack());
            return -1;
        } else {
            boolean toEnt = manager.getBot().getSettingsManager().getSettings(guildId).isForceToEndQue();
            return queue.add(qtrack, toEnt);
        }
    }

    public void addTrackIfRepeat(AudioTrack track) {
        // If in repeat mode, add the track to the end of the queue
        RepeatMode mode = manager.getBot().getSettingsManager().getSettings(guildId).getRepeatMode();
        boolean toEnt = manager.getBot().getSettingsManager().getSettings(guildId).isForceToEndQue();
        if (mode != RepeatMode.OFF) {
            queue.add(new QueuedTrack(track.makeClone(), track.getUserData(RequestMetadata.class)), toEnt);
        }
    }

    public FairQueue<QueuedTrack> getQueue() {
        return queue;
    }

    public void stopAndClear() {
        queue.clear();
        defaultQueue.clear();
        audioPlayer.stopTrack();
        //current = null;

        Guild guild = guild(manager.getBot().getJDA());
        Bot.updatePlayStatus(guild, guild.getSelfMember(), PlayStatus.STOPPED);
    }

    public boolean isMusicPlaying(JDA jda) {
        return guild(jda).getSelfMember().getVoiceState().inAudioChannel() && audioPlayer.getPlayingTrack() != null;
    }

    public Set<String> getVotes() {
        return votes;
    }

    public AudioPlayer getPlayer() {
        return audioPlayer;
    }

    public RequestMetadata getRequestMetadata() {
        if (audioPlayer.getPlayingTrack() == null)
            return RequestMetadata.EMPTY;
        RequestMetadata rm = audioPlayer.getPlayingTrack().getUserData(RequestMetadata.class);
        return rm == null ? RequestMetadata.EMPTY : rm;
    }

    /**
     * Update stats when a track is skipped
     */
    public void updateStatsOnSkip() {
        AudioTrack track = audioPlayer.getPlayingTrack();
        if (track != null) {
            Settings settings = manager.getBot().getSettingsManager().getSettings(guildId);
            settings.incrementSongsPlayed();
            // If it's a stream, calculate the actual listening time until skip
            if (track.getInfo().isStream) {
                long streamDuration = System.currentTimeMillis() - streamStartTime;
                settings.addPlayTime(streamDuration);
            } else {
                // For a normal track, count the time played
                settings.addPlayTime(track.getPosition());
            }
        }
    }

    public boolean playFromDefault() {
        if (!defaultQueue.isEmpty()) {
            audioPlayer.playTrack(defaultQueue.remove(0));
            return true;
        }
        Settings settings = manager.getBot().getSettingsManager().getSettings(guildId);
        if (settings == null || settings.getDefaultPlaylist() == null)
            return false;

        Playlist pl = manager.getBot().getPlaylistLoader().getPlaylist(stringGuildId, settings.getDefaultPlaylist());
        if (pl == null || pl.getItems().isEmpty())
            return false;
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

        // If it's a YouTube livestream that ended prematurely, replay it
        if (track != null && track.getInfo().isStream && track.getInfo().uri.contains("youtube.com") 
            && (endReason == AudioTrackEndReason.FINISHED || endReason == AudioTrackEndReason.LOAD_FAILED)) {
            // For YouTube livestreams, automatically replay the same stream
            
            // Update stats before replaying
            if (track.getInfo().isStream) {
                Settings settings = manager.getBot().getSettingsManager().getSettings(guildId);
                long streamDuration = System.currentTimeMillis() - streamStartTime;
                settings.addPlayTime(streamDuration);
            }
            
            player.playTrack(track.makeClone());
            return;
        }

        // If the song finishes playing normally and repeat mode is enabled (!OFF), it will be re-added to the queue.
        if (endReason == AudioTrackEndReason.FINISHED) {
            // Update stats when a track finishes playing
            if (track != null) {
                Settings settings = manager.getBot().getSettingsManager().getSettings(guildId);
                settings.incrementSongsPlayed();
                // If it's a stream, calculate the actual listening time
                if (track.getInfo().isStream) {
                    long streamDuration = System.currentTimeMillis() - streamStartTime;
                    settings.addPlayTime(streamDuration);
                } else {
                    settings.addPlayTime(track.getDuration());
                }
            }
            
            if (repeatMode != RepeatMode.OFF) {
                // in RepeatMode.ALL
                if (repeatMode == RepeatMode.ALL) {
                    queue.add(new QueuedTrack(track.makeClone(), track.getUserData(RequestMetadata.class)));

                    // in RepeatMode.SINGLE
                } else if (repeatMode == RepeatMode.SINGLE) {
                    queue.addAt(0, new QueuedTrack(track.makeClone(), track.getUserData(RequestMetadata.class)));
                }
            }
        }

        if (queue.isEmpty()) {
            if (!playFromDefault()) {
                manager.getBot().getNowplayingHandler().onTrackUpdate(guildId, null, this);
                if (!manager.getBot().getConfig().getStay()) manager.getBot().closeAudioConnection(guildId);

                player.setPaused(false);

                Guild guild = guild(manager.getBot().getJDA());
                Bot.updatePlayStatus(guild, guild.getSelfMember(), PlayStatus.STOPPED);
            }
        } else {
            QueuedTrack qt = queue.pull();
            player.playTrack(qt.getTrack());
        }
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        votes.clear();
        manager.getBot().getNowplayingHandler().onTrackUpdate(guildId, track, this);

        // If it's a stream, record when it starts
        if (track.getInfo().isStream) {
            streamStartTime = System.currentTimeMillis();
        }

        // Check if this is not a radio track, clear radio data if needed
        if (!isRadioTrack(track)) {
            // Clear radio data from RadioCmd maps when a non-radio track starts playing
            if (dev.cosgy.jmusicbot.slashcommands.music.RadioCmd.lastStationPaths.containsKey(stringGuildId)) {
                dev.cosgy.jmusicbot.slashcommands.music.RadioCmd.lastStationPaths.remove(stringGuildId);
                dev.cosgy.jmusicbot.slashcommands.music.RadioCmd.lastStationLogos.remove(stringGuildId);
            }
        }
        
        // Fix for the Spotify display issue:
        // Previously, we only cleared Spotify data if the source wasn't "spotify",
        // but now we use our improved isSpotifyTrack method to check if this exact track
        // matches our stored Spotify track information.
        // This ensures we only keep Spotify formatting data for tracks that are actually from Spotify.
        if (!isSpotifyTrack(track)) {
            dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.lastTrackIds.remove(stringGuildId);
        }

        Guild guild = guild(manager.getBot().getJDA());
        Bot.updatePlayStatus(guild, guild.getSelfMember(), PlayStatus.PLAYING);
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
            
            // Different display based on track type (Spotify, Radio, Regular)
            // First, verify this is actually a Spotify track using our improved isSpotifyTrack method
            if (isSpotifyTrack(track)) {
                // Handle Spotify tracks
                String trackId = dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.lastTrackIds.get(stringGuildId);
                if (trackId != null) {
                    // Get track details from SpotifyCmd's stored information
                    dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.SpotifyTrackInfo trackInfo = 
                        dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.getTrackInfo(stringGuildId);
                    
                    if (trackInfo != null) {
                        eb.setTitle("~ Now Playing Spotify Track ~");
                        
                        // Set user who requested the track
                        if (rm.getOwner() != 0L) {
                            User u = guild.getJDA().getUserById(rm.user.id);
                            if (u == null)
                                eb.setAuthor(rm.user.username, null, rm.user.avatar);
                            else
                                eb.setAuthor(u.getName(), null, u.getEffectiveAvatarUrl());
                        }
                        
                        // Rich description with track details
                        StringBuilder description = new StringBuilder();
                        description.append("**Track:** ").append(trackInfo.trackName);
                        description.append("\n**Album:** ").append(trackInfo.albumName);
                        description.append("\n**Artist:** ").append(trackInfo.artistName);
                        
                        // Add progress bar
                        double progress = (double) audioPlayer.getPlayingTrack().getPosition() / track.getDuration();
                        description.append("\n\n");
                        description.append((audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI))
                                .append(" ")
                                .append(FormatUtil.progressBar(progress))
                                .append(" `[")
                                .append(FormatUtil.formatTime(track.getPosition()))
                                .append("/")
                                .append(FormatUtil.formatTime(track.getDuration()))
                                .append("]` ")
                                .append(FormatUtil.volumeIcon(audioPlayer.getVolume()));
                        
                        eb.setDescription(description.toString());
                        
                        // Add album art as image if npimages is enabled
                        if (manager.getBot().getConfig().useNPImages() && 
                            trackInfo.albumImageUrl != null && 
                            !trackInfo.albumImageUrl.isEmpty()) {
                            eb.setImage(trackInfo.albumImageUrl);
                        }
                        
                        // Set color and footer
                        eb.setColor(trackInfo.color);
                        eb.setFooter("Source: Spotify", "https://images.seeklogo.com/logo-png/26/3/spotify-2015-logo-png_seeklogo-266802.png");
                    }
                }
            }
            else if (isRadioTrack(track)) {
                // Handle Radio tracks - always get current information based on playing track
                String stationPath = getCurrentRadioStationPath(track);
                
                if (stationPath != null) {
                    // Get station logo from path (if available)
                    String logoUrl = dev.cosgy.jmusicbot.slashcommands.music.RadioCmd.lastStationLogos.getOrDefault(stationPath, null);
                    
                    // Radio-style embed
                    eb.setTitle("~ Now Playing Radio ~");
                    
                    // Set user who requested the track
                    if (rm.getOwner() != 0L) {
                        User u = guild.getJDA().getUserById(rm.user.id);
                        if (u == null)
                            eb.setAuthor(rm.user.username, null, rm.user.avatar);
                        else
                            eb.setAuthor(u.getName(), null, u.getEffectiveAvatarUrl());
                    }
                    
                    // Get station title from track info
                    String stationTitle = track.getInfo().title;
                    if (stationTitle.contains(" | ")) {
                        stationTitle = stationTitle.substring(stationTitle.lastIndexOf(" | ") + 3);
                    }
                    if (stationTitle.endsWith(" Radio")) {
                        stationTitle = stationTitle.substring(0, stationTitle.length() - 6);
                    }
                    
                    // Description with station and track info
                    StringBuilder description = new StringBuilder();
                    String stationUrl = "https://onlineradiobox.com/" + stationPath;
                    description.append("**Station:** [").append(stationTitle).append("](").append(stationUrl).append(")");
                    
                    // Try to get current track info from RadioCmd
                    try {
                        // This requires creating an instance to access non-static methods
                        dev.cosgy.jmusicbot.slashcommands.music.RadioCmd radioCmd = 
                            new dev.cosgy.jmusicbot.slashcommands.music.RadioCmd(manager.getBot());
                        
                        // Always fetch fresh track info from the station
                        dev.cosgy.jmusicbot.slashcommands.music.RadioCmd.TrackInfo trackInfo = 
                            radioCmd.getDetailedTrackInfo(stationPath);
                        
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
                    } catch (Exception e) {
                        // If we can't get track info, continue without it
                        System.out.println("Error getting radio track info: " + e.getMessage());
                    }
                    
                    // Add play status for streams
                    description.append("\n\n");
                    
                    eb.setDescription(description.toString());
                    
                    // Add station logo as thumbnail if npimages is enabled
                    if (manager.getBot().getConfig().useNPImages() && 
                        logoUrl != null && 
                        !logoUrl.isEmpty()) {
                        eb.setThumbnail(logoUrl);
                    }
                    
                    // Set footer to show source
                    eb.setFooter("Source: Online Radio Box", "https://static.semrush.com/power-pages/media/favicons/onlineradiobox-com-favicon-7dd1a612.png");
                } else {
                    // Generic radio/stream display if we can't identify the station
                    eb.setTitle("~ Now Playing Stream ~");
                    
                    if (rm.getOwner() != 0L) {
                        User u = guild.getJDA().getUserById(rm.user.id);
                        if (u == null)
                            eb.setAuthor(rm.user.username, null, rm.user.avatar);
                        else
                            eb.setAuthor(u.getName(), null, u.getEffectiveAvatarUrl());
                    }
                    
                    // Add basic stream information
                    StringBuilder description = new StringBuilder();
                    description.append("**Stream:** ").append(track.getInfo().title);
                    
                    // Add play status for streams
                    description.append("\n\n");
                    description.append((audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI))
                            .append(" ")
                            .append(FormatUtil.progressBar(-1)) // -1 for infinite streams
                            .append(" `[LIVE]` ")
                            .append(FormatUtil.volumeIcon(audioPlayer.getVolume()));
                    
                    eb.setDescription(description.toString());
                    eb.setFooter("Source: Stream", "https://cdn-icons-png.flaticon.com/512/2305/2305955.png");
                }
            }
            else if (!track.getInfo().uri.matches(".*stream.gensokyoradio.net/.*")) {
                // Regular tracks
                if (rm.getOwner() != 0L) {
                    User u = guild.getJDA().getUserById(rm.user.id);
                    if (u == null)
                        eb.setAuthor(rm.user.username, null, rm.user.avatar);
                    else
                        eb.setAuthor(u.getName(), null, u.getEffectiveAvatarUrl());
                }
                try {
                    eb.setTitle(track.getInfo().title, track.getInfo().uri);
                } catch (Exception e) {
                    eb.setTitle(track.getInfo().title);
                }

                // Improved thumbnail handling for all track types
                if (manager.getBot().getConfig().useNPImages()) {
                    // First try to use artworkUrl if it exists (works for most platforms including SoundCloud)
                    if (track.getInfo().artworkUrl != null && !track.getInfo().artworkUrl.isEmpty()) {
                        eb.setThumbnail(track.getInfo().artworkUrl);
                    }
                    // Special handling for YouTube tracks
                    else if (track instanceof YoutubeAudioTrack || 
                            (track.getSourceManager() != null && track.getSourceManager().getSourceName().equalsIgnoreCase("youtube"))) {
                        // Extract the video ID
                        String videoId = track.getIdentifier();
                        
                        // If the identifier is a full URL, extract the video ID from it
                        if (videoId.contains("?v=")) {
                            videoId = videoId.substring(videoId.indexOf("?v=") + 3);
                            if (videoId.contains("&")) {
                                videoId = videoId.substring(0, videoId.indexOf("&"));
                            }
                        }
                        
                        // Use the highest quality thumbnail available
                        eb.setThumbnail("https://img.youtube.com/vi/" + videoId + "/maxresdefault.jpg");
                    }
                    // Special handling for Spotify tracks - use track URI as identifier
                    else if (track.getSourceManager() != null && track.getSourceManager().getSourceName().equalsIgnoreCase("spotify")) {
                        // For Spotify, we rely on Lavaplayer to provide artworkUrl, but log debugging info
                        System.out.println("Spotify track detected but no artwork URL found: " + track.getInfo().title);
                    }
                }

                // Determine source platform from track information
                String sourcePlatform = "Unknown Source";
                if (track instanceof YoutubeAudioTrack) {
                    sourcePlatform = "YouTube";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("soundcloud")) {
                    sourcePlatform = "SoundCloud";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("bandcamp")) {
                    sourcePlatform = "Bandcamp";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("twitch")) {
                    sourcePlatform = "Twitch";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("vimeo")) {
                    sourcePlatform = "Vimeo";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("http")) {
                    sourcePlatform = "HTTP Stream";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("local")) {
                    sourcePlatform = "Local File";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("beam")) {
                    sourcePlatform = "Beam";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("niconico")) {
                    sourcePlatform = "Niconico";
                } else {
                    sourcePlatform = track.getSourceManager().getSourceName();
                }

                // Set footer to show source platform
                String faviconUrl = "";
                if (track instanceof YoutubeAudioTrack || sourcePlatform.equals("YouTube")) {
                    faviconUrl = "https://www.fsi.us.com/wp-content/uploads/2019/04/YouTube-Logo.png";
                } else if (sourcePlatform.equals("SoundCloud")) {
                    faviconUrl = "https://www.shareicon.net/data/128x128/2015/09/17/102358_soundcloud_512x512.png";
                } else if (sourcePlatform.equals("Bandcamp")) {
                    faviconUrl = "https://images.seeklogo.com/logo-png/52/3/bandcamp-logo-png_seeklogo-528569.png?v=1957857986033129792";
                } else if (sourcePlatform.equals("Twitch")) {
                    faviconUrl = "https://seeklogo.com/images/T/twitch-new-logo-BAB7E776B9-seeklogo.com.png";
                } else if (sourcePlatform.equals("Vimeo")) {
                    faviconUrl = "https://pluspng.com/img-png/vimeo-logo-png-vimeo-color-icon-vimeo-video-social-png-and-vector-vimeo-logo-840x859.png";
                } else if (sourcePlatform.equals("HTTP Stream")) {
                    faviconUrl = "https://cdn-icons-png.flaticon.com/512/1384/1384061.png";
                } else if (sourcePlatform.equals("Local File")) {
                    faviconUrl = "https://cdn-icons-png.flaticon.com/512/1384/1384061.png";
                } else if (sourcePlatform.equals("Niconico")) {
                    faviconUrl = "http://images.shoutwiki.com/sanrio/d/d2/Niconico_logo.png";
                } else {
                    faviconUrl = "https://cdn-icons-png.flaticon.com/512/1384/1384061.png"; // Default music icon
                }

                // Set footer to show source platform with favicon
                eb.setFooter("Source: " + sourcePlatform, faviconUrl);

                double progress = (double) audioPlayer.getPlayingTrack().getPosition() / track.getDuration();
                StringBuilder descBuilder = new StringBuilder();
                
                // Add artist information if available
                if (track.getInfo().author != null && !track.getInfo().author.isEmpty()) {
                    descBuilder.append("**Artist:** ").append(track.getInfo().author).append("\n\n");
                }
                
                // Add progress bar and other information
                descBuilder.append((audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI))
                        .append(" ")
                        .append(FormatUtil.progressBar(progress))
                        .append(" `[")
                        .append(FormatUtil.formatTime(track.getPosition()))
                        .append("/")
                        .append(FormatUtil.formatTime(track.getDuration()))
                        .append("]` ")
                        .append(FormatUtil.volumeIcon(audioPlayer.getVolume()));
                
                eb.setDescription(descBuilder.toString());

            } else {
                // Gensokyo Radio tracks
                if (rm.getOwner() != 0L) {
                    User u = guild.getJDA().getUserById(rm.user.id);
                    if (u == null)
                        eb.setAuthor(rm.user.username, null, rm.user.avatar);
                    else
                        eb.setAuthor(u.getName(), null, u.getEffectiveAvatarUrl());
                }
                try {
                    eb.setTitle(track.getInfo().title, track.getInfo().uri);
                } catch (Exception e) {
                    eb.setTitle(track.getInfo().title);
                }

                // Improved thumbnail handling for all track types
                if (manager.getBot().getConfig().useNPImages()) {
                    // First try to use artworkUrl if it exists (works for most platforms including SoundCloud)
                    if (track.getInfo().artworkUrl != null && !track.getInfo().artworkUrl.isEmpty()) {
                        eb.setThumbnail(track.getInfo().artworkUrl);
                    }
                    // Special handling for YouTube tracks
                    else if (track instanceof YoutubeAudioTrack || 
                            (track.getSourceManager() != null && track.getSourceManager().getSourceName().equalsIgnoreCase("youtube"))) {
                        // Extract the video ID
                        String videoId = track.getIdentifier();
                        
                        // If the identifier is a full URL, extract the video ID from it
                        if (videoId.contains("?v=")) {
                            videoId = videoId.substring(videoId.indexOf("?v=") + 3);
                            if (videoId.contains("&")) {
                                videoId = videoId.substring(0, videoId.indexOf("&"));
                            }
                        }
                        
                        // Use the highest quality thumbnail available
                        eb.setThumbnail("https://img.youtube.com/vi/" + videoId + "/maxresdefault.jpg");
                    }
                    // Special handling for Spotify tracks - use track URI as identifier
                    else if (track.getSourceManager() != null && track.getSourceManager().getSourceName().equalsIgnoreCase("spotify")) {
                        // For Spotify, we rely on Lavaplayer to provide artworkUrl, but log debugging info
                        System.out.println("Spotify track detected but no artwork URL found: " + track.getInfo().title);
                    }
                }

                // Determine source platform from track information
                String sourcePlatform = "Unknown Source";
                if (track instanceof YoutubeAudioTrack) {
                    sourcePlatform = "YouTube";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("soundcloud")) {
                    sourcePlatform = "SoundCloud";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("bandcamp")) {
                    sourcePlatform = "Bandcamp";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("twitch")) {
                    sourcePlatform = "Twitch";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("vimeo")) {
                    sourcePlatform = "Vimeo";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("http")) {
                    sourcePlatform = "HTTP Stream";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("local")) {
                    sourcePlatform = "Local File";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("beam")) {
                    sourcePlatform = "Beam";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("niconico")) {
                    sourcePlatform = "Niconico";
                } else {
                    sourcePlatform = track.getSourceManager().getSourceName();
                }

                // Set footer to show source platform
                String faviconUrl = "";
                if (track instanceof YoutubeAudioTrack || sourcePlatform.equals("YouTube")) {
                    faviconUrl = "https://www.youtube.com/s/desktop/6ee70b2c/img/favicon.ico";
                } else if (sourcePlatform.equals("SoundCloud")) {
                    faviconUrl = "https://a-v2.sndcdn.com/assets/images/sc-icons/favicon-2cadd14b.ico";
                } else if (sourcePlatform.equals("Bandcamp")) {
                    faviconUrl = "https://s4.bcbits.com/img/favicon/favicon-32x32.png";
                } else if (sourcePlatform.equals("Twitch")) {
                    faviconUrl = "https://static.twitchcdn.net/assets/favicon-32-e29e246c157142c94346.png";
                } else if (sourcePlatform.equals("Vimeo")) {
                    faviconUrl = "https://vimeo.com/favicon.ico";
                } else if (sourcePlatform.equals("HTTP Stream")) {
                    faviconUrl = "https://cdn-icons-png.flaticon.com/512/2305/2305955.png";
                } else if (sourcePlatform.equals("Local File")) {
                    faviconUrl = "https://cdn-icons-png.flaticon.com/512/2305/2305990.png";
                } else if (sourcePlatform.equals("Niconico")) {
                    faviconUrl = "https://www.nicovideo.jp/favicon.ico";
                } else {
                    faviconUrl = "https://cdn-icons-png.flaticon.com/512/1384/1384061.png"; // Default music icon
                }

                // Set footer to show source platform with favicon
                eb.setFooter("Source: " + sourcePlatform, faviconUrl);

                double progress = (double) audioPlayer.getPlayingTrack().getPosition() / track.getDuration();
                StringBuilder descBuilder = new StringBuilder();
                
                // Add progress bar and other information
                descBuilder.append((audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI))
                        .append(" ")
                        .append(FormatUtil.progressBar(progress))
                        .append(" `[")
                        .append(FormatUtil.formatTime(track.getPosition()))
                        .append("/")
                        .append(FormatUtil.formatTime(track.getDuration()))
                        .append("]` ")
                        .append(FormatUtil.volumeIcon(audioPlayer.getVolume()));
                
                eb.setDescription(descBuilder.toString());
            }

            return mb.addEmbeds(eb.build()).build();
        } else return null;
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

    public String getTopicFormat(JDA jda) {
        if (isMusicPlaying(jda)) {
            long userid = getRequestMetadata().getOwner();
            AudioTrack track = audioPlayer.getPlayingTrack();

            // Check if Gensokyo Radio is playing.
            if (track.getInfo().uri.matches(".*stream.gensokyoradio.net/.*")) {
                return "**Gensokyo Radio** [" + (userid == 0 ? "📻" : "<@" + userid + ">") + "]"
                        + "\n" + (audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI) + " "
                        + "[LIVE] "
                        + FormatUtil.volumeIcon(audioPlayer.getVolume());
            }

            String title = track.getInfo().title;
            if (title == null || title.equals("Unknown title"))
                title = track.getInfo().uri;
            return "**" + title + "** [" + (userid == 0 ? "📻" : "<@" + userid + ">") + "]"
                    + "\n" + (audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI) + " "
                    + "[" + FormatUtil.formatTime(track.getDuration()) + "] "
                    + FormatUtil.volumeIcon(audioPlayer.getVolume());
        } else return "No music is playing" + JMusicBot.STOP_EMOJI + " " + FormatUtil.volumeIcon(audioPlayer.getVolume());
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
