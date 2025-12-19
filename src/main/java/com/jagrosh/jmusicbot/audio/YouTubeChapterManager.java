/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.audio;

import com.jagrosh.jmusicbot.utils.YouTubeChapterExtractor;
import com.jagrosh.jmusicbot.audio.PlayerManager.TrackContext;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Class to manage YouTube chapters for tracks.
 * Provides caching to avoid repeated API calls.
 */
public class YouTubeChapterManager {
    private static final Logger log = LoggerFactory.getLogger(YouTubeChapterManager.class);
    
    private final Map<String, List<YouTubeChapterExtractor.Chapter>> chapterCache = new ConcurrentHashMap<>();
    private final ScheduledExecutorService cleanupExecutor = Executors.newSingleThreadScheduledExecutor();
    
    public YouTubeChapterManager() {
        // Schedule periodic cleanup of the chapter cache
        cleanupExecutor.scheduleAtFixedRate(this::cleanupCache, 1, 1, TimeUnit.HOURS);
    }

    /**
     * Get chapters for a YouTube track. Will use cached values if available.
     * @param track The audio track
     * @return List of chapters or empty list if none or not a YouTube track
     */
    public List<YouTubeChapterExtractor.Chapter> getChapters(AudioTrack track) {
        if (track == null) {
            return List.of();
        }

        // Prefer canonical YouTube video id when available, but fall back to full URL (yt-dlp cases)
        String videoId = extractYoutubeVideoId(track);
        String youtubeUrl = resolveYoutubeUrl(track, videoId);
        String cacheKey = videoId != null ? videoId : youtubeUrl;
        if (cacheKey == null || cacheKey.isEmpty()) {
            return List.of();
        }

        // Check cache first
        if (chapterCache.containsKey(cacheKey)) {
            return chapterCache.get(cacheKey);
        }

        List<YouTubeChapterExtractor.Chapter> chapters = List.of();

        // 1) Try built-in HTML scraper
        if (videoId != null) {
            chapters = YouTubeChapterExtractor.extractChapters(videoId);
        }

        // 2) Fallback to yt-dlp JSON when HTML scraping fails or when only a URL is known
        if (chapters.isEmpty() && youtubeUrl != null) {
            chapters = YouTubeChapterExtractor.extractChaptersWithYtDlp(youtubeUrl);
        }

        if (!chapters.isEmpty()) {
            chapterCache.put(cacheKey, chapters);
        }

        return chapters;
    }

    /**
     * Get current chapter for a YouTube track based on the current playback position.
     * @param track The audio track
     * @param positionMs The current position in milliseconds
     * @return The current chapter or null if not found or not a YouTube track
     */
    public YouTubeChapterExtractor.Chapter getCurrentChapter(AudioTrack track, long positionMs) {
        if (track == null) {
            return null;
        }
        
        List<YouTubeChapterExtractor.Chapter> chapters = getChapters(track);
        if (chapters.isEmpty()) {
            return null;
        }
        
        return YouTubeChapterExtractor.getCurrentChapter(chapters, positionMs);
    }

    /**
     * Extract YouTube video ID from an audio track
     * @param track The audio track
     * @return The video ID or null if not found
     */
    private String extractYoutubeVideoId(AudioTrack track) {
        if (track == null) {
            return null;
        }

        String candidate = track.getIdentifier();

        // If the identifier is a full URL, extract the video ID from it
        String parsed = parseVideoId(candidate);
        if (parsed != null) {
            return parsed;
        }

        // Some yt-dlp fallbacks keep the original YouTube URL in the AudioTrackInfo uri
        if (track.getInfo() != null && track.getInfo().uri != null) {
            parsed = parseVideoId(track.getInfo().uri);
            if (parsed != null) {
                return parsed;
            }
        }

        // As a last resort, see if TrackContext has yt-dlp metadata with the page URL
        Object ud = track.getUserData();
        if (ud instanceof TrackContext tc && tc.ytMeta != null && tc.ytMeta.webpageUrl() != null) {
            return parseVideoId(tc.ytMeta.webpageUrl());
        }

        return null;
    }

    private String parseVideoId(String value) {
        if (value == null) {
            return null;
        }
        String videoId = value;
        if (videoId.contains("?v=")) {
            videoId = videoId.substring(videoId.indexOf("?v=") + 3);
            if (videoId.contains("&")) {
                videoId = videoId.substring(0, videoId.indexOf("&"));
            }
            return videoId;
        }
        if (videoId.contains("youtu.be/")) {
            videoId = videoId.substring(videoId.indexOf("youtu.be/") + 9);
            if (videoId.contains("?")) {
                videoId = videoId.substring(0, videoId.indexOf("?"));
            }
            return videoId;
        }
        // Not a recognized YouTube pattern
        return null;
    }

    private String resolveYoutubeUrl(AudioTrack track, String videoId) {
        if (videoId != null && !videoId.isEmpty()) {
            return "https://www.youtube.com/watch?v=" + videoId;
        }
        if (track != null && track.getInfo() != null && track.getInfo().uri != null) {
            String uri = track.getInfo().uri;
            if (uri.contains("youtube.com/") || uri.contains("youtu.be/")) {
                return uri;
            }
        }

        Object ud = track != null ? track.getUserData() : null;
        if (ud instanceof TrackContext tc && tc.ytMeta != null && tc.ytMeta.webpageUrl() != null) {
            return tc.ytMeta.webpageUrl();
        }
        return null;
    }

    /**
     * Clean up old entries from the chapter cache
     */
    private void cleanupCache() {
        try {
            // We'll simply clear the entire cache periodically
            // A more sophisticated approach would track usage time
            log.debug("Clearing YouTube chapter cache");
            chapterCache.clear();
        } catch (Exception e) {
            log.error("Error during chapter cache cleanup", e);
        }
    }

    /**
     * Shutdown the executor service
     */
    public void shutdown() {
        cleanupExecutor.shutdownNow();
    }
} 