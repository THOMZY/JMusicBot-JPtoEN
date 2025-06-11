/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a permission for a Discord channel
 */
public class ChannelPermission {
    
    private String name;
    private boolean allowed;
    private String description;
    
    public ChannelPermission() {
        // Default constructor for serialization
    }
    
    public ChannelPermission(String name, boolean allowed, String description) {
        this.name = name;
        this.allowed = allowed;
        this.description = description;
    }
    
    @JsonProperty("name")
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    @JsonProperty("allowed")
    public boolean isAllowed() {
        return allowed;
    }
    
    public void setAllowed(boolean allowed) {
        this.allowed = allowed;
    }
    
    @JsonProperty("description")
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description;
    }
} 