/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.controller;

import com.jagrosh.jmusicbot.webpanel.service.MusicBotService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/command")
public class CommandController {

    private final MusicBotService musicBotService;

    public CommandController(MusicBotService musicBotService) {
        this.musicBotService = musicBotService;
    }

    @PostMapping("/execute")
    public ResponseEntity<Map<String, Object>> executeCommand(@RequestBody Map<String, String> payload) {
        String command = payload.get("command");
        Map<String, Object> response = new HashMap<>();
        
        if (command == null || command.trim().isEmpty()) {
            response.put("success", false);
            response.put("message", "No command provided");
            return ResponseEntity.badRequest().body(response);
        }
        
        try {
            String result = musicBotService.executeDiscordCommand(command);
            response.put("success", true);
            response.put("message", result);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error executing command: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
    }
} 