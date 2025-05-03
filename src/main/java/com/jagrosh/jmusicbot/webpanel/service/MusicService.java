/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.service;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.IcyMetadataHandler;
import com.jagrosh.jmusicbot.audio.PlayerManager;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.webpanel.model.Guild;
import com.jagrosh.jmusicbot.webpanel.model.MusicStatus;
import com.jagrosh.jmusicbot.webpanel.model.QueueTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Service
public class MusicService {

    private final Bot bot;
    private String selectedGuildId; // Currently selected guild ID

    public MusicService(Bot bot) {
        this.bot = bot;
        
        // Initialize the selectedGuildId safely
        try {
            // Wait a short time for JDA to initialize if needed
            if (bot.getJDA() == null) {
                System.out.println("Web Panel: Waiting for JDA to initialize...");
                Thread.sleep(5000); // Wait 5 seconds for JDA to initialize
            }
            
            // Set the first guild as the default selected guild if available
            if (bot.getJDA() != null && !bot.getJDA().getGuilds().isEmpty()) {
                this.selectedGuildId = bot.getJDA().getGuilds().get(0).getId();
                System.out.println("Web Panel: Selected guild ID: " + this.selectedGuildId);
            } else {
                this.selectedGuildId = null;
                System.out.println("Web Panel: No guilds available for selection");
            }
        } catch (Exception e) {
            this.selectedGuildId = null;
            System.out.println("Web Panel: Error setting initial guild: " + e.getMessage());
            e.printStackTrace();
        }
    }
    
    /**
     * Gets a list of all guilds that the bot is connected to
     */
    public List<Guild> getGuilds() {
        // Check if JDA is null or not fully initialized
        if (bot.getJDA() == null) {
            System.out.println("Web Panel: JDA is null, cannot get guilds list");
            return Collections.emptyList();
        }
        
        // Check if the bot is connected to any guilds
        if (bot.getJDA().getGuilds().isEmpty()) {
            System.out.println("Web Panel: Bot is not connected to any guilds");
            return Collections.emptyList();
        }
        
        try {
            return bot.getJDA().getGuilds().stream()
                    .map(g -> {
                        AudioHandler audioHandler = (AudioHandler) g.getAudioManager().getSendingHandler();
                        boolean hasConnectedAudio = audioHandler != null;
                        
                        // Get the guild icon URL if available
                        String iconUrl = g.getIconUrl();
                        
                        return new Guild(
                                g.getId(),
                                g.getName(),
                                hasConnectedAudio,
                                iconUrl
                        );
                    })
                    .collect(Collectors.toList());
        } catch (Exception e) {
            System.out.println("Web Panel: Error getting guilds: " + e.getMessage());
            e.printStackTrace();
            return Collections.emptyList();
        }
    }
    
    /**
     * Sets the currently selected guild
     */
    public boolean setSelectedGuild(String guildId) {
        // Check if JDA is initialized
        if (bot.getJDA() == null) {
            return false;
        }
        
        // Check if the guild exists
        net.dv8tion.jda.api.entities.Guild guild = bot.getJDA().getGuildById(guildId);
        if (guild != null) {
            this.selectedGuildId = guildId;
            return true;
        } else {
            return false;
        }
    }
    
    /**
     * Gets the currently selected guild
     */
    public String getSelectedGuildId() {
        // Handle the case where no guild is selected
        if (selectedGuildId == null) {
            // Try to select a guild if one is available
            try {
                if (bot.getJDA() != null && !bot.getJDA().getGuilds().isEmpty()) {
                    selectedGuildId = bot.getJDA().getGuilds().get(0).getId();
                    System.out.println("Web Panel: Auto-selected guild ID: " + selectedGuildId);
                    return selectedGuildId;
                }
            } catch (Exception e) {
                System.out.println("Web Panel: Error auto-selecting guild: " + e.getMessage());
            }
        }
        return selectedGuildId;
    }
    
    /**
     * Gets the audio handler for the currently selected guild
     */
    private Optional<AudioHandler> getAudioHandler() {
        if (selectedGuildId == null) {
            return Optional.empty();
        }
        
        net.dv8tion.jda.api.entities.Guild guild = bot.getJDA().getGuildById(selectedGuildId);
        if (guild == null) {
            return Optional.empty();
        }
        
        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        return Optional.ofNullable(handler);
    }

    /**
     * Gets the current music status from the selected guild
     */
    public MusicStatus getCurrentStatus() {
        Optional<AudioHandler> handler = getAudioHandler();

        if (handler.isPresent() && handler.get().getPlayer().getPlayingTrack() != null) {
            AudioHandler audioHandler = handler.get();
            AudioTrack track = audioHandler.getPlayer().getPlayingTrack();
            RequestMetadata rm = track.getUserData(RequestMetadata.class);
            
            // Get source type and thumbnail
            String sourceType = "Unknown";
            String source = "Unknown";
            String thumbnailUrl = "";
            
            // Determine track type using AudioHandler methods
            AudioHandler.TrackType trackType = audioHandler.getTrackType(track);
            
            // Set values based on track type
            switch (trackType) {
                case YOUTUBE:
                    sourceType = "YouTube";
                    source = "YouTube";
                    
                    // Extract video ID for thumbnail
                    String videoId = null;
                    if (track.getInfo().uri.contains("youtu.be/")) {
                        videoId = track.getInfo().uri.substring(track.getInfo().uri.lastIndexOf("/") + 1);
                        if (videoId.contains("?")) {
                            videoId = videoId.substring(0, videoId.indexOf("?"));
                        }
                    } else if (track.getInfo().uri.contains("watch?v=")) {
                        videoId = track.getInfo().uri.substring(track.getInfo().uri.indexOf("watch?v=") + 8);
                        if (videoId.contains("&")) {
                            videoId = videoId.substring(0, videoId.indexOf("&"));
                        }
                    }
                    
                    if (videoId != null) {
                        thumbnailUrl = "https://img.youtube.com/vi/" + videoId + "/maxresdefault.jpg";
                    }
                    break;
                    
                case SPOTIFY:
                    sourceType = "Spotify";
                    source = "Spotify";
                    
                    // Get Spotify track info from AudioHandler
                    dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.SpotifyTrackInfo spotifyInfo = 
                        audioHandler.getSpotifyTrackInfo();
                    
                    if (spotifyInfo != null && spotifyInfo.albumImageUrl != null && !spotifyInfo.albumImageUrl.isEmpty()) {
                        thumbnailUrl = spotifyInfo.albumImageUrl;
                    } else {
                        thumbnailUrl = "https://www.freepnglogos.com/uploads/spotify-logo-png/file-spotify-logo-png-4.png";
                    }
                    break;
                    
                case RADIO:
                    sourceType = "Radio";
                    
                    // Get radio info from AudioHandler
                    String stationName = audioHandler.getRadioStationName(track);
                    String logoUrl = audioHandler.getRadioLogoUrl(track);
                    
                    source = stationName != null ? stationName : "Radio Stream";
                    
                    // For radio streams, use the station logo
                    if (logoUrl != null && !logoUrl.isEmpty()) {
                        thumbnailUrl = logoUrl;
                    } else {
                        thumbnailUrl = "https://static.semrush.com/power-pages/media/favicons/onlineradiobox-com-favicon-7dd1a612.png";
                    }
                    
                    // Special case for Gensokyo Radio
                    if (audioHandler.isGensokyoRadioTrack(track)) {
                        sourceType = "Gensokyo Radio";
                        source = "Gensokyo Radio";
                        thumbnailUrl = "https://stream.gensokyoradio.net/images/logo.png";
                        
                        // Try to get album art from Gensokyo Agent
                        try {
                            dev.cosgy.agent.objects.ResultSet info = dev.cosgy.agent.GensokyoInfoAgent.getInfo();
                            if (info != null && info.getMisc() != null && info.getMisc().getFullAlbumArtUrl() != null) {
                                String albumArtUrl = info.getMisc().getFullAlbumArtUrl();
                                if (!albumArtUrl.isEmpty()) {
                                    thumbnailUrl = albumArtUrl;
                                }
                            }
                        } catch (Exception e) {
                            // Fall back to logo if there's an error
                        }
                    }
                    break;
                    
                case SOUNDCLOUD:
                    sourceType = "SoundCloud";
                    source = "SoundCloud";
                    
                    // Use the artwork URL if available
                    if (track.getInfo().artworkUrl != null && !track.getInfo().artworkUrl.isEmpty()) {
                        thumbnailUrl = track.getInfo().artworkUrl;
                    } else {
                        thumbnailUrl = "https://developers.soundcloud.com/assets/logo_big_white-65c2b096da68dd533db18b5a2bcfbcce.png";
                    }
                    break;
                    
                case LOCAL:
                    sourceType = "Local";
                    source = "Local File";
                    
                    // Check if we have local file metadata in RequestMetadata
                    if (rm != null && rm.hasLocalFileData()) {
                        if (rm.getLocalFileAlbum() != null && !rm.getLocalFileAlbum().isEmpty()) {
                            source = rm.getLocalFileAlbum();
                        }
                    }
                    
                    // Get artwork from local files cache if available
                    String artworkPath = bot.getLocalArtworkUrl(track.getInfo().identifier);
                    if (artworkPath != null && !artworkPath.isEmpty()) {
                        java.io.File artworkFile = new java.io.File(artworkPath);
                        if (artworkFile.exists() && artworkFile.isFile()) {
                            // Transform the local path to a URL accessible from the web
                            thumbnailUrl = "/api/artwork/" + track.getInfo().identifier;
                        }
                    }
                    
                    // Fall back to generic icon if no artwork
                    if (thumbnailUrl.isEmpty()) {
                        thumbnailUrl = "https://cdn-icons-png.flaticon.com/512/4725/4725478.png";
                    }
                    break;
                    
                default: // OTHER or unknown types
                    // Check if it's a stream with ICY metadata
                    IcyMetadataHandler.StreamMetadata metadata = bot.getIcyMetadataHandler().getMetadata(selectedGuildId);
                    if (metadata != null && !metadata.hasFailed()) {
                        sourceType = "Stream";
                        source = metadata.getStationName() != null ? metadata.getStationName() : "Web Stream";
                        
                        // Use station logo or album art if available
                        if (metadata.getStationLogo() != null && !metadata.getStationLogo().isEmpty()) {
                            thumbnailUrl = metadata.getStationLogo();
                        } else if (metadata.getAlbumArt() != null && !metadata.getAlbumArt().isEmpty()) {
                            thumbnailUrl = metadata.getAlbumArt();
                        } else {
                            thumbnailUrl = "https://cdn-icons-png.flaticon.com/128/11796/11796884.png";
                        }
                    } else if (track.getInfo().isStream) {
                        sourceType = "Stream";
                        source = "Web Stream";
                        thumbnailUrl = "https://cdn-icons-png.flaticon.com/128/11796/11796884.png";
                    } else {
                        // Try to determine source from track info
                        if (track.getSourceManager() != null) {
                            sourceType = track.getSourceManager().getSourceName();
                            
                            // Make proper capitalization and friendly names
                            if (sourceType.equalsIgnoreCase("http")) {
                                sourceType = "Stream";
                                source = "Web Stream";
                            } else {
                                sourceType = sourceType.substring(0, 1).toUpperCase() + sourceType.substring(1).toLowerCase();
                                source = sourceType;
                            }
                            
                            // Use AudioHandler's icon cache
                            thumbnailUrl = audioHandler.getSourceIconUrl(sourceType.toLowerCase());
                        }
                    }
                    break;
            }
            
            // Get requester info
            String requesterName = "Unknown";
            String requesterAvatar = "";
            if (rm != null && rm.user != null) {
                requesterName = rm.user.username;
                requesterAvatar = rm.user.avatar;
            }
            
            // Get volume
            int volume = audioHandler.getPlayer().getVolume();
            
            return new MusicStatus(
                    track.getInfo().title,
                    track.getInfo().author,
                    track.getInfo().uri,
                    thumbnailUrl,
                    track.getPosition(),
                    track.getDuration(),
                    audioHandler.getPlayer().getPlayingTrack() != null,
                    audioHandler.getPlayer().isPaused(),
                    !audioHandler.getQueue().isEmpty(),
                    audioHandler.getQueue().size(),
                    source,
                    requesterName,
                    requesterAvatar,
                    volume,
                    sourceType
            );
        }
        
        // Return empty status if no track is playing
        return new MusicStatus(
                "No track playing",
                "",
                "",
                "",
                0,
                0,
                false,
                false,
                false,
                0,
                "",
                "",
                "",
                100,
                ""
        );
    }
    
    /**
     * Gets the identifier for a queued track 
     */
    private String getRequesterInfo(QueuedTrack queuedTrack) {
        try {
            if (queuedTrack != null && queuedTrack.getTrack() != null && queuedTrack.getTrack().getUserData(RequestMetadata.class) != null) {
                return String.valueOf(queuedTrack.getIdentifier());
            }
        } catch (Exception e) {
            // Ignore
        }
        return "Unknown";
    }

    /**
     * Gets the current queue from the selected guild
     */
    public List<QueueTrack> getQueue() {
        Optional<AudioHandler> handler = getAudioHandler();
        
        if (handler.isEmpty()) {
            return Collections.emptyList();
        }
        
        return handler.get().getQueue().getList().stream()
                .map(queuedTrack -> {
                    AudioTrack track = queuedTrack.getTrack();
                    
                    // Get metadata for source type, requester, etc.
                    RequestMetadata rm = track.getUserData(RequestMetadata.class);
                    
                    // Get source type and thumbnail
                    String sourceType = "Unknown";
                    String source = "Unknown";
                    String thumbnailUrl = "";
                    
                    // Determine track type using AudioHandler methods
                    AudioHandler.TrackType trackType = handler.get().getTrackType(track);
                    
                    // For queue items, simplify thumbnail handling
                    switch (trackType) {
                        case YOUTUBE:
                            sourceType = "YouTube";
                            source = "YouTube";
                            
                            // Extract video ID for thumbnail
                            String videoId = null;
                            if (track.getInfo().uri.contains("youtu.be/")) {
                                videoId = track.getInfo().uri.substring(track.getInfo().uri.lastIndexOf("/") + 1);
                                if (videoId.contains("?")) {
                                    videoId = videoId.substring(0, videoId.indexOf("?"));
                                }
                            } else if (track.getInfo().uri.contains("watch?v=")) {
                                videoId = track.getInfo().uri.substring(track.getInfo().uri.indexOf("watch?v=") + 8);
                                if (videoId.contains("&")) {
                                    videoId = videoId.substring(0, videoId.indexOf("&"));
                                }
                            }
                            
                            if (videoId != null) {
                                thumbnailUrl = "https://img.youtube.com/vi/" + videoId + "/mqdefault.jpg";
                            }
                            break;
                            
                        case SPOTIFY:
                            sourceType = "Spotify";
                            source = "Spotify";
                            
                            // For queue items, just use the Spotify logo
                            thumbnailUrl = "https://www.freepnglogos.com/uploads/spotify-logo-png/file-spotify-logo-png-4.png";
                            
                            // Try to get actual album art from SpotifyCmd
                            dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.SpotifyTrackInfo spotifyInfo = 
                                handler.get().getSpotifyTrackInfo();
                            if (spotifyInfo != null && spotifyInfo.albumImageUrl != null && !spotifyInfo.albumImageUrl.isEmpty()) {
                                thumbnailUrl = spotifyInfo.albumImageUrl;
                            }
                            break;
                            
                        case RADIO:
                            sourceType = "Radio";
                            
                            // For radio streams in queue, only show the logo since content changes
                            String stationName = handler.get().getRadioStationName(track);
                            String logoUrl = handler.get().getRadioLogoUrl(track);
                            
                            source = stationName != null ? stationName : "Radio Stream";
                            
                            if (logoUrl != null && !logoUrl.isEmpty()) {
                                thumbnailUrl = logoUrl;
                            } else {
                                thumbnailUrl = "https://static.semrush.com/power-pages/media/favicons/onlineradiobox-com-favicon-7dd1a612.png";
                            }
                            
                            // Special case for Gensokyo Radio
                            if (handler.get().isGensokyoRadioTrack(track)) {
                                sourceType = "Gensokyo Radio";
                                source = "Gensokyo Radio";
                                thumbnailUrl = "https://stream.gensokyoradio.net/images/logo.png";
                            }
                            break;
                            
                        case SOUNDCLOUD:
                            sourceType = "SoundCloud";
                            source = "SoundCloud";
                            
                            // Use the artwork URL if available
                            if (track.getInfo().artworkUrl != null && !track.getInfo().artworkUrl.isEmpty()) {
                                thumbnailUrl = track.getInfo().artworkUrl;
                            } else {
                                thumbnailUrl = "https://developers.soundcloud.com/assets/logo_big_white-65c2b096da68dd533db18b5a2bcfbcce.png";
                            }
                            break;
                            
                        case LOCAL:
                            sourceType = "Local";
                            source = "Local File";
                            
                            // For local files in queue, use the generic icon
                            thumbnailUrl = "https://cdn-icons-png.flaticon.com/512/4725/4725478.png";
                            break;
                            
                        default: // OTHER or unknown types
                            if (track.getInfo().isStream) {
                                sourceType = "Stream";
                                source = "Web Stream";
                                thumbnailUrl = "https://cdn-icons-png.flaticon.com/128/11796/11796884.png";
                            } else {
                                // Try to determine source from track info
                                if (track.getSourceManager() != null) {
                                    sourceType = track.getSourceManager().getSourceName();
                                    
                                    // Make proper capitalization and friendly names
                                    if (sourceType.equalsIgnoreCase("http")) {
                                        sourceType = "Stream";
                                        source = "Web Stream";
                                    } else {
                                        sourceType = sourceType.substring(0, 1).toUpperCase() + sourceType.substring(1).toLowerCase();
                                        source = sourceType;
                                    }
                                    
                                    // Use AudioHandler's icon cache
                                    thumbnailUrl = handler.get().getSourceIconUrl(sourceType.toLowerCase());
                                }
                            }
                            break;
                    }
                    
                    // Get requester info
                    String requesterName = "Unknown";
                    String requesterAvatar = "";
                    if (rm != null && rm.user != null) {
                        requesterName = rm.user.username;
                        requesterAvatar = rm.user.avatar;
                    }
                    
                    return new QueueTrack(
                            track.getInfo().title,
                            track.getInfo().author,
                            track.getInfo().uri,
                            track.getDuration(),
                            thumbnailUrl,
                            requesterName,
                            requesterAvatar,
                            source,
                            sourceType
                    );
                })
                .collect(Collectors.toList());
    }
    
    /**
     * Utility method to access the player manager
     */
    private PlayerManager getAudioManager() {
        return bot.getPlayerManager();
    }
    
    /**
     * Adds a track to the queue by URL
     * @param url The URL of the track to add
     * @return A CompletableFuture that will be completed with a result message
     */
    public CompletableFuture<String> addTrackByUrl(String url) {
        CompletableFuture<String> result = new CompletableFuture<>();
        
        Optional<AudioHandler> handler = getAudioHandler();
        
        if (handler.isEmpty()) {
            result.complete("No audio handler found for the selected guild");
            return result;
        }
        
        AudioHandler audioHandler = handler.get();
        
        getAudioManager().loadItem(url, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                // Use a RequestMetadata object with 0 as the owner ID to indicate it came from the web panel
                RequestMetadata rm = new RequestMetadata(null);
                track.setUserData(rm);
                
                // Add track to queue
                audioHandler.addTrack(new QueuedTrack(track, rm));
                result.complete("Added track: " + track.getInfo().title);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (playlist.getTracks().isEmpty()) {
                    result.complete("Playlist is empty");
                    return;
                }
                
                int count = 0;
                
                // If it's a search result, just add the first track
                if (playlist.isSearchResult()) {
                    AudioTrack track = playlist.getTracks().get(0);
                    RequestMetadata rm = new RequestMetadata(null);
                    track.setUserData(rm);
                    
                    audioHandler.addTrack(new QueuedTrack(track, rm));
                    result.complete("Added track: " + track.getInfo().title);
                    return;
                }
                
                // Add all tracks from the playlist
                for (AudioTrack track : playlist.getTracks()) {
                    RequestMetadata rm = new RequestMetadata(null);
                    track.setUserData(rm);
                    
                    audioHandler.addTrack(new QueuedTrack(track, rm));
                    count++;
                }
                
                result.complete("Added " + count + " tracks from playlist");
            }

            @Override
            public void noMatches() {
                result.complete("No matches found for the URL");
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                result.complete("Failed to load track: " + exception.getMessage());
            }
        });
        
        return result;
    }
    
    /**
     * Basic controls for playback
     */
    public boolean playTrack() {
        Optional<AudioHandler> handler = getAudioHandler();
                
        if (handler.isPresent()) {
            AudioHandler audioHandler = handler.get();
            audioHandler.getPlayer().setPaused(false);
            return true;
        }
        return false;
    }
    
    public boolean pauseTrack() {
        Optional<AudioHandler> handler = getAudioHandler();
                
        if (handler.isPresent()) {
            AudioHandler audioHandler = handler.get();
            audioHandler.getPlayer().setPaused(true);
            return true;
        }
        return false;
    }
    
    public boolean skipTrack() {
        Optional<AudioHandler> handler = getAudioHandler();
                
        if (handler.isPresent()) {
            AudioHandler audioHandler = handler.get();
            audioHandler.getPlayer().stopTrack();
            return true;
        }
        return false;
    }
    
    public boolean stopTrack() {
        Optional<AudioHandler> handler = getAudioHandler();
                
        if (handler.isPresent()) {
            AudioHandler audioHandler = handler.get();
            audioHandler.getPlayer().stopTrack();
            return true;
        }
        return false;
    }
    
    /**
     * Seeks to a position in the currently playing track
     * 
     * @param position Position to seek to in milliseconds
     * @return true if successful, false otherwise including if the track isn't seekable
     */
    public boolean seekTrack(long position) {
        Optional<AudioHandler> handler = getAudioHandler();
        
        if (handler.isPresent()) {
            AudioHandler audioHandler = handler.get();
            AudioTrack track = audioHandler.getPlayer().getPlayingTrack();
            
            if (track != null && track.isSeekable()) {
                try {
                    // Verify the position is within the track's duration
                    if (position >= 0 && position <= track.getDuration()) {
                        track.setPosition(position);
                        return true;
                    }
                } catch (Exception e) {
                    System.out.println("Web Panel: Error seeking track: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
        return false;
    }
    
    /**
     * Removes a track from the queue by its index
     */
    public boolean removeTrack(int index) {
        Optional<AudioHandler> handler = getAudioHandler();
        if (handler.isPresent()) {
            try {
                if (index >= 0 && index < handler.get().getQueue().size()) {
                    handler.get().getQueue().remove(index);
                    return true;
                }
            } catch (Exception e) {
                System.out.println("Web Panel: Error removing track: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return false;
    }
    
    /**
     * Moves a track from one position to another in the queue
     *
     * @param fromIndex The index of the track to move (1-based for compatibility with the MoveTrackCmd)
     * @param toIndex The index to move the track to (1-based for compatibility with the MoveTrackCmd)
     * @return true if successful, false otherwise
     */
    public boolean moveTrack(int fromIndex, int toIndex) {
        Optional<AudioHandler> handler = getAudioHandler();
        if (handler.isPresent()) {
            try {
                // Adjust to 0-based indices for internal use
                int from = fromIndex - 1;
                int to = toIndex - 1;
                
                // Validate indices
                if (from < 0 || to < 0 || from >= handler.get().getQueue().size() || to >= handler.get().getQueue().size()) {
                    return false;
                }
                
                if (from == to) {
                    return true; // No change needed
                }
                
                // Move the track
                handler.get().getQueue().moveItem(from, to);
                return true;
            } catch (Exception e) {
                System.out.println("Web Panel: Error moving track: " + e.getMessage());
                e.printStackTrace();
            }
        }
        return false;
    }
} 