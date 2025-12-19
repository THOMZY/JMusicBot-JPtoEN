/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.utils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility class for extracting chapter information from YouTube videos.
 */
public class YouTubeChapterExtractor {
    private static final Logger log = LoggerFactory.getLogger(YouTubeChapterExtractor.class);
    private static final ObjectMapper mapper = new ObjectMapper();
    private static final Pattern CHAPTER_PATTERN = Pattern.compile("\"chapterRenderer\":\\{\"title\":\\{\"simpleText\":\"([^\"]+)\"\\}[^}]*\"timeRangeStartMillis\":([0-9]+)");
    private static final int YT_DLP_TIMEOUT_SECONDS = 30;

    /**
     * Represents a chapter in a YouTube video
     */
    public static class Chapter {
        private final String name;
        private final long startTimeMs;
        private long endTimeMs;

        public Chapter(String name, long startTimeMs) {
            this.name = name;
            this.startTimeMs = startTimeMs;
            this.endTimeMs = 0; // Will be set later
        }

        public String getName() {
            return name;
        }

        public long getStartTimeMs() {
            return startTimeMs;
        }

        public long getEndTimeMs() {
            return endTimeMs;
        }

        public void setEndTimeMs(long endTimeMs) {
            this.endTimeMs = endTimeMs;
        }

        public long getDurationMs() {
            return endTimeMs - startTimeMs;
        }

        @Override
        public String toString() {
            return String.format("%s (%s - %s)",
                    name,
                    FormatUtil.formatTime(startTimeMs),
                    FormatUtil.formatTime(endTimeMs));
        }
    }

    /**
     * Extract chapters from a YouTube video using its ID
     * @param videoId The YouTube video ID
     * @return A list of chapters, or an empty list if none were found or an error occurred
     */
    public static List<Chapter> extractChapters(String videoId) {
        if (videoId == null || videoId.trim().isEmpty()) {
            return Collections.emptyList();
        }

        try {
            String videoUrl = "https://www.youtube.com/watch?v=" + videoId;
            String pageContent = fetchPage(videoUrl);
            if (pageContent == null) {
                return Collections.emptyList();
            }

            List<Chapter> chapters = parseChapters(pageContent);
            if (!chapters.isEmpty()) {
                // Set end times for each chapter
                for (int i = 0; i < chapters.size() - 1; i++) {
                    chapters.get(i).setEndTimeMs(chapters.get(i + 1).getStartTimeMs());
                }
                
                // For the last chapter, we'll try to extract the video duration
                try {
                    Pattern durationPattern = Pattern.compile("\"approxDurationMs\":\"([0-9]+)\"");
                    Matcher durationMatcher = durationPattern.matcher(pageContent);
                    if (durationMatcher.find()) {
                        long durationMs = Long.parseLong(durationMatcher.group(1));
                        chapters.get(chapters.size() - 1).setEndTimeMs(durationMs);
                    }
                } catch (Exception e) {
                    log.warn("Failed to extract video duration for last chapter end time", e);
                    // If we can't get the duration, set the last chapter's end time to a large value
                    if (!chapters.isEmpty()) {
                        chapters.get(chapters.size() - 1).setEndTimeMs(Long.MAX_VALUE);
                    }
                }
            }
            
            return chapters;
        } catch (Exception e) {
            log.error("Error extracting YouTube chapters", e);
            return Collections.emptyList();
        }
    }

    /**
     * Extract chapters using yt-dlp's JSON output (works for yt-dlp-resolved YouTube sources).
     * @param youtubeUrl The canonical YouTube URL for the video.
     * @return List of chapters or empty list on failure.
     */
    public static List<Chapter> extractChaptersWithYtDlp(String youtubeUrl) {
        if (youtubeUrl == null || youtubeUrl.isBlank()) {
            return Collections.emptyList();
        }
        try {
            // Locate the bundled yt-dlp binary (downloaded via YtDlpManager)
            Path ytDlpBinary = Paths.get("yt-dlp", "bin");
            if (!Files.isDirectory(ytDlpBinary)) {
                log.debug("yt-dlp binary directory missing: {}", ytDlpBinary);
                return Collections.emptyList();
            }

            Path exe = Files.list(ytDlpBinary)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().startsWith("yt-dlp"))
                    .findFirst()
                    .orElse(null);

            if (exe == null || !Files.isExecutable(exe)) {
                log.debug("yt-dlp binary not found or not executable; skipping yt-dlp chapter extraction");
                return Collections.emptyList();
            }

            ProcessBuilder pb = new ProcessBuilder(exe.toString(), "--dump-json", "--no-warnings", "--encoding", "utf-8", youtubeUrl);
            pb.redirectErrorStream(true);
            pb.environment().put("PYTHONIOENCODING", "utf-8");
            // Ensure console encoding for Windows compatibility
            if (System.getProperty("os.name").toLowerCase().contains("win")) {
                pb.environment().put("PYTHONUTF8", "1");
            }

            Process proc = pb.start();
            boolean finished = proc.waitFor(YT_DLP_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                proc.destroyForcibly();
                log.debug("yt-dlp chapter extraction timed out");
                return Collections.emptyList();
            }

            if (proc.exitValue() != 0) {
                log.debug("yt-dlp chapter extraction failed with code {}", proc.exitValue());
                return Collections.emptyList();
            }

            String json;
            try (InputStream in = proc.getInputStream()) {
                json = new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
            }

            JsonNode node = mapper.readTree(json);
            JsonNode chapters = node.path("chapters");
            if (!chapters.isArray()) {
                return Collections.emptyList();
            }

            List<Chapter> parsed = new ArrayList<>();
            for (JsonNode c : chapters) {
                String name = c.path("title").asText(null);
                long start = (long) (c.path("start_time").asDouble(-1) * 1000);
                long end = (long) (c.path("end_time").asDouble(-1) * 1000);
                if (name == null || start < 0) {
                    continue;
                }
                Chapter chapter = new Chapter(name, start);
                if (end > 0) {
                    chapter.setEndTimeMs(end);
                }
                parsed.add(chapter);
            }

            // Ensure end times are filled if missing
            if (!parsed.isEmpty()) {
                for (int i = 0; i < parsed.size() - 1; i++) {
                    Chapter current = parsed.get(i);
                    if (current.getEndTimeMs() <= 0) {
                        current.setEndTimeMs(parsed.get(i + 1).getStartTimeMs());
                    }
                }
                if (parsed.get(parsed.size() - 1).getEndTimeMs() <= 0) {
                    long durationMs = node.path("duration").isNumber()
                            ? (long) (node.path("duration").asDouble() * 1000)
                            : Long.MAX_VALUE;
                    parsed.get(parsed.size() - 1).setEndTimeMs(durationMs);
                }
            }

            return parsed;
        } catch (Exception e) {
            log.debug("yt-dlp chapter extraction failed: {}", e.toString());
            return Collections.emptyList();
        }
    }

    /**
     * Fetch the HTML page for a YouTube video
     * @param url The URL of the video
     * @return The page content or null if there was an error
     */
    private static String fetchPage(String url) {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(10000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                log.warn("Error fetching YouTube page: HTTP " + responseCode);
                return null;
            }
            
            // Read the content
            Scanner scanner = new Scanner(connection.getInputStream(), "UTF-8");
            scanner.useDelimiter("\\A");
            String content = scanner.hasNext() ? scanner.next() : "";
            scanner.close();
            
            return content;
        } catch (IOException e) {
            log.warn("Error fetching YouTube page", e);
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Parse the chapter information from the YouTube page HTML
     * @param pageContent The HTML content of the page
     * @return A list of chapters
     */
    private static List<Chapter> parseChapters(String pageContent) {
        List<Chapter> chapters = new ArrayList<>();
        
        Matcher matcher = CHAPTER_PATTERN.matcher(pageContent);
        while (matcher.find()) {
            String chapterName = matcher.group(1);
            long startTimeMs = Long.parseLong(matcher.group(2));
            chapters.add(new Chapter(chapterName, startTimeMs));
        }
        
        return chapters;
    }
    
    /**
     * Get the current chapter for a specific time
     * @param chapters List of chapters
     * @param currentTimeMs Current playback position in milliseconds
     * @return The current chapter or null if not found
     */
    public static Chapter getCurrentChapter(List<Chapter> chapters, long currentTimeMs) {
        if (chapters == null || chapters.isEmpty()) {
            return null;
        }
        
        for (Chapter chapter : chapters) {
            if (currentTimeMs >= chapter.getStartTimeMs() && currentTimeMs < chapter.getEndTimeMs()) {
                return chapter;
            }
        }
        
        // If we're at the very end of the video, return the last chapter
        if (!chapters.isEmpty() && currentTimeMs >= chapters.get(chapters.size() - 1).getStartTimeMs()) {
            return chapters.get(chapters.size() - 1);
        }
        
        return null;
    }
} 