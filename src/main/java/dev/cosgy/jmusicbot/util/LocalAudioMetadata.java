/*
 * Copyright 2025 THOMZY.
 */
package dev.cosgy.jmusicbot.util;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
// import com.sedmelluq.discord.lavaplayer.source.local.LocalAudioTrack;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.io.IOUtils;
import org.jaudiotagger.audio.AudioFile;
import org.jaudiotagger.audio.AudioFileIO;
import org.jaudiotagger.tag.FieldKey;
import org.jaudiotagger.tag.Tag;
import org.jaudiotagger.tag.images.Artwork;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
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
    
    private static final Path ARTWORK_DIR = Paths.get("local_artwork");

    static {
        // Disable jaudiotagger logging
        java.util.logging.Logger.getLogger("org.jaudiotagger").setLevel(Level.OFF);
        java.util.logging.Logger.getLogger("org.jaudiotagger.tag").setLevel(Level.OFF);
        java.util.logging.Logger.getLogger("org.jaudiotagger.audio").setLevel(Level.OFF);

        // Create artwork directory if it doesn't exist
        try {
            if (!Files.exists(ARTWORK_DIR)) {
                Files.createDirectories(ARTWORK_DIR);
                log.info("Created artwork directory at: {}", ARTWORK_DIR.toAbsolutePath());
            }
        } catch (IOException e) {
            log.error("Failed to create artwork directory: {}", ARTWORK_DIR.toAbsolutePath(), e);
        }
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
        // Store the path to the artwork file instead of raw data
        private String artworkPath; 
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

        // Returns the path to the artwork file
        public String getArtworkPath() {
            return artworkPath;
        }

        public boolean hasArtwork() {
            // Artwork exists if there is a path to it
            return artworkPath != null && !artworkPath.isEmpty();
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
     * @param originalTrackIdentifier The original identifier of the track (e.g., Discord URL) to be used as cache key.
     * @param tempFile The downloaded temporary file
     * @return The LocalTrackInfo containing the extracted metadata
     */
    public static LocalTrackInfo extractMetadata(String originalTrackIdentifier, File tempFile) {
        // Use the originalTrackIdentifier as the primary key for the cache.
        // If originalTrackIdentifier is null or empty, we might fall back to a file-based hash,
        // but ideally, it should always be provided.
        String cacheKey = originalTrackIdentifier;
        if (cacheKey == null || cacheKey.isEmpty()) {
            // Fallback for safety, though this path should ideally not be taken if called correctly.
            log.warn("LocalAudioMetadata.extractMetadata called without an originalTrackIdentifier. Falling back to temp file hash for cache key.");
            cacheKey = "file_" + tempFile.getAbsolutePath().hashCode();
        }

        // Extract filename from the temporary file's path for title cleanup if needed.
        // Or, if the originalTrackIdentifier is a URL, we could try to get a filename from it.
        // For now, let's assume the tempFile's name (after download) is representative enough or use the original URI if it was passed.
        // The LocalTrackInfo constructor will use this for initial title.
        String filenameForTitle = extractFilenameFromUrl(tempFile.getName()); // Prefer tempFile.getName() which might be original
         if (originalTrackIdentifier != null && (originalTrackIdentifier.startsWith("http://") || originalTrackIdentifier.startsWith("https://"))) {
            filenameForTitle = extractFilenameFromUrl(originalTrackIdentifier);
        }


        // Check if we already have cached info for this track using the definitive cacheKey
        if (trackInfoCache.containsKey(cacheKey)) {
            return trackInfoCache.get(cacheKey);
        }
        
        // Create a new track info object with the (potentially better) original filename
        LocalTrackInfo info = new LocalTrackInfo(cleanupFilename(filenameForTitle));
        
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
                        byte[] artworkData = artwork.getBinaryData();
                        // Save artwork and store its path
                        try {
                            String artworkHash = calculateArtworkHash(artworkData);
                            String extension = determineImageExtension(artworkData);
                            String artworkFilename = artworkHash + "." + extension;
                            Path artworkFilePath = ARTWORK_DIR.resolve(artworkFilename);

                            if (!Files.exists(artworkFilePath)) {
                                Files.write(artworkFilePath, artworkData);
                                log.debug("Saved new artwork: {}", artworkFilePath);
                            } else {
                                log.debug("Artwork already exists: {}", artworkFilePath);
                            }
                            // Store the relative path
                            info.artworkPath = ARTWORK_DIR.getFileName().toString() + "/" + artworkFilename;
                        } catch (NoSuchAlgorithmException | IOException e) {
                            log.error("Failed to save or hash artwork for track ID {}: {}", cacheKey, e.getMessage());
                        }
                    }
                } catch (Exception e) {
                    log.warn("Failed to extract artwork: {}", e.getMessage());
                }
                
                info.setMetadataExtracted(true);
            }
        } catch (Exception e) {
            log.warn("Failed to extract metadata from local file: {}", filenameForTitle, e);
        }
        
        // For files with minimal metadata, ensure we at least have a good title
        if (info.title == null || info.title.isEmpty() || 
            info.title.equalsIgnoreCase("Unknown Title") || 
            info.title.equalsIgnoreCase("Unknown") ||
            // If the title is still the raw hash.ext, try to clean it up better or use original filename.
            (info.artworkPath != null && info.title.equals(FilenameUtils.removeExtension(new File(info.artworkPath).getName()))) ) {
            info.title = cleanupFilename(filenameForTitle);
        }
        
        // Cache the track info for future use with the definitive cacheKey
        trackInfoCache.put(cacheKey, info);
        return info;
    }

    /**
     * Retrieves cached LocalTrackInfo for a given track ID.
     * @param trackId The identifier of the track.
     * @return The cached LocalTrackInfo, or null if not found.
     */
    public static LocalTrackInfo getCachedTrackInfo(String trackId) {
        return trackInfoCache.get(trackId);
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
            String originalFilename = extractFilenameFromUrl(fileUrl); // Get original filename for extension
            String extension = FilenameUtils.getExtension(originalFilename);
            if (extension.isEmpty()) extension = "tmp";
            
            tempFile = File.createTempFile("jmusicbot_dl_", "." + extension);
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
        
        // Decode URL-encoded characters (e.g., %20 to space)
        try {
            filename = java.net.URLDecoder.decode(filename, "UTF-8");
        } catch (java.io.UnsupportedEncodingException e) {
            // This should not happen with UTF-8
            log.warn("UTF-8 decoding not supported, proceeding with original filename.", e);
        }
        
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
    
    /**
     * Determines the image format from raw image data
     * @param data The image data
     * @return The file extension (jpg, png, etc.)
     */
    private static String determineImageExtension(byte[] data) {
        if (data == null || data.length < 4) {
            return "jpg"; // Default to JPG
        }
        
        // Check PNG signature: 89 50 4E 47 (‰PNG)
        if (data[0] == (byte) 0x89 && data[1] == (byte) 0x50 && data[2] == (byte) 0x4E && data[3] == (byte) 0x47) {
            return "png";
        }
        
        // Check JPEG signature: FF D8 FF
        if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8 && data[2] == (byte) 0xFF) {
            return "jpg";
        }
        
        // Check GIF signature: 47 49 46 (GIF)
        if (data[0] == (byte) 0x47 && data[1] == (byte) 0x49 && data[2] == (byte) 0x46) {
            return "gif";
        }
        
        // Add WebP check: RIFF ???? WEBP
        if (data.length >= 12 &&
            data[0] == 'R' && data[1] == 'I' && data[2] == 'F' && data[3] == 'F' &&
            data[8] == 'W' && data[9] == 'E' && data[10] == 'B' && data[11] == 'P') {
            return "webp";
        }

        return "jpg"; // Default to JPG if unknown
    }

    /**
     * Calculates SHA-256 hash for artwork data to be used as filename.
     * @param data The image data.
     * @return Hex string representation of the hash.
     * @throws NoSuchAlgorithmException If SHA-256 is not available.
     */
    private static String calculateArtworkHash(byte[] data) throws NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        byte[] hash = digest.digest(data);
        Formatter formatter = new Formatter();
        for (byte b : hash) {
            formatter.format("%02x", b);
        }
        String result = formatter.toString();
        formatter.close();
        return result;
    }
} 