/*
 * Copyright 2026 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.service;

import com.jagrosh.jmusicbot.Bot;
import net.dv8tion.jda.api.entities.User;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service to handle caching and serving of Discord user avatars locally.
 */
@Service
public class AvatarCacheService {

    private static final String AVATARS_DIR = "discord_avatars";
    private static final long AVATAR_FILE_CACHE_DURATION = 24 * 60 * 60 * 1000L; // 24 hours check frequency
    private final ExecutorService executor;

    public AvatarCacheService() {
        this.executor = Executors.newSingleThreadExecutor();
        // Ensure directory exists
        try {
            Files.createDirectories(Paths.get(AVATARS_DIR));
        } catch (Exception e) {
            System.err.println("Could not create avatars directory: " + e.getMessage());
        }
    }

    /**
     * Get avatar url (local if available, or direct discord info to attempt caching)
     * For use when we have the user ID.
     */
    public String getAvatarUrl(String userId) {
        if (userId == null || userId.isEmpty() || !userId.matches("\\d+")) {
            return null;
        }
        
        // Define local path
        Path localPath = Paths.get(AVATARS_DIR, userId + ".png");
        boolean localExists = Files.exists(localPath);
        
        // Return local path web URL if exists
        String localWebUrl = "/discord_avatars/" + userId + ".png";
        
        // Check if we should update (not exists, or old)
        boolean shouldUpdate = !localExists;
        
        if (localExists) {
            try {
                long lastModified = Files.getLastModifiedTime(localPath).toMillis();
                if (System.currentTimeMillis() - lastModified > AVATAR_FILE_CACHE_DURATION) {
                    shouldUpdate = true;
                }
            } catch (Exception e) {
                shouldUpdate = true;
            }
        }
        
        if (shouldUpdate) {
            updateAvatarAsync(userId);
        }
        
        if (localExists) {
            return localWebUrl;
        }
        
        // Fallback to JDA if user is cached and we don't have local yet
        try {
            if (Bot.INSTANCE != null && Bot.INSTANCE.getJDA() != null) {
                User user = Bot.INSTANCE.getJDA().getUserById(userId);
                if (user != null) {
                    return user.getEffectiveAvatarUrl();
                }
            }
        } catch (Exception ignored) {}
        
        return null;
    }

    /**
     * Async task to download and save user avatar
     */
    private void updateAvatarAsync(String userId) {
        executor.submit(() -> {
            try {
                if (Bot.INSTANCE == null || Bot.INSTANCE.getJDA() == null) return;

                User user = Bot.INSTANCE.getJDA().getUserById(userId);
                
                // If not in cache, try to retrieve it
                if (user == null) {
                    try {
                        user = Bot.INSTANCE.getJDA().retrieveUserById(userId).complete();
                    } catch (Exception e) {
                        return; // User lookup failed (invalid ID or deleted user)
                    }
                }
                
                if (user != null) {
                    String avatarUrl = user.getEffectiveAvatarUrl() + "?size=128";
                    try (InputStream in = new URL(avatarUrl).openStream()) {
                        Path targetPath = Paths.get(AVATARS_DIR, userId + ".png");
                        Files.createDirectories(targetPath.getParent());
                        Files.copy(in, targetPath, StandardCopyOption.REPLACE_EXISTING);
                    }
                }
            } catch (Exception e) {
                System.err.println("Failed to update avatar for user " + userId + ": " + e.getMessage());
            }
        });
    }
}
