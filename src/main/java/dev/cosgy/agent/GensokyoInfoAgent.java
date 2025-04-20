/*
 *  Copyright 2022 Cosgy Dev (info@cosgy.dev).
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
import dev.cosgy.agent.objects.ResultSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public class GensokyoInfoAgent extends Thread {
    private static final Logger log = LoggerFactory.getLogger(GensokyoInfoAgent.class);
    private static final long UPDATE_INTERVAL_MILLIS = 1000; // 1 second interval for incrementing time
    private static ResultSet info = null;
    private static String lastSong = "";
    private static boolean needsUpdate = true;
    private static long lastUpdateTime = 0;
    private static final long MIN_UPDATE_INTERVAL = 30000; // Minimum 30 seconds between API calls
    
    // Store the API URL and update it if redirected
    private static String API_URL = "https://gensokyoradio.net/api/station/playing/";
    
    // Single instance management
    private static GensokyoInfoAgent instance = null;
    private static boolean isRunning = false;

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
            log.debug("Started GensokyoInfoAgent");
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
            // Check if current song is still playing and update not forced
            if (info != null && !needsUpdate) {
                if (info.getSongtimes().getPlayed() < info.getSongtimes().getDuration()) {
                    return info;
                }
            }
            
            // Limit API calls to avoid overloading the server
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastUpdateTime < MIN_UPDATE_INTERVAL && info != null && !needsUpdate) {
                return info;
            }
            
            lastUpdateTime = currentTime;
            needsUpdate = false;
            
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
                        
                        // Check if song has changed
                        if (info == null || !newInfo.getSonginfo().getTitle().equals(lastSong)) {
                            lastSong = newInfo.getSonginfo().getTitle();
                            log.debug("Now playing on Gensokyo Radio: {} - {}", 
                                     newInfo.getSonginfo().getArtist(), 
                                     newInfo.getSonginfo().getTitle());
                        }
                        
                        info = newInfo;
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
        } catch (Exception e) {
            log.error("Error during initial fetch: {}", e.getMessage());
        }

        // Main loop
        while (true) {
            try {
                sleep(UPDATE_INTERVAL_MILLIS);
                
                // Increment played time
                if (info != null) {
                    info.getSongtimes().setPlayed(info.getSongtimes().getPlayed() + 1);
                    
                    // Check if song has ended
                    if (info.getSongtimes().getPlayed() >= info.getSongtimes().getDuration()) {
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
            } catch (InterruptedException e) {
                log.error("GensokyoInfoAgent interrupted: {}", e.getMessage());
                break;
            } catch (Exception e) {
                log.error("Error in GensokyoInfoAgent: {}", e.getMessage());
            }
        }
    }
}
