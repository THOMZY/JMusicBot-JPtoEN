/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.controller;

import com.jagrosh.jmusicbot.Bot;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Paths;
import java.util.Map;

/**
 * Controller that serves artwork images for local audio files
 */
@RestController
@RequestMapping("/api/artwork")
public class ArtworkController {

    private final Bot bot;

    public ArtworkController(Bot bot) {
        this.bot = bot;
    }

    /**
     * Serves the artwork for a specific track ID
     * @param trackId The track identifier
     * @return The image file as a response
     */
    @GetMapping("/{trackId}")
    public ResponseEntity<Resource> getArtwork(@PathVariable String trackId) {
        // Get the artwork path from the bot
        String artworkPath = bot.getLocalArtworkUrl(trackId);
        
        if (artworkPath == null || artworkPath.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        
        try {
            // Create a file resource
            File file = new File(artworkPath);
            
            if (!file.exists() || !file.isFile()) {
                return ResponseEntity.notFound().build();
            }
            
            // Determine the content type based on file extension
            String contentType = determineContentType(file.getName());
            
            // Create the resource
            Resource resource = new FileSystemResource(file);
            
            // Return the file with appropriate headers
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + file.getName() + "\"")
                .body(resource);
                
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }
    
    /**
     * Determines the content type based on file extension
     * @param filename The filename
     * @return The content type as a string
     */
    private String determineContentType(String filename) {
        Map<String, String> extensions = Map.of(
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "png", "image/png",
            "gif", "image/gif",
            "webp", "image/webp",
            "bmp", "image/bmp"
        );
        
        String extension = "";
        int i = filename.lastIndexOf('.');
        if (i > 0) {
            extension = filename.substring(i + 1).toLowerCase();
        }
        
        return extensions.getOrDefault(extension, "application/octet-stream");
    }
} 