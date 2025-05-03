/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.audio;

import com.jagrosh.jmusicbot.utils.YouTubeChapterExtractor;
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
        
        String videoId = extractYoutubeVideoId(track);
        if (videoId == null || videoId.isEmpty()) {
            return List.of();
        }
        
        // Check cache first
        if (chapterCache.containsKey(videoId)) {
            return chapterCache.get(videoId);
        }
        
        // Extract chapters and cache them
        List<YouTubeChapterExtractor.Chapter> chapters = YouTubeChapterExtractor.extractChapters(videoId);
        if (!chapters.isEmpty()) {
            chapterCache.put(videoId, chapters);
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
        
        String videoId = track.getIdentifier();
        
        // If the identifier is a full URL, extract the video ID from it
        if (videoId.contains("?v=")) {
            videoId = videoId.substring(videoId.indexOf("?v=") + 3);
            if (videoId.contains("&")) {
                videoId = videoId.substring(0, videoId.indexOf("&"));
            }
        } else if (videoId.contains("youtu.be/")) {
            videoId = videoId.substring(videoId.indexOf("youtu.be/") + 9);
            if (videoId.contains("?")) {
                videoId = videoId.substring(0, videoId.indexOf("?"));
            }
        }
        
        return videoId;
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