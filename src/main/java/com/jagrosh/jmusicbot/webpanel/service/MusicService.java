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

    private static final class YtDlpSourceResolution {
        private final String sourceType;
        private final String source;
        private final String sourceIconUrl;
        private final String thumbnailUrl;
        private final boolean platformFromYt;

        private YtDlpSourceResolution(
                String sourceType,
                String source,
                String sourceIconUrl,
                String thumbnailUrl,
                boolean platformFromYt) {
            this.sourceType = sourceType;
            this.source = source;
            this.sourceIconUrl = sourceIconUrl;
            this.thumbnailUrl = thumbnailUrl;
            this.platformFromYt = platformFromYt;
        }
    }

    private YtDlpSourceResolution resolveYtDlpSource(AudioTrack track, String sourceType, String source, String thumbnailUrl) {
        String resolvedSourceType = sourceType;
        String resolvedSource = source;
        String resolvedSourceIconUrl = null;
        String resolvedThumbnail = thumbnailUrl;
        boolean platformFromYt = false;

        dev.cosgy.jmusicbot.util.YtDlpManager.YtDlpMetadata ytMeta = PlayerManager.getYtDlpMetadata(track);
        FallbackPlatform ytPlatform = PlayerManager.getYtDlpPlatform(track);

        if (ytPlatform != null && ytPlatform != FallbackPlatform.NONE) {
            switch (ytPlatform) {
                case INSTAGRAM -> resolvedSourceType = resolvedSource = "Instagram";
                case TIKTOK -> resolvedSourceType = resolvedSource = "TikTok";
                case TWITTER -> resolvedSourceType = resolvedSource = "Twitter";
                case BILIBILI -> resolvedSourceType = resolvedSource = "Bilibili";
                case VIMEO -> resolvedSourceType = resolvedSource = "Vimeo";
                case TWITCH -> resolvedSourceType = resolvedSource = "Twitch";
                case SOUNDCLOUD -> resolvedSourceType = resolvedSource = "SoundCloud";
                case YOUTUBE -> resolvedSourceType = resolvedSource = "YouTube";
                default -> {
                    if (ytMeta != null && ytMeta.webpageUrl() != null) {
                        try {
                            URI uri = new URI(ytMeta.webpageUrl());
                            String host = uri.getHost();
                            if (host != null) {
                                String fullDomain = host;
                                host = host.startsWith("www.") ? host.substring(4) : host;
                                int lastDot = host.lastIndexOf('.');
                                if (lastDot > 0) {
                                    host = host.substring(0, lastDot);
                                }
                                if (!host.isEmpty()) {
                                    resolvedSourceType = resolvedSource = host.substring(0, 1).toUpperCase() + host.substring(1);
                                    resolvedSourceIconUrl = "https://www.google.com/s2/favicons?domain=" + fullDomain + "&sz=64";
                                }
                            }
                        } catch (Exception ignored) {
                        }
                    }
                }
            }

            if (ytMeta != null && ytMeta.thumbnailUrl() != null && !ytMeta.thumbnailUrl().isEmpty()) {
                resolvedThumbnail = ytMeta.thumbnailUrl();
            }

            platformFromYt = !"Unknown".equals(resolvedSourceType);
        }

        return new YtDlpSourceResolution(
                resolvedSourceType,
                resolvedSource,
                resolvedSourceIconUrl,
                resolvedThumbnail,
                platformFromYt
        );
    }

    private static final class TrackPresentationData {
        private String sourceType = "Unknown";
        private String source = "Unknown";
        private String thumbnailUrl = "";
        private String sourceIconUrl;
        private boolean platformFromYt;
        private boolean isStreamFlag;
        private String localAlbum;
        private String localGenre;
        private String localYear;
        private String radioLogoUrl;
        private String radioSongImageUrl;
    }

    private static boolean isEmpty(String value) {
        return value == null || value.isEmpty();
    }

    private String extractYoutubeVideoId(String uri) {
        if (uri == null) {
            return null;
        }
        if (uri.contains("youtu.be/")) {
            String videoId = uri.substring(uri.lastIndexOf('/') + 1);
            if (videoId.contains("?")) {
                videoId = videoId.substring(0, videoId.indexOf('?'));
            }
            return videoId;
        }
        if (uri.contains("watch?v=")) {
            String videoId = uri.substring(uri.indexOf("watch?v=") + 8);
            if (videoId.contains("&")) {
                videoId = videoId.substring(0, videoId.indexOf('&'));
            }
            return videoId;
        }
        return null;
    }

    private TrackPresentationData resolveTrackPresentation(AudioHandler audioHandler, AudioTrack track, AudioTrackInfo info, RequestMetadata rm) {
        TrackPresentationData data = new TrackPresentationData();
        data.isStreamFlag = track.getInfo().isStream;

        YtDlpSourceResolution ytResolution = resolveYtDlpSource(track, data.sourceType, data.source, data.thumbnailUrl);
        data.sourceType = ytResolution.sourceType;
        data.source = ytResolution.source;
        data.sourceIconUrl = ytResolution.sourceIconUrl;
        data.thumbnailUrl = ytResolution.thumbnailUrl;
        data.platformFromYt = ytResolution.platformFromYt;
        if (data.platformFromYt) {
            data.isStreamFlag = false;
        }

        AudioHandler.TrackType trackType = audioHandler.getTrackType(track);
        switch (trackType) {
            case YOUTUBE -> {
                if (!data.platformFromYt) {
                    data.sourceType = "YouTube";
                    data.source = "YouTube";
                }
                if (isEmpty(data.thumbnailUrl)) {
                    String videoId = extractYoutubeVideoId(info.uri);
                    if (videoId != null) {
                        data.thumbnailUrl = "https://img.youtube.com/vi/" + videoId + "/mqdefault.jpg";
                    }
                }
            }
            case SPOTIFY -> {
                if (!data.platformFromYt) {
                    data.sourceType = "Spotify";
                    data.source = "Spotify";
                }
                String spotifyTrackId = rm != null && rm.hasSpotifyData() ? rm.getSpotifyTrackId() : null;
                if (spotifyTrackId != null) {
                    String albumUrl = dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.albumImageUrls.get(spotifyTrackId);
                    data.thumbnailUrl = !isEmpty(albumUrl)
                            ? albumUrl
                            : "https://www.freepnglogos.com/uploads/spotify-logo-png/file-spotify-logo-png-4.png";
                } else {
                    dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.SpotifyTrackInfo spotifyInfo = audioHandler.getSpotifyTrackInfo();
                    data.thumbnailUrl = (spotifyInfo != null && !isEmpty(spotifyInfo.albumImageUrl))
                            ? spotifyInfo.albumImageUrl
                            : "https://www.freepnglogos.com/uploads/spotify-logo-png/file-spotify-logo-png-4.png";
                }
            }
            case RADIO -> {
                data.isStreamFlag = true;
                data.sourceType = "Radio";
                String stationName = audioHandler.getRadioStationName(track);
                String logoUrl = audioHandler.getRadioLogoUrl(track);
                dev.cosgy.jmusicbot.slashcommands.music.RadioCmd.TrackInfo radioInfo = audioHandler.getRadioTrackInfo(track);
                String songImageUrl = (radioInfo != null && !isEmpty(radioInfo.imageUrl)) ? radioInfo.imageUrl : null;

                if (!isEmpty(songImageUrl)) {
                    data.thumbnailUrl = songImageUrl;
                } else if (!isEmpty(logoUrl)) {
                    data.thumbnailUrl = logoUrl;
                } else {
                    data.thumbnailUrl = "https://static.semrush.com/power-pages/media/favicons/onlineradiobox-com-favicon-7dd1a612.png";
                }

                data.source = stationName != null ? stationName : "Radio Stream";
                data.radioLogoUrl = logoUrl;
                data.radioSongImageUrl = songImageUrl;

                if (audioHandler.isGensokyoRadioTrack(track)) {
                    data.sourceType = "Gensokyo Radio";
                    data.source = "Gensokyo Radio";
                    data.thumbnailUrl = "https://stream.gensokyoradio.net/images/logo.png";
                    try {
                        dev.cosgy.agent.objects.ResultSet grInfo = dev.cosgy.agent.GensokyoInfoAgent.getInfo();
                        if (grInfo != null && grInfo.getMisc() != null && grInfo.getMisc().getFullAlbumArtUrl() != null && !grInfo.getMisc().getFullAlbumArtUrl().isEmpty()) {
                            data.thumbnailUrl = grInfo.getMisc().getFullAlbumArtUrl();
                        }
                    } catch (Exception ignored) {
                    }
                }
            }
            case SOUNDCLOUD -> {
                if (!data.platformFromYt) {
                    data.sourceType = "SoundCloud";
                    data.source = "SoundCloud";
                }
                data.thumbnailUrl = !isEmpty(info.artworkUrl)
                        ? info.artworkUrl
                        : "https://developers.soundcloud.com/assets/logo_big_white-65c2b096da68dd533db18b5a2bcfbcce.png";
            }
            case LOCAL -> {
                if (!data.platformFromYt) {
                    data.sourceType = "Local File";
                    data.source = "Local File";
                }

                if (rm != null && rm.hasLocalFileData()) {
                    data.localAlbum = rm.getLocalFileAlbum();
                    data.localGenre = rm.getLocalFileGenre();
                    data.localYear = rm.getLocalFileYear();
                    if (!isEmpty(rm.getLocalFileArtworkHash())) {
                        data.thumbnailUrl = "/" + rm.getLocalFileArtworkHash();
                    }
                } else {
                    dev.cosgy.jmusicbot.util.LocalAudioMetadata.LocalTrackInfo cachedInfo = bot.getLocalMetadataCache().get(track.getInfo().identifier);
                    if (cachedInfo != null) {
                        data.localAlbum = cachedInfo.getAlbum();
                        data.localGenre = cachedInfo.getGenre();
                        data.localYear = cachedInfo.getYear();
                        if (cachedInfo.hasArtwork() && cachedInfo.getArtworkPath() != null) {
                            data.thumbnailUrl = "/" + cachedInfo.getArtworkPath();
                        }
                    }
                }

                if (isEmpty(data.thumbnailUrl)) {
                    data.thumbnailUrl = "/images/default_local.png";
                }
            }
            default -> {
                if (!data.platformFromYt) {
                    FallbackPlatform platform = PlayerManager.getYtDlpPlatform(track);
                    if (platform != null && platform != FallbackPlatform.NONE) {
                        if ("Unknown".equals(data.sourceType)) {
                            data.sourceType = platform.name().charAt(0) + platform.name().substring(1).toLowerCase();
                            data.source = data.sourceType;
                        }
                        if (!isEmpty(info.artworkUrl)) {
                            data.thumbnailUrl = info.artworkUrl;
                        }
                    } else if (track.getInfo().isStream) {
                        data.sourceType = "Stream";
                        data.source = "Web Stream";
                        data.thumbnailUrl = "https://cdn-icons-png.flaticon.com/128/11796/11796884.png";
                    }
                }

                if ("Unknown".equals(data.sourceType) || "Stream".equals(data.sourceType)) {
                    IcyMetadataHandler.StreamMetadata metadata = bot.getIcyMetadataHandler().getMetadata(selectedGuildId);
                    if (metadata != null && !metadata.hasFailed()) {
                        data.sourceType = "Stream";
                        data.source = metadata.getStationName() != null ? metadata.getStationName() : "Web Stream";
                        data.isStreamFlag = true;

                        if (!isEmpty(metadata.getStationLogo())) {
                            data.thumbnailUrl = metadata.getStationLogo();
                        } else if (!isEmpty(metadata.getAlbumArt())) {
                            data.thumbnailUrl = metadata.getAlbumArt();
                        } else {
                            data.thumbnailUrl = "https://cdn-icons-png.flaticon.com/128/11796/11796884.png";
                        }
                    } else if (track.getInfo().isStream) {
                        data.sourceType = "Stream";
                        data.source = "Web Stream";
                        data.thumbnailUrl = "https://cdn-icons-png.flaticon.com/128/11796/11796884.png";
                        data.isStreamFlag = true;
                    } else if (track.getSourceManager() != null) {
                        String srcType = track.getSourceManager().getSourceName();
                        if ("http".equalsIgnoreCase(srcType)) {
                            data.sourceType = "Stream";
                            data.source = "Web Stream";
                        } else {
                            data.sourceType = srcType.substring(0, 1).toUpperCase() + srcType.substring(1).toLowerCase();
                            data.source = data.sourceType;
                        }
                        data.thumbnailUrl = audioHandler.getSourceIconUrl(data.sourceType.toLowerCase());
                    }
                }
            }
        }

        return data;
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
        return getCurrentStatusInternal();
    }

    private MusicStatus getCurrentStatusInternal() {
        Optional<AudioHandler> handler = getAudioHandler();
        boolean inVoiceChannel = isBotInVoiceChannel();
        if (handler.isEmpty() || handler.get().getPlayer().getPlayingTrack() == null) {
            return buildEmptyStatus(inVoiceChannel);
        }

        AudioHandler audioHandler = handler.get();
        AudioTrack track = audioHandler.getPlayer().getPlayingTrack();
        AudioTrackInfo info = PlayerManager.getDisplayInfo(track);
        if (info == null) {
            info = track.getInfo();
        }
        RequestMetadata rm = getRequestMetadata(track);
        AudioHandler.TrackType trackType = audioHandler.getTrackType(track);
        TrackPresentationData presentation = resolveTrackPresentation(audioHandler, track, info, rm);
        RequesterData requester = resolveRequester(rm);
        Map<String, Object> spotifyInfoMap = buildExtendedTrackInfo(audioHandler, track, trackType);
        RadioStationData radioStationData = resolveRadioStationData(audioHandler, track, trackType, rm);

        return new MusicStatus(
                info.title,
                info.author,
                info.uri,
                presentation.thumbnailUrl,
                track.getPosition(),
                info.length,
                audioHandler.getPlayer().getPlayingTrack() != null,
                audioHandler.getPlayer().isPaused(),
                !audioHandler.getQueue().isEmpty(),
                audioHandler.getQueue().size(),
                presentation.source,
                requester.name,
                requester.avatar,
                audioHandler.getPlayer().getVolume(),
                presentation.sourceType,
                inVoiceChannel,
                spotifyInfoMap,
                trackType == AudioHandler.TrackType.RADIO ? presentation.radioLogoUrl : null,
                trackType == AudioHandler.TrackType.RADIO ? presentation.radioSongImageUrl : null,
                radioStationData.country,
                radioStationData.alias,
                presentation.localAlbum,
                presentation.localGenre,
                presentation.localYear,
                presentation.isStreamFlag,
                presentation.sourceIconUrl
        );
    }

    private boolean isBotInVoiceChannel() {
        if (selectedGuildId == null || bot.getJDA() == null) {
            return false;
        }
        net.dv8tion.jda.api.entities.Guild guild = bot.getJDA().getGuildById(selectedGuildId);
        if (guild == null) {
            return false;
        }
        GuildVoiceState voiceState = guild.getSelfMember().getVoiceState();
        return voiceState != null && voiceState.inAudioChannel();
    }

    private MusicStatus buildEmptyStatus(boolean inVoiceChannel) {
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
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                false,
                null
        );
    }

    private static final class RequesterData {
        private final String name;
        private final String avatar;

        private RequesterData(String name, String avatar) {
            this.name = name;
            this.avatar = avatar;
        }
    }

    private RequesterData resolveRequester(RequestMetadata rm) {
        if (rm != null && rm.user != null) {
            String cachedAvatar = avatarCacheService.getAvatarUrl(String.valueOf(rm.getOwner()));
            String avatar = cachedAvatar != null ? cachedAvatar : rm.user.avatar;
            return new RequesterData(rm.user.username, avatar);
        }
        if (bot.getJDA() != null) {
            return new RequesterData(bot.getJDA().getSelfUser().getName(), bot.getJDA().getSelfUser().getEffectiveAvatarUrl());
        }
        return new RequesterData("Unknown", "");
    }

    private Map<String, Object> buildExtendedTrackInfo(AudioHandler audioHandler, AudioTrack track, AudioHandler.TrackType trackType) {
        if (trackType == AudioHandler.TrackType.SPOTIFY) {
            return buildSpotifyExtendedTrackInfo(audioHandler);
        }

        if (!audioHandler.isGensokyoRadioTrack(track)) {
            return null;
        }

        return buildGensokyoExtendedTrackInfo();
    }

    private Map<String, Object> buildSpotifyExtendedTrackInfo(AudioHandler audioHandler) {
        dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.SpotifyTrackInfo spotInfo = audioHandler.getSpotifyTrackInfo();
        if (spotInfo == null) {
            return null;
        }

        Map<String, Object> spotifyInfoMap = new HashMap<>();
        spotifyInfoMap.put("trackId", spotInfo.trackId);
        spotifyInfoMap.put("albumName", spotInfo.albumName);
        spotifyInfoMap.put("albumImageUrl", spotInfo.albumImageUrl);
        spotifyInfoMap.put("artistName", spotInfo.artistName);
        spotifyInfoMap.put("releaseYear", spotInfo.releaseYear);
        return spotifyInfoMap;
    }

    private Map<String, Object> buildGensokyoExtendedTrackInfo() {
        try {
            dev.cosgy.agent.objects.ResultSet grInfo = dev.cosgy.agent.GensokyoInfoAgent.getInfo();
            if (grInfo == null || grInfo.getSonginfo() == null) {
                return null;
            }

            Map<String, Object> infoMap = new HashMap<>();
            putIfNotBlank(infoMap, "albumName", grInfo.getSonginfo().getAlbum());
            putIfNotBlank(infoMap, "circleName", grInfo.getSonginfo().getCircle());
            putIfNotBlank(infoMap, "releaseYear", grInfo.getSonginfo().getYear());

            if (grInfo.getMisc() != null) {
                putIfNotBlank(infoMap, "albumImageUrl", grInfo.getMisc().getFullAlbumArtUrl());
            }

            addGensokyoTimingInfo(infoMap, grInfo);
            return infoMap;
        } catch (Exception e) {
            System.out.println("Error fetching Gensokyo Radio metadata: " + e.getMessage());
            return null;
        }
    }

    private void addGensokyoTimingInfo(Map<String, Object> infoMap, dev.cosgy.agent.objects.ResultSet grInfo) {
        if (grInfo.getSongtimes() == null) {
            return;
        }

        if (grInfo.getSongtimes().getDuration() != null) {
            infoMap.put("gensokyoDuration", grInfo.getSongtimes().getDuration() * 1000);
        }
        if (grInfo.getSongtimes().getPlayed() != null) {
            infoMap.put("gensokyoPlayed", grInfo.getSongtimes().getPlayed() * 1000);
        }
        if (grInfo.getSongtimes().getRemaining() != null) {
            infoMap.put("gensokyoRemaining", grInfo.getSongtimes().getRemaining() * 1000);
        }
    }

    private void putIfNotBlank(Map<String, Object> target, String key, String value) {
        if (value != null && !value.isEmpty()) {
            target.put(key, value);
        }
    }

    private static final class RadioStationData {
        private final String country;
        private final String alias;

        private RadioStationData(String country, String alias) {
            this.country = country;
            this.alias = alias;
        }
    }

    private RadioStationData resolveRadioStationData(AudioHandler audioHandler, AudioTrack track, AudioHandler.TrackType trackType, RequestMetadata rm) {
        if (trackType != AudioHandler.TrackType.RADIO) {
            return new RadioStationData(null, null);
        }
        String stationPath = null;
        if (rm != null && rm.hasRadioData()) {
            stationPath = rm.getRadioStationPath();
        }
        if (stationPath == null) {
            stationPath = audioHandler.getCurrentRadioStationPath(track);
        }
        if (stationPath == null || !stationPath.contains("/")) {
            return new RadioStationData(null, null);
        }
        String[] parts = stationPath.split("/");
        if (parts.length < 2) {
            return new RadioStationData(null, null);
        }
        return new RadioStationData(parts[0], parts[1]);
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
        return getQueueInternal();
    }

    private List<QueueTrack> getQueueInternal() {
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
                    
                    AudioHandler.TrackType trackType = handler.get().getTrackType(track);
                    TrackPresentationData presentation = resolveTrackPresentation(handler.get(), track, info, rm);
                    String sourceType = presentation.sourceType;
                    String source = presentation.source;
                    String thumbnailUrl = presentation.thumbnailUrl;
                    String sourceIconUrl = presentation.sourceIconUrl;
                    
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