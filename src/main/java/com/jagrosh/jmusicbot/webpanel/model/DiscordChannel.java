/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;

/**
 * Represents a Discord channel for the web panel
 */
public class DiscordChannel {
    
    private String id;
    private String name;
    private String type; // "text", "voice", "category", etc.
    private String parentId; // ID of parent category channel
    private String topic; // Channel topic (for text channels)
    private int position;
    private boolean accessible; // Whether the bot can access this channel
    private List<ChannelPermission> permissions;
    private List<ChannelMember> connectedUsers; // Users connected to voice channel
    
    public DiscordChannel() {
        // Default constructor for serialization
        this.connectedUsers = new ArrayList<>();
    }
    
    public DiscordChannel(String id, String name, String type, String parentId, String topic, 
                         int position, boolean accessible) {
        this.id = id;
        this.name = name;
        this.type = type;
        this.parentId = parentId;
        this.topic = topic;
        this.position = position;
        this.accessible = accessible;
        this.connectedUsers = new ArrayList<>();
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
    
    @JsonProperty("type")
    public String getType() {
        return type;
    }
    
    public void setType(String type) {
        this.type = type;
    }
    
    @JsonProperty("parentId")
    public String getParentId() {
        return parentId;
    }
    
    public void setParentId(String parentId) {
        this.parentId = parentId;
    }
    
    @JsonProperty("topic")
    public String getTopic() {
        return topic;
    }
    
    public void setTopic(String topic) {
        this.topic = topic;
    }
    
    @JsonProperty("position")
    public int getPosition() {
        return position;
    }
    
    public void setPosition(int position) {
        this.position = position;
    }
    
    @JsonProperty("accessible")
    public boolean isAccessible() {
        return accessible;
    }
    
    public void setAccessible(boolean accessible) {
        this.accessible = accessible;
    }
    
    @JsonProperty("permissions")
    public List<ChannelPermission> getPermissions() {
        return permissions;
    }
    
    public void setPermissions(List<ChannelPermission> permissions) {
        this.permissions = permissions;
    }
    
    @JsonProperty("connectedUsers")
    public List<ChannelMember> getConnectedUsers() {
        return connectedUsers;
    }
    
    public void setConnectedUsers(List<ChannelMember> connectedUsers) {
        this.connectedUsers = connectedUsers;
    }
    
    /**
     * Add a user to the connected users list
     * @param member The member to add
     */
    public void addConnectedUser(ChannelMember member) {
        if (this.connectedUsers == null) {
            this.connectedUsers = new ArrayList<>();
        }
        this.connectedUsers.add(member);
    }
} 