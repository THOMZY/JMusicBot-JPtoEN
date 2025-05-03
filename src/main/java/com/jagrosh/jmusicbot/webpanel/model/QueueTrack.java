/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.model;

/**
 * Represents a track in the queue for the web panel
 */
public class QueueTrack {
    private String title;
    private String author;
    private String uri;
    private long duration;
    private String thumbnailUrl;
    private String requester;
    private String requesterAvatar;
    private String source;
    private String sourceType;

    public QueueTrack() {
    }

    public QueueTrack(String title, String author, String uri, long duration) {
        this.title = title;
        this.author = author;
        this.uri = uri;
        this.duration = duration;
        this.thumbnailUrl = "";
        this.requester = "";
        this.requesterAvatar = "";
        this.source = "";
        this.sourceType = "";
    }

    public QueueTrack(String title, String author, String uri, long duration, 
                    String thumbnailUrl, String requester, String requesterAvatar,
                    String source, String sourceType) {
        this.title = title;
        this.author = author;
        this.uri = uri;
        this.duration = duration;
        this.thumbnailUrl = thumbnailUrl;
        this.requester = requester;
        this.requesterAvatar = requesterAvatar;
        this.source = source;
        this.sourceType = sourceType;
    }

    // Getters and setters
    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public long getDuration() {
        return duration;
    }

    public void setDuration(long duration) {
        this.duration = duration;
    }
    
    public String getThumbnailUrl() {
        return thumbnailUrl;
    }
    
    public void setThumbnailUrl(String thumbnailUrl) {
        this.thumbnailUrl = thumbnailUrl;
    }
    
    public String getRequester() {
        return requester;
    }
    
    public void setRequester(String requester) {
        this.requester = requester;
    }
    
    public String getRequesterAvatar() {
        return requesterAvatar;
    }
    
    public void setRequesterAvatar(String requesterAvatar) {
        this.requesterAvatar = requesterAvatar;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
    }
    
    public String getSourceType() {
        return sourceType;
    }
    
    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }
} 