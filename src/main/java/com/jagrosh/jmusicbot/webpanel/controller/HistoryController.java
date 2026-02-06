/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.controller;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.MusicHistory;
import com.jagrosh.jmusicbot.webpanel.service.AvatarCacheService;
import net.dv8tion.jda.api.entities.User;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.stream.Collectors;

/**
 * Controller for handling history-related API requests
 */
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final AvatarCacheService avatarCacheService;

    public HistoryController(AvatarCacheService avatarCacheService) {
        this.avatarCacheService = avatarCacheService;
    }

    private static boolean containsIgnoreCase(String value, String lowerNeedle) {
        return value != null && !value.isEmpty() && value.toLowerCase().contains(lowerNeedle);
    }

    /**
     * Enriches history records with dynamic user avatars (cached)
     */
    private List<Map<String, Object>> enrichWithAvatars(List<MusicHistory.PlayRecord> records) {
        return records.stream().map(record -> {
            Map<String, Object> enriched = new HashMap<>();
            
            // Copy all basic fields
            enriched.put("title", record.getTitle());
            enriched.put("artist", record.getArtist());
            enriched.put("duration", record.getDuration());
            enriched.put("url", record.getUrl());
            enriched.put("playedAt", record.getPlayedAt());
            enriched.put("requesterId", record.getRequesterId());
            enriched.put("requesterName", record.getRequesterName());
            enriched.put("guildName", record.getGuildName());
            enriched.put("guildId", record.getGuildId());
            enriched.put("formattedPlayedAt", record.getFormattedPlayedAt());
            enriched.put("formattedDuration", record.getFormattedDuration());
            
            // Get avatar from cache service
            String requesterAvatar = avatarCacheService.getAvatarUrl(record.getRequesterId());
            enriched.put("requesterAvatar", requesterAvatar);
            
            // Copy all metadata fields
            if (record.hasSpotifyData()) {
                enriched.put("spotifyTrackId", record.getSpotifyTrackId());
                enriched.put("spotifyAlbumName", record.getSpotifyAlbumName());
                enriched.put("spotifyAlbumImageUrl", record.getSpotifyAlbumImageUrl());
                enriched.put("spotifyArtistName", record.getSpotifyArtistName());
                enriched.put("spotifyReleaseYear", record.getSpotifyReleaseYear());
            }
            
            if (record.hasRadioData()) {
                enriched.put("radioStationName", record.getRadioStationName());
                enriched.put("radioLogoUrl", record.getRadioLogoUrl());
                enriched.put("radioSongImageUrl", record.getRadioSongImageUrl());
            }
            
            if (record.hasYoutubeData()) {
                enriched.put("youtubeVideoId", record.getYoutubeVideoId());
            }
            
            if (record.hasLocalData()) {
                enriched.put("localAlbum", record.getLocalAlbum());
                enriched.put("localGenre", record.getLocalGenre());
                enriched.put("localYear", record.getLocalYear());
                enriched.put("localArtworkHash", record.getLocalArtworkHash());
            }
            
            if (record.hasGensokyoData()) {
                enriched.put("gensokyoTitle", record.getGensokyoTitle());
                enriched.put("gensokyoArtist", record.getGensokyoArtist());
                enriched.put("gensokyoAlbum", record.getGensokyoAlbum());
                enriched.put("gensokyoCircle", record.getGensokyoCircle());
                enriched.put("gensokyoYear", record.getGensokyoYear());
                enriched.put("gensokyoAlbumArtUrl", record.getGensokyoAlbumArtUrl());
            }
            
            if (record.hasStreamData()) {
                enriched.put("streamName", record.getStreamName());
                enriched.put("streamGenre", record.getStreamGenre());
                enriched.put("streamLogo", record.getStreamLogo());
                enriched.put("isLiveStream", record.isLiveStream());
            }
            
            if (record.hasSoundCloudData()) {
                enriched.put("soundCloudArtworkUrl", record.getSoundCloudArtworkUrl());
            }
            
            if (record.hasYtDlpData()) {
                enriched.put("ytDlpSourceType", record.getYtDlpSourceType());
                enriched.put("ytDlpThumbnailUrl", record.getYtDlpThumbnailUrl());
                enriched.put("ytDlpSourceIconUrl", record.getYtDlpSourceIconUrl());
            }
            
            return enriched;
        }).collect(Collectors.toList());
    }

    private static List<MusicHistory.PlayRecord> applyStartEndDateFilter(
            List<MusicHistory.PlayRecord> records,
            String startDate,
            String endDate) {
        if (records == null || records.isEmpty()) return records;
        if (startDate == null || startDate.isEmpty()) return records;

        final String effectiveEnd = (endDate == null || endDate.isEmpty()) ? startDate : endDate;

        final LocalDate start = LocalDate.parse(startDate);
        final LocalDate end = LocalDate.parse(effectiveEnd);
        final LocalDate min = start.isBefore(end) ? start : end;
        final LocalDate max = start.isBefore(end) ? end : start;

        final ZoneId zone = ZoneId.systemDefault();
        final long startMillis = min.atStartOfDay(zone).toInstant().toEpochMilli();
        final long endExclusiveMillis = max.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli();

        return records.stream()
                .filter(r -> r.getPlayedAt() >= startMillis && r.getPlayedAt() < endExclusiveMillis)
                .collect(Collectors.toList());
    }

    /**
     * Get the music history
     * @param limit Optional limit of records to return
     * @param offset Optional offset for pagination
     * @param guildId Optional guild ID to filter by
     * @param type Optional source type to filter by (spotify, youtube, etc.)
     * @param requester Optional requester name to filter by
     * @param timeRange Optional time range to filter by (today, week, month, all)
     * @return List of play records
     */
    @GetMapping("")
    public ResponseEntity<Map<String, Object>> getHistory(
            @RequestParam(value = "limit", required = false, defaultValue = "0") int limit,
            @RequestParam(value = "offset", required = false, defaultValue = "0") int offset,
            @RequestParam(value = "guildId", required = false) String guildId,
            @RequestParam(value = "type", required = false) List<String> types,
            @RequestParam(value = "requester", required = false) String requester,
            @RequestParam(value = "timeRange", required = false) String timeRange,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (Bot.INSTANCE != null && Bot.INSTANCE.getMusicHistory() != null) {
                MusicHistory musicHistory = Bot.INSTANCE.getMusicHistory();
                List<MusicHistory.PlayRecord> allHistory = musicHistory.getHistory();
                List<MusicHistory.PlayRecord> filteredHistory;
                
                // Filter by guild first if specified
                if (guildId != null && !guildId.isEmpty() && !guildId.equals("all")) {
                    filteredHistory = allHistory.stream()
                            .filter(record -> guildId.equals(record.getGuildId()))
                            .collect(Collectors.toList());
                } else {
                    filteredHistory = allHistory;
                }
                
                // Filter by type if specified
                if (types != null && !types.isEmpty() && !types.contains("all")) {
                    filteredHistory = filteredHistory.stream()
                            .filter(record -> {
                                for(String type : types) {
                                    switch (type) {
                                        case "spotify": if (record.hasSpotifyData()) return true; break;
                                        case "youtube": if (record.getYoutubeVideoId() != null && !record.getYoutubeVideoId().isEmpty()) return true; break;
                                        case "radio": if (record.hasRadioData()) return true; break;
                                        case "gensokyo": if (record.hasGensokyoData()) return true; break;
                                        case "local": if (record.hasLocalData()) return true; break;
                                        case "soundcloud": if (record.hasSoundCloudData()) return true; break;
                                        case "instagram": if (record.hasYtDlpData() && "Instagram".equalsIgnoreCase(record.getYtDlpSourceType())) return true; break;
                                        case "tiktok": if (record.hasYtDlpData() && "TikTok".equalsIgnoreCase(record.getYtDlpSourceType())) return true; break;
                                        case "twitter": if (record.hasYtDlpData() && ("Twitter".equalsIgnoreCase(record.getYtDlpSourceType()) || "X".equalsIgnoreCase(record.getYtDlpSourceType()))) return true; break;
                                        case "generic": if (record.hasYtDlpData() 
                                                && !"Instagram".equalsIgnoreCase(record.getYtDlpSourceType())
                                                && !"TikTok".equalsIgnoreCase(record.getYtDlpSourceType())
                                                && !"Twitter".equalsIgnoreCase(record.getYtDlpSourceType())
                                                && !"X".equalsIgnoreCase(record.getYtDlpSourceType())
                                                && !"YouTube".equalsIgnoreCase(record.getYtDlpSourceType())
                                                && !"SoundCloud".equalsIgnoreCase(record.getYtDlpSourceType())) return true; break;
                                    }
                                }
                                return false;
                            })
                            .collect(Collectors.toList());
                }
                
                // Filter by requester if specified
                if (requester != null && !requester.isEmpty() && !requester.equals("all")) {
                    filteredHistory = filteredHistory.stream()
                            .filter(record -> requester.equals(record.getRequesterName()))
                            .collect(Collectors.toList());
                }
                
                // Filter by time range if specified
                if (timeRange != null && !timeRange.isEmpty() && !timeRange.equals("all")) {
                    final long currentTime = System.currentTimeMillis();
                    final long timeFilter;
                    
                    switch (timeRange) {
                        case "today":
                            // Last 24 hours
                            timeFilter = currentTime - (24 * 60 * 60 * 1000L);
                            break;
                        case "week":
                            // Last 7 days
                            timeFilter = currentTime - (7 * 24 * 60 * 60 * 1000L);
                            break;
                        case "month":
                            // Last 30 days
                            timeFilter = currentTime - (30L * 24 * 60 * 60 * 1000L);
                            break;
                        default:
                            timeFilter = 0; // All time
                    }
                    
                    if (timeFilter > 0) {
                        filteredHistory = filteredHistory.stream()
                                .filter(record -> record.getPlayedAt() >= timeFilter)
                                .collect(Collectors.toList());
                    }
                }

                // Filter by explicit start/end date if specified (inclusive)
                if (startDate != null && !startDate.isEmpty()) {
                    filteredHistory = applyStartEndDateFilter(filteredHistory, startDate, endDate);
                }
                
                // Calculate total before pagination for correct counts
                int totalRecords = filteredHistory.size();
                
                // Apply pagination after filtering
                if (limit > 0) {
                    int startIndex = Math.min(offset, totalRecords);
                    int endIndex = Math.min(offset + limit, totalRecords);
                    
                    if (startIndex < endIndex) {
                        filteredHistory = filteredHistory.subList(startIndex, endIndex);
                    } else {
                        filteredHistory = List.of(); // Empty list if out of bounds
                    }
                }
                
                // Enrich with dynamic avatars
                List<Map<String, Object>> enrichedHistory = enrichWithAvatars(filteredHistory);
                
                response.put("success", true);
                response.put("history", enrichedHistory);
                response.put("total", totalRecords); // Use the filtered total
            } else {
                response.put("success", false);
                response.put("message", "Music history is not available");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error retrieving music history: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Search within the music history
     * @param query Search query
     * @param limit Optional limit of records to return
     * @param offset Optional offset for pagination
     * @param guildId Optional guild ID to filter by
     * @param type Optional source type to filter by (spotify, youtube, etc.)
     * @param requester Optional requester name to filter by
     * @param timeRange Optional time range to filter by (today, week, month, all)
     * @return Filtered list of play records
     */
    @GetMapping("/search")
    public ResponseEntity<Map<String, Object>> searchHistory(
            @RequestParam(value = "query") String query,
            @RequestParam(value = "limit", required = false, defaultValue = "0") int limit,
            @RequestParam(value = "offset", required = false, defaultValue = "0") int offset,
            @RequestParam(value = "guildId", required = false) String guildId,
            @RequestParam(value = "type", required = false) List<String> types,
            @RequestParam(value = "requester", required = false) String requester,
            @RequestParam(value = "timeRange", required = false) String timeRange,
            @RequestParam(value = "startDate", required = false) String startDate,
            @RequestParam(value = "endDate", required = false) String endDate) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (Bot.INSTANCE != null && Bot.INSTANCE.getMusicHistory() != null) {
                MusicHistory musicHistory = Bot.INSTANCE.getMusicHistory();
                List<MusicHistory.PlayRecord> allHistory = musicHistory.getHistory();
                
                // Filter by guild if specified
                if (guildId != null && !guildId.isEmpty() && !guildId.equals("all")) {
                    allHistory = allHistory.stream()
                            .filter(record -> guildId.equals(record.getGuildId()))
                            .collect(Collectors.toList());
                }
                
                // Filter by type if specified
                if (types != null && !types.isEmpty() && !types.contains("all")) {
                    allHistory = allHistory.stream()
                            .filter(record -> {
                                for(String type: types) {
                                    switch (type) {
                                        case "spotify": if (record.hasSpotifyData()) return true; break;
                                        case "youtube": if (record.getYoutubeVideoId() != null && !record.getYoutubeVideoId().isEmpty()) return true; break;
                                        case "radio": if (record.hasRadioData()) return true; break;
                                        case "gensokyo": if (record.hasGensokyoData()) return true; break;
                                        case "local": if (record.hasLocalData()) return true; break;
                                        case "soundcloud": if (record.hasSoundCloudData()) return true; break;
                                        case "instagram": if (record.hasYtDlpData() && "Instagram".equalsIgnoreCase(record.getYtDlpSourceType())) return true; break;
                                        case "tiktok": if (record.hasYtDlpData() && "TikTok".equalsIgnoreCase(record.getYtDlpSourceType())) return true; break;
                                        case "twitter": if (record.hasYtDlpData() && ("Twitter".equalsIgnoreCase(record.getYtDlpSourceType()) || "X".equalsIgnoreCase(record.getYtDlpSourceType()))) return true; break;
                                        case "generic": if (record.hasYtDlpData() 
                                                && !"Instagram".equalsIgnoreCase(record.getYtDlpSourceType())
                                                && !"TikTok".equalsIgnoreCase(record.getYtDlpSourceType())
                                                && !"Twitter".equalsIgnoreCase(record.getYtDlpSourceType())
                                                && !"X".equalsIgnoreCase(record.getYtDlpSourceType())
                                                && !"YouTube".equalsIgnoreCase(record.getYtDlpSourceType())
                                                && !"SoundCloud".equalsIgnoreCase(record.getYtDlpSourceType())) return true; break;
                                    }
                                }
                                return false;
                            })
                            .collect(Collectors.toList());
                }
                
                // Filter by requester if specified
                if (requester != null && !requester.isEmpty() && !requester.equals("all")) {
                    allHistory = allHistory.stream()
                            .filter(record -> requester.equals(record.getRequesterName()))
                            .collect(Collectors.toList());
                }
                
                // Filter by time range if specified
                if (timeRange != null && !timeRange.isEmpty() && !timeRange.equals("all")) {
                    final long currentTime = System.currentTimeMillis();
                    final long timeFilter;
                    
                    switch (timeRange) {
                        case "today":
                            // Last 24 hours
                            timeFilter = currentTime - (24 * 60 * 60 * 1000L);
                            break;
                        case "week":
                            // Last 7 days
                            timeFilter = currentTime - (7 * 24 * 60 * 60 * 1000L);
                            break;
                        case "month":
                            // Last 30 days
                            timeFilter = currentTime - (30L * 24 * 60 * 60 * 1000L);
                            break;
                        default:
                            timeFilter = 0; // All time
                    }
                    
                    if (timeFilter > 0) {
                        allHistory = allHistory.stream()
                                .filter(record -> record.getPlayedAt() >= timeFilter)
                                .collect(Collectors.toList());
                    }
                }

                // Filter by explicit start/end date if specified (inclusive)
                if (startDate != null && !startDate.isEmpty()) {
                    allHistory = applyStartEndDateFilter(allHistory, startDate, endDate);
                }
                
                // Full-text search across all history fields (case-insensitive)
                String lowerQuery = query == null ? "" : query.trim().toLowerCase();
                List<MusicHistory.PlayRecord> filteredHistory = lowerQuery.isEmpty()
                    ? allHistory
                    : allHistory.stream()
                    .filter(record ->
                        // Core fields
                        containsIgnoreCase(record.getTitle(), lowerQuery) ||
                        containsIgnoreCase(record.getArtist(), lowerQuery) ||
                        containsIgnoreCase(record.getUrl(), lowerQuery) ||
                        containsIgnoreCase(String.valueOf(record.getDuration()), lowerQuery) ||
                        containsIgnoreCase(record.getFormattedDuration(), lowerQuery) ||
                        containsIgnoreCase(String.valueOf(record.getPlayedAt()), lowerQuery) ||
                        containsIgnoreCase(record.getFormattedPlayedAt(), lowerQuery) ||
                        containsIgnoreCase(record.getRequesterId(), lowerQuery) ||
                        containsIgnoreCase(record.getRequesterName(), lowerQuery) ||
                        containsIgnoreCase(record.getGuildId(), lowerQuery) ||
                        containsIgnoreCase(record.getGuildName(), lowerQuery) ||

                        // Spotify
                        (record.hasSpotifyData() && (
                            containsIgnoreCase(record.getSpotifyTrackId(), lowerQuery) ||
                            containsIgnoreCase(record.getSpotifyAlbumName(), lowerQuery) ||
                            containsIgnoreCase(record.getSpotifyAlbumImageUrl(), lowerQuery) ||
                            containsIgnoreCase(record.getSpotifyArtistName(), lowerQuery) ||
                            containsIgnoreCase(record.getSpotifyReleaseYear(), lowerQuery)
                        )) ||

                        // Radio
                        (record.hasRadioData() && (
                            containsIgnoreCase(record.getRadioStationName(), lowerQuery) ||
                            containsIgnoreCase(record.getRadioLogoUrl(), lowerQuery) ||
                            containsIgnoreCase(record.getRadioSongImageUrl(), lowerQuery)
                        )) ||

                        // YouTube
                        containsIgnoreCase(record.getYoutubeVideoId(), lowerQuery) ||

                        // Local files
                        (record.hasLocalData() && (
                            containsIgnoreCase(record.getLocalAlbum(), lowerQuery) ||
                            containsIgnoreCase(record.getLocalGenre(), lowerQuery) ||
                            containsIgnoreCase(record.getLocalYear(), lowerQuery) ||
                            containsIgnoreCase(record.getLocalArtworkHash(), lowerQuery)
                        )) ||

                        // Gensokyo Radio
                        (record.hasGensokyoData() && (
                            containsIgnoreCase(record.getGensokyoTitle(), lowerQuery) ||
                            containsIgnoreCase(record.getGensokyoArtist(), lowerQuery) ||
                            containsIgnoreCase(record.getGensokyoAlbum(), lowerQuery) ||
                            containsIgnoreCase(record.getGensokyoCircle(), lowerQuery) ||
                            containsIgnoreCase(record.getGensokyoYear(), lowerQuery) ||
                            containsIgnoreCase(record.getGensokyoAlbumArtUrl(), lowerQuery)
                        )) ||

                        // Streams
                        (record.hasStreamData() && (
                            containsIgnoreCase(record.getStreamName(), lowerQuery) ||
                            containsIgnoreCase(record.getStreamGenre(), lowerQuery) ||
                            containsIgnoreCase(record.getStreamLogo(), lowerQuery) ||
                            containsIgnoreCase(String.valueOf(record.isLiveStream()), lowerQuery)
                        )) ||

                        // SoundCloud
                        (record.hasSoundCloudData() && containsIgnoreCase(record.getSoundCloudArtworkUrl(), lowerQuery)) ||

                        // yt-dlp (Instagram/TikTok/Twitter/etc.)
                        (record.hasYtDlpData() && (
                            containsIgnoreCase(record.getYtDlpSourceType(), lowerQuery) ||
                            containsIgnoreCase(record.getYtDlpThumbnailUrl(), lowerQuery)
                        ))
                    )
                    .collect(Collectors.toList());
                
                // Calculate total records before limiting
                int totalRecords = filteredHistory.size();
                
                // Apply pagination after search filtering is done
                if (limit > 0) {
                    int startIndex = Math.min(offset, totalRecords);
                    int endIndex = Math.min(offset + limit, totalRecords);
                    
                    if (startIndex < endIndex) {
                        filteredHistory = filteredHistory.subList(startIndex, endIndex);
                    } else {
                        filteredHistory = List.of(); // Empty list if out of bounds
                    }
                }
                
                // Enrich with dynamic avatars
                List<Map<String, Object>> enrichedHistory = enrichWithAvatars(filteredHistory);
                
                response.put("success", true);
                response.put("history", enrichedHistory);
                response.put("total", totalRecords);
            } else {
                response.put("success", false);
                response.put("message", "Music history is not available");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error searching music history: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Get the list of all unique requesters
     * @param guildId Optional guild ID to filter requesters by guild
     * @return List of requester names
     */
    @GetMapping("/requesters")
    public ResponseEntity<Map<String, Object>> getRequesters(
            @RequestParam(value = "guildId", required = false) String guildId) {
        
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (Bot.INSTANCE != null && Bot.INSTANCE.getMusicHistory() != null) {
                MusicHistory musicHistory = Bot.INSTANCE.getMusicHistory();
                List<MusicHistory.PlayRecord> allHistory = musicHistory.getHistory();
                
                // Map name to ID to get avatar
                Map<String, String> nameToIdMap = new HashMap<>();
                
                java.util.stream.Stream<MusicHistory.PlayRecord> stream = allHistory.stream();
                
                // Filter by guild if specified
                if (guildId != null && !guildId.isEmpty() && !guildId.equals("all")) {
                    stream = stream.filter(record -> guildId.equals(record.getGuildId()));
                }
                
                stream.forEach(record -> {
                    String name = record.getRequesterName();
                    String id = record.getRequesterId();
                    if (name != null && !name.isEmpty()) {
                        // Keep the ID associated with the name
                        nameToIdMap.putIfAbsent(name, id);
                    }
                });

                // Convert to list of objects with avatar
                List<Map<String, String>> requesters = nameToIdMap.entrySet().stream()
                        .map(entry -> {
                            Map<String, String> req = new HashMap<>();
                            req.put("name", entry.getKey());
                            req.put("avatar", avatarCacheService.getAvatarUrl(entry.getValue()));
                            return req;
                        })
                        .sorted((a, b) -> a.get("name").compareToIgnoreCase(b.get("name")))
                        .collect(Collectors.toList());
                
                response.put("success", true);
                response.put("requesters", requesters);
            } else {
                response.put("success", false);
                response.put("message", "Music history is not available");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error retrieving requesters: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
} 