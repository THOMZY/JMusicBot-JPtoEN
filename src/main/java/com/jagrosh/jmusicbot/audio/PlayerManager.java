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
import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.source.nico.NicoAudioSourceManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.cosgy.jmusicbot.util.YtDlpManager;
import dev.cosgy.jmusicbot.util.YtDlpManager.FallbackPlatform;
import dev.cosgy.jmusicbot.util.YtDlpManager.YtDlpMetadata;
import dev.cosgy.jmusicbot.util.YtDlpManager.YtDlpResult;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.*;
import dev.lavalink.youtube.clients.skeleton.Client;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class PlayerManager extends DefaultAudioPlayerManager {
    private final Bot bot;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private Path ytDlpPath;
    private YtDlpManager ytDlpManager;

    public PlayerManager(Bot bot) {
        this.bot = bot;
    }

    public void init() {
        // Prepare yt-dlp for YouTube fallback
        try {
            Path botDir = Paths.get("").toAbsolutePath();
            this.ytDlpManager = new YtDlpManager(
                    botDir,
                    bot.getConfig().getYtDlpDenoPath(),
                    bot.getConfig().getYtDlpCookiesPath()
            );
            this.ytDlpPath = ytDlpManager.prepare();
            ytDlpManager.startAutoUpdate(Duration.ofHours(6));
            logger.info("yt-dlp ready at {}", ytDlpPath);
        } catch (Exception e) {
            logger.error("Failed to initialize yt-dlp. YouTube fallback is disabled.", e);
            this.ytDlpPath = null;
            this.ytDlpManager = null;
        }

        if (bot.getConfig().isNicoNicoEnabled()) {
            registerSourceManager(
                    new NicoAudioSourceManager(
                            bot.getConfig().getNicoNicoEmailAddress(),
                            bot.getConfig().getNicoNicoPassword())
            );
        }

        // Prepare YouTube source options (e.g., remote cipher server)
        YoutubeSourceOptions ytOptions = new YoutubeSourceOptions();
        String cipherUrl = bot.getConfig().getYouTubeCipherUrl();
        String cipherPassword = bot.getConfig().getYouTubeCipherPassword();
        String cipherUserAgent = bot.getConfig().getYouTubeCipherUserAgent();
        if (cipherUrl != null && !cipherUrl.isEmpty()) {
            // Configure remote cipher server compatible with yt-cipher API
            String ua = (cipherUserAgent != null && !cipherUserAgent.isBlank()) ? cipherUserAgent : null;
            ytOptions.setRemoteCipher(cipherUrl, cipherPassword, ua);
            if (ua == null) {
                logger.info("Enabled remote cipher server for YouTube at {}", cipherUrl);
            } else {
                logger.info("Enabled remote cipher server for YouTube at {} using UA {}", cipherUrl, ua);
            }
        }

        registerSourceManager(new YoutubeAudioSourceManager(ytOptions, new Client[] { 
                new Tv(),
                new Web(),
                new MWeb()
                }));

        TransformativeAudioSourceManager.createTransforms(bot.getConfig().getTransforms()).forEach(this::registerSourceManager);
        AudioSourceManagers.registerRemoteSources(this);
        AudioSourceManagers.registerLocalSource(this);

        source(YoutubeAudioSourceManager.class).setPlaylistPageCount(10);

        // YouTube OAuth2 integration with persistent refresh token
        String ytRefreshToken = bot.getConfig().getYouTubeRefreshToken();
        if (ytRefreshToken != null && !ytRefreshToken.isEmpty()) {
            source(YoutubeAudioSourceManager.class).useOauth2(ytRefreshToken, true);
        } else {
            source(YoutubeAudioSourceManager.class).useOauth2(null, false);
        }

        if (getConfiguration().getOpusEncodingQuality() != 10) {
            logger.debug("OpusEncodingQuality is {}, setting quality to 10.", getConfiguration().getOpusEncodingQuality());
            getConfiguration().setOpusEncodingQuality(10);
        }

        if (getConfiguration().getResamplingQuality() != AudioConfiguration.ResamplingQuality.HIGH) {
            logger.debug("ResamplingQuality is {} (not HIGH), setting quality to HIGH.", getConfiguration().getResamplingQuality().name());
            getConfiguration().setResamplingQuality(AudioConfiguration.ResamplingQuality.HIGH);
        }
    }

    public Bot getBot() {
        return bot;
    }

    public boolean hasHandler(Guild guild) {
        return guild.getAudioManager().getSendingHandler() != null;
    }

    public AudioHandler setUpHandler(Guild guild) {
        AudioHandler handler;
        if (guild.getAudioManager().getSendingHandler() == null) {
            AudioPlayer player = createPlayer();
            player.setVolume(bot.getSettingsManager().getSettings(guild).getVolume());
            handler = new AudioHandler(this, guild, player);
            player.addListener(handler);
            player.addListener(new YtDlpExceptionListener(this, player, handler));
            guild.getAudioManager().setSendingHandler(handler);
        } else
            handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        return handler;
    }

    @Override
    public Future<Void> loadItemOrdered(Object orderingKey, String identifier, AudioLoadResultHandler handler) {
        return super.loadItemOrdered(orderingKey, identifier, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                handler.trackLoaded(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                handler.playlistLoaded(playlist);
            }

            @Override
            public void noMatches() {
                if (shouldFallbackToYtDlp(identifier)) {
                    tryFallbackDownload(orderingKey, identifier, handler, null);
                } else {
                    handler.noMatches();
                }
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                if (shouldFallbackToYtDlp(identifier)) {
                    tryFallbackDownload(orderingKey, identifier, handler, exception);
                } else {
                    handler.loadFailed(exception);
                }
            }
        });
    }

    boolean shouldFallbackToYtDlp(String identifier) {
        return ytDlpManager != null && ytDlpManager.detectPlatform(identifier) != FallbackPlatform.NONE;
    }

    private void tryFallbackDownload(Object orderingKey,
                                     String identifier,
                                     AudioLoadResultHandler handler,
                                     FriendlyException cause) {
        if (ytDlpManager == null) {
            handler.loadFailed(cause != null ? cause : new FriendlyException("yt-dlp not initialized", FriendlyException.Severity.SUSPICIOUS, null));
            return;
        }

        FallbackPlatform platform = ytDlpManager.detectPlatform(identifier);
        logger.warn("{} load failed. Falling back to yt-dlp: {}", platform, identifier);
        try {
            YtDlpResult result = ytDlpManager.download(identifier);
            Path out = result.file();
            if (out == null || !Files.isRegularFile(out)) {
                throw new IllegalStateException("yt-dlp output not found: " + out);
            }
            super.loadItemOrdered(orderingKey, out.toAbsolutePath().toString(), new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(AudioTrack track) {
                    applyYtDlpMetadata(track, result);
                    try {
                        handler.trackLoaded(track);
                    } catch (Exception e) {
                        logger.error("Handler trackLoaded failed", e);
                    }
                }

                @Override
                public void playlistLoaded(AudioPlaylist playlist) {
                    if (!playlist.getTracks().isEmpty()) {
                        AudioTrack t = playlist.getTracks().get(0);
                        applyYtDlpMetadata(t, result);
                    }
                    try {
                        handler.playlistLoaded(playlist);
                    } catch (Exception e) {
                        logger.error("Handler playlistLoaded failed", e);
                    }
                }

                @Override
                public void noMatches() {
                    handler.noMatches();
                }

                @Override
                public void loadFailed(FriendlyException exception) {
                    handler.loadFailed(exception);
                }
            });
        } catch (Exception ex) {
            logger.error("yt-dlp fallback failed: {}", ex.toString());
            String platformLabel = platform == null ? "Unknown" : platform.name();
            if (cause != null) {
                handler.loadFailed(new FriendlyException(
                        platformLabel + " load failed and yt-dlp fallback also failed: " + ex.getMessage(),
                        FriendlyException.Severity.SUSPICIOUS,
                        cause));
            } else {
                handler.loadFailed(new FriendlyException(
                        "No matches and yt-dlp fallback failed: " + ex.getMessage(),
                        FriendlyException.Severity.SUSPICIOUS,
                        ex));
            }
        }
    }

    /**
     * Deletes yt-dlp cache files created for fallback downloads.
     */
    public void clearYtDlpCache() {
        if (ytDlpManager == null) {
            return;
        }

        Path cacheDir = ytDlpManager.getCacheDir();
        if (!Files.isDirectory(cacheDir)) {
            return;
        }

        try (Stream<Path> paths = Files.walk(cacheDir)) {
            paths.sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        if (p.equals(cacheDir)) {
                            return; // keep the cache directory itself
                        }
                        try {
                            Files.deleteIfExists(p);
                        } catch (IOException e) {
                            logger.warn("Failed to delete yt-dlp cache entry {}: {}", p, e.toString());
                        }
                    });

            Files.createDirectories(cacheDir); // ensure directory still exists
            logger.info("Cleared yt-dlp cache at {}", cacheDir);
        } catch (IOException e) {
            logger.warn("Unable to clear yt-dlp cache at {}: {}", cacheDir, e.toString());
        }
    }

    void applyReplacementContext(AudioTrack newTrack, AudioTrack oldTrack) {
        if (newTrack == null || oldTrack == null) {
            return;
        }
        Object ud = oldTrack.getUserData();
        // Preserve existing TrackContext metadata if present in newTrack
        Object newUd = newTrack.getUserData();
        YtDlpMetadata meta = null;
        FallbackPlatform platform = null;
        
        if (newUd instanceof TrackContext) {
            TrackContext tc = (TrackContext) newUd;
            meta = tc.ytMeta;
            platform = tc.platform;
        }
        
        newTrack.setUserData(new TrackContext(oldTrack.getInfo(), ud, meta, platform));
    }

    void applyYtDlpMetadata(AudioTrack track, YtDlpResult result) {
        if (track == null || result == null) {
            return;
        }
        YtDlpMetadata meta = result.metadata();
        AudioTrackInfo base = track.getInfo();
        AudioTrackInfo enriched = buildInfoFromMetadata(base, meta);
        Object currentUserData = track.getUserData();
        track.setUserData(new TrackContext(enriched, currentUserData, meta, meta != null ? meta.platform() : null));
    }

    private AudioTrackInfo buildInfoFromMetadata(AudioTrackInfo base, YtDlpMetadata meta) {
        if (meta == null) {
            return base;
        }
        String title = firstNonNull(meta.title(), defaultTitleFor(meta, base));
        String author = firstNonNull(meta.author(), base != null ? base.author : "");
        long length = meta.durationMs() > 0 ? meta.durationMs() : (base != null ? base.length : 0);
        // Only keep stream flag for true livestream platforms (e.g., Twitch/YouTube lives); VOD-only platforms are forced to VOD
        boolean isStream;
        if (meta.platform() == FallbackPlatform.TWITCH || meta.platform() == FallbackPlatform.YOUTUBE) {
            isStream = meta.durationMs() <= 0 && base != null && base.isStream;
        } else {
            isStream = false;
        }
        String uri = firstNonNull(meta.webpageUrl(), base != null ? base.uri : null);
        String identifier = firstNonNull(meta.webpageUrl(), base != null ? base.identifier : uri);
        String artwork = firstNonNull(meta.thumbnailUrl(), base != null ? base.artworkUrl : null);
        String isrc = base != null ? base.isrc : null;
        return new AudioTrackInfo(title, author, length, identifier, isStream, uri, artwork, isrc);
    }

    private String defaultTitleFor(YtDlpMetadata meta, AudioTrackInfo base) {
        FallbackPlatform p = meta != null ? meta.platform() : null;
        String shortTag = "yt";
        if (p != null) {
            switch (p) {
                case INSTAGRAM -> shortTag = "insta";
                case TIKTOK -> shortTag = "tiktok";
                case TWITTER -> shortTag = "twitter";
                case TWITCH -> shortTag = "twitch";
                case BILIBILI -> shortTag = "bilibili";
                case VIMEO -> shortTag = "vimeo";
                case SOUNDCLOUD -> shortTag = "soundcloud";
                case YOUTUBE -> shortTag = "youtube";
                default -> shortTag = "source";
            }
        }
        String author = meta != null ? meta.author() : null;
        if ((author == null || author.isBlank()) && base != null) {
            author = base.author;
        }
        if (author != null && !author.isBlank()) {
            return author + " - [" + shortTag + "]";
        }
        return "[" + shortTag + "]";
    }

    private String firstNonNull(String a, String b) {
        return (a != null && !a.isBlank()) ? a : b;
    }

    // Changed from private to package-private (default) so QueuedTrack can access it
    static final class TrackContext {
        final AudioTrackInfo originalInfo;
        final Object userData;
        final YtDlpMetadata ytMeta;
        final FallbackPlatform platform;

        TrackContext(AudioTrackInfo info, Object userData, YtDlpMetadata meta, FallbackPlatform platform) {
            this.originalInfo = info;
            this.userData = userData;
            this.ytMeta = meta;
            this.platform = platform;
        }
    }

    public static AudioTrackInfo getDisplayInfo(AudioTrack track) {
        if (track == null) {
            return null;
        }
        Object ud = track.getUserData();
        if (ud instanceof TrackContext) {
            TrackContext tc = (TrackContext) ud;
            if (tc.originalInfo != null) {
                return tc.originalInfo;
            }
        }
        return track.getInfo();
    }

    public static YtDlpMetadata getYtDlpMetadata(AudioTrack track) {
        if (track == null) {
            return null;
        }
        Object ud = track.getUserData();
        if (ud instanceof TrackContext) {
            return ((TrackContext) ud).ytMeta;
        }
        return null;
    }

    public static FallbackPlatform getYtDlpPlatform(AudioTrack track) {
        if (track == null) {
            return null;
        }
        Object ud = track.getUserData();
        if (ud instanceof TrackContext) {
            return ((TrackContext) ud).platform;
        }
        return null;
    }

    private static class YtDlpExceptionListener extends AudioEventAdapter {
        private final PlayerManager pm;
        private final AudioPlayer player;
        private final AudioHandler handler;
        private final AtomicBoolean fallingBack = new AtomicBoolean(false);
        private final Set<String> attempted = Collections.synchronizedSet(new HashSet<>());

        YtDlpExceptionListener(PlayerManager pm, AudioPlayer player, AudioHandler handler) {
            this.pm = pm;
            this.player = player;
            this.handler = handler;
        }

        @Override
        public void onTrackException(AudioPlayer player, AudioTrack track, FriendlyException exception) {
            String id = track != null ? track.getIdentifier() : null;
            pm.logger.warn("Playback exception. id={} msg={}", id, exception.getMessage());
            if (track == null || !pm.shouldFallbackToYtDlp(id)) {
                return;
            }

            handler.suppressAutoLeaveOnce();

            if (!attempted.add(id)) {
                pm.logger.debug("Fallback already attempted for this track: {}", id);
                return;
            }
            if (!fallingBack.compareAndSet(false, true)) {
                return;
            }

            CompletableFuture.runAsync(() -> {
                try {
                    if (pm.ytDlpManager == null) {
                        pm.logger.warn("yt-dlp manager missing; cannot fallback for id={}", id);
                        return;
                    }
                    YtDlpResult result = pm.ytDlpManager.download(id);
                    Path out = result.file();
                    if (out == null || !Files.isRegularFile(out)) {
                        throw new IllegalStateException("yt-dlp output missing: " + out);
                    }
                    pm.logger.info("yt-dlp fallback succeeded; replacing with local track: {}", out);

                    pm.loadItemOrdered(handler, out.toAbsolutePath().toString(), new AudioLoadResultHandler() {
                        @Override
                        public void trackLoaded(AudioTrack newTrack) {
                            pm.applyYtDlpMetadata(newTrack, result);
                            pm.applyReplacementContext(newTrack, track);
                            player.startTrack(newTrack, false);
                        }

                        @Override
                        public void playlistLoaded(AudioPlaylist playlist) {
                            AudioTrack t = playlist.getTracks().isEmpty() ? null : playlist.getTracks().get(0);
                            if (t != null) {
                                pm.applyYtDlpMetadata(t, result);
                                pm.applyReplacementContext(t, track);
                                player.startTrack(t, false);
                            } else {
                                noMatches();
                            }
                        }

                        @Override
                        public void noMatches() {
                            pm.logger.error("Local replacement load failed (noMatches): {}", out);
                        }

                        @Override
                        public void loadFailed(FriendlyException e) {
                            pm.logger.error("Local replacement load failed: {}", e.getMessage());
                        }
                    });
                } catch (Exception ex) {
                    pm.logger.error("yt-dlp fallback during playback failed: {}", ex.toString());
                } finally {
                    fallingBack.set(false);
                }
            });
        }

        @Override
        public void onTrackStuck(AudioPlayer player, AudioTrack track, long thresholdMs) {
            if (track == null) {
                return;
            }
            String id = track.getIdentifier();
            if (pm.shouldFallbackToYtDlp(id)) {
                pm.logger.warn("Track stuck; trying yt-dlp fallback: id={}, stuck={}ms", id, thresholdMs);
                onTrackException(player, track, new FriendlyException("stuck " + thresholdMs + "ms", FriendlyException.Severity.SUSPICIOUS, null));
            }
        }
    }
}
