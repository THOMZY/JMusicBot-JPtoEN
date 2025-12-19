/*
 * Copyright 2025 THOMZY
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
package com.jagrosh.jmusicbot.audio;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.jagrosh.jmusicbot.Bot;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd;
import dev.cosgy.jmusicbot.slashcommands.music.RadioCmd;
import dev.cosgy.jmusicbot.util.LocalAudioMetadata;
import dev.cosgy.jmusicbot.util.YtDlpManager.FallbackPlatform;
import dev.cosgy.jmusicbot.util.YtDlpManager.YtDlpMetadata;
import net.dv8tion.jda.api.entities.User;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Class that manages the music playback history
 */
public class MusicHistory {
    private static final String HISTORY_FILENAME = "music_history.json";
    private final Bot bot;
    private final Path historyFile;
    private final ObjectMapper objectMapper;
    private final List<PlayRecord> history;
    private boolean enabled;
    
    // Track the last added Gensokyo Radio song and timestamp to prevent duplicates
    private String lastGensokyoSong = null;
    private long lastGensokyoTimestamp = 0;
    private static final long GENSOKYO_DUPLICATE_THRESHOLD = 2000; // 2 seconds threshold

    /**
     * Creates a new MusicHistory instance
     * @param bot The bot instance
     */
    public MusicHistory(Bot bot) {
        this.bot = bot;
        this.historyFile = Paths.get(HISTORY_FILENAME);
        this.objectMapper = new ObjectMapper();
        this.objectMapper.enable(SerializationFeature.INDENT_OUTPUT);
        this.history = new CopyOnWriteArrayList<>();
        this.enabled = bot.getConfig().isHistoryEnabled();
        
        // Create history file if it doesn't exist
        if (!Files.exists(historyFile)) {
            try {
                writeEmptyHistoryFile();
            } catch (IOException e) {
                System.err.println("Error creating history file: " + e.getMessage());
            }
        } else {
            // Load existing history
            loadHistory();
        }
    }

    private RequestMetadata getRequestMetadata(AudioTrack track) {
        if (track == null) return null;
        Object userData = track.getUserData();
        if (userData instanceof RequestMetadata) return (RequestMetadata) userData;
        if (userData instanceof PlayerManager.TrackContext) return (RequestMetadata) ((PlayerManager.TrackContext) userData).userData;
        return track.getUserData(RequestMetadata.class);
    }

    /**
     * Adds a track to the history
     * @param track The audio track that was played
     * @param handler The audio handler for the guild where the track was played
     */
    public void addTrack(AudioTrack track, AudioHandler handler) {
        if (!enabled) {
            return;
        }
        
        try {
            AudioTrackInfo info = PlayerManager.getDisplayInfo(track);
            if (info == null) {
                info = track.getInfo();
            }

            // Check for duplicates (same URL and Guild) using display info
            if (!history.isEmpty()) {
                PlayRecord lastRecord = history.get(0);
                String currentGuildId = String.valueOf(handler.getGuildId());
                String currentUrl = info.uri;
                long currentTime = System.currentTimeMillis();
                
                if (lastRecord.getGuildId().equals(currentGuildId) && lastRecord.getUrl().equals(currentUrl)) {
                    // If it's a stream (like YouTube Live), avoid adding duplicates regardless of time
                    // This handles long streams that might reconnect multiple times
                    // But exclude Gensokyo Radio and other Radios which have their own duplicate handling
                    boolean isStream = info.isStream;
                    if (isStream && !handler.isGensokyoRadioTrack(track) && !handler.isRadioTrack(track)) {
                        return;
                    }
                    
                    // For regular tracks, 60s window to prevent accidental double-adds
                    if (currentTime - lastRecord.getPlayedAt() < 60000) {
                        return;
                    }
                }
            }

            // Check for Gensokyo Radio duplicates first
            if (handler.isGensokyoRadioTrack(track)) {
                try {
                    dev.cosgy.agent.objects.ResultSet grInfo = dev.cosgy.agent.GensokyoInfoAgent.getInfo();
                    if (grInfo != null && grInfo.getSonginfo() != null) {
                        String currentSong = grInfo.getSonginfo().getArtist() + " - " + grInfo.getSonginfo().getTitle();
                        long currentTime = System.currentTimeMillis();
                        
                        // Check if this exact song was just added (within threshold)
                        if (currentSong.equals(lastGensokyoSong) && 
                            (currentTime - lastGensokyoTimestamp) < GENSOKYO_DUPLICATE_THRESHOLD) {
                            // Skip adding duplicate entry
                            System.out.println("Skipping duplicate Gensokyo Radio track: " + currentSong);
                            return;
                        }
                        
                        // Update tracking variables
                        lastGensokyoSong = currentSong;
                        lastGensokyoTimestamp = currentTime;
                    }
                } catch (Exception e) {
                    // Ignore error and continue with adding the track
                }
            }
            
            RequestMetadata rm = getRequestMetadata(track);
            
            User requester = rm != null && rm.getOwner() != 0 
                ? bot.getJDA().getUserById(rm.getOwner()) 
                : null;
            
            String guildId = String.valueOf(handler.getGuildId());
            String guildName = bot.getJDA().getGuildById(handler.getGuildId()).getName();
            
            // For Gensokyo Radio, get the current track info directly from the agent
            String title = info.title;
            String artist = info.author;
            
            // Clean radio titles by removing "| RADIO NAME" suffix
            if (handler.isRadioTrack(track)) {
                int pipeIndex = title.lastIndexOf(" | ");
                if (pipeIndex > 0) {
                    title = title.substring(0, pipeIndex).trim();
                }
            }
            
            if (handler.isGensokyoRadioTrack(track)) {
                try {
                    // Force an update to get the very latest info
                    dev.cosgy.agent.GensokyoInfoAgent.forceUpdate();
                    dev.cosgy.agent.objects.ResultSet grInfo = dev.cosgy.agent.GensokyoInfoAgent.getInfo();
                    
                    if (grInfo != null && grInfo.getSonginfo() != null) {
                        // Always use the info directly from the agent for Gensokyo Radio tracks
                        title = grInfo.getSonginfo().getTitle();
                        artist = grInfo.getSonginfo().getArtist();
                        
                        // Check for empty values and provide defaults
                        if (title == null || title.isEmpty()) {
                            title = "Unknown Title";
                            System.out.println("Warning: Empty title from Gensokyo Radio API");
                        }
                        
                        if (artist == null || artist.isEmpty()) {
                            artist = "Unknown Artist";
                            System.out.println("Warning: Empty artist from Gensokyo Radio API");
                        }
                        
                    } else {
                        System.out.println("Warning: No track info available from Gensokyo Radio API");
                    }
                } catch (Exception e) {
                    // Ignore error and use track data
                    System.out.println("Error getting Gensokyo Radio info: " + e.getMessage());
                }
            }
            
            PlayRecord record = new PlayRecord(
                title,  // Use our cleaned detected title
                artist, // Use our detected artist
                track.getDuration(),
                info.uri,
                System.currentTimeMillis(),
                requester != null ? requester.getId() : "unknown",
                requester != null ? requester.getName() : "Unknown User",
                guildName,
                guildId
            );
            
            // Check for YtDlp metadata
            YtDlpMetadata ytMeta = PlayerManager.getYtDlpMetadata(track);
            FallbackPlatform ytPlatform = PlayerManager.getYtDlpPlatform(track);

            if (ytPlatform != null && ytPlatform != FallbackPlatform.NONE) {
                String sourceType = "Unknown";
                String thumbnailUrl = "";

                switch (ytPlatform) {
                    case INSTAGRAM -> sourceType = "Instagram";
                    case TIKTOK -> sourceType = "TikTok";
                    case TWITTER -> sourceType = "Twitter";
                    case BILIBILI -> sourceType = "Bilibili";
                    case VIMEO -> sourceType = "Vimeo";
                    case TWITCH -> sourceType = "Twitch";
                    case SOUNDCLOUD -> sourceType = "SoundCloud";
                    case YOUTUBE -> sourceType = "YouTube";
                    default -> { }
                }

                if (ytMeta != null && ytMeta.thumbnailUrl() != null && !ytMeta.thumbnailUrl().isEmpty()) {
                    thumbnailUrl = ytMeta.thumbnailUrl();
                }
                
                if (!"Unknown".equals(sourceType)) {
                    record.setYtDlpData(sourceType, thumbnailUrl);
                }
            }

            // Add metadata based on track type
            AudioHandler.TrackType type = handler.getTrackType(track);
            
            if (type == AudioHandler.TrackType.SPOTIFY) {
                SpotifyCmd.SpotifyTrackInfo spotifyInfo = handler.getSpotifyTrackInfo();
                if (spotifyInfo != null) {
                    record.setSpotifyData(
                        spotifyInfo.trackId,
                        spotifyInfo.albumName,
                        spotifyInfo.albumImageUrl,
                        spotifyInfo.artistName,
                        spotifyInfo.releaseYear
                    );
                }
            } else if (type == AudioHandler.TrackType.RADIO) {
                RadioCmd.TrackInfo radioInfo = handler.getRadioTrackInfo(track);
                String songImageUrl = (radioInfo != null) ? radioInfo.imageUrl : null;
                
                // For radio tracks, store the actual station name but use only the formatted title for display
                record.setRadioData(
                    handler.getRadioStationName(track),  // We still store the real station name
                    songImageUrl,
                    handler.getRadioLogoUrl(track)
                );
            } else if (type == AudioHandler.TrackType.YOUTUBE) {
                String videoId = info.uri;
                if (videoId.contains("v=")) {
                    videoId = videoId.substring(videoId.indexOf("v=") + 2);
                    if (videoId.contains("&")) {
                        videoId = videoId.substring(0, videoId.indexOf("&"));
                    }
                    record.setYoutubeData(videoId);
                }
            } else if (type == AudioHandler.TrackType.SOUNDCLOUD) {
                // Add SoundCloud artwork URL to the record
                if (track.getInfo().artworkUrl != null && !track.getInfo().artworkUrl.isEmpty()) {
                    record.setSoundCloudData(track.getInfo().artworkUrl);
                }
            } else if (type == AudioHandler.TrackType.LOCAL) {
                String trackIdentifier = track.getInfo().identifier;
                LocalAudioMetadata.LocalTrackInfo cachedInfo = LocalAudioMetadata.getCachedTrackInfo(trackIdentifier);
                
                if (cachedInfo != null) {
                    record.setLocalData(
                        cachedInfo.getAlbum(),
                        cachedInfo.getGenre(),
                        cachedInfo.getYear(),
                        cachedInfo.getArtworkPath() // artworkPath is the filename of the cover art (hash.ext)
                    );
                } else if (rm != null && rm.hasLocalFileData()) { // Fallback, should happen less often now
                    record.setLocalData(
                        rm.getLocalFileAlbum(),
                        rm.getLocalFileGenre(),
                        rm.getLocalFileYear(),
                        rm.getLocalFileArtworkHash()
                    );
                } else {
                    // Maybe set default values or do nothing if no local info is found
                    record.setLocalData("Unknown Album", "Unknown Genre", "", ""); // Default values
                }
            }
            
            // Check for Gensokyo Radio tracks
            if (handler.isGensokyoRadioTrack(track)) {
                try {
                    dev.cosgy.agent.objects.ResultSet grInfo = dev.cosgy.agent.GensokyoInfoAgent.getInfo();
                    if (grInfo != null && grInfo.getSonginfo() != null) {
                        // Set Gensokyo Radio data
                        record.setGensokyoData(
                            grInfo.getSonginfo().getTitle(),
                            grInfo.getSonginfo().getArtist(),
                            grInfo.getSonginfo().getAlbum(),
                            grInfo.getSonginfo().getCircle(),
                            grInfo.getSonginfo().getYear(),
                            grInfo.getMisc() != null ? grInfo.getMisc().getFullAlbumArtUrl() : ""
                        );
                    }
                } catch (Exception e) {
                    // Ignore error and continue with regular stream data
                }
            }
            // Handle regular streams if not Gensokyo Radio
            else if (info.isStream && !record.hasYtDlpData()) {
                // Check for ICY metadata
                IcyMetadataHandler.StreamMetadata icyMetadata = 
                    bot.getIcyMetadataHandler().getMetadata(String.valueOf(handler.getGuildId()));
                
                if (icyMetadata != null) {
                    // Use ICY metadata for stream information
                    String stationName = icyMetadata.getStationName();
                    String currentTrack = icyMetadata.getCurrentTrack();
                    String stationGenre = icyMetadata.getStationGenre();
                    String stationLogo = icyMetadata.getStationLogo();
                    
                    // Set stream data
                    record.setStreamData(
                        stationName != null ? stationName : "Stream", 
                        stationGenre != null ? stationGenre : "",
                        stationLogo != null ? stationLogo : "",
                        true
                    );
                    
                    // If we have current track info, update record title
                    if (currentTrack != null && !currentTrack.isEmpty()) {
                        // For streams, the record title might be just the station name
                        // Manually override with the current track if available
                        try {
                            java.lang.reflect.Field titleField = record.getClass().getDeclaredField("title");
                            titleField.setAccessible(true);
                            titleField.set(record, currentTrack);
                        } catch (Exception e) {
                            // Ignore reflection errors, keep original title
                        }
                    }
                } else {
                    // Simple stream with no ICY metadata
                    record.setStreamData("Stream", "", "", true);
                }
            }
            
            // Add to the beginning of the history list
            history.add(0, record);
            
            // Save the updated history
            saveHistory();
        } catch (Exception e) {
            System.err.println("Error adding track to history: " + e.getMessage());
        }
    }

    /**
     * Gets the playback history
     * @return The list of play records
     */
    public List<PlayRecord> getHistory() {
        return history;
    }

    /**
     * Gets a subset of the history
     * @param limit Maximum number of entries to return
     * @return Limited history list
     */
    public List<PlayRecord> getHistory(int limit) {
        int size = Math.min(limit, history.size());
        return history.subList(0, size);
    }

    /**
     * Loads the history from the JSON file
     */
    private void loadHistory() {
        try {
            if (Files.exists(historyFile)) {
                var jsonNode = objectMapper.readTree(historyFile.toFile());
                
                // Check if the JSON node is a valid ObjectNode
                if (jsonNode == null || jsonNode.isMissingNode() || !jsonNode.isObject()) {
                    System.out.println("Invalid or empty history file, creating new one");
                    writeEmptyHistoryFile();
                    return;
                }
                
                ObjectNode root = (ObjectNode) jsonNode;
                ArrayNode records = (ArrayNode) root.get("history");
                
                if (records != null) {
                    history.clear();
                    for (int i = 0; i < records.size(); i++) {
                        ObjectNode recordNode = (ObjectNode) records.get(i);
                        
                        // Get the guildId if available, otherwise use "unknown"
                        String guildId = recordNode.has("guildId") 
                            ? recordNode.get("guildId").asText() 
                            : "unknown";
                        
                        PlayRecord record = new PlayRecord(
                            recordNode.get("title").asText(),
                            recordNode.get("artist").asText(),
                            recordNode.get("duration").asLong(),
                            recordNode.get("url").asText(),
                            recordNode.get("playedAt").asLong(),
                            recordNode.get("requesterId").asText(),
                            recordNode.get("requesterName").asText(),
                            recordNode.get("guildName").asText(),
                            guildId
                        );
                        
                        // Load metadata based on type
                        if (recordNode.has("spotifyData")) {
                            ObjectNode spotifyData = (ObjectNode) recordNode.get("spotifyData");
                            record.setSpotifyData(
                                spotifyData.path("trackId").asText(null),
                                spotifyData.path("albumName").asText(null),
                                spotifyData.path("albumImageUrl").asText(null),
                                spotifyData.path("artistName").asText(null),
                                spotifyData.path("releaseYear").asText(null)
                            );
                        } else if (recordNode.has("radioData")) {
                            ObjectNode radioData = (ObjectNode) recordNode.get("radioData");
                            record.setRadioData(
                                radioData.path("stationName").asText(null),
                                radioData.path("songImageUrl").asText(null),
                                radioData.path("logoUrl").asText(null)
                            );
                        } else if (recordNode.has("soundcloudData")) {
                            ObjectNode soundcloudData = (ObjectNode) recordNode.get("soundcloudData");
                            record.setSoundCloudData(
                                soundcloudData.path("artworkUrl").asText(null)
                            );
                        } else if (recordNode.has("ytDlpData")) {
                            ObjectNode ytDlpData = (ObjectNode) recordNode.get("ytDlpData");
                            record.setYtDlpData(
                                ytDlpData.path("sourceType").asText(null),
                                ytDlpData.path("thumbnailUrl").asText(null)
                            );
                        } else if (recordNode.has("youtubeData")) {
                            ObjectNode ytData = (ObjectNode) recordNode.get("youtubeData");
                            record.setYoutubeData(ytData.path("videoId").asText(null));
                        } else if (recordNode.has("localData")) {
                            ObjectNode localData = (ObjectNode) recordNode.get("localData");
                            record.setLocalData(
                                localData.path("album").asText(""),
                                localData.path("genre").asText(""),
                                localData.path("year").asText(""),
                                localData.path("artworkHash").asText("")
                            );
                        } else if (recordNode.has("gensokyoData")) {
                            ObjectNode gensokyoData = (ObjectNode) recordNode.get("gensokyoData");
                            record.setGensokyoData(
                                gensokyoData.path("title").asText(null),
                                gensokyoData.path("artist").asText(null),
                                gensokyoData.path("album").asText(null),
                                gensokyoData.path("circle").asText(null),
                                gensokyoData.path("year").asText(null),
                                gensokyoData.path("albumArtUrl").asText(null)
                            );
                        } else if (recordNode.has("streamData")) {
                            ObjectNode streamData = (ObjectNode) recordNode.get("streamData");
                            record.setStreamData(
                                streamData.path("streamName").asText(null),
                                streamData.path("streamGenre").asText(null),
                                streamData.path("streamLogo").asText(null),
                                streamData.path("isLive").asBoolean(false)
                            );
                        }
                        
                        history.add(record);
                    }
                }
            }
        } catch (IOException e) {
            System.err.println("Error loading history: " + e.getMessage());
        }
    }

    /**
     * Saves the history to the JSON file
     */
    private void saveHistory() {
        try {
            ObjectNode root = objectMapper.createObjectNode();
            ArrayNode records = root.putArray("history");
            
            for (PlayRecord record : history) {
                ObjectNode recordNode = records.addObject();
                recordNode.put("title", record.getTitle());
                recordNode.put("artist", record.getArtist());
                recordNode.put("duration", record.getDuration());
                recordNode.put("url", record.getUrl());
                recordNode.put("playedAt", record.getPlayedAt());
                recordNode.put("requesterId", record.getRequesterId());
                recordNode.put("requesterName", record.getRequesterName());
                recordNode.put("guildName", record.getGuildName());
                recordNode.put("guildId", record.getGuildId());
                
                // Save metadata based on type
                if (record.hasSpotifyData()) {
                    ObjectNode spotifyData = recordNode.putObject("spotifyData");
                    spotifyData.put("trackId", record.getSpotifyTrackId());
                    spotifyData.put("albumName", record.getSpotifyAlbumName());
                    spotifyData.put("albumImageUrl", record.getSpotifyAlbumImageUrl());
                    spotifyData.put("artistName", record.getSpotifyArtistName());
                    spotifyData.put("releaseYear", record.getSpotifyReleaseYear());
                } else if (record.hasRadioData()) {
                    ObjectNode radioData = recordNode.putObject("radioData");
                    radioData.put("stationName", record.getRadioStationName());
                    radioData.put("songImageUrl", record.getRadioSongImageUrl());
                    radioData.put("logoUrl", record.getRadioLogoUrl());
                } else if (record.hasSoundCloudData()) {
                    ObjectNode soundcloudData = recordNode.putObject("soundcloudData");
                    soundcloudData.put("artworkUrl", record.getSoundCloudArtworkUrl());
                } else if (record.hasYtDlpData()) {
                    ObjectNode ytDlpData = recordNode.putObject("ytDlpData");
                    ytDlpData.put("sourceType", record.getYtDlpSourceType());
                    ytDlpData.put("thumbnailUrl", record.getYtDlpThumbnailUrl());
                } else if (record.hasYoutubeData()) {
                    ObjectNode ytData = recordNode.putObject("youtubeData");
                    ytData.put("videoId", record.getYoutubeVideoId());
                } else if (record.hasLocalData()) {
                    ObjectNode localData = recordNode.putObject("localData");
                    localData.put("album", record.getLocalAlbum());
                    localData.put("genre", record.getLocalGenre());
                    localData.put("year", record.getLocalYear());
                    localData.put("artworkHash", record.getLocalArtworkHash());
                } else if (record.hasGensokyoData()) {
                    ObjectNode gensokyoData = recordNode.putObject("gensokyoData");
                    gensokyoData.put("title", record.getGensokyoTitle());
                    gensokyoData.put("artist", record.getGensokyoArtist());
                    gensokyoData.put("album", record.getGensokyoAlbum());
                    gensokyoData.put("circle", record.getGensokyoCircle());
                    gensokyoData.put("year", record.getGensokyoYear());
                    gensokyoData.put("albumArtUrl", record.getGensokyoAlbumArtUrl());
                } else if (record.hasStreamData()) {
                    ObjectNode streamData = recordNode.putObject("streamData");
                    streamData.put("streamName", record.getStreamName());
                    streamData.put("streamGenre", record.getStreamGenre());
                    streamData.put("streamLogo", record.getStreamLogo());
                    streamData.put("isLive", record.isLiveStream());
                }
            }
            
            objectMapper.writeValue(historyFile.toFile(), root);
        } catch (IOException e) {
            System.err.println("Error saving history: " + e.getMessage());
        }
    }

    /**
     * Creates an empty history file
     */
    private void writeEmptyHistoryFile() throws IOException {
        ObjectNode root = objectMapper.createObjectNode();
        root.putArray("history");
        objectMapper.writeValue(historyFile.toFile(), root);
    }

    /**
     * Sets whether history tracking is enabled
     * @param enabled True to enable, false to disable
     */
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    /**
     * Checks if history tracking is enabled
     * @return True if enabled, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Clear the history
     */
    public void clearHistory() {
        history.clear();
        try {
            writeEmptyHistoryFile();
        } catch (IOException e) {
            System.err.println("Error clearing history: " + e.getMessage());
        }
    }

    /**
     * Class representing a record in the play history
     */
    public static class PlayRecord {
        private final String title;
        private final String artist;
        private final long duration;
        private final String url;
        private final long playedAt;
        private final String requesterId;
        private final String requesterName;
        private final String guildName;
        private final String guildId;
        
        // Metadata for different sources
        private String spotifyTrackId;
        private String spotifyAlbumName;
        private String spotifyAlbumImageUrl;
        private String spotifyArtistName;
        private String spotifyReleaseYear;
        
        private String radioStationName;
        private String radioLogoUrl;
        private String radioSongImageUrl;
        
        private String youtubeVideoId;
        
        private String localAlbum;
        private String localGenre;
        private String localYear;
        private String localArtworkHash; // Added for local file artwork hash (filename with extension)
        
        // Gensokyo Radio metadata
        private String gensokyoTitle;
        private String gensokyoArtist;
        private String gensokyoAlbum;
        private String gensokyoCircle;
        private String gensokyoYear;
        private String gensokyoAlbumArtUrl;
        
        // Stream metadata
        private String streamName;
        private String streamGenre;
        private String streamLogo;
        private boolean isLiveStream;
        
        // SoundCloud metadata
        private String soundCloudArtworkUrl;

        // YtDlp metadata
        private String ytDlpSourceType;
        private String ytDlpThumbnailUrl;

        /**
         * Creates a new play record
         * @param title The track title
         * @param artist The track artist
         * @param duration The track duration
         * @param url The track URL
         * @param playedAt The timestamp when the track was played
         * @param requesterId The ID of the user who requested the track
         * @param requesterName The name of the user who requested the track
         * @param guildName The name of the guild where the track was played
         * @param guildId The ID of the guild where the track was played
         */
        public PlayRecord(String title, String artist, long duration, String url, 
                         long playedAt, String requesterId, String requesterName, String guildName, String guildId) {
            this.title = title;
            this.artist = artist;
            this.duration = duration;
            this.url = url;
            this.playedAt = playedAt;
            this.requesterId = requesterId;
            this.requesterName = requesterName;
            this.guildName = guildName;
            this.guildId = guildId;
        }
        
        // Getters
        public String getTitle() { return title; }
        public String getArtist() { return artist; }
        public long getDuration() { return duration; }
        public String getUrl() { return url; }
        public long getPlayedAt() { return playedAt; }
        public String getRequesterId() { return requesterId; }
        public String getRequesterName() { return requesterName; }
        public String getGuildName() { return guildName; }
        public String getGuildId() { return guildId; }
        
        // Format the played at time as a string
        public String getFormattedPlayedAt() {
            return DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                    .withZone(ZoneId.systemDefault())
                    .format(Instant.ofEpochMilli(playedAt));
        }
        
        // Format the duration as a string (mm:ss)
        public String getFormattedDuration() {
            long seconds = duration / 1000;
            return String.format("%d:%02d", seconds / 60, seconds % 60);
        }
        
        // Spotify metadata
        public void setSpotifyData(String trackId, String albumName, String albumImageUrl, String artistName, String releaseYear) {
            this.spotifyTrackId = trackId;
            this.spotifyAlbumName = albumName;
            this.spotifyAlbumImageUrl = albumImageUrl;
            this.spotifyArtistName = artistName;
            this.spotifyReleaseYear = releaseYear;
        }
        
        public boolean hasSpotifyData() { return spotifyTrackId != null; }
        public String getSpotifyTrackId() { return spotifyTrackId; }
        public String getSpotifyAlbumName() { return spotifyAlbumName; }
        public String getSpotifyAlbumImageUrl() { return spotifyAlbumImageUrl; }
        public String getSpotifyArtistName() { return spotifyArtistName; }
        public String getSpotifyReleaseYear() { return spotifyReleaseYear; }
        
        // Radio metadata
        public void setRadioData(String stationName, String songImageUrl, String stationLogoUrl) {
            this.radioStationName = stationName; // Store the actual station name
            this.radioSongImageUrl = songImageUrl;
            this.radioLogoUrl = stationLogoUrl;
        }
        
        public boolean hasRadioData() { return radioStationName != null; }
        public String getRadioStationName() { return radioStationName; }
        public String getRadioLogoUrl() { return radioLogoUrl; }
        public String getRadioSongImageUrl() { return radioSongImageUrl; }
        
        // YouTube metadata
        public void setYoutubeData(String videoId) {
            this.youtubeVideoId = videoId;
        }
        
        public boolean hasYoutubeData() { return youtubeVideoId != null; }
        public String getYoutubeVideoId() { return youtubeVideoId; }
        
        // Local file metadata
        public void setLocalData(String album, String genre, String year, String artworkHash) {
            this.localAlbum = album;
            this.localGenre = genre;
            this.localYear = year;
            this.localArtworkHash = artworkHash; // Set artwork hash
        }
        
        public boolean hasLocalData() { 
            return localAlbum != null || localGenre != null || localYear != null || localArtworkHash != null; 
        }
        public String getLocalAlbum() { return localAlbum; }
        public String getLocalGenre() { return localGenre; }
        public String getLocalYear() { return localYear; }
        public String getLocalArtworkHash() { return localArtworkHash; } // Getter for artwork hash
        
        // Gensokyo Radio metadata
        public void setGensokyoData(String title, String artist, String album, String circle, String year, String albumArtUrl) {
            this.gensokyoTitle = title;
            this.gensokyoArtist = artist;
            this.gensokyoAlbum = album;
            this.gensokyoCircle = circle;
            this.gensokyoYear = year;
            this.gensokyoAlbumArtUrl = albumArtUrl;
        }
        
        public boolean hasGensokyoData() { return gensokyoTitle != null; }
        public String getGensokyoTitle() { return gensokyoTitle; }
        public String getGensokyoArtist() { return gensokyoArtist; }
        public String getGensokyoAlbum() { return gensokyoAlbum; }
        public String getGensokyoCircle() { return gensokyoCircle; }
        public String getGensokyoYear() { return gensokyoYear; }
        public String getGensokyoAlbumArtUrl() { return gensokyoAlbumArtUrl; }
        
        // Stream metadata
        public void setStreamData(String streamName, String streamGenre, String streamLogo, boolean isLiveStream) {
            this.streamName = streamName;
            this.streamGenre = streamGenre;
            this.streamLogo = streamLogo;
            this.isLiveStream = isLiveStream;
        }
        
        public boolean hasStreamData() { return streamName != null; }
        public String getStreamName() { return streamName; }
        public String getStreamGenre() { return streamGenre; }
        public String getStreamLogo() { return streamLogo; }
        public boolean isLiveStream() { return isLiveStream; }
        
        // SoundCloud metadata
        public void setSoundCloudData(String artworkUrl) {
            this.soundCloudArtworkUrl = artworkUrl;
        }
        
        public boolean hasSoundCloudData() { return soundCloudArtworkUrl != null; }
        public String getSoundCloudArtworkUrl() { return soundCloudArtworkUrl; }

        // YtDlp metadata
        public void setYtDlpData(String sourceType, String thumbnailUrl) {
            this.ytDlpSourceType = sourceType;
            this.ytDlpThumbnailUrl = thumbnailUrl;
        }

        public boolean hasYtDlpData() { return ytDlpSourceType != null; }
        public String getYtDlpSourceType() { return ytDlpSourceType; }
        public String getYtDlpThumbnailUrl() { return ytDlpThumbnailUrl; }
    }
} 