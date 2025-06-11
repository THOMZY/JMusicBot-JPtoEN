/*
 *  Copyright 2022 Cosgy Dev (info@cosgy.dev).
 * Edit 2025 THOMZY
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package dev.cosgy.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.cosgy.agent.objects.ResultSet;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class GensokyoInfoAgent extends Thread {
    private static final Logger log = LoggerFactory.getLogger(GensokyoInfoAgent.class);
    private static final long UPDATE_INTERVAL_MILLIS = 1000; // 1 second interval for incrementing time
    private static ResultSet info = null;
    private static String lastSong = "";
    private static boolean needsUpdate = true;
    private static long lastUpdateTime = 0;
    private static final long MIN_UPDATE_INTERVAL = 30000; // Minimum 30 seconds between API calls
    
    // Maximum track duration to prevent excessive delays in track updates
    private static final int MAX_TRACK_DURATION = 600; // 10 minutes (in seconds)
    
    // Force API check interval regardless of track duration
    private static final long FORCE_API_CHECK_INTERVAL = 120000; // 2 minutes (in milliseconds)
    private static long lastForceCheckTime = 0;
    
    // Store the API URL and update it if redirected
    private static String API_URL = "https://gensokyoradio.net/api/station/playing/";
    
    // Single instance management
    private static GensokyoInfoAgent instance = null;
    private static boolean isRunning = false;

    // Listener for track changes
    private static final List<GensokyoTrackChangeListener> trackChangeListeners = new ArrayList<>();
    
    // Bot instance for adding tracks to history
    private static Bot bot = null;
    
    // Map to track playing Gensokyo Radio tracks by guild ID
    private static final Map<String, AudioTrack> gensokyoTracks = new HashMap<>();
    
    /**
     * Interface for listening to Gensokyo Radio track changes
     */
    public interface GensokyoTrackChangeListener {
        void onTrackChanged(ResultSet trackInfo);
    }
    
    /**
     * Adds a listener for track changes
     * @param listener The listener to add
     */
    public static void addTrackChangeListener(GensokyoTrackChangeListener listener) {
        if (listener != null && !trackChangeListeners.contains(listener)) {
            trackChangeListeners.add(listener);
        }
    }
    
    /**
     * Removes a listener for track changes
     * @param listener The listener to remove
     */
    public static void removeTrackChangeListener(GensokyoTrackChangeListener listener) {
        trackChangeListeners.remove(listener);
    }
    
    /**
     * Fires the track changed event to all listeners
     * @param trackInfo The new track information
     */
    private static void fireTrackChangedEvent(ResultSet trackInfo) {
        // Create a copy of the list to avoid concurrent modification issues
        List<GensokyoTrackChangeListener> listeners = new ArrayList<>(trackChangeListeners);
        
        // Notify all listeners
        for (GensokyoTrackChangeListener listener : listeners) {
            try {
                listener.onTrackChanged(trackInfo);
            } catch (Exception e) {
                log.error("Error notifying track change listener: {}", e.getMessage());
            }
        }
        
        // Add the track to music history if a bot is set and tracks are registered
        addToMusicHistory(trackInfo);
    }
    
    /**
     * Adds the current Gensokyo Radio track to the music history
     * @param trackInfo The current track information
     */
    private static void addToMusicHistory(ResultSet trackInfo) {
        if (bot == null || gensokyoTracks.isEmpty() || trackInfo == null || 
            trackInfo.getSonginfo() == null) {
            return;
        }
        
        // Check if critical information is missing
        String trackTitle = trackInfo.getSonginfo().getTitle();
        String trackArtist = trackInfo.getSonginfo().getArtist();
        
        if (trackTitle == null || trackTitle.isEmpty() || 
            trackArtist == null || trackArtist.isEmpty()) {
            log.warn("Missing track information from Gensokyo Radio API, skipping history update");
            return;
        }
        
        
        // Process each guild that has a Gensokyo Radio track playing
        for (Map.Entry<String, AudioTrack> entry : gensokyoTracks.entrySet()) {
            String guildId = entry.getKey();
            AudioTrack track = entry.getValue();
            
            try {
                // Get the guild from the ID
                long guildIdLong = Long.parseLong(guildId);
                Guild guild = bot.getJDA().getGuildById(guildIdLong);
                if (guild == null) continue;
                
                // Get the audio handler
                AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
                if (handler == null) continue;
                
                // Mark the original track with the updated information
                // (This will be picked up by NowplayingHandler)
                Object originalUserData = track.getUserData();
                if (originalUserData instanceof com.jagrosh.jmusicbot.audio.RequestMetadata) {
                    // Preserve the original RequestMetadata if it exists
                    log.debug("Preserving original RequestMetadata for track");
                } else {
                    // If we don't have a RequestMetadata, use a simple string marker
                    track.setUserData("TRACK_UPDATE:" + trackTitle + "|" + trackArtist);
                    log.debug("Set track info marker on original track");
                }
                
                // Create a completely new track copy to avoid metadata inheritance issues
                AudioTrack trackCopy = track.makeClone();
                
                // Create a fresh RequestMetadata for the history entry
                // We need this because MusicHistory expects a RequestMetadata object
                com.jagrosh.jmusicbot.audio.RequestMetadata rm = new com.jagrosh.jmusicbot.audio.RequestMetadata((net.dv8tion.jda.api.entities.User)null);
                
                // Store the track info directly in the trackCopy fields
                // Don't try to store in RequestMetadata as it doesn't have a method for this
                trackCopy.setUserData(rm);
                
                try {
                    // Update title
                    java.lang.reflect.Field titleField = trackCopy.getInfo().getClass().getDeclaredField("title");
                    titleField.setAccessible(true);
                    titleField.set(trackCopy.getInfo(), trackTitle);
                    
                    // Update artist
                    java.lang.reflect.Field artistField = trackCopy.getInfo().getClass().getDeclaredField("author");
                    artistField.setAccessible(true);
                    artistField.set(trackCopy.getInfo(), trackArtist);
                    
                    log.debug("Updated track metadata - Title: '{}', Artist: '{}'", trackTitle, trackArtist);
                } catch (Exception e) {
                    log.error("Error updating track metadata: {}", e.getMessage());
                }
                
                // Add the track to history
                if (bot.getConfig().isHistoryEnabled()) {
                    // Force a refresh of the history with the latest track info
                    bot.getMusicHistory().addTrack(trackCopy, handler);
                }
            } catch (Exception e) {
                log.error("Error adding Gensokyo Radio track to history: {}", e.getMessage());
            }
        }
    }

    /**
     * Register a track as a Gensokyo Radio track for a specific guild
     * @param guildId The ID of the guild
     * @param track The Gensokyo Radio track
     */
    public static void registerTrack(String guildId, AudioTrack track) {
        if (guildId != null && track != null) {
            gensokyoTracks.put(guildId, track);
            log.debug("Registered Gensokyo Radio track for guild: {}", guildId);
        }
    }
    
    /**
     * Unregister a track for a specific guild
     * @param guildId The ID of the guild
     */
    public static void unregisterTrack(String guildId) {
        if (guildId != null) {
            gensokyoTracks.remove(guildId);
            log.debug("Unregistered Gensokyo Radio track for guild: {}", guildId);
        }
    }
    
    /**
     * Set the bot instance for adding tracks to history
     * @param botInstance The bot instance
     */
    public static void setBot(Bot botInstance) {
        bot = botInstance;
    }

    private GensokyoInfoAgent() {
        setDaemon(true);
        setName("GensokyoInfoAgent");
    }

    /**
     * Get or create the single instance of GensokyoInfoAgent
     * @return The GensokyoInfoAgent instance
     */
    public static synchronized GensokyoInfoAgent getInstance() {
        if (instance == null) {
            instance = new GensokyoInfoAgent();
        }
        return instance;
    }

    /**
     * Start the agent if it's not already running
     */
    public static synchronized void startAgent() {
        if (!isRunning) {
            getInstance().start();
            isRunning = true;
            log.info("Started GensokyoInfoAgent - Will update Gensokyo Radio track information");
        }
    }

    /**
     * Force the agent to update information on next call
     */
    public static void forceUpdate() {
        needsUpdate = true;
    }

    /**
     * Fetch information from Gensokyo Radio API
     * @return ResultSet containing radio information or null if error
     */
    private static ResultSet fetch() throws Exception {
        HttpURLConnection connection = null;
        try {
            // Check if it's time for a forced update regardless of duration
            long currentTime = System.currentTimeMillis();
            boolean forceCheck = (currentTime - lastForceCheckTime) >= FORCE_API_CHECK_INTERVAL;
            
            if (forceCheck) {
                log.debug("Forcing API check based on time interval");
                lastForceCheckTime = currentTime;
                needsUpdate = true;
            }
            
            // Check if current song is still playing and update not forced
            if (info != null && !needsUpdate && !forceCheck) {
                // Validate and limit the song duration
                if (info.getSongtimes() != null && info.getSongtimes().getDuration() != null) {
                    // Ensure the duration doesn't exceed the maximum allowed
                    int reportedDuration = info.getSongtimes().getDuration();
                    int reportedPlayed = info.getSongtimes().getPlayed();
                    
                    // If duration is unrealistically long, cap it
                    if (reportedDuration > MAX_TRACK_DURATION) {
                        log.warn("Suspicious track duration detected: {} seconds. Capping at {} seconds", 
                                reportedDuration, MAX_TRACK_DURATION);
                        info.getSongtimes().setDuration(MAX_TRACK_DURATION);
                    }
                    
                    // Only return cached info if we haven't reached the end of the song
                    if (reportedPlayed < info.getSongtimes().getDuration()) {
                        return info;
                    }
                }
            }
            
            // Limit API calls to avoid overloading the server (except for forced checks)
            if (!forceCheck && currentTime - lastUpdateTime < MIN_UPDATE_INTERVAL && info != null && !needsUpdate) {
                return info;
            }
            
            lastUpdateTime = currentTime;
            needsUpdate = false;
            
            log.debug("Fetching Gensokyo Radio information from API...");
            
            // Set up HTTP request
            System.setProperty("http.agent", "Chrome");

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(10))
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .build();

            HttpRequest req = HttpRequest.newBuilder(new URI(API_URL))
                    .GET()
                    .timeout(Duration.ofSeconds(10))
                    .setHeader("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                    .setHeader("accept-language", "en-US,en;q=0.9")
                    .build();

            HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
            String body = res.body();

            // Log headers and status code for debugging
            log.debug("Response status: {}", res.statusCode());
            res.headers().map().forEach((key, values) -> 
                log.debug("Header {} : {}", key, String.join(", ", values)));

            switch (res.statusCode()) {
                case 200:
                    // Log the response body for debugging
                    log.debug("Response body: {}", body);
                    
                    // Mapping the JSON of the HTTP response to the ResultSet class
                    try {
                        ResultSet newInfo = new ObjectMapper().readValue(body, ResultSet.class);
                        
                        // Validate the data
                        if (newInfo == null || newInfo.getSonginfo() == null) {
                            log.warn("Invalid or incomplete response from Gensokyo Radio API");
                            return info; // Return existing info if response is invalid
                        }
                        
                        // Check if song has changed by comparing title and artist
                        boolean songChanged = false;
                        
                        if (info == null || info.getSonginfo() == null) {
                            // First time we're getting info, definitely treat as changed
                            songChanged = true;
                            log.debug("First track information received");
                        } else {
                            // Compare title and artist - consider it changed if either is different
                            String prevTitle = info.getSonginfo().getTitle();
                            String newTitle = newInfo.getSonginfo().getTitle();
                            String prevArtist = info.getSonginfo().getArtist();
                            String newArtist = newInfo.getSonginfo().getArtist();
                            
                            // Null-safe comparison
                            boolean titleSame = (prevTitle == null && newTitle == null) || 
                                              (prevTitle != null && newTitle != null && prevTitle.equals(newTitle));
                            boolean artistSame = (prevArtist == null && newArtist == null) || 
                                               (prevArtist != null && newArtist != null && prevArtist.equals(newArtist));
                            
                            songChanged = !titleSame || !artistSame;
                            
                            if (songChanged) {
                                log.debug("Track change detected: '{}' by '{}' -> '{}' by '{}'", 
                                         prevTitle, prevArtist, newTitle, newArtist);
                            }
                        }
                        
                        // Validate and fix track duration
                        if (newInfo.getSongtimes() != null && newInfo.getSongtimes().getDuration() != null) {
                            int duration = newInfo.getSongtimes().getDuration();
                            
                            // If duration is suspicious (too long or 0/negative), set a reasonable default
                            if (duration <= 0 || duration > MAX_TRACK_DURATION) {
                                log.warn("Invalid track duration reported: {} seconds. Setting reasonable default.", duration);
                                
                                // Set a default duration of 3 minutes (180 seconds)
                                newInfo.getSongtimes().setDuration(180);
                                
                                // Reset played time
                                if (newInfo.getSongtimes().getPlayed() != null) {
                                    newInfo.getSongtimes().setPlayed(0);
                                }
                            }
                        }
                        
                        // Update our cached info
                        info = newInfo;
                        
                        if (songChanged) {
                            lastSong = info.getSonginfo().getTitle();
                            
                            // Safe access to potentially null fields
                            String artist = info.getSonginfo().getArtist() != null ? info.getSonginfo().getArtist() : "Unknown Artist";
                            String title = info.getSonginfo().getTitle() != null ? info.getSonginfo().getTitle() : "Unknown Title";
                            String album = info.getSonginfo().getAlbum() != null ? info.getSonginfo().getAlbum() : "Unknown Album";
                            String circle = info.getSonginfo().getCircle() != null ? info.getSonginfo().getCircle() : "Unknown Circle";
                                                                
                            // Fire track changed event
                            fireTrackChangedEvent(info);
                        }
                        
                        return info;
                    } catch (Exception e) {
                        log.warn("Failed to parse JSON response: {}", e.getMessage());
                        log.debug("JSON parsing error details: ", e);
                        return info; // Return existing info if parsing fails
                    }
                case 301:
                case 302:
                case 307:
                case 308:
                    // Redirection should be handled automatically by the HttpClient
                    // If we still get here, log the location header
                    String location = res.headers().firstValue("Location").orElse("No location header");
                    log.warn("Unexpected redirection. Status: {}, Location: {}", res.statusCode(), location);
                    log.info("Trying to fetch from the new location directly");
                    
                    // Try to update the URL for future requests if we got a location header
                    if (!location.isEmpty() && !location.equals("No location header")) {
                        try {
                            // Update our API URL for future requests
                            API_URL = location;
                            log.info("Updated API URL to: {}", API_URL);
                            
                            HttpRequest redirectRequest = HttpRequest.newBuilder(new URI(location))
                                .GET()
                                .timeout(Duration.ofSeconds(10))
                                .setHeader("user-agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36")
                                .setHeader("accept-language", "en-US,en;q=0.9")
                                .build();
                            
                            HttpResponse<String> redirectRes = client.send(redirectRequest, HttpResponse.BodyHandlers.ofString());
                            
                            if (redirectRes.statusCode() == 200) {
                                log.info("Successfully fetched from redirected URL");
                                
                                // Try to parse the response
                                try {
                                    ResultSet redirectInfo = new ObjectMapper().readValue(redirectRes.body(), ResultSet.class);
                                    info = redirectInfo;
                                    return info;
                                } catch (Exception e) {
                                    log.warn("Failed to parse JSON from redirect: {}", e.getMessage());
                                }
                            }
                        } catch (Exception e) {
                            log.warn("Failed to follow redirection manually: {}", e.getMessage());
                        }
                    }
                    
                    return info; // Return existing info if redirect failed
                case 403:
                    log.warn("Gensokyo Radio information retrieval error (403) - Access forbidden");
                    log.debug("Body: {}", res.body());
                    return info; // Return existing info if available
                case 404:
                    log.warn("Gensokyo Radio information retrieval error (404) - Resource not found");
                    log.debug("Body: {}", res.body());
                    return info; // Return existing info if available
                case 429:
                    log.warn("Gensokyo Radio information retrieval error (429) - Too many requests");
                    // Increase the wait time before next attempt
                    lastUpdateTime = System.currentTimeMillis() + 60000; // Wait at least 1 minute
                    return info;
                default:
                    log.warn("Gensokyo Radio information retrieval error ({}) - Unexpected response code", res.statusCode());
                    log.debug("Body: {}", res.body());
                    return info; // Return existing info if available
            }

        } catch (Exception e) {
            log.warn("Failed to fetch Gensokyo Radio information: {}", e.getMessage());
            return info; // Return existing info if available
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    /**
     * Get current radio information, fetching from API if needed
     */
    public static ResultSet getInfo() throws Exception {
        // Ensure agent is running
        if (!isRunning) {
            startAgent();
        }
        
        if (info == null || needsUpdate) {
            return fetch();
        }
        return info;
    }

    @Override
    public void run() {
        
        // Initial fetch
        try {
            fetch();
            lastForceCheckTime = System.currentTimeMillis(); // Initialize the force check timer
        } catch (Exception e) {
            log.error("Error during initial fetch: {}", e.getMessage());
        }

        // Main loop
        while (true) {
            try {
                sleep(UPDATE_INTERVAL_MILLIS);
                
                // Check if it's time for a forced update
                long currentTime = System.currentTimeMillis();
                if (currentTime - lastForceCheckTime >= FORCE_API_CHECK_INTERVAL) {
                    log.debug("Periodic API check triggered");
                    lastForceCheckTime = currentTime;
                    needsUpdate = true;
                }
                
                // Increment played time
                if (info != null && info.getSongtimes() != null) {
                    // Only increment if we have valid time data
                    if (info.getSongtimes().getPlayed() != null) {
                        info.getSongtimes().setPlayed(info.getSongtimes().getPlayed() + 1);
                    }
                    
                    // Check if song has ended
                    if (info.getSongtimes().getDuration() != null && 
                        info.getSongtimes().getPlayed() != null &&
                        info.getSongtimes().getPlayed() >= info.getSongtimes().getDuration()) {
                        
                        log.debug("Track ended based on duration. Fetching next track.");
                        needsUpdate = true;
                        try {
                            fetch();
                        } catch (Exception e) {
                            log.warn("Failed to update track info: {}", e.getMessage());
                        }
                    }
                } else {
                    // If we don't have any info yet, try to fetch it
                    needsUpdate = true;
                }
                
                // If an update is needed, fetch now
                if (needsUpdate) {
                    try {
                        fetch();
                    } catch (Exception e) {
                        log.warn("Failed to fetch track info: {}", e.getMessage());
                    }
                }
            } catch (InterruptedException e) {
                log.error("GensokyoInfoAgent interrupted: {}", e.getMessage());
                break;
            } catch (Exception e) {
                log.error("Error in GensokyoInfoAgent: {}", e.getMessage());
            }
        }
    }

    /**
     * Stop the agent if it's running
     */
    public static synchronized void stopAgent() {
        if (isRunning && instance != null) {
            instance.interrupt();
            isRunning = false;
            instance = null;
            info = null; // Clear cached information
            lastSong = "";
            
            // Clear all track change listeners
            trackChangeListeners.clear();
            
            // Clear all registered tracks
            gensokyoTracks.clear();
            
            log.info("Stopped GensokyoInfoAgent - No more Gensokyo Radio streams playing");
        }
    }
}
