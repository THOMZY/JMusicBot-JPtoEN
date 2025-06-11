/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.model;

import java.time.OffsetDateTime;
import java.util.List;
import java.awt.Color;

/**
 * Model representing a Discord message
 */
public class DiscordMessage {
    private String id;
    private String content;
    private OffsetDateTime timestamp;
    private MessageAuthor author;
    private List<MessageAttachment> attachments;
    private List<MessageEmbed> embeds;

    public DiscordMessage() {
    }

    public DiscordMessage(String id, String content, OffsetDateTime timestamp, MessageAuthor author, 
                         List<MessageAttachment> attachments, List<MessageEmbed> embeds) {
        this.id = id;
        this.content = content;
        this.timestamp = timestamp;
        this.author = author;
        this.attachments = attachments;
        this.embeds = embeds;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public OffsetDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(OffsetDateTime timestamp) {
        this.timestamp = timestamp;
    }

    public MessageAuthor getAuthor() {
        return author;
    }

    public void setAuthor(MessageAuthor author) {
        this.author = author;
    }

    public List<MessageAttachment> getAttachments() {
        return attachments;
    }

    public void setAttachments(List<MessageAttachment> attachments) {
        this.attachments = attachments;
    }
    
    public List<MessageEmbed> getEmbeds() {
        return embeds;
    }
    
    public void setEmbeds(List<MessageEmbed> embeds) {
        this.embeds = embeds;
    }

    /**
     * Class representing a message author
     */
    public static class MessageAuthor {
        private String id;
        private String name;
        private String avatarUrl;
        private boolean isBot;
        private String highestRoleColor;

        public MessageAuthor() {
        }

        public MessageAuthor(String id, String name, String avatarUrl, boolean isBot, String highestRoleColor) {
            this.id = id;
            this.name = name;
            this.avatarUrl = avatarUrl;
            this.isBot = isBot;
            this.highestRoleColor = highestRoleColor;
        }

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

        public String getAvatarUrl() {
            return avatarUrl;
        }

        public void setAvatarUrl(String avatarUrl) {
            this.avatarUrl = avatarUrl;
        }

        public boolean isBot() {
            return isBot;
        }

        public void setBot(boolean isBot) {
            this.isBot = isBot;
        }

        public String getHighestRoleColor() {
            return highestRoleColor;
        }

        public void setHighestRoleColor(String highestRoleColor) {
            this.highestRoleColor = highestRoleColor;
        }
    }

    /**
     * Class representing a message attachment
     */
    public static class MessageAttachment {
        private String id;
        private String url;
        private String proxyUrl;
        private String filename;
        private long size;
        private String contentType;

        public MessageAttachment() {
        }

        public MessageAttachment(String id, String url, String proxyUrl, String filename, long size, String contentType) {
            this.id = id;
            this.url = url;
            this.proxyUrl = proxyUrl;
            this.filename = filename;
            this.size = size;
            this.contentType = contentType;
        }

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public String getProxyUrl() {
            return proxyUrl;
        }

        public void setProxyUrl(String proxyUrl) {
            this.proxyUrl = proxyUrl;
        }

        public String getFilename() {
            return filename;
        }

        public void setFilename(String filename) {
            this.filename = filename;
        }

        public long getSize() {
            return size;
        }

        public void setSize(long size) {
            this.size = size;
        }

        public String getContentType() {
            return contentType;
        }

        public void setContentType(String contentType) {
            this.contentType = contentType;
        }
    }
    
    /**
     * Class representing a Discord embed
     */
    public static class MessageEmbed {
        private String title;
        private String description;
        private String url;
        private String timestamp;
        private Integer color;
        private EmbedFooter footer;
        private EmbedImage image;
        private EmbedThumbnail thumbnail;
        private EmbedAuthor author;
        private List<EmbedField> fields;
        
        public MessageEmbed() {
        }
        
        public MessageEmbed(String title, String description, String url, String timestamp, 
                           Integer color, EmbedFooter footer, EmbedImage image, 
                           EmbedThumbnail thumbnail, EmbedAuthor author, List<EmbedField> fields) {
            this.title = title;
            this.description = description;
            this.url = url;
            this.timestamp = timestamp;
            this.color = color;
            this.footer = footer;
            this.image = image;
            this.thumbnail = thumbnail;
            this.author = author;
            this.fields = fields;
        }
        
        public String getTitle() {
            return title;
        }
        
        public void setTitle(String title) {
            this.title = title;
        }
        
        public String getDescription() {
            return description;
        }
        
        public void setDescription(String description) {
            this.description = description;
        }
        
        public String getUrl() {
            return url;
        }
        
        public void setUrl(String url) {
            this.url = url;
        }
        
        public String getTimestamp() {
            return timestamp;
        }
        
        public void setTimestamp(String timestamp) {
            this.timestamp = timestamp;
        }
        
        public Integer getColor() {
            return color;
        }
        
        public void setColor(Integer color) {
            this.color = color;
        }
        
        public EmbedFooter getFooter() {
            return footer;
        }
        
        public void setFooter(EmbedFooter footer) {
            this.footer = footer;
        }
        
        public EmbedImage getImage() {
            return image;
        }
        
        public void setImage(EmbedImage image) {
            this.image = image;
        }
        
        public EmbedThumbnail getThumbnail() {
            return thumbnail;
        }
        
        public void setThumbnail(EmbedThumbnail thumbnail) {
            this.thumbnail = thumbnail;
        }
        
        public EmbedAuthor getAuthor() {
            return author;
        }
        
        public void setAuthor(EmbedAuthor author) {
            this.author = author;
        }
        
        public List<EmbedField> getFields() {
            return fields;
        }
        
        public void setFields(List<EmbedField> fields) {
            this.fields = fields;
        }
        
        /**
         * Convert the color integer to a CSS hex color
         */
        public String getColorAsHex() {
            if (color == null) return null;
            return String.format("#%06X", color);
        }
    }
    
    /**
     * Class representing an embed footer
     */
    public static class EmbedFooter {
        private String text;
        private String iconUrl;
        
        public EmbedFooter() {
        }
        
        public EmbedFooter(String text, String iconUrl) {
            this.text = text;
            this.iconUrl = iconUrl;
        }
        
        public String getText() {
            return text;
        }
        
        public void setText(String text) {
            this.text = text;
        }
        
        public String getIconUrl() {
            return iconUrl;
        }
        
        public void setIconUrl(String iconUrl) {
            this.iconUrl = iconUrl;
        }
    }
    
    /**
     * Class representing an embed image
     */
    public static class EmbedImage {
        private String url;
        private Integer width;
        private Integer height;
        
        public EmbedImage() {
        }
        
        public EmbedImage(String url, Integer width, Integer height) {
            this.url = url;
            this.width = width;
            this.height = height;
        }
        
        public String getUrl() {
            return url;
        }
        
        public void setUrl(String url) {
            this.url = url;
        }
        
        public Integer getWidth() {
            return width;
        }
        
        public void setWidth(Integer width) {
            this.width = width;
        }
        
        public Integer getHeight() {
            return height;
        }
        
        public void setHeight(Integer height) {
            this.height = height;
        }
    }
    
    /**
     * Class representing an embed thumbnail
     */
    public static class EmbedThumbnail {
        private String url;
        private Integer width;
        private Integer height;
        
        public EmbedThumbnail() {
        }
        
        public EmbedThumbnail(String url, Integer width, Integer height) {
            this.url = url;
            this.width = width;
            this.height = height;
        }
        
        public String getUrl() {
            return url;
        }
        
        public void setUrl(String url) {
            this.url = url;
        }
        
        public Integer getWidth() {
            return width;
        }
        
        public void setWidth(Integer width) {
            this.width = width;
        }
        
        public Integer getHeight() {
            return height;
        }
        
        public void setHeight(Integer height) {
            this.height = height;
        }
    }
    
    /**
     * Class representing an embed author
     */
    public static class EmbedAuthor {
        private String name;
        private String url;
        private String iconUrl;
        
        public EmbedAuthor() {
        }
        
        public EmbedAuthor(String name, String url, String iconUrl) {
            this.name = name;
            this.url = url;
            this.iconUrl = iconUrl;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getUrl() {
            return url;
        }
        
        public void setUrl(String url) {
            this.url = url;
        }
        
        public String getIconUrl() {
            return iconUrl;
        }
        
        public void setIconUrl(String iconUrl) {
            this.iconUrl = iconUrl;
        }
    }
    
    /**
     * Class representing an embed field
     */
    public static class EmbedField {
        private String name;
        private String value;
        private boolean inline;
        
        public EmbedField() {
        }
        
        public EmbedField(String name, String value, boolean inline) {
            this.name = name;
            this.value = value;
            this.inline = inline;
        }
        
        public String getName() {
            return name;
        }
        
        public void setName(String name) {
            this.name = name;
        }
        
        public String getValue() {
            return value;
        }
        
        public void setValue(String value) {
            this.value = value;
        }
        
        public boolean isInline() {
            return inline;
        }
        
        public void setInline(boolean inline) {
            this.inline = inline;
        }
    }
} 