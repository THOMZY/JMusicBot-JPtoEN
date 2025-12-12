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
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.*;
import dev.lavalink.youtube.clients.skeleton.Client;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class PlayerManager extends DefaultAudioPlayerManager {
    private final Bot bot;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());
    private Path ytDlpPath;

    public PlayerManager(Bot bot) {
        this.bot = bot;
    }

    public void init() {
        // Prepare yt-dlp for YouTube fallback
        try {
            Path botDir = Paths.get("").toAbsolutePath();
            YtDlpManager y = new YtDlpManager(botDir);
            this.ytDlpPath = y.prepare();
            y.startAutoUpdate(Duration.ofHours(6));
            logger.info("yt-dlp ready at {}", ytDlpPath);
        } catch (Exception e) {
            logger.error("Failed to initialize yt-dlp. YouTube fallback is disabled.", e);
            this.ytDlpPath = null;
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

        registerSourceManager(new YoutubeAudioSourceManager(ytOptions, new Client[] { new Music(),
                new TvHtml5Embedded(),
                new AndroidMusic(),
                new Web(),
                new WebEmbedded(),
                new Android(),
                new Ios()
        }));

        TransformativeAudioSourceManager.createTransforms(bot.getConfig().getTransforms()).forEach(this::registerSourceManager);
        AudioSourceManagers.registerRemoteSources(this);
        AudioSourceManagers.registerLocalSource(this);

        source(YoutubeAudioSourceManager.class).setPlaylistPageCount(10);

        // YouTube OAuth2 integration with persistent refresh token
        String ytRefreshToken = bot.getConfig().getYouTubeRefreshToken();
        if (ytRefreshToken != null && !ytRefreshToken.isEmpty()) {
            source(YoutubeAudioSourceManager.class).useOauth2(ytRefreshToken, false);
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
        if (ytDlpPath == null || identifier == null) {
            return false;
        }
        String id = identifier.toLowerCase(Locale.ROOT);
        if (id.startsWith("ytsearch:")) {
            return false;
        }
        if (id.startsWith("http://") || id.startsWith("https://")) {
            return id.contains("youtube.com/") || id.contains("youtu.be/");
        }
        return id.matches("^[a-zA-Z0-9_-]{10,}$");
    }

    private void tryFallbackDownload(Object orderingKey,
                                     String identifier,
                                     AudioLoadResultHandler handler,
                                     FriendlyException cause) {
        logger.warn("YouTube load failed. Falling back to yt-dlp: {}", identifier);
        try {
            Path out = downloadViaYtDlp(identifier);
            if (out == null || !Files.isRegularFile(out)) {
                throw new IllegalStateException("yt-dlp output not found: " + out);
            }
            super.loadItemOrdered(orderingKey, out.toAbsolutePath().toString(), handler);
        } catch (Exception ex) {
            logger.error("yt-dlp fallback failed: {}", ex.toString());
            if (cause != null) {
                handler.loadFailed(new FriendlyException(
                        "YouTube load failed and yt-dlp fallback also failed: " + ex.getMessage(),
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

    Path downloadViaYtDlp(String input) throws Exception {
        Path botRoot = Paths.get("").toAbsolutePath().normalize();
        Path cacheDir = botRoot.resolve("yt-dlp").resolve("cache");
        Files.createDirectories(cacheDir);

        String url = toYoutubeUrl(input);
        logger.info("Downloading via yt-dlp: {}", url);

        List<String> cmd = new ArrayList<>();
        cmd.add(ytDlpPath.toString());
        Collections.addAll(cmd,
                "--no-playlist",
                "--ignore-config",
                "--no-progress",
                "--newline",
                "--restrict-filenames",
                "--force-overwrites",
                "-f", "bestaudio[ext=webm][acodec=opus]/bestaudio[ext=m4a]/bestaudio",
                "--no-post-overwrites",
                "--output", cacheDir.resolve("%(id)s.%(ext)s").toString(),
                "--print", "after_move:filepath");
        cmd.add(url);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(botRoot.toFile());
        pb.redirectErrorStream(true);
        pb.environment().put("PYTHONIOENCODING", "utf-8");

        Process proc = pb.start();
        String lastNonEmpty = null;
        try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.isBlank()) {
                    lastNonEmpty = line.trim();
                }
                logger.debug("[yt-dlp] {}", line);
            }
        }

        if (!proc.waitFor(600, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            throw new RuntimeException("yt-dlp timeout (600s)");
        }
        if (proc.exitValue() != 0) {
            throw new RuntimeException("yt-dlp exit code=" + proc.exitValue());
        }

        if (lastNonEmpty == null) {
            String id = tryExtractYoutubeId(url);
            if (id != null) {
                Path guessWebm = cacheDir.resolve(id + ".webm");
                if (Files.isRegularFile(guessWebm)) {
                    return guessWebm;
                }
                Path guessM4a = cacheDir.resolve(id + ".m4a");
                if (Files.isRegularFile(guessM4a)) {
                    return guessM4a;
                }
            }
            throw new FileNotFoundException("yt-dlp output is unknown");
        }

        Path out = Paths.get(lastNonEmpty);
        if (!out.isAbsolute()) {
            out = botRoot.resolve(out).normalize();
        }
        if (!Files.isRegularFile(out)) {
            throw new FileNotFoundException("yt-dlp output missing: " + out);
        }
        logger.info("yt-dlp finished: {}", out);
        return out;
    }

    private String toYoutubeUrl(String input) {
        String s = input == null ? "" : input.trim();
        if (s.startsWith("http://") || s.startsWith("https://")) {
            return s;
        }
        return "https://www.youtube.com/watch?v=" + s;
    }

    private String tryExtractYoutubeId(String url) {
        if (url == null) {
            return null;
        }
        try {
            int vIndex = url.indexOf("v=");
            if (vIndex >= 0) {
                String v = url.substring(vIndex + 2);
                int amp = v.indexOf('&');
                return amp > 0 ? v.substring(0, amp) : v;
            }
            int idx = url.indexOf("youtu.be/");
            if (idx >= 0) {
                String v = url.substring(idx + "youtu.be/".length());
                int q = v.indexOf('?');
                return q > 0 ? v.substring(0, q) : v;
            }
            idx = url.indexOf("/shorts/");
            if (idx >= 0) {
                String v = url.substring(idx + "/shorts/".length());
                int q = v.indexOf('?');
                return q > 0 ? v.substring(0, q) : v;
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    void applyReplacementContext(AudioTrack newTrack, AudioTrack oldTrack) {
        if (newTrack == null || oldTrack == null) {
            return;
        }
        Object ud = oldTrack.getUserData();
        newTrack.setUserData(new TrackContext(oldTrack.getInfo(), ud));
    }

    static final class TrackContext {
        final AudioTrackInfo originalInfo;
        final Object userData;

        TrackContext(AudioTrackInfo info, Object userData) {
            this.originalInfo = info;
            this.userData = userData;
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
                    Path out = pm.downloadViaYtDlp(id);
                    if (out == null || !Files.isRegularFile(out)) {
                        throw new IllegalStateException("yt-dlp output missing: " + out);
                    }
                    pm.logger.info("yt-dlp fallback succeeded; replacing with local track: {}", out);

                    pm.loadItemOrdered(handler, out.toAbsolutePath().toString(), new AudioLoadResultHandler() {
                        @Override
                        public void trackLoaded(AudioTrack newTrack) {
                            pm.applyReplacementContext(newTrack, track);
                            player.startTrack(newTrack, false);
                        }

                        @Override
                        public void playlistLoaded(AudioPlaylist playlist) {
                            AudioTrack t = playlist.getTracks().isEmpty() ? null : playlist.getTracks().get(0);
                            if (t != null) {
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
