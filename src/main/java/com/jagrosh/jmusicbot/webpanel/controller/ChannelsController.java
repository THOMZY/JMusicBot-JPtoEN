/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.controller;

import com.jagrosh.jmusicbot.webpanel.model.DiscordChannel;
import com.jagrosh.jmusicbot.webpanel.model.DiscordRole;
import com.jagrosh.jmusicbot.webpanel.model.DiscordServer;
import com.jagrosh.jmusicbot.webpanel.model.DiscordMessage;
import com.jagrosh.jmusicbot.webpanel.model.DiscordUserProfile;
import com.jagrosh.jmusicbot.webpanel.service.ChannelsService;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Controller handling Discord channel-related API endpoints
 */
@RestController
@RequestMapping("/api")
public class ChannelsController {

    private final ChannelsService channelsService;

    @Autowired
    public ChannelsController(ChannelsService channelsService) {
        this.channelsService = channelsService;
    }

    /**
     * Get all servers (guilds) the bot is in
     */
    @GetMapping("/servers")
    public ResponseEntity<List<DiscordServer>> getServers() {
        return ResponseEntity.ok(channelsService.getServers());
    }

    /**
     * Get all channels for a specific server
     */
    @GetMapping("/servers/{serverId}/channels")
    public ResponseEntity<List<DiscordChannel>> getChannelsForServer(@PathVariable("serverId") String serverId) {
        List<DiscordChannel> channels = channelsService.getChannelsForServer(serverId);
        
        if (channels == null) {
            return ResponseEntity.notFound().build();
        }
        
        return ResponseEntity.ok(channels);
    }

    /**
     * Get all roles for a specific server
     */
    @GetMapping("/servers/{serverId}/roles")
    public ResponseEntity<List<DiscordRole>> getRolesForServer(@PathVariable("serverId") String serverId) {
        List<DiscordRole> roles = channelsService.getRolesForServer(serverId);
        if (roles == null) { 
            // Service returned null, indicating an internal error or guild not found for roles
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList());
        }
        // Return empty list if no roles, or the list of roles
        return ResponseEntity.ok(roles);
    }

    /**
     * Get detailed information about a specific channel
     */
    @GetMapping("/channels/{channelId}")
    public ResponseEntity<?> getChannelDetails(@PathVariable("channelId") String channelId) {
        DiscordChannel channel = channelsService.getChannelDetails(channelId);
        if (channel == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "Channel not found or bot has no access");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        return ResponseEntity.ok(channel);
    }

    /**
     * Get messages from a specific channel
     */
    @GetMapping("/channels/{channelId}/messages")
    public ResponseEntity<List<DiscordMessage>> getChannelMessages(
            @PathVariable("channelId") String channelId,
            @RequestParam(value = "before", required = false) String beforeMessageId,
            @RequestParam(value = "limit", defaultValue = "25") int limitInt) { // Renamed to avoid conflict, use Integer for service
        
        Integer limit = limitInt; // Convert to Integer for the service method
        // The service method is getChannelMessages(String channelId, Integer limit, String before, String after)
        // We are missing 'after', and the order was different. Assuming 'after' is not used for 'load more' (before).
        List<DiscordMessage> messages = channelsService.getChannelMessages(channelId, limit, beforeMessageId, null);
        
        if (messages == null) { // Service might return null on error
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(Collections.emptyList());
        }
        return ResponseEntity.ok(messages);
    }

    /**
     * Get all roles for a specific member in a server
     */
    @GetMapping("/servers/{serverId}/members/{memberId}/roles")
    public ResponseEntity<List<DiscordRole>> getMemberRoles(
            @PathVariable("serverId") String serverId,
            @PathVariable("memberId") String memberId) {
        List<DiscordRole> roles = channelsService.getMemberRoles(serverId, memberId);
        if (roles == null || roles.isEmpty()) {
            // Return empty list if no roles, or if service had an issue and returned null/empty
            return ResponseEntity.ok(Collections.emptyList()); 
        }
        return ResponseEntity.ok(roles);
    }

    /**
     * Get detailed profile information for a specific member in a server.
     */
    @GetMapping("/servers/{serverId}/members/{memberId}/profile")
    public ResponseEntity<?> getMemberProfile(
            @PathVariable("serverId") String serverId,
            @PathVariable("memberId") String memberId) {
        DiscordUserProfile profile = channelsService.getMemberProfile(serverId, memberId);
        if (profile == null) {
            Map<String, Object> response = new HashMap<>();
            response.put("error", "User profile not found or an error occurred.");
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(response);
        }
        return ResponseEntity.ok(profile);
    }

    /**
     * Send a message to a channel
     */
    @PostMapping("/channels/{channelId}/messages")
    public ResponseEntity<Map<String, Object>> sendMessage(
            @PathVariable String channelId, 
            @RequestParam String content) {
        
        Map<String, Object> response = new HashMap<>();
        boolean success = channelsService.sendMessageToChannel(channelId, content);
        
        response.put("success", success);
        if (!success) {
            response.put("message", "Failed to send message. Check bot permissions or channel ID.");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }
        
        response.put("message", "Message sent successfully");
        return ResponseEntity.ok(response);
    }
} 