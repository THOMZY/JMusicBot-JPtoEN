/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a member connected to a voice channel for the web panel.
 */
public class ChannelMember {
    private String id;
    private String name;
    private String nickname;
    private String avatarUrl;
    private boolean muted;
    private boolean deafened;
    private boolean streaming;
    private boolean videoEnabled;
    private boolean isBot;
    
    public ChannelMember() {
        // Default constructor for serialization
    }
    
    public ChannelMember(String id, String name, String nickname, String avatarUrl, 
                         boolean muted, boolean deafened, boolean streaming, 
                         boolean videoEnabled, boolean isBot) {
        this.id = id;
        this.name = name;
        this.nickname = nickname;
        this.avatarUrl = avatarUrl;
        this.muted = muted;
        this.deafened = deafened;
        this.streaming = streaming;
        this.videoEnabled = videoEnabled;
        this.isBot = isBot;
    }
    
    public ChannelMember(String id, String name, String avatarUrl, boolean isBot, 
                         boolean muted, boolean deafened, boolean streaming, boolean videoEnabled) {
        this.id = id;
        this.name = name;
        this.avatarUrl = avatarUrl;
        this.isBot = isBot;
        this.muted = muted;
        this.deafened = deafened;
        this.streaming = streaming;
        this.videoEnabled = videoEnabled;
    }
    
    @JsonProperty("id")
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    @JsonProperty("name")
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    @JsonProperty("nickname")
    public String getNickname() {
        return nickname;
    }
    
    public void setNickname(String nickname) {
        this.nickname = nickname;
    }
    
    @JsonProperty("avatarUrl")
    public String getAvatarUrl() {
        return avatarUrl;
    }
    
    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }
    
    @JsonProperty("muted")
    public boolean isMuted() {
        return muted;
    }
    
    public void setMuted(boolean muted) {
        this.muted = muted;
    }
    
    @JsonProperty("deafened")
    public boolean isDeafened() {
        return deafened;
    }
    
    public void setDeafened(boolean deafened) {
        this.deafened = deafened;
    }
    
    @JsonProperty("streaming")
    public boolean isStreaming() {
        return streaming;
    }
    
    public void setStreaming(boolean streaming) {
        this.streaming = streaming;
    }
    
    @JsonProperty("videoEnabled")
    public boolean isVideoEnabled() {
        return videoEnabled;
    }
    
    public void setVideoEnabled(boolean videoEnabled) {
        this.videoEnabled = videoEnabled;
    }
    
    @JsonProperty("isBot")
    public boolean isBot() {
        return isBot;
    }
    
    public void setBot(boolean isBot) {
        this.isBot = isBot;
    }
} 