/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.model;

public class MusicStatus {
    private String currentTrackTitle;
    private String currentTrackAuthor;
    private String currentTrackUrl;
    private String currentTrackThumbnail;
    private long currentTrackPosition;
    private long currentTrackDuration;
    private boolean playing;
    private boolean paused;
    private boolean hasNext;
    private int queueSize;
    private String source;
    private String requester;
    private String requesterAvatar;
    private int volume;
    private String sourceType;

    public MusicStatus() {
    }

    public MusicStatus(String currentTrackTitle, String currentTrackAuthor, String currentTrackUrl, 
                      String currentTrackThumbnail, long currentTrackPosition, long currentTrackDuration, 
                      boolean playing, boolean paused, boolean hasNext, int queueSize) {
        this.currentTrackTitle = currentTrackTitle;
        this.currentTrackAuthor = currentTrackAuthor;
        this.currentTrackUrl = currentTrackUrl;
        this.currentTrackThumbnail = currentTrackThumbnail;
        this.currentTrackPosition = currentTrackPosition;
        this.currentTrackDuration = currentTrackDuration;
        this.playing = playing;
        this.paused = paused;
        this.hasNext = hasNext;
        this.queueSize = queueSize;
        this.source = "";
        this.requester = "";
        this.requesterAvatar = "";
        this.volume = 100;
        this.sourceType = "";
    }

    public MusicStatus(String currentTrackTitle, String currentTrackAuthor, String currentTrackUrl, 
                      String currentTrackThumbnail, long currentTrackPosition, long currentTrackDuration, 
                      boolean playing, boolean paused, boolean hasNext, int queueSize, 
                      String source, String requester, String requesterAvatar, int volume, String sourceType) {
        this.currentTrackTitle = currentTrackTitle;
        this.currentTrackAuthor = currentTrackAuthor;
        this.currentTrackUrl = currentTrackUrl;
        this.currentTrackThumbnail = currentTrackThumbnail;
        this.currentTrackPosition = currentTrackPosition;
        this.currentTrackDuration = currentTrackDuration;
        this.playing = playing;
        this.paused = paused;
        this.hasNext = hasNext;
        this.queueSize = queueSize;
        this.source = source;
        this.requester = requester;
        this.requesterAvatar = requesterAvatar;
        this.volume = volume;
        this.sourceType = sourceType;
    }

    // Getters and setters
    public String getCurrentTrackTitle() {
        return currentTrackTitle;
    }

    public void setCurrentTrackTitle(String currentTrackTitle) {
        this.currentTrackTitle = currentTrackTitle;
    }

    public String getCurrentTrackAuthor() {
        return currentTrackAuthor;
    }

    public void setCurrentTrackAuthor(String currentTrackAuthor) {
        this.currentTrackAuthor = currentTrackAuthor;
    }

    public String getCurrentTrackUrl() {
        return currentTrackUrl;
    }

    public void setCurrentTrackUrl(String currentTrackUrl) {
        this.currentTrackUrl = currentTrackUrl;
    }

    public String getCurrentTrackThumbnail() {
        return currentTrackThumbnail;
    }

    public void setCurrentTrackThumbnail(String currentTrackThumbnail) {
        this.currentTrackThumbnail = currentTrackThumbnail;
    }

    public long getCurrentTrackPosition() {
        return currentTrackPosition;
    }

    public void setCurrentTrackPosition(long currentTrackPosition) {
        this.currentTrackPosition = currentTrackPosition;
    }

    public long getCurrentTrackDuration() {
        return currentTrackDuration;
    }

    public void setCurrentTrackDuration(long currentTrackDuration) {
        this.currentTrackDuration = currentTrackDuration;
    }

    public boolean isPlaying() {
        return playing;
    }

    public void setPlaying(boolean playing) {
        this.playing = playing;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public void setHasNext(boolean hasNext) {
        this.hasNext = hasNext;
    }

    public int getQueueSize() {
        return queueSize;
    }

    public void setQueueSize(int queueSize) {
        this.queueSize = queueSize;
    }
    
    public String getSource() {
        return source;
    }
    
    public void setSource(String source) {
        this.source = source;
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
    
    public int getVolume() {
        return volume;
    }
    
    public void setVolume(int volume) {
        this.volume = volume;
    }
    
    public String getSourceType() {
        return sourceType;
    }
    
    public void setSourceType(String sourceType) {
        this.sourceType = sourceType;
    }
} 