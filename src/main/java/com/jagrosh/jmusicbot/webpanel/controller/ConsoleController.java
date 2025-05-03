/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.controller;

import com.jagrosh.jmusicbot.webpanel.service.ConsoleService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/console")
public class ConsoleController {

    private final ConsoleService consoleService;

    public ConsoleController(ConsoleService consoleService) {
        this.consoleService = consoleService;
    }

    @GetMapping("/logs")
    public ResponseEntity<List<String>> getConsoleLogs() {
        return ResponseEntity.ok(consoleService.getConsoleLog());
    }

    @PostMapping("/command")
    public ResponseEntity<Map<String, Object>> executeCommand(@RequestParam(name = "command") String command) {
        String result = consoleService.executeCommand(command);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        response.put("result", result);
        
        return ResponseEntity.ok(response);
    }

    @GetMapping("/config")
    public ResponseEntity<Map<String, Object>> getConfig() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            String content = consoleService.getConfigContent();
            response.put("success", true);
            response.put("content", content);
        } catch (IOException e) {
            response.put("success", false);
            response.put("message", "Error reading config file: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }

    @PostMapping("/config")
    public ResponseEntity<Map<String, Object>> saveConfig(@RequestBody Map<String, String> request) {
        String content = request.get("content");
        boolean success = consoleService.saveConfigContent(content);
        
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        if (!success) {
            response.put("message", "Failed to save config file");
        }
        
        return ResponseEntity.ok(response);
    }
} 