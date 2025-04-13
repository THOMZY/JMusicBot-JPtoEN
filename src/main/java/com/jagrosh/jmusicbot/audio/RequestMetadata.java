/*
 *  Copyright 2021 Cosgy Dev (info@cosgy.dev).
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

    /**
     * Creates a new RequestMetadata instance
     * @param user The user who requested the track
     */
    public RequestMetadata(User user) {
        this.user = user == null ? null : new UserInfo(user.getIdLong(), user.getName(), user.getDiscriminator(), user.getEffectiveAvatarUrl());
        this.spotifyTrackId = null;
        this.radioInfo = null;
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
        
        private RadioInfo(String stationPath, String stationName, String logoUrl) {
            this.stationPath = stationPath;
            this.stationName = stationName;
            this.logoUrl = logoUrl;
        }
    }
}
