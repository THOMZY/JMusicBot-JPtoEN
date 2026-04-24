/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.controller;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.MusicHistory;
import com.jagrosh.jmusicbot.webpanel.service.AvatarCacheService;
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

    private static List<MusicHistory.PlayRecord> applyTypeFilter(
            List<MusicHistory.PlayRecord> records,
            List<String> types) {
        if (types == null || types.isEmpty() || types.contains("all")) {
            return records;
        }

        return records.stream()
                .filter(record -> matchesAnySelectedType(record, types))
                .collect(Collectors.toList());
    }

    private static boolean matchesAnySelectedType(MusicHistory.PlayRecord record, List<String> types) {
        for (String type : types) {
            if (matchesType(record, type)) {
                return true;
            }
        }
        return false;
    }

    private static boolean matchesType(MusicHistory.PlayRecord record, String type) {
        switch (type) {
            case "spotify":
                return record.hasSpotifyData();
            case "youtube":
                return record.getYoutubeVideoId() != null && !record.getYoutubeVideoId().isEmpty();
            case "radio":
                return record.hasRadioData();
            case "gensokyo":
                return record.hasGensokyoData();
            case "local":
                return record.hasLocalData();
            case "soundcloud":
                return record.hasSoundCloudData();
            case "instagram":
                return record.hasYtDlpData() && "Instagram".equalsIgnoreCase(record.getYtDlpSourceType());
            case "tiktok":
                return record.hasYtDlpData() && "TikTok".equalsIgnoreCase(record.getYtDlpSourceType());
            case "twitter":
                return record.hasYtDlpData()
                        && ("Twitter".equalsIgnoreCase(record.getYtDlpSourceType())
                        || "X".equalsIgnoreCase(record.getYtDlpSourceType()));
            case "generic":
                return isGenericYtDlpSource(record);
            default:
                return false;
        }
    }

    private static boolean isGenericYtDlpSource(MusicHistory.PlayRecord record) {
        return record.hasYtDlpData()
                && !"Instagram".equalsIgnoreCase(record.getYtDlpSourceType())
                && !"TikTok".equalsIgnoreCase(record.getYtDlpSourceType())
                && !"Twitter".equalsIgnoreCase(record.getYtDlpSourceType())
                && !"X".equalsIgnoreCase(record.getYtDlpSourceType())
                && !"YouTube".equalsIgnoreCase(record.getYtDlpSourceType())
                && !"SoundCloud".equalsIgnoreCase(record.getYtDlpSourceType());
    }

    private List<MusicHistory.PlayRecord> applyCommonFilters(
            List<MusicHistory.PlayRecord> records,
            String guildId,
            List<String> types,
            String requester,
            String timeRange,
            String startDate,
            String endDate) {

        List<MusicHistory.PlayRecord> filtered = records;

        if (guildId != null && !guildId.isEmpty() && !guildId.equals("all")) {
            filtered = filtered.stream()
                    .filter(record -> guildId.equals(record.getGuildId()))
                    .collect(Collectors.toList());
        }

        filtered = applyTypeFilter(filtered, types);

        if (requester != null && !requester.isEmpty() && !requester.equals("all")) {
            filtered = filtered.stream()
                    .filter(record -> requester.equals(record.getRequesterName()))
                    .collect(Collectors.toList());
        }

        filtered = applyTimeRangeFilter(filtered, timeRange);

        if (startDate != null && !startDate.isEmpty()) {
            filtered = applyStartEndDateFilter(filtered, startDate, endDate);
        }

        return filtered;
    }

    private List<MusicHistory.PlayRecord> applyTimeRangeFilter(List<MusicHistory.PlayRecord> records, String timeRange) {
        if (timeRange == null || timeRange.isEmpty() || timeRange.equals("all")) {
            return records;
        }

        final long currentTime = System.currentTimeMillis();
        final long timeFilter;
        switch (timeRange) {
            case "today":
                timeFilter = currentTime - (24 * 60 * 60 * 1000L);
                break;
            case "week":
                timeFilter = currentTime - (7 * 24 * 60 * 60 * 1000L);
                break;
            case "month":
                timeFilter = currentTime - (30L * 24 * 60 * 60 * 1000L);
                break;
            default:
                timeFilter = 0;
        }

        if (timeFilter <= 0) {
            return records;
        }

        return records.stream()
                .filter(record -> record.getPlayedAt() >= timeFilter)
                .collect(Collectors.toList());
    }

    private List<MusicHistory.PlayRecord> applyPagination(List<MusicHistory.PlayRecord> records, int limit, int offset) {
        if (limit <= 0) {
            return records;
        }
        int totalRecords = records.size();
        int startIndex = Math.min(offset, totalRecords);
        int endIndex = Math.min(offset + limit, totalRecords);
        if (startIndex < endIndex) {
            return records.subList(startIndex, endIndex);
        }
        return List.of();
    }

    private ResponseEntity<Map<String, Object>> successHistoryResponse(List<MusicHistory.PlayRecord> records, int totalRecords) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("history", enrichWithAvatars(records));
        response.put("total", totalRecords);
        return ResponseEntity.ok(response);
    }

    private ResponseEntity<Map<String, Object>> failureHistoryResponse(String message) {
        Map<String, Object> response = new HashMap<>();
        response.put("success", false);
        response.put("message", message);
        return ResponseEntity.ok(response);
    }

    private List<MusicHistory.PlayRecord> applyTextSearch(List<MusicHistory.PlayRecord> records, String query) {
        String lowerQuery = query == null ? "" : query.trim().toLowerCase();
        if (lowerQuery.isEmpty()) {
            return records;
        }

        return records.stream()
            .filter(record -> matchesAnyField(record, lowerQuery))
                .collect(Collectors.toList());
    }

        private boolean matchesAnyField(MusicHistory.PlayRecord record, String query) {
        return matchesCoreFields(record, query)
            || matchesSpotifyFields(record, query)
            || matchesRadioFields(record, query)
            || containsIgnoreCase(record.getYoutubeVideoId(), query)
            || matchesLocalFields(record, query)
            || matchesGensokyoFields(record, query)
            || matchesStreamFields(record, query)
            || (record.hasSoundCloudData() && containsIgnoreCase(record.getSoundCloudArtworkUrl(), query))
            || matchesYtDlpFields(record, query);
        }

        private boolean matchesCoreFields(MusicHistory.PlayRecord record, String query) {
        return matchesAny(query,
            record.getTitle(),
            record.getArtist(),
            record.getUrl(),
            String.valueOf(record.getDuration()),
            record.getFormattedDuration(),
            String.valueOf(record.getPlayedAt()),
            record.getFormattedPlayedAt(),
            record.getRequesterId(),
            record.getRequesterName(),
            record.getGuildId(),
            record.getGuildName());
        }

        private boolean matchesSpotifyFields(MusicHistory.PlayRecord record, String query) {
        return record.hasSpotifyData() && matchesAny(query,
            record.getSpotifyTrackId(),
            record.getSpotifyAlbumName(),
            record.getSpotifyAlbumImageUrl(),
            record.getSpotifyArtistName(),
            record.getSpotifyReleaseYear());
        }

        private boolean matchesRadioFields(MusicHistory.PlayRecord record, String query) {
        return record.hasRadioData() && matchesAny(query,
            record.getRadioStationName(),
            record.getRadioLogoUrl(),
            record.getRadioSongImageUrl());
        }

        private boolean matchesLocalFields(MusicHistory.PlayRecord record, String query) {
        return record.hasLocalData() && matchesAny(query,
            record.getLocalAlbum(),
            record.getLocalGenre(),
            record.getLocalYear(),
            record.getLocalArtworkHash());
        }

        private boolean matchesGensokyoFields(MusicHistory.PlayRecord record, String query) {
        return record.hasGensokyoData() && matchesAny(query,
            record.getGensokyoTitle(),
            record.getGensokyoArtist(),
            record.getGensokyoAlbum(),
            record.getGensokyoCircle(),
            record.getGensokyoYear(),
            record.getGensokyoAlbumArtUrl());
        }

        private boolean matchesStreamFields(MusicHistory.PlayRecord record, String query) {
        return record.hasStreamData() && matchesAny(query,
            record.getStreamName(),
            record.getStreamGenre(),
            record.getStreamLogo(),
            String.valueOf(record.isLiveStream()));
        }

        private boolean matchesYtDlpFields(MusicHistory.PlayRecord record, String query) {
        return record.hasYtDlpData() && matchesAny(query,
            record.getYtDlpSourceType(),
            record.getYtDlpThumbnailUrl());
        }

        private boolean matchesAny(String query, String... values) {
        return java.util.Arrays.stream(values).anyMatch(value -> containsIgnoreCase(value, query));
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
        
        try {
            if (Bot.INSTANCE == null || Bot.INSTANCE.getMusicHistory() == null) {
                return failureHistoryResponse("Music history is not available");
            }

            List<MusicHistory.PlayRecord> filteredHistory = applyCommonFilters(
                    Bot.INSTANCE.getMusicHistory().getHistory(),
                    guildId, types, requester, timeRange, startDate, endDate);

            int totalRecords = filteredHistory.size();
            filteredHistory = applyPagination(filteredHistory, limit, offset);
            return successHistoryResponse(filteredHistory, totalRecords);
        } catch (Exception e) {
            return failureHistoryResponse("Error retrieving music history: " + e.getMessage());
        }
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
        
        try {
            if (Bot.INSTANCE == null || Bot.INSTANCE.getMusicHistory() == null) {
                return failureHistoryResponse("Music history is not available");
            }

            List<MusicHistory.PlayRecord> filtered = applyCommonFilters(
                    Bot.INSTANCE.getMusicHistory().getHistory(),
                    guildId, types, requester, timeRange, startDate, endDate);

            List<MusicHistory.PlayRecord> searched = applyTextSearch(filtered, query);
            int totalRecords = searched.size();
            searched = applyPagination(searched, limit, offset);
            return successHistoryResponse(searched, totalRecords);
        } catch (Exception e) {
            return failureHistoryResponse("Error searching music history: " + e.getMessage());
        }
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