package com.jagrosh.jmusicbot.webpanel.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Represents a Discord role for the web panel
 */
public class DiscordRole {

    private String id;
    private String name;
    private String color; // Hex color string

    public DiscordRole() {
        // Default constructor for serialization
    }

    public DiscordRole(String id, String name, String color) {
        this.id = id;
        this.name = name;
        this.color = color;
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

    @JsonProperty("color")
    public String getColor() {
        return color;
    }

    public void setColor(String color) {
        this.color = color;
    }
} 