/*
 *  Copyright 2021 Cosgy Dev (info@cosgy.dev).
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

package com.jagrosh.jmusicbot.audio;

import net.dv8tion.jda.api.entities.User;

/**
 * Class to store metadata about track requests
 */
public class RequestMetadata {
    public static final RequestMetadata EMPTY = new RequestMetadata(null);

    public final UserInfo user;
    private String spotifyTrackId;
    private RadioInfo radioInfo;
    private LocalFileInfo localFileInfo;

    /**
     * Creates a new RequestMetadata instance
     * @param user The user who requested the track
     */
    public RequestMetadata(User user) {
        this.user = user == null ? null : new UserInfo(user.getIdLong(), user.getName(), user.getDiscriminator(), user.getEffectiveAvatarUrl());
        this.spotifyTrackId = null;
        this.radioInfo = null;
        this.localFileInfo = null;
    }

    /**
     * Gets the ID of the user who requested the track
     * @return The user ID or 0 if not set
     */
    public long getOwner() {
        return user == null ? 0L : user.id;
    }

    // ===== Spotify Methods =====

    /**
     * Checks if this track has Spotify data attached
     * @return true if spotifyTrackId is set
     */
    public boolean hasSpotifyData() {
        return spotifyTrackId != null && !spotifyTrackId.isEmpty();
    }

    /**
     * Gets the Spotify track ID associated with this track
     * @return the Spotify track ID or null if not set
     */
    public String getSpotifyTrackId() {
        return spotifyTrackId;
    }

    /**
     * Sets the Spotify track ID for this track
     * @param trackId the Spotify track ID
     */
    public void setSpotifyTrackId(String trackId) {
        this.spotifyTrackId = trackId;
    }

    // ===== Radio Methods =====
    
    /**
     * Checks if this track has radio data attached
     * @return true if radioInfo is set
     */
    public boolean hasRadioData() {
        return radioInfo != null;
    }
    
    /**
     * Gets the radio station path (identifier)
     * @return the station path or null if not set
     */
    public String getRadioStationPath() {
        return radioInfo != null ? radioInfo.stationPath : null;
    }
    
    /**
     * Gets the radio station logo URL
     * @return the logo URL or null if not set
     */
    public String getRadioLogoUrl() {
        return radioInfo != null ? radioInfo.logoUrl : null;
    }
    
    /**
     * Gets the radio station name
     * @return the station name or null if not set
     */
    public String getRadioStationName() {
        return radioInfo != null ? radioInfo.stationName : null;
    }
    
    /**
     * Gets the radio station UUID
     * @return the station UUID or null if not set
     */
    public String getRadioStationUuid() {
        return radioInfo != null ? radioInfo.stationUuid : null;
    }
    
    /**
     * Sets radio information for this track including the station UUID
     * @param stationPath the station path/identifier
     * @param stationName the station name
     * @param logoUrl the station logo URL
     * @param stationUuid the station UUID
     */
    public void setRadioInfo(String stationPath, String stationName, String logoUrl, String stationUuid) {
        this.radioInfo = new RadioInfo(stationPath, stationName, logoUrl, stationUuid);
    }
    
    /**
     * Sets radio information for this track
     * @param stationPath the station path/identifier
     * @param stationName the station name
     * @param logoUrl the station logo URL
     */
    public void setRadioInfo(String stationPath, String stationName, String logoUrl) {
        this.radioInfo = new RadioInfo(stationPath, stationName, logoUrl);
    }
    
    /**
     * Gets all radio information as a RadioInfo object
     * @return the RadioInfo object or null if not set
     */
    public RadioInfo getRadioInfo() {
        return radioInfo;
    }

    // ===== Local File Methods =====
    
    /**
     * Checks if this track has local file metadata attached
     * @return true if localFileInfo is set
     */
    public boolean hasLocalFileData() {
        return localFileInfo != null;
    }
    
    /**
     * Gets the title of the local file
     * @return the title or null if not set
     */
    public String getLocalFileTitle() {
        return localFileInfo != null ? localFileInfo.title : null;
    }
    
    /**
     * Gets the artist of the local file
     * @return the artist or null if not set
     */
    public String getLocalFileArtist() {
        return localFileInfo != null ? localFileInfo.artist : null;
    }
    
    /**
     * Gets the album of the local file
     * @return the album or null if not set
     */
    public String getLocalFileAlbum() {
        return localFileInfo != null ? localFileInfo.album : null;
    }
    
    /**
     * Gets the year of the local file
     * @return the year or null if not set
     */
    public String getLocalFileYear() {
        return localFileInfo != null ? localFileInfo.year : null;
    }
    
    /**
     * Gets the genre of the local file
     * @return the genre or null if not set
     */
    public String getLocalFileGenre() {
        return localFileInfo != null ? localFileInfo.genre : null;
    }
    
    /**
     * Sets metadata for a local audio file
     * @param title the track title
     * @param artist the track artist
     * @param album the track album
     * @param year the track year
     * @param genre the track genre
     */
    public void setLocalFileMetadata(String title, String artist, String album, String year, String genre) {
        this.localFileInfo = new LocalFileInfo(title, artist, album, year, genre);
    }
    
    /**
     * Gets all local file information as a LocalFileInfo object
     * @return the LocalFileInfo object or null if not set
     */
    public LocalFileInfo getLocalFileInfo() {
        return localFileInfo;
    }

    /**
     * Class to store information about a request
     */
    public class RequestInfo {
        public final String query, url;

        private RequestInfo(String query, String url) {
            this.query = query;
            this.url = url;
        }
    }

    /**
     * Class to store information about a user
     */
    public class UserInfo {
        public final long id;
        public final String username, discrim, avatar;

        private UserInfo(long id, String username, String discrim, String avatar) {
            this.id = id;
            this.username = username;
            this.discrim = discrim;
            this.avatar = avatar;
        }
    }
    
    /**
     * Class to store information about a radio station
     */
    public class RadioInfo {
        public final String stationPath;
        public final String stationName;
        public final String logoUrl;
        public final String stationUuid;
        
        private RadioInfo(String stationPath, String stationName, String logoUrl) {
            this.stationPath = stationPath;
            this.stationName = stationName;
            this.logoUrl = logoUrl;
            this.stationUuid = null;
        }
        
        private RadioInfo(String stationPath, String stationName, String logoUrl, String stationUuid) {
            this.stationPath = stationPath;
            this.stationName = stationName;
            this.logoUrl = logoUrl;
            this.stationUuid = stationUuid;
        }
    }

    /**
     * Class to store information about a local audio file
     */
    public class LocalFileInfo {
        public final String title;
        public final String artist;
        public final String album;
        public final String year;
        public final String genre;
        
        private LocalFileInfo(String title, String artist, String album, String year, String genre) {
            this.title = title;
            this.artist = artist;
            this.album = album;
            this.year = year;
            this.genre = genre;
        }
    }
}
