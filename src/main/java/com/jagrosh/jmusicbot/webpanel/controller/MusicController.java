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
                
                // Add banner URL if available
                try {
                    // We need to retrieve the banner from the self user
                    net.dv8tion.jda.api.entities.User user = Bot.INSTANCE.getJDA().getSelfUser().getJDA().retrieveUserById(Bot.INSTANCE.getJDA().getSelfUser().getId()).complete();
                    String bannerUrl = user.retrieveProfile().complete().getBannerUrl();
                    if (bannerUrl != null) {
                        response.put("bannerUrl", bannerUrl);
                    }
                } catch (Exception e) {
                    // Ignore if we can't get the banner
                    System.out.println("Could not retrieve banner: " + e.getMessage());
                }
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
    
    @PostMapping("/stop")
    public ResponseEntity<Map<String, Object>> stopTrack() {
        boolean success = musicService.stopTrack();
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        if (success) {
            response.put("message", "Playback stopped and queue cleared");
        } else {
            response.put("message", "Failed to stop playback");
        }
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
    
    @PostMapping("/volume")
    public ResponseEntity<Map<String, Object>> setVolume(@RequestParam(name = "volume") int volume) {
        boolean success = musicService.setVolume(volume);
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        if (success) {
            response.put("message", "Volume set to " + volume);
        } else {
            response.put("message", "Failed to set volume");
        }
        return ResponseEntity.ok(response);
    }
    
    @DeleteMapping("/queue/{index}")
    public ResponseEntity<Map<String, Object>> removeFromQueue(@PathVariable(name = "index") int index) {
        boolean success = musicService.removeTrack(index);
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/queue/clear")
    public ResponseEntity<Map<String, Object>> clearQueue() {
        boolean success = musicService.clearQueue();
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        if (success) {
            response.put("message", "Queue cleared");
        } else {
            response.put("message", "Failed to clear queue");
        }
        return ResponseEntity.ok(response);
    }
    
    @PostMapping("/queue/add")
    public ResponseEntity<Map<String, Object>> addTrackToQueue(@RequestParam(name = "url") String url) {
        // Check if the URL is a Spotify link
        if (isSpotifyUrl(url)) {
            return handleSpotifyUrl(url, false);
        }
        
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
    
    @PostMapping("/queue/playnext")
    public ResponseEntity<Map<String, Object>> playNextTrack(@RequestParam(name = "url") String url) {
        // Check if the URL is a Spotify link
        if (isSpotifyUrl(url)) {
            return handleSpotifyUrl(url, true);
        }
        
        CompletableFuture<String> future = musicService.playNextTrackByUrl(url);
        Map<String, Object> response = new HashMap<>();
        
        try {
            String result = future.get();
            response.put("success", !result.startsWith("Failed") && !result.startsWith("No matches"));
            response.put("message", result);
        } catch (InterruptedException | ExecutionException e) {
            response.put("success", false);
            response.put("message", "Error adding track to play next: " + e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Helper method to check if a URL is a Spotify track URL
     */
    private boolean isSpotifyUrl(String url) {
        // Clean URL to handle query parameters
        String cleanUrl = url.split("\\?")[0];
        return cleanUrl.matches("https://open\\.spotify\\.com/(intl-[a-z]+/)?track/[a-zA-Z0-9]+");
    }
    
    /**
     * Helper method to handle Spotify URLs
     */
    private ResponseEntity<Map<String, Object>> handleSpotifyUrl(String url, boolean playNext) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            // Extract the track ID from the URL
            String trackId = dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.extractTrackIdFromUrl(url);
            if (trackId == null) {
                response.put("success", false);
                response.put("message", "Invalid Spotify track URL format");
                return ResponseEntity.ok(response);
            }
            
            // Check if we can access the SpotifyCmd class
            try {
                Class.forName("dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd");
            } catch (ClassNotFoundException e) {
                response.put("success", false);
                response.put("message", "Spotify support is not available");
                return ResponseEntity.ok(response);
            }
            
            // Use the processSpotifyTrack method
            String searchResult = musicService.processSpotifyTrack(trackId, playNext);
            
            response.put("success", !searchResult.startsWith("Failed") && !searchResult.startsWith("Error"));
            response.put("message", searchResult);
            
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", "Error processing Spotify track: " + e.getMessage());
            return ResponseEntity.ok(response);
        }
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

    /**
     * Set the bot's banner image
     */
    @PostMapping("/bot/setbanner")
    public ResponseEntity<Map<String, Object>> setBotBanner(@RequestParam(value = "url") String url) {
        Map<String, Object> response = new HashMap<>();
        
        try {
            if (Bot.INSTANCE != null && Bot.INSTANCE.getJDA() != null) {
                // Use the OtherUtil to load the image from the URL
                InputStream imageStream = com.jagrosh.jmusicbot.utils.OtherUtil.imageFromUrl(url);
                
                if (imageStream != null) {
                    // Update the bot's banner using JDA's interface
                    Bot.INSTANCE.getJDA().getSelfUser().getManager().setBanner(net.dv8tion.jda.api.entities.Icon.from(imageStream)).queue(
                        success -> {
                            // Success handling happens in the frontend
                        },
                        error -> {
                            // Error handling happens in the frontend
                        }
                    );
                    
                    response.put("success", true);
                    response.put("message", "Bot banner update initiated");
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

    /**
     * Get YouTube chapters for the current track
     */
    @GetMapping("/track/chapters")
    public ResponseEntity<Map<String, Object>> getTrackChapters() {
        Map<String, Object> response = new HashMap<>();
        
        try {
            var chaptersResult = musicService.getCurrentTrackChapters();
            response.put("success", true);
            response.put("chapters", chaptersResult);
        } catch (Exception e) {
            response.put("success", false);
            response.put("message", e.getMessage());
        }
        
        return ResponseEntity.ok(response);
    }
    
    /**
     * Seek to a specific time in the track
     */
    @PostMapping("/player/seek/{seconds}")
    public ResponseEntity<Map<String, Object>> seekToPosition(@PathVariable(value = "seconds") long seconds) {
        boolean success = musicService.seekTrack(seconds * 1000);
        Map<String, Object> response = new HashMap<>();
        response.put("success", success);
        if (!success) {
            response.put("message", "Failed to seek to position. Track may not be seekable.");
        }
        return ResponseEntity.ok(response);
    }
} 