/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.controller;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.MusicHistory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Controller for handling history-related API requests
 */
@RestController
@RequestMapping("/api/history")
public class HistoryController {

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
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "requester", required = false) String requester,
            @RequestParam(value = "timeRange", required = false) String timeRange) {
        
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
                if (type != null && !type.isEmpty() && !type.equals("all")) {
                    filteredHistory = filteredHistory.stream()
                            .filter(record -> {
                                switch (type) {
                                    case "spotify": return record.hasSpotifyData();
                                    case "youtube": return record.getYoutubeVideoId() != null && !record.getYoutubeVideoId().isEmpty();
                                    case "radio": return record.hasRadioData();
                                    case "gensokyo": return record.hasGensokyoData();
                                    case "local": return record.hasLocalData();
                                    case "soundcloud": return record.hasSoundCloudData();
                                    default: return true;
                                }
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
                
                response.put("success", true);
                response.put("history", filteredHistory);
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
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "requester", required = false) String requester,
            @RequestParam(value = "timeRange", required = false) String timeRange) {
        
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
                if (type != null && !type.isEmpty() && !type.equals("all")) {
                    allHistory = allHistory.stream()
                            .filter(record -> {
                                switch (type) {
                                    case "spotify": return record.hasSpotifyData();
                                    case "youtube": return record.getYoutubeVideoId() != null && !record.getYoutubeVideoId().isEmpty();
                                    case "radio": return record.hasRadioData();
                                    case "gensokyo": return record.hasGensokyoData();
                                    case "local": return record.hasLocalData();
                                    case "soundcloud": return record.hasSoundCloudData();
                                    default: return true;
                                }
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
                
                // Filter history by search query (case insensitive)
                String lowerQuery = query.toLowerCase();
                List<MusicHistory.PlayRecord> filteredHistory = allHistory.stream()
                    .filter(record -> 
                        (record.getTitle() != null && record.getTitle().toLowerCase().contains(lowerQuery)) ||
                        (record.getArtist() != null && record.getArtist().toLowerCase().contains(lowerQuery)) ||
                        (record.getRequesterName() != null && record.getRequesterName().toLowerCase().contains(lowerQuery)) ||
                        (record.getGuildName() != null && record.getGuildName().toLowerCase().contains(lowerQuery)) ||
                        (record.hasSpotifyData() && record.getSpotifyAlbumName() != null && 
                         record.getSpotifyAlbumName().toLowerCase().contains(lowerQuery)) ||
                        (record.hasRadioData() && record.getRadioStationName() != null && 
                         record.getRadioStationName().toLowerCase().contains(lowerQuery))
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
                
                response.put("success", true);
                response.put("history", filteredHistory);
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
                List<String> requesters;
                
                // Filter by guild if specified
                if (guildId != null && !guildId.isEmpty() && !guildId.equals("all")) {
                    requesters = allHistory.stream()
                            .filter(record -> guildId.equals(record.getGuildId()))
                            .map(MusicHistory.PlayRecord::getRequesterName)
                            .filter(name -> name != null && !name.isEmpty())
                            .distinct()
                            .collect(Collectors.toList());
                } else {
                    requesters = allHistory.stream()
                            .map(MusicHistory.PlayRecord::getRequesterName)
                            .filter(name -> name != null && !name.isEmpty())
                            .distinct()
                            .collect(Collectors.toList());
                }
                
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