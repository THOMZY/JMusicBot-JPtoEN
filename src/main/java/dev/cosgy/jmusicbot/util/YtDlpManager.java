/*
 *  Copyright 2025 Cosgy Dev (info@cosgy.dev).
 *  https://github.com/Cosgy-Dev/JMusicBot-JP/blob/0c93918fc282592d820eec30a7bc9ff90bc94749/src/main/java/dev/cosgy/jmusicbot/util/YtDlpManager.java
 *  Thx Cosby and sorry for this shitty fork. <3
 */

package dev.cosgy.jmusicbot.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
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
import java.util.Set;
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
    private final Path binDir;
    private final Path exePath;
    private final String assetName;

    public YtDlpManager(Path botDir) {
        Path baseDir = Objects.requireNonNull(botDir, "botDir").resolve(ROOT_DIR);
        this.binDir = baseDir.resolve("bin");
        this.assetName = pickAssetForCurrentPlatform();
        this.exePath = binDir.resolve(assetNameForLocal(assetName));
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
        log.info("yt-dlp ready: {} (version {})", exePath, version);
        return exePath;
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
}
