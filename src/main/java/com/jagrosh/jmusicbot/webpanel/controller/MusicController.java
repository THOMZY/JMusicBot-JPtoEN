/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.controller;

import com.jagrosh.jmusicbot.webpanel.model.Guild;
import com.jagrosh.jmusicbot.webpanel.model.MusicStatus;
import com.jagrosh.jmusicbot.webpanel.model.QueueTrack;
import com.jagrosh.jmusicbot.webpanel.service.MusicService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.webpanel.WebPanelApplication;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;

@RestController
@RequestMapping("/api")
public class MusicController {

    private final MusicService musicService;

    public MusicController(MusicService musicService) {
        this.musicService = musicService;
    }

    @GetMapping("/status")
    public ResponseEntity<MusicStatus> getStatus() {
        return ResponseEntity.ok(musicService.getCurrentStatus());
    }
    
    @GetMapping("/queue")
    public ResponseEntity<List<QueueTrack>> getQueue() {
        return ResponseEntity.ok(musicService.getQueue());
    }
    
    @GetMapping("/guilds")
    public ResponseEntity<List<Guild>> getGuilds() {
        return ResponseEntity.ok(musicService.getGuilds());
    }
    
    @GetMapping("/guild/selected")
    public ResponseEntity<Map<String, Object>> getSelectedGuild() {
        Map<String, Object> response = new HashMap<>();
        response.put("guildId", musicService.getSelectedGuildId());
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/bot/info")
    public ResponseEntity<Map<String, Object>> getBotInfo() {
        Map<String, Object> response = new HashMap<>();
        response.put("success", true);
        
        try {
            if (Bot.INSTANCE != null && Bot.INSTANCE.getJDA() != null) {
                response.put("name", Bot.INSTANCE.getJDA().getSelfUser().getName());
                response.put("avatarUrl", Bot.INSTANCE.getJDA().getSelfUser().getEffectiveAvatarUrl());
                response.put("id", Bot.INSTANCE.getJDA().getSelfUser().getId());
            } else {
                response.put("name", "JMusicBot");
                response.put("avatarUrl", "https://cdn.discordapp.com/embed/avatars/0.png");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("error", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/guild/select/{guildId}")
    public ResponseEntity<Map<String, Object>> selectGuild(@PathVariable(value = "guildId") String guildId) {
        boolean success = musicService.setSelectedGuild(guildId);
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        if (success) {
            response.put("guildId", guildId);
        } else {
            response.put("message", "Failed to select guild, it might not exist");
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/play")
    public ResponseEntity<Map<String, Object>> playTrack() {
        boolean success = musicService.playTrack();
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/pause")
    public ResponseEntity<Map<String, Object>> pauseTrack() {
        boolean success = musicService.pauseTrack();
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/skip")
    public ResponseEntity<Map<String, Object>> skipTrack() {
        boolean success = musicService.skipTrack();
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/seek")
    public ResponseEntity<Map<String, Object>> seekTrack(@RequestParam(name = "position") long position) {
        boolean success = musicService.seekTrack(position);
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        if (!success) {
            response.put("message", "Failed to seek. Track may not be seekable or position is invalid.");
        }
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/queue/{index}")
    public ResponseEntity<Map<String, Object>> removeFromQueue(@PathVariable int index) {
        boolean success = musicService.removeTrack(index);
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/queue/add")
    public ResponseEntity<Map<String, Object>> addTrackToQueue(@RequestParam(name = "url") String url) {
        CompletableFuture<String> future = musicService.addTrackByUrl(url);
        Map<String, Object> response = new HashMap<>();
        
        try {
            String result = future.get();
            response.put("success", !result.startsWith("Failed") && !result.startsWith("No matches"));
            response.put("message", result);
        } catch (InterruptedException | ExecutionException e) {
            response.put("success", false);
            response.put("message", "Error adding track: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/queue/move")
    public ResponseEntity<Map<String, Object>> moveTrack(@RequestParam(name = "from") int fromIndex, 
                                                        @RequestParam(name = "to") int toIndex) {
        boolean success = musicService.moveTrack(fromIndex, toIndex);
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        if (success) {
            response.put("message", "Track moved successfully");
        } else {
            response.put("message", "Failed to move track");
        }
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reboot")
    public ResponseEntity<Map<String, Object>> rebootBot() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Get the current jar path
            String jarPath = getJarPath();
            if (jarPath == null) {
                response.put("success", false);
                response.put("message", "Could not determine application path for reboot");
                return ResponseEntity.ok(response);
            }
            
            // Create a new process that will start the application again
            String javaPath = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
            ProcessBuilder processBuilder = new ProcessBuilder(javaPath, "-jar", jarPath);
            processBuilder.inheritIO(); // Inherit standard IO streams
            processBuilder.start(); // Start the new process
            
            // Prepare for shutdown
            new Thread(() -> {
                try {
                    // Allow time for response to be sent
                    Thread.sleep(1000);
                    
                    // Shutdown the bot
                    if (Bot.INSTANCE != null) {
                        Bot.INSTANCE.shutdown();
                    }
                    
                    // Stop Spring application
                    WebPanelApplication.stop();
                    
                    // Exit with success status
                    System.exit(0);
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }).start();
            
            response.put("success", true);
            response.put("message", "Bot is rebooting...");
            
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Failed to reboot: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    // Helper method to get the current JAR file path
    private String getJarPath() {
        try {
            String path = new File(MusicController.class.getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI())
                    .getPath();
            
            if (path.endsWith(".jar")) {
                return path;
            } else {
                // Running from IDE or exploded JAR
                return System.getProperty("user.dir") + File.separator + "target" + File.separator + "JMusicBot.jar";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @PostMapping("/bot/setname")
    public ResponseEntity<Map<String, Object>> setBotName(@RequestParam(value = "name") String name) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (Bot.INSTANCE != null && Bot.INSTANCE.getJDA() != null) {
                // Update the bot's name
                Bot.INSTANCE.getJDA().getSelfUser().getManager().setName(name).queue(
                    success -> {
                        // Success handling happens in the frontend
                    },
                    error -> {
                        // Error handling happens in the frontend
                    }
                );
                
                response.put("success", true);
                response.put("message", "Bot name update initiated");
            } else {
                response.put("success", false);
                response.put("message", "Bot is not available");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/bot/setavatar")
    public ResponseEntity<Map<String, Object>> setBotAvatar(@RequestParam(value = "url") String url) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (Bot.INSTANCE != null && Bot.INSTANCE.getJDA() != null) {
                // Use the OtherUtil to load the image from the URL
                InputStream imageStream = com.jagrosh.jmusicbot.utils.OtherUtil.imageFromUrl(url);
                
                if (imageStream != null) {
                    // Update the bot's avatar
                    Bot.INSTANCE.getJDA().getSelfUser().getManager().setAvatar(net.dv8tion.jda.api.entities.Icon.from(imageStream)).queue(
                        success -> {
                            // Success handling happens in the frontend
                        },
                        error -> {
                            // Error handling happens in the frontend
                        }
                    );
                    
                    response.put("success", true);
                    response.put("message", "Bot avatar update initiated");
                } else {
                    response.put("success", false);
                    response.put("message", "Could not load image from provided URL");
                }
            } else {
                response.put("success", false);
                response.put("message", "Bot is not available");
            }
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
} 