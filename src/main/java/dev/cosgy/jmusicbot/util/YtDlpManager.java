/*
 *  Copyright 2025 Cosgy Dev (info@cosgy.dev).
 *  https://github.com/Cosgy-Dev/JMusicBot-JP/blob/0c93918fc282592d820eec30a7bc9ff90bc94749/src/main/java/dev/cosgy/jmusicbot/util/YtDlpManager.java
 *  Thx Cosby and sorry for this shitty fork. <3
 */

package dev.cosgy.jmusicbot.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.time.Duration;
import java.util.Locale;
import java.util.Objects;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Minimal yt-dlp bootstrapper used as a fallback when YouTube loading fails.
 * Downloads the yt-dlp binary for the current platform into {@code bin/},
 * verifies it starts, and optionally runs periodic self-updates.
 */
public final class YtDlpManager {
    private static final Logger log = LoggerFactory.getLogger(YtDlpManager.class);

    private static final String GITHUB_LATEST_BASE = "https://github.com/yt-dlp/yt-dlp/releases/latest/download/";
    private static final int PROC_TIMEOUT_SEC = 120;

    private static final ScheduledExecutorService SCHEDULER = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "yt-dlp-auto-update");
        t.setDaemon(true);
        return t;
    });

    private static final String ROOT_DIR = "yt-dlp";
    private final Path botRoot;
    private final Path binDir;
    private final Path exePath;
    private final String assetName;
    private volatile Path preparedExe;
    private final Map<FallbackPlatform, List<String>> extractorArgs = new HashMap<>();
    private final Map<FallbackPlatform, String> formatOverrides = new HashMap<>();

    public YtDlpManager(Path botDir) {
        this.botRoot = Objects.requireNonNull(botDir, "botDir").toAbsolutePath().normalize();
        Path baseDir = botRoot.resolve(ROOT_DIR);
        this.binDir = baseDir.resolve("bin");
        this.assetName = pickAssetForCurrentPlatform();
        this.exePath = binDir.resolve(assetNameForLocal(assetName));

        // Minimal sensible defaults; can be extended later or sourced from config
        extractorArgs.put(FallbackPlatform.TIKTOK, List.of("--extractor-args", "tiktok:player_client=web,download=h264-yes"));
        extractorArgs.put(FallbackPlatform.TWITTER, List.of("--extractor-args", "twitter:player=desktop"));
        extractorArgs.put(FallbackPlatform.BILIBILI, List.of("--extractor-args", "bilibili:lang=zh-Hans"));
        extractorArgs.put(FallbackPlatform.INSTAGRAM, List.of("--extractor-args", "instagram:retry_download_errors=3"));

        // Formats: prefer audio-only when available; otherwise fall back to best
        formatOverrides.put(FallbackPlatform.TIKTOK, "bestaudio/best");
        formatOverrides.put(FallbackPlatform.TWITTER, "bestaudio/best");
        formatOverrides.put(FallbackPlatform.INSTAGRAM, "bestaudio/best");
        formatOverrides.put(FallbackPlatform.BILIBILI, "bestaudio/best");
        formatOverrides.put(FallbackPlatform.VIMEO, "bestaudio/best");
        formatOverrides.put(FallbackPlatform.TWITCH, "bestaudio/best");
    }

    /**
     * Ensures yt-dlp exists and is runnable. Downloads it when missing or broken.
     * @return absolute path to the prepared yt-dlp executable
     */
    public Path prepare() throws Exception {
        Files.createDirectories(binDir);

        boolean needsDownload = !Files.isRegularFile(exePath);
        if (!needsDownload) {
            log.debug("Existing yt-dlp found, verifying: {}", exePath);
            if (!isExecutableOk(exePath)) {
                log.warn("Existing yt-dlp failed verification, redownloading");
                needsDownload = true;
            }
        }

        if (needsDownload) {
            downloadBinary(assetName, exePath);
            grantExecuteIfNeeded(exePath);
        }

        String version = runAndCapture(exePath.toString(), "--version").trim();
        this.preparedExe = exePath;
        log.info("yt-dlp ready: {} (version {})", exePath, version);
        return exePath;
    }

    public boolean isReady() {
        Path p = preparedExe;
        return p != null && Files.isRegularFile(p);
    }

    public YtDlpResult download(String input) throws Exception {
        FallbackPlatform platform = detectPlatform(input);
        if (platform == FallbackPlatform.NONE) {
            throw new IllegalArgumentException("Unsupported fallback platform for: " + input);
        }
        Path cacheDir = getCacheDir();
        Files.createDirectories(cacheDir);

        String url = toFallbackUrl(input, platform);
        log.info("Downloading via yt-dlp ({}): {}", platform, url);

        List<String> cmd = new ArrayList<>();
        cmd.add(exePath.toString());

        String format = formatOverrides.getOrDefault(platform, "bestaudio[ext=webm][acodec=opus]/bestaudio[ext=m4a]/bestaudio");
        Collections.addAll(cmd,
            "--no-playlist",
            "--ignore-config",
            "--no-progress",
            "--newline",
            "--restrict-filenames",
            "--force-overwrites",
            "-f", format,
            "--no-post-overwrites",
            "--encoding", "utf-8",
            "--output", cacheDir.resolve("%(id)s.%(ext)s").toString(),
            "--print", "after_move:filepath",
            "--print", "meta:%(title)s\t%(uploader)s\t%(webpage_url)s\t%(duration)s\t%(thumbnail)s\t%(description)s",
            "--add-header", "User-Agent: Mozilla/5.0");

        List<String> extra = extractorArgs.getOrDefault(platform, List.of());
        cmd.addAll(extra);
        cmd.add(url);

        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(botRoot.toFile());
        pb.redirectErrorStream(true);
        pb.environment().put("PYTHONIOENCODING", "utf-8");
        // Ensure console encoding for Windows compatibility
        if (System.getProperty("os.name").toLowerCase().contains("win")) {
            pb.environment().put("PYTHONUTF8", "1");
        }

        Process proc = pb.start();
        String lastNonEmpty = null;
        YtDlpMetadata metadata = null;
        List<String> combinedOutput = new ArrayList<>();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                if (!line.isBlank()) {
                    lastNonEmpty = line.trim();
                }
                if (line.startsWith("meta:")) {
                    metadata = parseMetadataLine(line.substring("meta:".length()), platform);
                }
                combinedOutput.add(line);
                log.debug("[yt-dlp] {}", line);
            }
        }

        if (!proc.waitFor(600, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            throw new RuntimeException("yt-dlp timeout (600s)");
        }
        if (proc.exitValue() != 0) {
            String tail = combinedOutput.size() > 10
                    ? String.join("\n", combinedOutput.subList(Math.max(0, combinedOutput.size() - 10), combinedOutput.size()))
                    : String.join("\n", combinedOutput);
            throw new RuntimeException("yt-dlp exit code=" + proc.exitValue() + " tail=" + tail);
        }

        if (lastNonEmpty == null) {
            if (platform == FallbackPlatform.YOUTUBE) {
                String id = tryExtractYoutubeId(url);
                if (id != null) {
                    Path guessWebm = cacheDir.resolve(id + ".webm");
                    if (Files.isRegularFile(guessWebm)) {
                        return new YtDlpResult(guessWebm, metadata != null ? metadata : new YtDlpMetadata(null, null, url, null, null, -1, platform));
                    }
                    Path guessM4a = cacheDir.resolve(id + ".m4a");
                    if (Files.isRegularFile(guessM4a)) {
                        return new YtDlpResult(guessM4a, metadata != null ? metadata : new YtDlpMetadata(null, null, url, null, null, -1, platform));
                    }
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
        log.info("yt-dlp finished: {}", out);
        return new YtDlpResult(out, metadata != null ? metadata : new YtDlpMetadata(null, null, url, null, null, -1, platform));
    }

    public FallbackPlatform detectPlatform(String identifier) {
        if (!isReady() || identifier == null) {
            return FallbackPlatform.NONE;
        }
        String id = identifier.toLowerCase(Locale.ROOT);
        if (id.startsWith("ytsearch:") || id.startsWith("scsearch:")) {
            return FallbackPlatform.NONE;
        }

        boolean isHttp = id.startsWith("http://") || id.startsWith("https://");
        if (isHttp) {
            if (id.contains("soundcloud.com/") || id.contains("sndcdn.com/") || id.contains("on.soundcloud.com/")) {
                return FallbackPlatform.SOUNDCLOUD;
            }
            if (id.contains("youtube.com/") || id.contains("youtu.be/")) {
                return FallbackPlatform.YOUTUBE;
            }
            if (id.contains("instagram.com/") || id.contains("instagr.am/")) {
                return FallbackPlatform.INSTAGRAM;
            }
            if (id.contains("tiktok.com/")) {
                return FallbackPlatform.TIKTOK;
            }
            if (id.contains("twitter.com/") || id.contains("x.com/")) {
                return FallbackPlatform.TWITTER;
            }
            if (id.contains("twitch.tv/")) {
                return FallbackPlatform.TWITCH;
            }
            if (id.contains("bilibili.com/") || id.contains("b23.tv/")) {
                return FallbackPlatform.BILIBILI;
            }
            if (id.contains("vimeo.com/") || id.contains("player.vimeo.com/")) {
                return FallbackPlatform.VIMEO;
            }
        }

        if (id.matches("^[a-zA-Z0-9_-]{10,}$")) {
            return FallbackPlatform.YOUTUBE;
        }

        return FallbackPlatform.NONE;
    }

    public Path getCacheDir() {
        return botRoot.resolve(ROOT_DIR).resolve("cache");
    }

    /**
     * Starts a lightweight auto-update loop. Errors are logged but ignored.
     */
    public void startAutoUpdate(Duration interval) {
        Duration effective = interval == null ? Duration.ofHours(24) : interval;
        long period = Math.max(300, effective.toSeconds());
        SCHEDULER.scheduleAtFixedRate(() -> {
            try {
                runUpdateCommand(exePath);
            } catch (Exception e) {
                log.debug("yt-dlp auto-update failed: {}", e.toString());
            }
        }, period, period, TimeUnit.SECONDS);
    }

    private void downloadBinary(String asset, Path dest) throws Exception {
        log.info("Downloading yt-dlp binary: {}", asset);
        HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.ALWAYS).build();

        URI uri = URI.create(GITHUB_LATEST_BASE + asset);
        HttpResponse<InputStream> response = client.send(
                HttpRequest.newBuilder(uri).GET().build(),
                HttpResponse.BodyHandlers.ofInputStream());

        if (response.statusCode() >= 400) {
            throw new IOException("Failed to download yt-dlp: HTTP " + response.statusCode());
        }

        Path tmp = Files.createTempFile("yt-dlp-", ".dl");
        try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(tmp)) {
            byte[] buf = new byte[8192];
            int r;
            while ((r = in.read(buf)) > 0) {
                out.write(buf, 0, r);
            }
        }

        Files.move(tmp, dest, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        grantExecuteIfNeeded(dest);
        log.info("yt-dlp downloaded to {}", dest);
    }

    private static void runUpdateCommand(Path exe) throws Exception {
        if (exe == null || !Files.isRegularFile(exe)) return;
        log.info("Checking for yt-dlp updates...");
        runAndCaptureWithTimeout(600, exe.toString(), "-U");
    }

    private static boolean isExecutableOk(Path exe) {
        try {
            String out = runAndCapture(exe.toString(), "--version");
            return !out.isBlank();
        } catch (Exception e) {
            log.debug("yt-dlp verification failed: {}", e.toString());
            return false;
        }
    }

    private static String pickAssetForCurrentPlatform() {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (os.contains("win")) {
            if (arch.contains("aarch64") || arch.contains("arm64")) {
                return "yt-dlp_arm64.exe";
            }
            return "yt-dlp.exe";
        }
        if (os.contains("mac") || os.contains("darwin")) {
            return "yt-dlp_macos";
        }
        return "yt-dlp_linux";
    }

    private static String assetNameForLocal(String asset) {
        return asset.endsWith(".exe") ? "yt-dlp.exe" : "yt-dlp";
    }

    private static void grantExecuteIfNeeded(Path p) throws IOException {
        try {
            Set<PosixFilePermission> perms = Files.getPosixFilePermissions(p);
            if (!perms.contains(PosixFilePermission.OWNER_EXECUTE)) {
                perms.add(PosixFilePermission.OWNER_EXECUTE);
                perms.add(PosixFilePermission.GROUP_EXECUTE);
                perms.add(PosixFilePermission.OTHERS_EXECUTE);
                Files.setPosixFilePermissions(p, perms);
            }
        } catch (UnsupportedOperationException ignored) {
            // Windows/non-POSIX
        }
    }

    private static String runAndCapture(String... cmd) throws Exception {
        Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        byte[] data;
        try (InputStream in = proc.getInputStream()) {
            data = in.readAllBytes();
        }
        if (!proc.waitFor(PROC_TIMEOUT_SEC, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            throw new RuntimeException("Process timeout: " + String.join(" ", cmd));
        }
        return new String(data, StandardCharsets.UTF_8);
    }

    private static String runAndCaptureWithTimeout(int timeoutSec, String... cmd) throws Exception {
        Process proc = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        StringBuilder sb = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        if (!proc.waitFor(timeoutSec, TimeUnit.SECONDS)) {
            proc.destroyForcibly();
            throw new RuntimeException("Process timeout: " + String.join(" ", cmd));
        }
        return sb.toString();
    }

    private String toFallbackUrl(String input, FallbackPlatform platform) {
        String s = input == null ? "" : input.trim();
        if (platform == FallbackPlatform.YOUTUBE) {
            if (s.startsWith("http://") || s.startsWith("https://")) {
                return s;
            }
            return "https://www.youtube.com/watch?v=" + s;
        }

        if (s.startsWith("http://") || s.startsWith("https://")) {
            return s;
        }

        throw new IllegalArgumentException(platform + " fallback requires a valid URL: " + input);
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

    public enum FallbackPlatform {
        NONE,
        YOUTUBE,
        SOUNDCLOUD,
        INSTAGRAM,
        TIKTOK,
        TWITTER,
        TWITCH,
        BILIBILI,
        VIMEO
    }

    public record YtDlpMetadata(String title,
                                 String author,
                                 String webpageUrl,
                                 String thumbnailUrl,
                                 String description,
                                 long durationMs,
                                 FallbackPlatform platform) { }

    public record YtDlpResult(Path file, YtDlpMetadata metadata) { }

    private YtDlpMetadata parseMetadataLine(String raw, FallbackPlatform platform) {
        try {
            String[] parts = raw.split("\t", -1);
            String title = parts.length > 0 ? nullIfBlank(parts[0]) : null;
            String author = parts.length > 1 ? nullIfBlank(parts[1]) : null;
            String webpage = parts.length > 2 ? nullIfBlank(parts[2]) : null;
            String durationStr = parts.length > 3 ? parts[3] : null;
            String thumb = parts.length > 4 ? nullIfBlank(parts[4]) : null;
            String desc = parts.length > 5 ? nullIfBlank(parts[5]) : null;
            long durationMs = -1;
            if (durationStr != null && !durationStr.isBlank()) {
                try {
                    durationMs = (long) (Double.parseDouble(durationStr) * 1000);
                } catch (NumberFormatException ignored) {
                    durationMs = -1;
                }
            }
            return new YtDlpMetadata(title, author, webpage, thumb, desc, durationMs, platform);
        } catch (Exception e) {
            log.debug("Failed to parse yt-dlp metadata line: {}", e.toString());
            return new YtDlpMetadata(null, null, null, null, null, -1, platform);
        }
    }

    private static String nullIfBlank(String s) {
        return (s == null || s.isBlank()) ? null : s;
    }
}
