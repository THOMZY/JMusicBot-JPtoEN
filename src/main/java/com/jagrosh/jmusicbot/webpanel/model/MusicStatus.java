/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.model;

import java.util.Map;

/**
 * Data class for the current status of the music player
 */
public class MusicStatus {
    private final String currentTrackTitle;
    private final String currentTrackAuthor;
    private final String currentTrackUri;
    private final String currentTrackThumbnail;
    private final long currentTrackPosition;
    private final long currentTrackDuration;
    private final boolean playing;
    private final boolean paused;
    private final boolean hasNext;
    private final int queueSize;
    private final String source;
    private final String requester;
    private final String requesterAvatar;
    private final int volume;
    private final String sourceType;
    private final boolean inVoiceChannel;
    private final Map<String, Object> spotifyInfo;
    private final String radioLogoUrl;
    private final String radioSongImageUrl;
    private final String radioCountry;
    private final String radioAlias;

    // Add fields for local file metadata
    private final String localAlbum;
    private final String localGenre;
    private final String localYear;

    public MusicStatus(
            String currentTrackTitle,
            String currentTrackAuthor,
            String currentTrackUri,
            String currentTrackThumbnail,
            long currentTrackPosition,
            long currentTrackDuration,
            boolean playing,
            boolean paused,
            boolean hasNext,
            int queueSize
    ) {
        this.currentTrackTitle = currentTrackTitle;
        this.currentTrackAuthor = currentTrackAuthor;
        this.currentTrackUri = currentTrackUri;
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
        this.inVoiceChannel = false;
        this.spotifyInfo = null;
        this.radioLogoUrl = null;
        this.radioSongImageUrl = null;
        this.radioCountry = null;
        this.radioAlias = null;
        this.localAlbum = null;
        this.localGenre = null;
        this.localYear = null;
    }

    public MusicStatus(
            String currentTrackTitle,
            String currentTrackAuthor,
            String currentTrackUri,
            String currentTrackThumbnail,
            long currentTrackPosition,
            long currentTrackDuration,
            boolean playing,
            boolean paused,
            boolean hasNext,
            int queueSize,
            String source,
            String requester,
            String requesterAvatar,
            int volume,
            String sourceType
    ) {
        this.currentTrackTitle = currentTrackTitle;
        this.currentTrackAuthor = currentTrackAuthor;
        this.currentTrackUri = currentTrackUri;
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
        this.inVoiceChannel = false;
        this.spotifyInfo = null;
        this.radioLogoUrl = null;
        this.radioSongImageUrl = null;
        this.radioCountry = null;
        this.radioAlias = null;
        this.localAlbum = null;
        this.localGenre = null;
        this.localYear = null;
    }
    
    public MusicStatus(
            String currentTrackTitle,
            String currentTrackAuthor,
            String currentTrackUri,
            String currentTrackThumbnail,
            long currentTrackPosition,
            long currentTrackDuration,
            boolean playing,
            boolean paused,
            boolean hasNext,
            int queueSize,
            String source,
            String requester,
            String requesterAvatar,
            int volume,
            String sourceType,
            boolean inVoiceChannel,
            Map<String, Object> spotifyInfo
    ) {
        this(currentTrackTitle, currentTrackAuthor, currentTrackUri, currentTrackThumbnail,
             currentTrackPosition, currentTrackDuration, playing, paused, hasNext,
             queueSize, source, requester, requesterAvatar, volume, sourceType,
             inVoiceChannel, spotifyInfo, null, null, null, null, null, null, null);
    }

    public MusicStatus(
            String currentTrackTitle,
            String currentTrackAuthor,
            String currentTrackUri,
            String currentTrackThumbnail,
            long currentTrackPosition,
            long currentTrackDuration,
            boolean playing,
            boolean paused,
            boolean hasNext,
            int queueSize,
            String source,
            String requester,
            String requesterAvatar,
            int volume,
            String sourceType,
            boolean inVoiceChannel,
            Map<String, Object> spotifyInfo,
            String radioLogoUrl,
            String radioSongImageUrl,
            String radioCountry,
            String radioAlias,
            String localAlbum,
            String localGenre,
            String localYear
    ) {
        this.currentTrackTitle = currentTrackTitle;
        this.currentTrackAuthor = currentTrackAuthor;
        this.currentTrackUri = currentTrackUri;
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
        this.inVoiceChannel = inVoiceChannel;
        this.spotifyInfo = spotifyInfo;
        this.radioLogoUrl = radioLogoUrl;
        this.radioSongImageUrl = radioSongImageUrl;
        this.radioCountry = radioCountry;
        this.radioAlias = radioAlias;
        this.localAlbum = localAlbum;
        this.localGenre = localGenre;
        this.localYear = localYear;
    }

    public MusicStatus(
            String currentTrackTitle,
            String currentTrackAuthor,
            String currentTrackUri,
            String currentTrackThumbnail,
            long currentTrackPosition,
            long currentTrackDuration,
            boolean playing,
            boolean paused,
            boolean hasNext,
            int queueSize,
            String source,
            String requester,
            String requesterAvatar,
            int volume,
            String sourceType,
            boolean inVoiceChannel
    ) {
        this(currentTrackTitle, currentTrackAuthor, currentTrackUri, currentTrackThumbnail,
             currentTrackPosition, currentTrackDuration, playing, paused, hasNext,
             queueSize, source, requester, requesterAvatar, volume, sourceType,
             inVoiceChannel, null, null, null, null, null, null, null, null);
    }

    // Getters and setters
    public String getCurrentTrackTitle() {
        return currentTrackTitle;
    }

    public String getCurrentTrackAuthor() {
        return currentTrackAuthor;
    }
    
    public String getCurrentTrackUri() {
        return currentTrackUri;
    }

    public String getCurrentTrackThumbnail() {
        return currentTrackThumbnail;
    }

    public long getCurrentTrackPosition() {
        return currentTrackPosition;
    }

    public long getCurrentTrackDuration() {
        return currentTrackDuration;
    }

    public boolean isPlaying() {
        return playing;
    }

    public boolean isPaused() {
        return paused;
    }

    public boolean isHasNext() {
        return hasNext;
    }

    public int getQueueSize() {
        return queueSize;
    }
    
    public String getSource() {
        return source;
    }
    
    public String getRequester() {
        return requester;
    }
    
    public String getRequesterAvatar() {
        return requesterAvatar;
    }
    
    public int getVolume() {
        return volume;
    }
    
    public String getSourceType() {
        return sourceType;
    }
    
    public boolean isInVoiceChannel() {
        return inVoiceChannel;
    }
    
    public Map<String, Object> getSpotifyInfo() {
        return spotifyInfo;
    }
    
    public String getRadioLogoUrl() {
        return radioLogoUrl;
    }
    
    public String getRadioSongImageUrl() {
        return radioSongImageUrl;
    }

    public String getRadioCountry() {
        return radioCountry;
    }

    public String getRadioAlias() {
        return radioAlias;
    }

    // Getters for local file metadata
    public String getLocalAlbum() {
        return localAlbum;
    }

    public String getLocalGenre() {
        return localGenre;
    }

    public String getLocalYear() {
        return localYear;
    }
} 