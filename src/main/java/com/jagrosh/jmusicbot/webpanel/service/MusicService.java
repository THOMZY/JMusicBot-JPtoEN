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
import com.jagrosh.jmusicbot.audio.YouTubeChapterManager;
import com.jagrosh.jmusicbot.utils.YouTubeChapterExtractor;
import com.jagrosh.jmusicbot.webpanel.model.Guild;
import com.jagrosh.jmusicbot.webpanel.model.MusicStatus;
import com.jagrosh.jmusicbot.webpanel.model.QueueTrack;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.cosgy.jmusicbot.util.YtDlpManager.FallbackPlatform;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import org.springframework.stereotype.Service;

import java.awt.Color;
import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.stream.Collectors;

import org.json.JSONObject;

import com.jagrosh.jmusicbot.PlayStatus;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;

import net.dv8tion.jda.api.EmbedBuilder;

@Service
public class MusicService {

    private final Bot bot;
    private final AvatarCacheService avatarCacheService;
    private String selectedGuildId; // Currently selected guild ID

    public MusicService(Bot bot, AvatarCacheService avatarCacheService) {
        this.bot = bot;
        this.avatarCacheService = avatarCacheService;
        
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
     * Safely retrieve RequestMetadata even when yt-dlp fallback wrapped it in TrackContext.
     */
    private RequestMetadata getRequestMetadata(AudioTrack track) {
        if (track == null) {
            return null;
        }

        Object userData = track.getUserData();
        if (userData instanceof RequestMetadata rm) {
            return rm;
        }

        if (userData != null) {
            // TrackContext is package-private; use reflection to read its userData field when present
            if ("com.jagrosh.jmusicbot.audio.PlayerManager$TrackContext".equals(userData.getClass().getName())) {
                try {
                    java.lang.reflect.Field userDataField = userData.getClass().getDeclaredField("userData");
                    userDataField.setAccessible(true);
                    Object inner = userDataField.get(userData);
                    if (inner instanceof RequestMetadata rm) {
                        return rm;
                    }
                } catch (Exception ignored) {
                    // fall through to final attempt
                }
            }
        }

        return track.getUserData(RequestMetadata.class);
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
            // Get music history to count activity per server
            Map<String, Long> activityCount = new HashMap<>();
            if (Bot.INSTANCE.getMusicHistory() != null) {
                List<com.jagrosh.jmusicbot.audio.MusicHistory.PlayRecord> history = 
                    Bot.INSTANCE.getMusicHistory().getHistory();
                
                // Count number of tracks played per guild
                activityCount = history.stream()
                    .collect(Collectors.groupingBy(
                        com.jagrosh.jmusicbot.audio.MusicHistory.PlayRecord::getGuildId,
                        Collectors.counting()
                    ));
            }
            
            final Map<String, Long> finalActivityCount = activityCount;
            
            List<Guild> guilds = bot.getJDA().getGuilds().stream()
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
            
            // Sort guilds by music activity (most active servers first), then by name as tiebreaker
            guilds.sort((g1, g2) -> {
                long activity1 = finalActivityCount.getOrDefault(g1.getId(), 0L);
                long activity2 = finalActivityCount.getOrDefault(g2.getId(), 0L);
                
                // Primary sort: by activity (descending)
                int activityCompare = Long.compare(activity2, activity1);
                if (activityCompare != 0) {
                    return activityCompare;
                }
                
                // Secondary sort: by name (alphabetically) for servers with same activity
                return g1.getName().compareToIgnoreCase(g2.getName());
            });
            
            return guilds;
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
        
        // Check if the bot is in a voice channel in the selected guild
        boolean inVoiceChannel = false;
        
        if (selectedGuildId != null && bot.getJDA() != null) {
            net.dv8tion.jda.api.entities.Guild guild = bot.getJDA().getGuildById(selectedGuildId);
            if (guild != null) {
                // Get the bot's voice state
                GuildVoiceState voiceState = guild.getSelfMember().getVoiceState();
                // Check if the bot is in a voice channel
                inVoiceChannel = voiceState != null && voiceState.inAudioChannel();
            }
        }
        
        // Variables for radio info that need broader scope
        String radioLogoUrl = null;
        String radioSongImageUrl = null;

        if (handler.isPresent() && handler.get().getPlayer().getPlayingTrack() != null) {
            AudioHandler audioHandler = handler.get();
            AudioTrack track = audioHandler.getPlayer().getPlayingTrack();
            AudioTrackInfo info = PlayerManager.getDisplayInfo(track);
            if (info == null) {
                info = track.getInfo();
            }
            RequestMetadata rm = getRequestMetadata(track);
            
            // Get source type and thumbnail
            String sourceType = "Unknown";
            String source = "Unknown";
            String thumbnailUrl = "";
            String sourceIconUrl = null;

            dev.cosgy.jmusicbot.util.YtDlpManager.YtDlpMetadata ytMeta = PlayerManager.getYtDlpMetadata(track);
            FallbackPlatform ytPlatform = PlayerManager.getYtDlpPlatform(track);
            boolean platformFromYt = false;
            boolean isStreamFlag = track.getInfo().isStream;

            if (ytPlatform != null && ytPlatform != FallbackPlatform.NONE) {
                switch (ytPlatform) {
                    case INSTAGRAM -> sourceType = source = "Instagram";
                    case TIKTOK -> sourceType = source = "TikTok";
                    case TWITTER -> sourceType = source = "Twitter";
                    case BILIBILI -> sourceType = source = "Bilibili";
                    case VIMEO -> sourceType = source = "Vimeo";
                    case TWITCH -> sourceType = source = "Twitch";
                    case SOUNDCLOUD -> sourceType = source = "SoundCloud";
                    case YOUTUBE -> sourceType = source = "YouTube";
                    default -> {
                        if (ytMeta != null && ytMeta.webpageUrl() != null) {
                            try {
                                java.net.URI uri = new java.net.URI(ytMeta.webpageUrl());
                                String host = uri.getHost();
                                if (host != null) {
                                    // Store the full domain for favicon before processing
                                    String fullDomain = host;
                                    
                                    host = host.startsWith("www.") ? host.substring(4) : host;
                                    int lastDot = host.lastIndexOf('.');
                                    if (lastDot > 0) {
                                        host = host.substring(0, lastDot);
                                    }
                                    if (!host.isEmpty()) {
                                        sourceType = source = host.substring(0, 1).toUpperCase() + host.substring(1);
                                        // Set custom favicon URL
                                        sourceIconUrl = "https://www.google.com/s2/favicons?domain=" + fullDomain + "&sz=64";
                                    }
                                }
                            } catch (Exception ignored) {}
                        }
                    }
                }

                if (ytMeta != null && ytMeta.thumbnailUrl() != null && !ytMeta.thumbnailUrl().isEmpty()) {
                    thumbnailUrl = ytMeta.thumbnailUrl();
                }

                if (!"Unknown".equals(sourceType)) {
                    platformFromYt = true;
                    isStreamFlag = false; // yt-dlp VODs should not be marked live
                }
            }

            // Variables for local file metadata
            String localAlbum = null;
            String localGenre = null;
            String localYear = null;
            
            // Determine track type using AudioHandler methods
            AudioHandler.TrackType trackType = audioHandler.getTrackType(track);
            
            // Set values based on track type
            switch (trackType) {
                case YOUTUBE:
                    if (!platformFromYt) {
                        sourceType = "YouTube";
                        source = "YouTube";
                    }
                    
                    // Extract video ID for thumbnail only if not already set by yt-dlp
                    if (thumbnailUrl.isEmpty()) {
                        String videoId = null;
                        if (info.uri.contains("youtu.be/")) {
                            videoId = info.uri.substring(info.uri.lastIndexOf("/") + 1);
                            if (videoId.contains("?")) {
                                videoId = videoId.substring(0, videoId.indexOf("?"));
                            }
                        } else if (info.uri.contains("watch?v=")) {
                            videoId = info.uri.substring(info.uri.indexOf("watch?v=") + 8);
                            if (videoId.contains("&")) {
                                videoId = videoId.substring(0, videoId.indexOf("&"));
                            }
                        }
                        
                        if (videoId != null) {
                            thumbnailUrl = "https://img.youtube.com/vi/" + videoId + "/mqdefault.jpg";
                        }
                    }
                    break;
                    
                case SPOTIFY:
                    if (!platformFromYt) {
                        sourceType = "Spotify";
                        source = "Spotify";
                    }
                    
                    // Get Spotify track ID from RequestMetadata
                    String spotifyTrackId = null;
                    RequestMetadata spotifyRm = getRequestMetadata(track);
                    if (spotifyRm != null && spotifyRm.hasSpotifyData()) {
                        spotifyTrackId = spotifyRm.getSpotifyTrackId();
                        
                        // If we have a Spotify track ID, try to get the album image
                        if (spotifyTrackId != null) {
                            // Check if we have album art information stored for this track ID
                            String albumUrl = dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.albumImageUrls.get(spotifyTrackId);
                            if (albumUrl != null && !albumUrl.isEmpty()) {
                                thumbnailUrl = albumUrl;
                            } else {
                                // Fallback to default Spotify logo
                                thumbnailUrl = "https://www.freepnglogos.com/uploads/spotify-logo-png/file-spotify-logo-png-4.png";
                            }
                        } else {
                            // Fallback to default Spotify logo
                            thumbnailUrl = "https://www.freepnglogos.com/uploads/spotify-logo-png/file-spotify-logo-png-4.png";
                        }
                    } else {
                        // Try to get current track's Spotify info if this is the playing track
                        dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.SpotifyTrackInfo spotifyInfo = 
                            audioHandler.getSpotifyTrackInfo();
                        if (spotifyInfo != null && spotifyInfo.albumImageUrl != null && !spotifyInfo.albumImageUrl.isEmpty()) {
                            thumbnailUrl = spotifyInfo.albumImageUrl;
                        } else {
                            // Fallback to default Spotify logo
                            thumbnailUrl = "https://www.freepnglogos.com/uploads/spotify-logo-png/file-spotify-logo-png-4.png";
                        }
                    }
                    break;
                    
                case RADIO:
                    isStreamFlag = true;
                    sourceType = "Radio";
                    
                    // Get radio info from AudioHandler
                    String stationName = audioHandler.getRadioStationName(track);
                    String logoUrl = audioHandler.getRadioLogoUrl(track);
                    String songImageUrl = null;
                    
                    // Try to get current song image for radio
                    dev.cosgy.jmusicbot.slashcommands.music.RadioCmd.TrackInfo radioInfo = 
                        audioHandler.getRadioTrackInfo(track);
                    if (radioInfo != null && radioInfo.imageUrl != null && !radioInfo.imageUrl.isEmpty()) {
                        songImageUrl = radioInfo.imageUrl;
                        thumbnailUrl = songImageUrl; // Use song image as main thumbnail
                    } else if (logoUrl != null && !logoUrl.isEmpty()) {
                        // Fallback to station logo if no song image
                        thumbnailUrl = logoUrl;
                    } else {
                        thumbnailUrl = "https://static.semrush.com/power-pages/media/favicons/onlineradiobox-com-favicon-7dd1a612.png";
                    }
                    
                    source = stationName != null ? stationName : "Radio Stream";
                    
                    // Save radio info for return value
                    radioLogoUrl = logoUrl;
                    radioSongImageUrl = songImageUrl;
                    
                    // Special case for Gensokyo Radio
                    if (audioHandler.isGensokyoRadioTrack(track)) {
                        sourceType = "Gensokyo Radio";
                        source = "Gensokyo Radio";
                        thumbnailUrl = "https://stream.gensokyoradio.net/images/logo.png";
                        
                        // Try to get album art from Gensokyo Agent
                        try {
                            dev.cosgy.agent.objects.ResultSet grInfo = dev.cosgy.agent.GensokyoInfoAgent.getInfo();
                            if (grInfo != null) {
                                // Get album art if available
                                if (grInfo.getMisc() != null && grInfo.getMisc().getFullAlbumArtUrl() != null) {
                                    String albumArtUrl = grInfo.getMisc().getFullAlbumArtUrl();
                                    if (!albumArtUrl.isEmpty()) {
                                        thumbnailUrl = albumArtUrl;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            // Fall back to logo if there's an error
                            System.out.println("Error fetching Gensokyo Radio album art: " + e.getMessage());
                        }
                    }
                    break;
                    
                case SOUNDCLOUD:
                    if (!platformFromYt) {
                        sourceType = "SoundCloud";
                        source = "SoundCloud";
                    }
                    
                    // Use the artwork URL if available
                    if (track.getInfo().artworkUrl != null && !track.getInfo().artworkUrl.isEmpty()) {
                        thumbnailUrl = track.getInfo().artworkUrl;
                    } else {
                        thumbnailUrl = "https://developers.soundcloud.com/assets/logo_big_white-65c2b096da68dd533db18b5a2bcfbcce.png";
                    }
                    break;
                    
                case LOCAL:
                    if (!platformFromYt) {
                        sourceType = "Local File";
                        source = "Local File";
                    }
                    
                    // Get local metadata from RequestMetadata or Bot's cache
                    if (rm != null && rm.hasLocalFileData()) {
                        localAlbum = rm.getLocalFileAlbum();
                        localGenre = rm.getLocalFileGenre();
                        localYear = rm.getLocalFileYear();
                        if (rm.getLocalFileArtworkHash() != null && !rm.getLocalFileArtworkHash().isEmpty()) {
                            thumbnailUrl = "/" + rm.getLocalFileArtworkHash(); // Prepend /
                        }
                    } else {
                        // Fallback to bot's cache if RM is not populated (should be less common now)
                        dev.cosgy.jmusicbot.util.LocalAudioMetadata.LocalTrackInfo cachedInfo = 
                            bot.getLocalMetadataCache().get(track.getInfo().identifier);
                        if (cachedInfo != null) {
                            localAlbum = cachedInfo.getAlbum();
                            localGenre = cachedInfo.getGenre();
                            localYear = cachedInfo.getYear();
                            if (cachedInfo.hasArtwork() && cachedInfo.getArtworkPath() != null) {
                                thumbnailUrl = "/" + cachedInfo.getArtworkPath(); // Prepend /
                            }
                        }
                    }
                    
                    // Default thumbnail if still none
                    if (thumbnailUrl == null || thumbnailUrl.isEmpty()) {
                        thumbnailUrl = "/images/default_local.png"; // Placeholder for default local
                    }
                    break;
                    
                default: // OTHER or unknown types
                    FallbackPlatform platform = PlayerManager.getYtDlpPlatform(track);
                    if (platform != null && platform != FallbackPlatform.NONE) {
                        if (sourceType.equals("Unknown")) { // Don't overwrite if already set by generic logic above
                            sourceType = platform.name().charAt(0) + platform.name().substring(1).toLowerCase();
                            source = sourceType;
                        }
                        if (info.artworkUrl != null && !info.artworkUrl.isEmpty()) {
                            thumbnailUrl = info.artworkUrl;
                        }
                    } else if (track.getInfo().isStream) { // Check if it's a stream with ICY metadata but NOT if it's ytdlp
                        sourceType = "Stream";
                        source = "Web Stream";
                        thumbnailUrl = "https://cdn-icons-png.flaticon.com/128/11796/11796884.png";
                    }
                    // Check if it's a stream with ICY metadata
                    if (sourceType.equals("Unknown") || sourceType.equals("Stream")) {
                        IcyMetadataHandler.StreamMetadata metadata = bot.getIcyMetadataHandler().getMetadata(selectedGuildId);
                        if (metadata != null && !metadata.hasFailed()) {
                            sourceType = "Stream";
                            source = metadata.getStationName() != null ? metadata.getStationName() : "Web Stream";
                            isStreamFlag = true;
                            
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
                            isStreamFlag = true;
                        } else {
                            if (track.getSourceManager() != null) {
                                sourceType = track.getSourceManager().getSourceName();
                                
                                if (sourceType.equalsIgnoreCase("http")) {
                                    sourceType = "Stream";
                                    source = "Web Stream";
                                } else {
                                    sourceType = sourceType.substring(0, 1).toUpperCase() + sourceType.substring(1).toLowerCase();
                                    source = sourceType;
                                }
                                
                                thumbnailUrl = audioHandler.getSourceIconUrl(sourceType.toLowerCase());
                            }
                        }
                    }
                    break;
            }
            
            // Get requester info
            String requesterName = "Unknown";
            String requesterAvatar = "";
            if (rm != null && rm.user != null) {
                requesterName = rm.user.username;
                // Use the cache service to get the avatar URL
                String cachedAvatar = avatarCacheService.getAvatarUrl(String.valueOf(rm.getOwner()));
                if (cachedAvatar != null) {
                    requesterAvatar = cachedAvatar;
                } else {
                    requesterAvatar = rm.user.avatar;
                }
            } else if (bot.getJDA() != null) {
                requesterName = bot.getJDA().getSelfUser().getName();
                requesterAvatar = bot.getJDA().getSelfUser().getEffectiveAvatarUrl();
            }
            
            // Get volume
            int volume = audioHandler.getPlayer().getVolume();
            
            // Prepare Spotify info if this is a Spotify track
            Map<String, Object> spotifyInfoMap = null;
            if (trackType == AudioHandler.TrackType.SPOTIFY) {
                dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.SpotifyTrackInfo spotInfo = 
                    audioHandler.getSpotifyTrackInfo();
                if (spotInfo != null) {
                    spotifyInfoMap = new HashMap<>();
                    spotifyInfoMap.put("trackId", spotInfo.trackId);
                    spotifyInfoMap.put("albumName", spotInfo.albumName);
                    spotifyInfoMap.put("albumImageUrl", spotInfo.albumImageUrl);
                    spotifyInfoMap.put("artistName", spotInfo.artistName);
                    spotifyInfoMap.put("releaseYear", spotInfo.releaseYear);
                }
            }
            // Add Gensokyo Radio metadata if applicable
            else if (audioHandler.isGensokyoRadioTrack(track)) {
                try {
                    dev.cosgy.agent.objects.ResultSet grInfo = dev.cosgy.agent.GensokyoInfoAgent.getInfo();
                    if (grInfo != null && grInfo.getSonginfo() != null) {
                        // Create gensokyoInfo map similar to spotifyInfo
                        spotifyInfoMap = new HashMap<>();
                        
                        // Add album, circle, and year information
                        if (grInfo.getSonginfo().getAlbum() != null && !grInfo.getSonginfo().getAlbum().isEmpty()) {
                            spotifyInfoMap.put("albumName", grInfo.getSonginfo().getAlbum());
                        }
                        
                        if (grInfo.getSonginfo().getCircle() != null && !grInfo.getSonginfo().getCircle().isEmpty()) {
                            spotifyInfoMap.put("circleName", grInfo.getSonginfo().getCircle());
                        }
                        
                        if (grInfo.getSonginfo().getYear() != null && !grInfo.getSonginfo().getYear().isEmpty()) {
                            spotifyInfoMap.put("releaseYear", grInfo.getSonginfo().getYear());
                        }
                        
                        // Add album art URL if available
                        if (grInfo.getMisc() != null && grInfo.getMisc().getFullAlbumArtUrl() != null) {
                            String albumArtUrl = grInfo.getMisc().getFullAlbumArtUrl();
                            if (!albumArtUrl.isEmpty()) {
                                spotifyInfoMap.put("albumImageUrl", albumArtUrl);
                            }
                        }
                        
                        // Add song time information if available
                        if (grInfo.getSongtimes() != null) {
                            if (grInfo.getSongtimes().getDuration() != null) {
                                spotifyInfoMap.put("gensokyoDuration", grInfo.getSongtimes().getDuration() * 1000); // Convert to milliseconds
                            }
                            
                            if (grInfo.getSongtimes().getPlayed() != null) {
                                spotifyInfoMap.put("gensokyoPlayed", grInfo.getSongtimes().getPlayed() * 1000); // Convert to milliseconds
                            }
                            
                            if (grInfo.getSongtimes().getRemaining() != null) {
                                spotifyInfoMap.put("gensokyoRemaining", grInfo.getSongtimes().getRemaining() * 1000); // Convert to milliseconds
                            }
                        }
                    }
                } catch (Exception e) {
                    System.out.println("Error fetching Gensokyo Radio metadata: " + e.getMessage());
                }
            }
            
            // Extract radio country and alias from the station path if this is a radio track
            String radioCountry = null;
            String radioAlias = null;
            if (trackType == AudioHandler.TrackType.RADIO) {
                // Get the station path from RequestMetadata first
                String stationPath = null;
                if (rm != null && rm.hasRadioData()) {
                    stationPath = rm.getRadioStationPath();
                }
                
                // Or try to get it from AudioHandler
                if (stationPath == null) {
                    stationPath = audioHandler.getCurrentRadioStationPath(track);
                }
                
                // Extract country and alias from the path (format: "country/alias")
                if (stationPath != null && stationPath.contains("/")) {
                    String[] parts = stationPath.split("/");
                    if (parts.length >= 2) {
                        radioCountry = parts[0];
                        radioAlias = parts[1];
                    }
                }
            }
            
                return new MusicStatus(
                    info.title,
                    info.author,
                    info.uri,
                    thumbnailUrl,
                    track.getPosition(),
                    info.length,
                    audioHandler.getPlayer().getPlayingTrack() != null,
                    audioHandler.getPlayer().isPaused(),
                    !audioHandler.getQueue().isEmpty(),
                    audioHandler.getQueue().size(),
                    source,
                    requesterName,
                    requesterAvatar,
                    volume,
                    sourceType,
                    inVoiceChannel,
                    spotifyInfoMap,
                    trackType == AudioHandler.TrackType.RADIO ? radioLogoUrl : null,
                    trackType == AudioHandler.TrackType.RADIO ? radioSongImageUrl : null,
                    radioCountry,
                    radioAlias,
                    localAlbum,
                    localGenre,
                    localYear,
                    isStreamFlag,
                    sourceIconUrl
            );
        }
        
        // Return empty status if no track is playing, but still include voice channel status
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
                "",
                inVoiceChannel,
                null, // spotifyInfo
                null, // radioLogoUrl
                null, // radioSongImageUrl
                null, // radioCountry
                null, // radioAlias
                null, // localAlbum
                null, // localGenre
                null, // localYear
                false, // isStream
                null // sourceIconUrl
        );
    }
    
    /**
     * Gets the identifier for a queued track 
     */
    private String getRequesterInfo(QueuedTrack queuedTrack) {
        try {
            if (queuedTrack != null && queuedTrack.getTrack() != null && getRequestMetadata(queuedTrack.getTrack()) != null) {
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
                    AudioTrackInfo info = PlayerManager.getDisplayInfo(track);
                    if (info == null) {
                        info = track.getInfo();
                    }
                    
                    // Get metadata for source type, requester, etc.
                    RequestMetadata rm = getRequestMetadata(track);
                    
                    // Get source type and thumbnail
                    String sourceType = "Unknown";
                    String source = "Unknown";
                    String thumbnailUrl = ""; // Initialize thumbnailUrl
                    String sourceIconUrl = null;

                    dev.cosgy.jmusicbot.util.YtDlpManager.YtDlpMetadata ytMeta = PlayerManager.getYtDlpMetadata(track);
                    FallbackPlatform ytPlatform = PlayerManager.getYtDlpPlatform(track);
                    boolean platformFromYt = false;

                    if (ytPlatform != null && ytPlatform != FallbackPlatform.NONE) {
                        switch (ytPlatform) {
                            case INSTAGRAM -> sourceType = source = "Instagram";
                            case TIKTOK -> sourceType = source = "TikTok";
                            case TWITTER -> sourceType = source = "Twitter";
                            case BILIBILI -> sourceType = source = "Bilibili";
                            case VIMEO -> sourceType = source = "Vimeo";
                            case TWITCH -> sourceType = source = "Twitch";
                            case SOUNDCLOUD -> sourceType = source = "SoundCloud";
                            case YOUTUBE -> sourceType = source = "YouTube";
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
                                                sourceType = source = host.substring(0, 1).toUpperCase() + host.substring(1);
                                                sourceIconUrl = "https://www.google.com/s2/favicons?domain=" + fullDomain + "&sz=64";
                                            }
                                        }
                                    } catch (Exception ignored) {}
                                }
                            }
                        }

                        if (ytMeta != null && ytMeta.thumbnailUrl() != null && !ytMeta.thumbnailUrl().isEmpty()) {
                            thumbnailUrl = ytMeta.thumbnailUrl();
                        }

                        if (!"Unknown".equals(sourceType)) {
                            platformFromYt = true;
                        }
                    }
                    
                    // Determine track type using AudioHandler methods
                    AudioHandler.TrackType trackType = handler.get().getTrackType(track);
                    
                    // For queue items, simplify thumbnail handling
                    switch (trackType) {
                        case YOUTUBE:
                            if (!platformFromYt) {
                                sourceType = "YouTube";
                                source = "YouTube";
                            }
                            
                            // Extract video ID for thumbnail only if not already set by yt-dlp
                            if (thumbnailUrl.isEmpty()) {
                                String videoId = null;
                                if (info.uri.contains("youtu.be/")) {
                                    videoId = info.uri.substring(info.uri.lastIndexOf("/") + 1);
                                    if (videoId.contains("?")) {
                                        videoId = videoId.substring(0, videoId.indexOf("?"));
                                    }
                                } else if (info.uri.contains("watch?v=")) {
                                    videoId = info.uri.substring(info.uri.indexOf("watch?v=") + 8);
                                    if (videoId.contains("&")) {
                                        videoId = videoId.substring(0, videoId.indexOf("&"));
                                    }
                                }
                                
                                if (videoId != null) {
                                    thumbnailUrl = "https://img.youtube.com/vi/" + videoId + "/mqdefault.jpg";
                                }
                            }
                            break;
                            
                        case SPOTIFY:
                            if (!platformFromYt) {
                                sourceType = "Spotify";
                                source = "Spotify";
                            }
                            
                            String spotifyTrackId = null;
                            RequestMetadata spotifyRm = getRequestMetadata(track);
                            if (spotifyRm != null && spotifyRm.hasSpotifyData()) {
                                spotifyTrackId = spotifyRm.getSpotifyTrackId();
                                
                                if (spotifyTrackId != null) {
                                    String albumUrl = dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.albumImageUrls.get(spotifyTrackId);
                                    if (albumUrl != null && !albumUrl.isEmpty()) {
                                        thumbnailUrl = albumUrl;
                                    } else {
                                        thumbnailUrl = "https://www.freepnglogos.com/uploads/spotify-logo-png/file-spotify-logo-png-4.png";
                                    }
                                } else {
                                    thumbnailUrl = "https://www.freepnglogos.com/uploads/spotify-logo-png/file-spotify-logo-png-4.png";
                                }
                            } else {
                                dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.SpotifyTrackInfo spotifyInfo = 
                                    handler.get().getSpotifyTrackInfo(); // This might be null if not the current playing track
                                if (spotifyInfo != null && spotifyInfo.albumImageUrl != null && !spotifyInfo.albumImageUrl.isEmpty()) {
                                    thumbnailUrl = spotifyInfo.albumImageUrl;
                                } else {
                                    thumbnailUrl = "https://www.freepnglogos.com/uploads/spotify-logo-png/file-spotify-logo-png-4.png";
                                }
                            }
                            break;
                            
                        case RADIO:
                            sourceType = "Radio";
                            String stationName = handler.get().getRadioStationName(track);
                            String logoUrl = handler.get().getRadioLogoUrl(track);
                            source = stationName != null ? stationName : "Radio Stream";
                            
                            if (logoUrl != null && !logoUrl.isEmpty()) {
                                thumbnailUrl = logoUrl;
                            } else {
                                thumbnailUrl = "https://static.semrush.com/power-pages/media/favicons/onlineradiobox-com-favicon-7dd1a612.png";
                            }
                            
                            if (handler.get().isGensokyoRadioTrack(track)) {
                                sourceType = "Gensokyo Radio";
                                source = "Gensokyo Radio";
                                thumbnailUrl = "https://stream.gensokyoradio.net/images/logo.png";
                            }
                            break;
                            
                        case SOUNDCLOUD:
                            if (!platformFromYt) {
                                sourceType = "SoundCloud";
                                source = "SoundCloud";
                            }
                            if (info.artworkUrl != null && !info.artworkUrl.isEmpty()) {
                                thumbnailUrl = info.artworkUrl;
                            } else {
                                thumbnailUrl = "https://developers.soundcloud.com/assets/logo_big_white-65c2b096da68dd533db18b5a2bcfbcce.png";
                            }
                            break;
                            
                        case LOCAL:
                            if (!platformFromYt) {
                                sourceType = "Local File"; // Corrected sourceType to match other places
                                source = "Local File";
                            }
                            // Get the artwork path for local files
                            thumbnailUrl = bot.getLocalArtworkPath(track.getIdentifier()); 
                            if (thumbnailUrl == null || thumbnailUrl.isEmpty()) {
                                // Fallback icon if no artwork path is found
                                thumbnailUrl = "https://cdn-icons-png.flaticon.com/512/4725/4725478.png"; 
                            }
                            break;
                            
                        default: // OTHER or unknown types
                            if (platformFromYt) {
                                break;
                            }
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
                        // Use the cache service to get the avatar URL
                        String cachedAvatar = avatarCacheService.getAvatarUrl(String.valueOf(rm.getOwner()));
                        if (cachedAvatar != null) {
                            requesterAvatar = cachedAvatar;
                        } else {
                            requesterAvatar = rm.user.avatar;
                        }
                    }
                    
                    // For Spotify tracks, prepare info map
                    Map<String, Object> spotifyInfoMap = null;
                    if (trackType == AudioHandler.TrackType.SPOTIFY) {
                        // Try to get Spotify track ID from metadata
                        if (rm != null && rm.hasSpotifyData()) {
                            spotifyInfoMap = new HashMap<>();
                            spotifyInfoMap.put("trackId", rm.getSpotifyTrackId());
                        }
                    }
                    
                    // For Radio tracks, extract radio station URL and path components for proper URLs
                    String radioStationUrl = null;
                    String radioCountry = null;
                    String radioAlias = null;
                    if (trackType == AudioHandler.TrackType.RADIO) {
                        // Extract station path
                        String stationPath = handler.get().getCurrentRadioStationPath(track);
                        
                        // Extract country and alias from the path (format: "country/alias")
                        if (stationPath != null && stationPath.contains("/")) {
                            String[] parts = stationPath.split("/");
                            if (parts.length >= 2) {
                                radioCountry = parts[0];
                                radioAlias = parts[1];
                                
                                // Construct station URL
                                radioStationUrl = "https://onlineradiobox.com/" + radioCountry + "/" + radioAlias + "/";
                            }
                        }
                    }
                    
                        // Create the queue track with all necessary info
                    return new QueueTrack(
                            handler.get().getQueue().getList().indexOf(queuedTrack), // position
                            info.title,
                            info.author,
                            info.uri,
                            thumbnailUrl,
                            track.getDuration(),
                            source,
                            sourceType,
                            requesterName,
                            requesterAvatar,
                            spotifyInfoMap,
                            radioStationUrl,
                            radioCountry,
                            radioAlias,
                            sourceIconUrl
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
        CompletableFuture<String> future = new CompletableFuture<>();
        
        try {
            Optional<AudioHandler> handler = getAudioHandler();
            if (handler.isEmpty()) {
                future.complete("No audio handler found for the selected guild");
                return future;
            }
            
            AudioHandler audioHandler = handler.get();
            
            getAudioManager().loadItemOrdered(bot.getJDA().getGuildById(selectedGuildId), url, new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(AudioTrack track) {
                    // Always use the bot's self user for metadata instead of null
                    RequestMetadata rm = new RequestMetadata(bot.getJDA().getSelfUser());
                    QueuedTrack qtrack = new QueuedTrack(track, rm);
                    
                    int position = audioHandler.addTrack(qtrack) + 1;
                    future.complete("Added track: " + track.getInfo().title + (position == 0 ? " (now playing)" : " at position " + position));
                }
                
                @Override
                public void playlistLoaded(AudioPlaylist playlist) {
                    if (playlist.getTracks().isEmpty()) {
                        future.complete("Playlist is empty");
                        return;
                    }
                    
                    if (playlist.isSearchResult()) {
                        AudioTrack track = playlist.getTracks().get(0);
                        // Always use the bot's self user for metadata instead of null
                        RequestMetadata rm = new RequestMetadata(bot.getJDA().getSelfUser());
                        QueuedTrack qtrack = new QueuedTrack(track, rm);
                        
                        int position = audioHandler.addTrack(qtrack) + 1;
                        future.complete("Added track: " + track.getInfo().title + (position == 0 ? " (now playing)" : " at position " + position));
                    } else {
                        int count = 0;
                        int position = -1;
                        
                        for (int i = 0; i < playlist.getTracks().size() && i < 10; i++) {
                            AudioTrack track = playlist.getTracks().get(i);
                            // Always use the bot's self user for metadata instead of null
                            RequestMetadata rm = new RequestMetadata(bot.getJDA().getSelfUser());
                            QueuedTrack qtrack = new QueuedTrack(track, rm);
                            
                            if (position == -1) {
                                position = audioHandler.addTrack(qtrack) + 1;
                            } else {
                                audioHandler.addTrack(qtrack);
                            }
                            count++;
                        }
                        
                        future.complete("Added " + count + " tracks from playlist: " + playlist.getName());
                    }
                }
                
                @Override
                public void noMatches() {
                    future.complete("No matches found for: " + url);
                }
                
                @Override
                public void loadFailed(FriendlyException exception) {
                    future.complete("Failed to load track: " + exception.getMessage());
                }
            });
        } catch (Exception e) {
            future.complete("Error adding track: " + e.getMessage());
        }
        
        return future;
    }
    
    /**
     * Adds a track to the front of the queue (play next) by URL
     * @param url The URL of the track to add
     * @return A CompletableFuture that will be completed with a result message
     */
    public CompletableFuture<String> playNextTrackByUrl(String url) {
        CompletableFuture<String> future = new CompletableFuture<>();
        
        try {
            Optional<AudioHandler> handler = getAudioHandler();
            if (handler.isEmpty()) {
                future.complete("No audio handler found for the selected guild");
                return future;
            }
            
            AudioHandler audioHandler = handler.get();
            
            getAudioManager().loadItemOrdered(bot.getJDA().getGuildById(selectedGuildId), url, new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(AudioTrack track) {
                    // Always use the bot's self user for metadata instead of null
                    RequestMetadata rm = new RequestMetadata(bot.getJDA().getSelfUser());
                    QueuedTrack qtrack = new QueuedTrack(track, rm);
                    
                    int position = audioHandler.addTrackToFront(qtrack) + 1;
                    future.complete("Added track to position " + position + ": " + track.getInfo().title);
                }
                
                @Override
                public void playlistLoaded(AudioPlaylist playlist) {
                    if (playlist.getTracks().isEmpty()) {
                        future.complete("Playlist is empty");
                        return;
                    }
                    
                    if (playlist.isSearchResult()) {
                        AudioTrack track = playlist.getTracks().get(0);
                        // Always use the bot's self user for metadata instead of null
                        RequestMetadata rm = new RequestMetadata(bot.getJDA().getSelfUser());
                        QueuedTrack qtrack = new QueuedTrack(track, rm);
                        
                        int position = audioHandler.addTrackToFront(qtrack) + 1;
                        future.complete("Added track to position " + position + ": " + track.getInfo().title);
                    } else {
                        // Only add the first 5 songs from playlists when using playnext
                        for (int i = Math.min(4, playlist.getTracks().size() - 1); i >= 0; i--) {
                            AudioTrack track = playlist.getTracks().get(i);
                            // Always use the bot's self user for metadata instead of null
                            RequestMetadata rm = new RequestMetadata(bot.getJDA().getSelfUser());
                            QueuedTrack qtrack = new QueuedTrack(track, rm);
                            audioHandler.addTrackToFront(qtrack);
                        }
                        
                        future.complete("Added first " + Math.min(5, playlist.getTracks().size()) + 
                            " tracks from playlist: " + playlist.getName() + " to the front of the queue");
                    }
                }
                
                @Override
                public void noMatches() {
                    future.complete("No matches found for: " + url);
                }
                
                @Override
                public void loadFailed(FriendlyException exception) {
                    future.complete("Failed to load track: " + exception.getMessage());
                }
            });
        } catch (Exception e) {
            future.complete("Error adding track: " + e.getMessage());
        }
        
        return future;
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
            
            // Stop playback and clear the queue, similar to how StopCmd works
            audioHandler.stopAndClear();
            
            // Close audio connection to leave the voice channel
            if (bot.getJDA().getGuildById(selectedGuildId) != null) {
                bot.getJDA().getGuildById(selectedGuildId).getAudioManager().closeAudioConnection();
            }
            
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
                track.setPosition(position);
                return true;
            }
        }
        return false;
    }
    
    /**
     * Gets the chapters for the currently playing YouTube track
     * 
     * @return List of chapter objects, or empty list if no chapters or not a YouTube track
     */
    public List<Map<String, Object>> getCurrentTrackChapters() {
        Optional<AudioHandler> handler = getAudioHandler();
        
        if (handler.isEmpty()) {
            return Collections.emptyList();
        }
        
        AudioHandler audioHandler = handler.get();
        AudioTrack currentTrack = audioHandler.getPlayer().getPlayingTrack();
        
        if (currentTrack == null) {
            return Collections.emptyList();
        }
        
        // Get YouTube chapter manager from bot
        YouTubeChapterManager chapterManager = bot.getYoutubeChapterManager();
        if (chapterManager == null) {
            return Collections.emptyList();
        }
        
        // Get chapters for the current track
        List<YouTubeChapterExtractor.Chapter> chapters = chapterManager.getChapters(currentTrack);
        
        // Convert chapters to Maps for JSON serialization
        return chapters.stream().map(chapter -> {
            Map<String, Object> chapterMap = Map.of(
                "name", chapter.getName(),
                "startTimeMs", chapter.getStartTimeMs(),
                "endTimeMs", chapter.getEndTimeMs(),
                "durationMs", chapter.getDurationMs()
            );
            return chapterMap;
        }).collect(Collectors.toList());
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
     * Clears the entire queue
     */
    public boolean clearQueue() {
        Optional<AudioHandler> handler = getAudioHandler();
        if (handler.isPresent()) {
            try {
                handler.get().getQueue().clear();
                return true;
            } catch (Exception e) {
                System.out.println("Web Panel: Error clearing queue: " + e.getMessage());
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

    /**
     * Processes a Spotify track and adds it to the queue
     * @param trackId The Spotify track ID
     * @param playNext Whether to play the track next or add it to the end of the queue
     * @return A message indicating the result of the operation
     */
    public String processSpotifyTrack(String trackId, boolean playNext) {
        try {
            // Get the access token using reflection
            Field accessTokenField = dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.class.getDeclaredField("accessToken");
            accessTokenField.setAccessible(true);
            String accessToken = (String) accessTokenField.get(null);
            
            if (accessToken == null) {
                return "Failed to authenticate with Spotify";
            }
            
            // Get the Spotify track information
            String endpoint = "https://api.spotify.com/v1/tracks/" + trackId;
            HttpClient httpClient = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(5))
                    .build();
            
            HttpRequest request = HttpRequest.newBuilder()
                    .header("Authorization", "Bearer " + accessToken)
                    .header("Accept-Language", "en")
                    .GET()
                    .uri(URI.create(endpoint))
                    .build();
            
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject json = new JSONObject(response.body());
            String trackName = json.getString("name");
            String artistName = json.getJSONArray("artists").getJSONObject(0).getString("name");
            String albumName = json.getJSONObject("album").getString("name");
            String albumImageUrl = json.getJSONObject("album").getJSONArray("images").getJSONObject(0).getString("url");
            
            // Extract release date information
            String releaseDate = json.getJSONObject("album").getString("release_date");
            String releaseDatePrecision = json.getJSONObject("album").getString("release_date_precision");
            String releaseYear = extractReleaseYear(releaseDate, releaseDatePrecision);
            
            // Use the Audio Features endpoint to retrieve track information
            endpoint = "https://api.spotify.com/v1/audio-features/" + trackId;
            request = HttpRequest.newBuilder()
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .uri(URI.create(endpoint))
                    .build();
            
            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            json = new JSONObject(response.body());
            // Use a default value when valence key does not exist
            double trackColor = json.has("valence") ? json.getDouble("valence") : 0.5;
            
            int hue = (int) (trackColor * 360);
            Color color = Color.getHSBColor((float) hue / 360, 1.0f, 1.0f);
            
            // Store track information for NowplayingCmd using the static maps
            String guildId = getSelectedGuildId();
            dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.lastTrackIds.put(guildId, trackId);
            dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.trackNames.put(trackId, trackName);
            dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.albumNames.put(trackId, albumName);
            dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.artistNames.put(trackId, artistName);
            dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.albumImageUrls.put(trackId, albumImageUrl);
            dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.trackColors.put(trackId, color);
            dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.releaseYears.put(trackId, releaseYear);
            
            // Create an embed with the track information to display
            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("Track Information :");
            embed.setDescription(
                "**Title** : " + trackName + "\n" +
                "**Album** : " + albumName + "\n" +
                "**Artist** : " + artistName + "\n" +
                "**Released** : " + releaseYear
            );
            embed.setImage(albumImageUrl);
            embed.setColor(color);
            
            // Send the embed to the music channel if available
            if (bot.getJDA().getGuildById(guildId) != null) {
                // Get the settings for the guild
                net.dv8tion.jda.api.entities.channel.concrete.TextChannel musicChannel = 
                    bot.getSettingsManager().getSettings(bot.getJDA().getGuildById(guildId))
                        .getTextChannel(bot.getJDA().getGuildById(guildId));
                
                if (musicChannel != null) {
                    musicChannel.sendMessageEmbeds(embed.build()).queue();
                }
            }
            
            // Search for the track on YouTube Music and add it to the queue
            CompletableFuture<String> future = new CompletableFuture<>();
            String searchQuery = "ytmsearch:" + trackName + " " + artistName;
            
            // Get the player manager and load the item
            Optional<AudioHandler> handler = getAudioHandler();
            if (handler.isEmpty()) {
                return "No audio handler found for the selected guild";
            }
            
            AudioHandler audioHandler = handler.get();
            
            getAudioManager().loadItemOrdered(bot.getJDA().getGuildById(guildId), searchQuery, new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(AudioTrack track) {
                    // Create RequestMetadata with the Spotify track ID
                    RequestMetadata rm = new RequestMetadata(bot.getJDA().getSelfUser());
                    rm.setSpotifyTrackId(trackId);
                    
                    QueuedTrack qtrack = new QueuedTrack(track, rm);
                    
                    int pos;
                    if (playNext) {
                        pos = audioHandler.addTrackToFront(qtrack) + 1;
                        future.complete("Added track to position " + pos + ": " + track.getInfo().title);
                    } else {
                        pos = audioHandler.addTrack(qtrack) + 1;
                        future.complete("Added track: " + track.getInfo().title + 
                                (pos == 0 ? " (now playing)" : " at position " + pos));
                    }
                }
                
                @Override
                public void playlistLoaded(AudioPlaylist playlist) {
                    if (playlist.getTracks().isEmpty()) {
                        future.complete("Playlist is empty");
                        return;
                    }
                    
                    AudioTrack track = playlist.getTracks().get(0);
                    RequestMetadata rm = new RequestMetadata(bot.getJDA().getSelfUser());
                    rm.setSpotifyTrackId(trackId);
                    
                    QueuedTrack qtrack = new QueuedTrack(track, rm);
                    
                    int pos;
                    if (playNext) {
                        pos = audioHandler.addTrackToFront(qtrack) + 1;
                        future.complete("Added track to position " + pos + ": " + track.getInfo().title);
                    } else {
                        pos = audioHandler.addTrack(qtrack) + 1;
                        future.complete("Added track: " + track.getInfo().title + 
                                (pos == 0 ? " (now playing)" : " at position " + pos));
                    }
                }
                
                @Override
                public void noMatches() {
                    future.complete("No matches found for: " + trackName + " by " + artistName);
                }
                
                @Override
                public void loadFailed(FriendlyException exception) {
                    future.complete("Failed to load track: " + exception.getMessage());
                }
            });
            
            try {
                return future.get(15, TimeUnit.SECONDS);
            } catch (InterruptedException | ExecutionException | TimeoutException e) {
                return "Error adding track: " + e.getMessage();
            }
            
        } catch (Exception e) {
            return "Error processing Spotify track: " + e.getMessage();
        }
    }
    
    /**
     * Helper method to extract release year based on precision
     */
    private String extractReleaseYear(String releaseDate, String precision) {
        if (releaseDate == null || releaseDate.isEmpty()) {
            return "Unknown";
        }
        
        switch (precision) {
            case "year":
                return releaseDate; // Already just the year
            case "month":
            case "day":
                // Extract just the year part (first 4 characters)
                if (releaseDate.length() >= 4) {
                    return releaseDate.substring(0, 4);
                }
            default:
                return releaseDate;
        }
    }
    
    /**
     * Set the volume for the current player
     * @param volume Volume level (0-150)
     * @return true if successful
     */
    public boolean setVolume(int volume) {
        if (selectedGuildId == null) return false;
        
        try {
            net.dv8tion.jda.api.entities.Guild guild = bot.getJDA().getGuildById(selectedGuildId);
            if (guild == null) return false;
            
            AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
            if (handler == null) return false;
            
            // Limit volume to 0-150
            if (volume < 0) volume = 0;
            if (volume > 150) volume = 150;
            
            handler.getPlayer().setVolume(volume);
            
            // Save setting
            com.jagrosh.jmusicbot.settings.Settings settings = bot.getSettingsManager().getSettings(guild);
            settings.setVolume(volume);
            
            return true;
        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }
} 