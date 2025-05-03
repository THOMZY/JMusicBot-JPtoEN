/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.model;

public class Guild {
    private String id;
    private String name;
    private boolean hasConnectedAudio;
    private String iconUrl;
    
    public Guild() {
    }
    
    public Guild(String id, String name, boolean hasConnectedAudio, String iconUrl) {
        this.id = id;
        this.name = name;
        this.hasConnectedAudio = hasConnectedAudio;
        this.iconUrl = iconUrl;
    }
    
    // Getters and setters
    public String getId() {
        return id;
    }
    
    public void setId(String id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public boolean isHasConnectedAudio() {
        return hasConnectedAudio;
    }
    
    public void setHasConnectedAudio(boolean hasConnectedAudio) {
        this.hasConnectedAudio = hasConnectedAudio;
    }
    
    public String getIconUrl() {
        return iconUrl;
    }
    
    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }
} 