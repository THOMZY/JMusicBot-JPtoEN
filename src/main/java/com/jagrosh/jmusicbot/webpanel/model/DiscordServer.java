/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a Discord server (guild) for the web panel
 */
public class DiscordServer {
    
    private String id;
    private String name;
    private String iconUrl;
    private int memberCount;
    private boolean botHasAdmin;
    
    public DiscordServer() {
        // Default constructor for serialization
    }
    
    public DiscordServer(String id, String name, String iconUrl, int memberCount, boolean botHasAdmin) {
        this.id = id;
        this.name = name;
        this.iconUrl = iconUrl;
        this.memberCount = memberCount;
        this.botHasAdmin = botHasAdmin;
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
    
    @JsonProperty("iconUrl")
    public String getIconUrl() {
        return iconUrl;
    }
    
    public void setIconUrl(String iconUrl) {
        this.iconUrl = iconUrl;
    }
    
    @JsonProperty("memberCount")
    public int getMemberCount() {
        return memberCount;
    }
    
    public void setMemberCount(int memberCount) {
        this.memberCount = memberCount;
    }
    
    @JsonProperty("botHasAdmin")
    public boolean isBotHasAdmin() {
        return botHasAdmin;
    }
    
    public void setBotHasAdmin(boolean botHasAdmin) {
        this.botHasAdmin = botHasAdmin;
    }
} 