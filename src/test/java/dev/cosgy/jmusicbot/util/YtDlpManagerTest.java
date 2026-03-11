package dev.cosgy.jmusicbot.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class YtDlpManagerTest {
    @Test
    public void picksWindowsBinaryOnX64() {
        String asset = YtDlpManager.pickAssetForPlatform("Windows 11", "amd64");
        assertEquals("yt-dlp.exe", asset);
    }

    @Test
    public void picksWindowsArmBinaryOnArm64() {
        String asset = YtDlpManager.pickAssetForPlatform("Windows 11", "arm64");
        assertEquals("yt-dlp_arm64.exe", asset);
    }

    @Test
    public void picksMacBinaryOnDarwin() {
        String asset = YtDlpManager.pickAssetForPlatform("Darwin", "x86_64");
        assertEquals("yt-dlp_macos", asset);
    }

    @Test
    public void picksMacBinaryOnMacOsName() {
        String asset = YtDlpManager.pickAssetForPlatform("Mac OS X", "aarch64");
        assertEquals("yt-dlp_macos", asset);
    }

    @Test
    public void picksLinuxBinaryOnX64() {
        String asset = YtDlpManager.pickAssetForPlatform("Linux", "amd64");
        assertEquals("yt-dlp_linux", asset);
    }

    @Test
    public void picksLinuxBinaryOnArm64() {
        String asset = YtDlpManager.pickAssetForPlatform("Linux", "arm64");
        assertEquals("yt-dlp_linux_aarch64", asset);
    }

    @Test
    public void picksLinuxBinaryOnBsd() {
        String asset = YtDlpManager.pickAssetForPlatform("FreeBSD", "x86_64");
        assertEquals("yt-dlp", asset);
    }

    @Test
    public void avoidsDarwinBeingDetectedAsWindows() {
        String asset = YtDlpManager.pickAssetForPlatform("Darwin", "arm64");
        assertEquals("yt-dlp_macos", asset);
    }

    @Test
    public void linuxX64CandidatesIncludeGenericFallback() {
        List<String> assets = YtDlpManager.pickAssetCandidatesForPlatform("Linux", "x86_64");
        assertEquals(List.of("yt-dlp_linux", "yt-dlp"), assets);
    }

    @Test
    public void linuxArmCandidatesIncludeGenericFallback() {
        List<String> assets = YtDlpManager.pickAssetCandidatesForPlatform("Linux", "aarch64");
        assertEquals(List.of("yt-dlp_linux_aarch64", "yt-dlp"), assets);
    }

    @Test
    public void youtubeRetryExtrasIncludeIpv4AndPlayerClient() {
        List<List<String>> extras = YtDlpManager.retryExtrasForPlatform(YtDlpManager.FallbackPlatform.YOUTUBE);
        assertEquals(
                List.of(
                        List.of(),
                        List.of("--force-ipv4"),
                        List.of("--extractor-args", "youtube:player_client=tv,ios,web")
                ),
                extras
        );
    }

    @Test
    public void genericRetryExtrasOnlyUseDefaultAttempt() {
        List<List<String>> extras = YtDlpManager.retryExtrasForPlatform(YtDlpManager.FallbackPlatform.GENERIC);
        assertEquals(List.of(List.of()), extras);
    }

    @Test
    public void applyExtraArgsInsertsBeforeUrl() {
        List<String> base = List.of("yt-dlp", "--output", "cache/%(id)s.%(ext)s", "https://youtu.be/test");
        List<String> merged = YtDlpManager.applyExtraArgsBeforeUrl(base, List.of("--force-ipv4"));
        assertEquals(
                List.of("yt-dlp", "--output", "cache/%(id)s.%(ext)s", "--force-ipv4", "https://youtu.be/test"),
                merged
        );
    }
}
