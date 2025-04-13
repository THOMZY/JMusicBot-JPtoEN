/*
 * Copyright 2023 Cosgy Dev
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.jagrosh.jmusicbot.audio;

import com.jagrosh.jmusicbot.Bot;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Handler for retrieving and processing ICY metadata from streaming audio sources
 * This class focuses on extracting artist, track, and station information from streams
 * added via the /play command rather than the /radio command.
 * 
 * @author Cosgy Dev
 */
public class IcyMetadataHandler {
    
    private final Bot bot;
    private final ScheduledExecutorService scheduler;
    private final Map<String, StreamMetadata> metadataCache; // guildId -> metadata
    private final Map<String, ScheduledFuture<?>> updateTasks; // guildId -> update task
    
    // Regex patterns for extracting data from ICY metadata
    private static final Pattern STREAM_TITLE_PATTERN = Pattern.compile("StreamTitle='([^']*)';");
    private static final Pattern ARTIST_TITLE_PATTERN = Pattern.compile("(.*?)\\s*[-–—]\\s*(.*)");
    
    /**
     * Constructor for IcyMetadataHandler
     * @param bot The bot instance
     */
    public IcyMetadataHandler(Bot bot) {
        this.bot = bot;
        this.scheduler = bot.getThreadpool();
        this.metadataCache = new ConcurrentHashMap<>();
        this.updateTasks = new ConcurrentHashMap<>();
    }
    
    /**
     * Class that stores metadata information for a stream
     */
    public static class StreamMetadata {
        private String stationName;
        private String currentTrack;
        private String artist;
        private String title;
        private String albumArt;
        private String stationLogo;
        private String stationGenre;
        private long lastUpdated;
        private boolean failed;
        
        public StreamMetadata() {
            this.stationName = "Unknown Station";
            this.currentTrack = "";
            this.artist = "";
            this.title = "";
            this.albumArt = "";
            this.stationLogo = "";
            this.stationGenre = "";
            this.lastUpdated = System.currentTimeMillis();
            this.failed = false;
        }
        
        public String getStationName() {
            return stationName;
        }
        
        public String getCurrentTrack() {
            return currentTrack;
        }
        
        public String getArtist() {
            return artist;
        }
        
        public String getTitle() {
            return title;
        }
        
        public String getAlbumArt() {
            return albumArt;
        }
        
        public String getStationLogo() {
            return stationLogo;
        }
        
        public String getStationGenre() {
            return stationGenre;
        }
        
        public long getLastUpdated() {
            return lastUpdated;
        }
        
        public boolean hasFailed() {
            return failed;
        }
        
        public void setStationName(String stationName) {
            this.stationName = stationName;
        }
        
        public void setCurrentTrack(String currentTrack) {
            this.currentTrack = currentTrack;
        }
        
        public void setArtist(String artist) {
            this.artist = artist;
        }
        
        public void setTitle(String title) {
            this.title = title;
        }
        
        public void setAlbumArt(String albumArt) {
            this.albumArt = albumArt;
        }
        
        public void setStationLogo(String stationLogo) {
            this.stationLogo = stationLogo;
        }
        
        public void setStationGenre(String stationGenre) {
            this.stationGenre = stationGenre;
        }
        
        public void setLastUpdated(long lastUpdated) {
            this.lastUpdated = lastUpdated;
        }
        
        public void setFailed(boolean failed) {
            this.failed = failed;
        }
        
        /**
         * Update the metadata from a stream title string
         * @param streamTitle The raw stream title from ICY metadata
         */
        public void updateFromStreamTitle(String streamTitle) {
            if (streamTitle == null || streamTitle.trim().isEmpty()) {
                return;
            }
            
            this.currentTrack = streamTitle.trim();
            this.lastUpdated = System.currentTimeMillis();
            this.failed = false;
            
            // Try to split artist and title if format is "Artist - Title"
            Matcher matcher = ARTIST_TITLE_PATTERN.matcher(streamTitle);
            if (matcher.matches()) {
                this.artist = matcher.group(1).trim();
                this.title = matcher.group(2).trim();
                
                // Ensure neither artist nor title is empty
                if (this.artist.isEmpty()) {
                    this.artist = "Unknown Artist";
                }
                if (this.title.isEmpty()) {
                    this.title = "Unknown Title";
                }
            } else {
                // If it doesn't match the pattern, use the whole string as title
                this.title = streamTitle.trim();
                this.artist = ""; // Clear artist if pattern doesn't match
            }
        }
    }
    
    /**
     * Start monitoring a stream for ICY metadata updates
     * @param guildId The guild ID
     * @param track The audio track to monitor
     */
    public void startMonitoring(String guildId, AudioTrack track) {
        // Don't start monitoring if the track is null
        if (track == null) {
            return;
        }
        
        // Don't process tracks that aren't streams
        if (!track.getInfo().isStream) {
            return;
        }
        
        // Don't process tracks that already have radio data from the /radio command
        RequestMetadata rm = track.getUserData(RequestMetadata.class);
        if (rm != null && rm.hasRadioData()) {
            return;
        }
        
        // Don't start if we're already monitoring this guild
        if (updateTasks.containsKey(guildId)) {
            return;
        }
        
        // Initialize metadata entry for this guild
        StreamMetadata metadata = new StreamMetadata();
        metadataCache.put(guildId, metadata);
        
        // Set initial station name from track info
        metadata.setStationName(getStationNameFromTrack(track));
        
        // Initialize with a first metadata fetch
        fetchMetadata(guildId, track);
        
        // Schedule periodic updates (every 30 seconds)
        ScheduledFuture<?> task = scheduler.scheduleAtFixedRate(
            () -> fetchMetadata(guildId, track),
            30, 30, TimeUnit.SECONDS
        );
        
        updateTasks.put(guildId, task);
    }
    
    /**
     * Stop monitoring a stream for ICY metadata updates
     * @param guildId The guild ID
     */
    public void stopMonitoring(String guildId) {
        ScheduledFuture<?> task = updateTasks.remove(guildId);
        if (task != null) {
            task.cancel(false);
        }
        
        metadataCache.remove(guildId);
    }
    
    /**
     * Get stream metadata for a guild
     * @param guildId The guild ID
     * @return The StreamMetadata or null if not available
     */
    public StreamMetadata getMetadata(String guildId) {
        return metadataCache.get(guildId);
    }
    
    /**
     * Check if a guild has cached metadata
     * @param guildId The guild ID
     * @return True if metadata is available
     */
    public boolean hasMetadata(String guildId) {
        return metadataCache.containsKey(guildId);
    }
    
    /**
     * Extract the station name from a track
     * @param track The audio track
     * @return The station name
     */
    private String getStationNameFromTrack(AudioTrack track) {
        String title = track.getInfo().title;
        
        // Clean up common stream title formats to extract station name
        if (title == null || title.isEmpty() || title.equals("Unknown title")) {
            // Try to extract a name from the URL if title is unavailable
            String url = track.getInfo().uri;
            if (url != null) {
                try {
                    URL u = new URL(url);
                    String host = u.getHost();
                    
                    // Convert something like "stream.example.com" to "Example Radio"
                    if (host.startsWith("stream.")) {
                        host = host.substring(7);
                    } else if (host.startsWith("listen.")) {
                        host = host.substring(7);
                    }
                    
                    if (host.contains(".")) {
                        host = host.substring(0, host.lastIndexOf('.'));
                    }
                    
                    // Format nicely: convert "example-radio" to "Example Radio"
                    String[] parts = host.split("[-.]+");
                    StringBuilder stationName = new StringBuilder();
                    for (String part : parts) {
                        if (!part.isEmpty()) {
                            stationName.append(Character.toUpperCase(part.charAt(0)))
                                      .append(part.substring(1))
                                      .append(" ");
                        }
                    }
                    
                    if (stationName.length() > 0) {
                        stationName.append("Radio");
                        return stationName.toString().trim();
                    }
                } catch (Exception e) {
                    // Ignore URL parsing errors
                }
            }
            
            return "Stream Radio";
        }
        
        // If title contains " - ", use the part after it as station name
        if (title.contains(" | ")) {
            return title.substring(title.lastIndexOf(" | ") + 3);
        } 
        
        // Return the title as is
        return title;
    }
    
    /**
     * Fetch metadata from a streaming URL
     * @param guildId The guild ID
     * @param track The audio track
     */
    private void fetchMetadata(String guildId, AudioTrack track) {
        if (track == null || !track.getInfo().isStream) {
            return;
        }
        
        String streamUrl = track.getInfo().uri;
        if (streamUrl == null || streamUrl.isEmpty()) {
            return;
        }
        
        // Create a copy of the track info to avoid potential concurrency issues
        final String trackUrl = streamUrl;
        final long trackPosition = track.getPosition();
        final String trackTitle = track.getInfo().title;
        
        final StreamMetadata metadata = metadataCache.get(guildId);
        if (metadata == null) {
            StreamMetadata newMetadata = new StreamMetadata();
            metadataCache.put(guildId, newMetadata);
            fetchMetadata(guildId, track); // Retry with the new metadata
            return;
        }
        
        // Start a separate thread for network operations
        new Thread(() -> {
            try {
                // First try using HTTP request with Icy-MetaData header
                tryIcyMetadataRequest(trackUrl, metadata);
                
                // If that fails, try using the ListenAPI for known radio stations
                if (metadata.hasFailed()) {
                    tryListenApiLookup(trackUrl, metadata);
                }
                
                // If still no success, try RadioBrowser API as last resort
                if (metadata.hasFailed() || metadata.getCurrentTrack().isEmpty()) {
                    tryRadioBrowserLookup(trackUrl, metadata);
                }
                
                // Update station logo if missing
                if (metadata.getStationLogo().isEmpty()) {
                    tryFindStationLogo(metadata.getStationName(), metadata);
                }
                
                // Update album art if missing but we have artist and title
                if (metadata.getAlbumArt().isEmpty() && 
                    !metadata.getArtist().isEmpty() && 
                    !metadata.getTitle().isEmpty()) {
                    tryFindAlbumArt(metadata.getArtist(), metadata.getTitle(), metadata);
                }
                
                // Get the current track from the player to ensure it's still the same
                Guild guild = bot.getJDA().getGuildById(guildId);
                if (guild != null) {
                    AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
                    if (handler != null) {
                        AudioTrack currentTrack = handler.getPlayer().getPlayingTrack();
                        
                        // Only update if we're still playing the same track
                        if (currentTrack != null && currentTrack.getInfo().uri.equals(trackUrl)) {
                            // Update the track with the new metadata
                            updateTrackWithMetadata(currentTrack, metadata);
                            
                            // Update the nowplaying display with the new metadata
                            bot.getNowplayingHandler().onTrackUpdate(guild.getIdLong(), currentTrack, handler);
                            
                            // Update the channel topic as well
                            bot.getNowplayingHandler().updateTopic(guild.getIdLong(), handler, false);
                        }
                    }
                }
                
            } catch (Exception e) {
                // Log error but don't crash
                System.out.println("Error fetching ICY metadata: " + e.getMessage());
                metadata.setFailed(true);
            }
        }).start();
    }
    
    /**
     * Try to fetch ICY metadata directly from the stream URL
     * @param streamUrl The stream URL
     * @param metadata The metadata object to update
     */
    private void tryIcyMetadataRequest(String streamUrl, StreamMetadata metadata) {
        HttpURLConnection connection = null;
        InputStream in = null;
        
        try {
            URL url = new URL(streamUrl);
            connection = (HttpURLConnection) url.openConnection();
            
            // Set up headers for ICY metadata
            connection.setRequestProperty("Icy-MetaData", "1");
            connection.setRequestProperty("User-Agent", "JMusicBot ICY Client/1.0");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            // Connect and check for ICY metadata interval
            connection.connect();
            
            // Check headers for station info
            String contentType = connection.getContentType();
            if (contentType != null && !contentType.isEmpty()) {
                if (contentType.contains("audio/")) {
                    // This is confirmed to be an audio stream
                    if (contentType.contains(";")) {
                        String[] parts = contentType.split(";");
                        for (String part : parts) {
                            part = part.trim();
                            if (part.startsWith("charset=")) {
                                // Found charset information
                            }
                        }
                    }
                }
            }
            
            // Extract ICY headers for station information
            extractIcyHeaders(connection, metadata);
            
            // Check if we can get stream metadata
            String icyMetaInt = connection.getHeaderField("icy-metaint");
            if (icyMetaInt != null) {
                int metaInt = Integer.parseInt(icyMetaInt);
                in = connection.getInputStream();
                
                // Skip to the first metadata
                in.skip(metaInt);
                
                // Read the metadata
                int metaLength = in.read() * 16;
                
                if (metaLength > 0) {
                    byte[] metaData = new byte[metaLength];
                    in.read(metaData);
                    
                    // Convert to string and extract stream title
                    String metaString = new String(metaData, StandardCharsets.UTF_8).trim();
                    Matcher matcher = STREAM_TITLE_PATTERN.matcher(metaString);
                    if (matcher.find()) {
                        String streamTitle = matcher.group(1);
                        metadata.updateFromStreamTitle(streamTitle);
                    }
                }
            } else {
                // If no ICY metadata, check if we received a redirect
                String location = connection.getHeaderField("Location");
                if (location != null && !location.equals(streamUrl)) {
                    // Follow the redirect
                    tryIcyMetadataRequest(location, metadata);
                }
            }
            
        } catch (Exception e) {
            metadata.setFailed(true);
        } finally {
            try {
                if (in != null) in.close();
                if (connection != null) connection.disconnect();
            } catch (IOException e) {
                // Ignore close errors
            }
        }
    }
    
    /**
     * Extract ICY headers from a connection to get station information
     * @param connection The HTTP connection
     * @param metadata The metadata to update
     */
    private void extractIcyHeaders(HttpURLConnection connection, StreamMetadata metadata) {
        // Get station name from icy-name
        String icyName = connection.getHeaderField("icy-name");
        if (icyName != null && !icyName.isEmpty()) {
            metadata.setStationName(icyName);
        }
        
        // Get station genre from icy-genre
        String icyGenre = connection.getHeaderField("icy-genre");
        if (icyGenre != null && !icyGenre.isEmpty()) {
            metadata.setStationGenre(icyGenre);
        }
        
        // Get station URL which might contain logo
        String icyUrl = connection.getHeaderField("icy-url");
        if (icyUrl != null && !icyUrl.isEmpty() && metadata.getStationLogo().isEmpty()) {
            // Try to get favicon from the URL
            try {
                URL url = new URL(icyUrl);
                String favicon = url.getProtocol() + "://" + url.getHost() + "/favicon.ico";
                metadata.setStationLogo(favicon);
            } catch (Exception e) {
                // Ignore URL parsing errors
            }
        }
    }
    
    /**
     * Try to look up stream info using the RadioBrowser API
     * @param streamUrl The stream URL
     * @param metadata The metadata to update
     */
    private void tryRadioBrowserLookup(String streamUrl, StreamMetadata metadata) {
        try {
            // RadioBrowser API to find station by stream URL
            String encodedUrl = Base64.getUrlEncoder().encodeToString(streamUrl.getBytes(StandardCharsets.UTF_8));
            String apiUrl = "https://de1.api.radio-browser.info/json/stations/byurl/" + encodedUrl;
            
            JSONObject json = fetchJson(apiUrl);
            if (json != null && json.has("name") && !json.isNull("name")) {
                // Found station info
                metadata.setStationName(json.getString("name"));
                
                if (json.has("favicon") && !json.isNull("favicon")) {
                    metadata.setStationLogo(json.getString("favicon"));
                }
                
                if (json.has("tags") && !json.isNull("tags")) {
                    metadata.setStationGenre(json.getString("tags"));
                }
                
                // If we found the station but don't have current track info,
                // try to get it from the station's "now playing" info
                if (metadata.getCurrentTrack().isEmpty() && json.has("now_playing") && !json.isNull("now_playing")) {
                    metadata.updateFromStreamTitle(json.getString("now_playing"));
                }
            }
        } catch (Exception e) {
            // Just ignore errors for this optional lookup
        }
    }
    
    /**
     * Try to look up stream info using the ListenAPI
     * @param streamUrl The stream URL
     * @param metadata The metadata to update
     */
    private void tryListenApiLookup(String streamUrl, StreamMetadata metadata) {
        try {
            // This is a placeholder - a real implementation would use an actual Radio API
            // This is just to illustrate the concept
            
            // In a real implementation, you would:
            // 1. Query an API like ListenAPI or RadioBrowser with the stream URL
            // 2. Parse the response to get station info and current track
            // 3. Update the metadata object
            
            // For now, we'll just use the basic information we have
            if (metadata.getStationName().equals("Unknown Station") && streamUrl.contains("radio")) {
                metadata.setStationName(extractStationNameFromUrl(streamUrl));
            }
        } catch (Exception e) {
            // Just ignore errors for this optional lookup
        }
    }
    
    /**
     * Try to find album art for a track
     * @param artist The artist name
     * @param title The track title
     * @param metadata The metadata to update
     */
    private void tryFindAlbumArt(String artist, String title, StreamMetadata metadata) {
        try {
            // Search LastFM or another service for album art
            // This is a placeholder for actual implementation
            
            // In a real implementation, you would:
            // 1. Query an API like LastFM, Spotify, or iTunes with artist and title
            // 2. Parse the response to get album art URL
            // 3. Update the metadata object
        } catch (Exception e) {
            // Just ignore errors for this optional lookup
        }
    }
    
    /**
     * Try to find a logo for a radio station
     * @param stationName The station name
     * @param metadata The metadata to update
     */
    private void tryFindStationLogo(String stationName, StreamMetadata metadata) {
        try {
            // Search for a station logo
            // This is a placeholder for actual implementation
            
            // In a real implementation, you would:
            // 1. Query RadioBrowser or another API with the station name
            // 2. Parse the response to get station logo URL
            // 3. Update the metadata object
        } catch (Exception e) {
            // Just ignore errors for this optional lookup
        }
    }
    
    /**
     * Helper method to extract a station name from a URL
     * @param url The stream URL
     * @return A cleaned up station name
     */
    private String extractStationNameFromUrl(String url) {
        try {
            URL u = new URL(url);
            String host = u.getHost();
            
            // Handle common radio URL patterns
            if (host.startsWith("stream.")) {
                host = host.substring(7);
            } else if (host.startsWith("listen.")) {
                host = host.substring(7);
            }
            
            // Remove domain extension
            if (host.contains(".")) {
                host = host.substring(0, host.lastIndexOf('.'));
            }
            
            // Format the host into a station name
            StringBuilder stationName = new StringBuilder();
            for (String part : host.split("[-.]+")) {
                if (!part.isEmpty()) {
                    stationName.append(Character.toUpperCase(part.charAt(0)))
                              .append(part.substring(1))
                              .append(" ");
                }
            }
            
            return stationName.toString().trim() + " Radio";
        } catch (Exception e) {
            return "Stream Radio";
        }
    }
    
    /**
     * Helper method to fetch JSON from a URL
     * @param urlString The URL to fetch from
     * @return A JSONObject or null if failed
     */
    private JSONObject fetchJson(String urlString) {
        HttpURLConnection connection = null;
        BufferedReader reader = null;
        
        try {
            URL url = new URL(urlString);
            connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "JMusicBot/1.0");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            
            int responseCode = connection.getResponseCode();
            if (responseCode != 200) {
                return null;
            }
            
            reader = new BufferedReader(new InputStreamReader(connection.getInputStream()));
            StringBuilder response = new StringBuilder();
            String line;
            
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            
            return new JSONObject(response.toString());
            
        } catch (IOException | JSONException e) {
            return null;
        } finally {
            try {
                if (reader != null) reader.close();
                if (connection != null) connection.disconnect();
            } catch (IOException e) {
                // Ignore close errors
            }
        }
    }
    
    /**
     * Update the RequestMetadata for a track with ICY metadata information
     * @param track The track to update
     * @param metadata The stream metadata
     * @return The updated track
     */
    public AudioTrack updateTrackWithMetadata(AudioTrack track, StreamMetadata metadata) {
        if (track == null || metadata == null) {
            return track;
        }
        
        // Get existing metadata or create new
        RequestMetadata rm = track.getUserData(RequestMetadata.class);
        if (rm == null) {
            rm = new RequestMetadata(null);
        }
        
        // Only update if we don't already have radio data (to avoid conflicts with /radio command)
        if (!rm.hasRadioData()) {
            // Create a station UUID from URL hash to identify this station
            String stationUuid = String.valueOf(track.getInfo().uri.hashCode());
            
            // Ensure we have a valid station name
            String stationName = metadata.getStationName();
            if (stationName == null || stationName.trim().isEmpty()) {
                stationName = "Radio Stream";
            }
            
            // Get station logo or use default
            String stationLogo = metadata.getStationLogo();
            if (stationLogo == null || stationLogo.trim().isEmpty()) {
                // Default stream icon
                stationLogo = "https://cdn-icons-png.flaticon.com/128/11796/11796884.png";
            }
            
            // Set radio info
            rm.setRadioInfo(
                stationUuid,  // Use URL hash as path 
                stationName,
                stationLogo,
                stationUuid
            );
            
            // Update track user data
            track.setUserData(rm);
        }
        
        return track;
    }
    
    /**
     * Shuts down the handler and cleans up resources
     */
    public void shutdown() {
        // Cancel all update tasks
        for (Map.Entry<String, ScheduledFuture<?>> entry : updateTasks.entrySet()) {
            try {
                if (entry.getValue() != null && !entry.getValue().isDone()) {
                    entry.getValue().cancel(false);
                }
            } catch (Exception e) {
                // Ignore shutdown errors
            }
        }
        
        // Clear all maps
        updateTasks.clear();
        metadataCache.clear();
    }
} 