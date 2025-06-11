package com.jagrosh.jmusicbot.webpanel.model;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * Represents a detailed user profile for the web panel, similar to Discord's profile view.
 */
public class DiscordUserProfile {

    private String id;
    private String username;
    private String discriminator; // May be "0" for newer usernames
    private String effectiveName; // Nickname or username
    private String avatarUrl;
    private String bannerUrl; // User's profile banner
    private String accentColorHex; // User's profile accent color
    private boolean isBot;
    private OffsetDateTime timeCreated; // Account creation date
    private OffsetDateTime timeJoined; // Server join date
    private String onlineStatus; // e.g., "ONLINE", "IDLE", "DND", "OFFLINE"
    private List<ActivityInfo> activities;
    private List<DiscordRole> roles; // Roles in the current server
    private String mutualGuildsCount; // Placeholder for potential future enhancement
    private String mutualFriendsCount; // Placeholder for potential future enhancement
    // Consider adding badges if fetchable, e.g. HypeSquad, Nitro, etc.

    // Constructors
    public DiscordUserProfile() {}

    // Getters and Setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getDiscriminator() {
        return discriminator;
    }

    public void setDiscriminator(String discriminator) {
        this.discriminator = discriminator;
    }

    public String getEffectiveName() {
        return effectiveName;
    }

    public void setEffectiveName(String effectiveName) {
        this.effectiveName = effectiveName;
    }

    public String getAvatarUrl() {
        return avatarUrl;
    }

    public void setAvatarUrl(String avatarUrl) {
        this.avatarUrl = avatarUrl;
    }

    public String getBannerUrl() {
        return bannerUrl;
    }

    public void setBannerUrl(String bannerUrl) {
        this.bannerUrl = bannerUrl;
    }

    public String getAccentColorHex() {
        return accentColorHex;
    }

    public void setAccentColorHex(String accentColorHex) {
        this.accentColorHex = accentColorHex;
    }

    public boolean isBot() {
        return isBot;
    }

    public void setBot(boolean bot) {
        isBot = bot;
    }

    public OffsetDateTime getTimeCreated() {
        return timeCreated;
    }

    public void setTimeCreated(OffsetDateTime timeCreated) {
        this.timeCreated = timeCreated;
    }

    public OffsetDateTime getTimeJoined() {
        return timeJoined;
    }

    public void setTimeJoined(OffsetDateTime timeJoined) {
        this.timeJoined = timeJoined;
    }

    public String getOnlineStatus() {
        return onlineStatus;
    }

    public void setOnlineStatus(String onlineStatus) {
        this.onlineStatus = onlineStatus;
    }

    public List<ActivityInfo> getActivities() {
        return activities;
    }

    public void setActivities(List<ActivityInfo> activities) {
        this.activities = activities;
    }

    public List<DiscordRole> getRoles() {
        return roles;
    }

    public void setRoles(List<DiscordRole> roles) {
        this.roles = roles;
    }

    public String getMutualGuildsCount() {
        return mutualGuildsCount;
    }

    public void setMutualGuildsCount(String mutualGuildsCount) {
        this.mutualGuildsCount = mutualGuildsCount;
    }

    public String getMutualFriendsCount() {
        return mutualFriendsCount;
    }

    public void setMutualFriendsCount(String mutualFriendsCount) {
        this.mutualFriendsCount = mutualFriendsCount;
    }

    /**
     * Inner class to represent a user's activity.
     */
    public static class ActivityInfo {
        private String name;
        private String type; // e.g., "PLAYING", "LISTENING", "WATCHING", "STREAMING"
        private String details; // e.g., For Spotify: song title
        private String state; // e.g., For Spotify: artist
        private String largeImageUrl;
        private String smallImageUrl;
        private String url; // For streaming status

        public ActivityInfo(String name, String type, String details, String state, String largeImageUrl, String smallImageUrl, String url) {
            this.name = name;
            this.type = type;
            this.details = details;
            this.state = state;
            this.largeImageUrl = largeImageUrl;
            this.smallImageUrl = smallImageUrl;
            this.url = url;
        }

        // Getters
        public String getName() { return name; }
        public String getType() { return type; }
        public String getDetails() { return details; }
        public String getState() { return state; }
        public String getLargeImageUrl() { return largeImageUrl; }
        public String getSmallImageUrl() { return smallImageUrl; }
        public String getUrl() { return url; }
    }
} 