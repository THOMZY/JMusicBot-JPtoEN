/*
 * Copyright 2023 THOMZY.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.cosgy.jmusicbot.util;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioTrack;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.logging.Level;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handles metadata extraction from local audio files
 * @author THOMZY
 */
public class LocalAudioMetadata {

    private static final Logger log = LoggerFactory.getLogger(LocalAudioMetadata.class);
    private static final Map<String, LocalTrackInfo> trackInfoCache = new ConcurrentHashMap<>();
    private static final Pattern DISCORD_ATTACHMENT_PATTERN = Pattern.compile("https://cdn\\.discord(?:app)?\\.com/attachments/\\d+/\\d+/([^?/]+)");
    private static final Pattern FILENAME_FROM_URL_PATTERN = Pattern.compile("/([^/?]+)(?:\\?.*)?$");

    static {
        // Disable jaudiotagger logging
        java.util.logging.Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
        java.util.logging.Logger.getLogger("org.jaudiotagger.tag").setLevel(Level.OFF);
        java.util.logging.Logger.getLogger("org.jaudiotagger.audio").setLevel(Level.OFF);
    }

    /**
     * Class to store information about local tracks
     */
    public static class LocalTrackInfo {
        private String title;
        private String artist;
        private String album;
        private String year;
        private String genre;
        private byte[] artworkData;
        private final String originalFilename;
        private boolean metadataExtracted = false;

        public LocalTrackInfo(String originalFilename) {
            this.originalFilename = originalFilename;
            this.title = cleanupFilename(originalFilename);
        }

        public String getTitle() {
            return title != null && !title.isEmpty() ? title : cleanupFilename(originalFilename);
        }

        public String getArtist() {
            return artist != null ? artist : "Unknown Artist";
        }

        public String getAlbum() {
            return album != null ? album : "Unknown Album";
        }

        public String getYear() {
            return year != null ? year : "";
        }

        public String getGenre() {
            return genre != null ? genre : "";
        }

        public byte[] getArtworkData() {
            return artworkData;
        }

        public boolean hasArtwork() {
            return artworkData != null && artworkData.length > 0;
        }

        public boolean isMetadataExtracted() {
            return metadataExtracted;
        }

        public void setMetadataExtracted(boolean metadataExtracted) {
            this.metadataExtracted = metadataExtracted;
        }
    }

    /**
     * Determines if a track is a local file uploaded through Discord
     * @param track The track to check
     * @return True if the track is a Discord uploaded file
     */
    public static boolean isDiscordUploadedFile(AudioTrack track) {
        if (track == null) return false;
        
        String uri = track.getInfo().uri;
        return uri != null && 
               (uri.contains("cdn.discord.com/attachments") || 
                uri.contains("cdn.discordapp.com/attachments"));
    }

    /**
     * Extracts metadata from a local file downloaded from Discord
     * @param track The audio track (can be null for direct file processing)
     * @param tempFile The downloaded temporary file
     * @return The LocalTrackInfo containing the extracted metadata
     */
    public static LocalTrackInfo extractMetadata(AudioTrack track, File tempFile) {
        // Generate a trackId - if track is null, use the file path's hashcode
        String trackId = track != null ? track.getInfo().identifier : 
                        ("file_" + tempFile.getAbsolutePath().hashCode());
        
        String fileUrl = track != null ? track.getInfo().uri : tempFile.getAbsolutePath();
        String filename = extractFilenameFromUrl(fileUrl);
        
        // Check if we already have cached info for this track
        if (trackInfoCache.containsKey(trackId)) {
            return trackInfoCache.get(trackId);
        }
        
        // Create a new track info object with the original filename
        LocalTrackInfo info = new LocalTrackInfo(filename);
        
        try {
            // Use jaudiotagger to extract metadata
            AudioFile audioFile = AudioFileIO.read(tempFile);
            Tag tag = audioFile.getTag();
            
            if (tag != null) {
                // Extract basic metadata
                if (tag.getFirst(FieldKey.TITLE) != null && !tag.getFirst(FieldKey.TITLE).isEmpty()) {
                    info.title = tag.getFirst(FieldKey.TITLE);
                }
                
                if (tag.getFirst(FieldKey.ARTIST) != null && !tag.getFirst(FieldKey.ARTIST).isEmpty()) {
                    info.artist = tag.getFirst(FieldKey.ARTIST);
                }
                
                if (tag.getFirst(FieldKey.ALBUM) != null && !tag.getFirst(FieldKey.ALBUM).isEmpty()) {
                    info.album = tag.getFirst(FieldKey.ALBUM);
                }
                
                if (tag.getFirst(FieldKey.YEAR) != null && !tag.getFirst(FieldKey.YEAR).isEmpty()) {
                    info.year = tag.getFirst(FieldKey.YEAR);
                }
                
                if (tag.getFirst(FieldKey.GENRE) != null && !tag.getFirst(FieldKey.GENRE).isEmpty()) {
                    info.genre = tag.getFirst(FieldKey.GENRE);
                }
                
                // Extract artwork if available
                try {
                    Artwork artwork = tag.getFirstArtwork();
                    if (artwork != null && artwork.getBinaryData() != null) {
                        info.artworkData = artwork.getBinaryData();
                    }
                } catch (Exception e) {
                    log.warn("Failed to extract artwork: {}", e.getMessage());
                }
                
                info.setMetadataExtracted(true);
            }
        } catch (Exception e) {
            log.warn("Failed to extract metadata from local file: {}", filename, e);
        }
        
        // For files with minimal metadata, ensure we at least have a good title
        if (info.title == null || info.title.isEmpty() || 
            info.title.equalsIgnoreCase("Unknown Title") || 
            info.title.equalsIgnoreCase("Unknown")) {
            info.title = cleanupFilename(filename);
        }
        
        // Cache the track info for future use
        trackInfoCache.put(trackId, info);
        return info;
    }

    /**
     * Downloads a file from a URL
     * @param fileUrl The URL to download from
     * @return A temporary File object containing the downloaded content
     */
    public static File downloadFile(String fileUrl) {
        File tempFile = null;
        HttpURLConnection connection = null;
        
        try {
            // Create a temporary file
            String extension = FilenameUtils.getExtension(extractFilenameFromUrl(fileUrl));
            if (extension.isEmpty()) extension = "tmp";
            
            tempFile = File.createTempFile("jmusicbot_", "." + extension);
            tempFile.deleteOnExit();
            
            // Download the file
            URL url = new URL(fileUrl);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestProperty("User-Agent", "JMusicBot/1.0");
            
            try (InputStream in = connection.getInputStream()) {
                byte[] data = IOUtils.toByteArray(in);
                org.apache.commons.io.FileUtils.writeByteArrayToFile(tempFile, data);
                return tempFile;
            }
        } catch (IOException e) {
            log.error("Failed to download file from " + fileUrl, e);
            if (tempFile != null && tempFile.exists()) {
                tempFile.delete();
            }
            return null;
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Extracts the filename from a URL
     * @param url The URL to extract from
     * @return The extracted filename
     */
    public static String extractFilenameFromUrl(String url) {
        if (url == null) return "Unknown title";
        
        // Try Discord attachment pattern first
        Matcher discordMatcher = DISCORD_ATTACHMENT_PATTERN.matcher(url);
        if (discordMatcher.find()) {
            return discordMatcher.group(1);
        }
        
        // Try general URL filename pattern
        Matcher filenameMatcher = FILENAME_FROM_URL_PATTERN.matcher(url);
        if (filenameMatcher.find()) {
            return filenameMatcher.group(1);
        }
        
        return "Unknown title";
    }

    /**
     * Cleans up a filename for display by removing extension and special characters
     * @param filename The filename to clean
     * @return The cleaned filename
     */
    public static String cleanupFilename(String filename) {
        if (filename == null) return "Unknown Title";
        
        // Remove file extension
        String nameWithoutExt = FilenameUtils.removeExtension(filename);
        
        // Replace underscores with spaces
        nameWithoutExt = nameWithoutExt.replace('_', ' ');
        
        // Replace multiple spaces with single space
        nameWithoutExt = nameWithoutExt.replaceAll("\\s+", " ").trim();
        
        return nameWithoutExt;
    }

    /**
     * Clears the track info cache
     */
    public static void clearCache() {
        trackInfoCache.clear();
    }
    
    /**
     * Removes a specific track from the cache
     * @param trackId The track ID to remove
     */
    public static void removeFromCache(String trackId) {
        if (trackId != null) {
            trackInfoCache.remove(trackId);
        }
    }
} 