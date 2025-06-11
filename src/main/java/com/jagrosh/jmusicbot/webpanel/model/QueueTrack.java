/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.model;

import java.util.Map;

/**
 * Data class for a track in the queue
 */
public class QueueTrack {
    private final int position;
    private final String title;
    private final String author;
    private final String uri;
    private final String thumbnail;
    private final long duration;
    private final String source;
    private final String sourceType;
    private final String requester;
    private final String requesterAvatar;
    private final Map<String, Object> spotifyInfo;
    private final String radioStationUrl;
    private final String radioCountry;
    private final String radioAlias;

    public QueueTrack(
            int position,
            String title,
            String author,
            String uri,
            String thumbnail,
            long duration,
            String source,
            String sourceType,
            String requester,
            String requesterAvatar,
            Map<String, Object> spotifyInfo,
            String radioStationUrl
    ) {
        this(position, title, author, uri, thumbnail, duration, source, sourceType, 
            requester, requesterAvatar, spotifyInfo, radioStationUrl, null, null);
    }
    
    public QueueTrack(
            int position,
            String title,
            String author,
            String uri,
            String thumbnail,
            long duration,
            String source,
            String sourceType,
            String requester,
            String requesterAvatar,
            Map<String, Object> spotifyInfo,
            String radioStationUrl,
            String radioCountry,
            String radioAlias
    ) {
        this.position = position;
        this.title = title;
        this.author = author;
        this.uri = uri;
        this.thumbnail = thumbnail;
        this.duration = duration;
        this.source = source;
        this.sourceType = sourceType;
        this.requester = requester;
        this.requesterAvatar = requesterAvatar;
        this.spotifyInfo = spotifyInfo;
        this.radioStationUrl = radioStationUrl;
        this.radioCountry = radioCountry;
        this.radioAlias = radioAlias;
    }

    public int getPosition() {
        return position;
    }

    public String getTitle() {
        return title;
    }

    public String getAuthor() {
        return author;
    }

    public String getUri() {
        return uri;
    }

    public String getThumbnail() {
        return thumbnail;
    }

    public long getDuration() {
        return duration;
    }

    public String getSource() {
        return source;
    }

    public String getSourceType() {
        return sourceType;
    }

    public String getRequester() {
        return requester;
    }

    public String getRequesterAvatar() {
        return requesterAvatar;
    }

    public Map<String, Object> getSpotifyInfo() {
        return spotifyInfo;
    }

    public String getRadioStationUrl() {
        return radioStationUrl;
    }
    
    public String getRadioCountry() {
        return radioCountry;
    }
    
    public String getRadioAlias() {
        return radioAlias;
    }
} 