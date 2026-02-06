/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.service;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.webpanel.model.ChannelPermission;
import com.jagrosh.jmusicbot.webpanel.model.DiscordChannel;
import com.jagrosh.jmusicbot.webpanel.model.DiscordMessage;
import com.jagrosh.jmusicbot.webpanel.model.DiscordRole;
import com.jagrosh.jmusicbot.webpanel.model.DiscordServer;
import com.jagrosh.jmusicbot.webpanel.model.DiscordUserProfile;
import com.jagrosh.jmusicbot.webpanel.model.ChannelMember;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.RichPresence;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.User.Profile;
import net.dv8tion.jda.api.entities.channel.Channel;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.concrete.Category;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.entities.channel.attribute.ICategorizableChannel;
import net.dv8tion.jda.api.entities.channel.attribute.IPositionableChannel;
import net.dv8tion.jda.api.exceptions.InsufficientPermissionException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.Color;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service for handling Discord channel-related operations
 */
@Service
public class ChannelsService {

    private static final Logger log = LoggerFactory.getLogger(ChannelsService.class);
    
    private final Bot bot;
    private final AvatarCacheService avatarCacheService;
    
    @Autowired
    public ChannelsService(Bot bot, AvatarCacheService avatarCacheService) {
        this.bot = bot;
        this.avatarCacheService = avatarCacheService;
    }

    /**
     * Get all servers (guilds) the bot is in
     */
    public List<DiscordServer> getServers() {
        List<DiscordServer> servers = new ArrayList<>();
        
        try {
            JDA jda = Bot.INSTANCE.getJDA();
            if (jda == null) {
                log.error("JDA instance is null");
                return servers;
            }
            
            // Get music history to count activity per server
            Map<String, Long> activityCount = new HashMap<>();
            if (Bot.INSTANCE.getMusicHistory() != null) {
                List<com.jagrosh.jmusicbot.audio.MusicHistory.PlayRecord> history = 
                    Bot.INSTANCE.getMusicHistory().getHistory();
                
                // Count number of tracks played per guild
                activityCount = history.stream()
                    .collect(Collectors.groupingBy(
                        com.jagrosh.jmusicbot.audio.MusicHistory.PlayRecord::getGuildId,
                        Collectors.counting()
                    ));
            }
            
            for (Guild guild : jda.getGuilds()) {
                boolean botHasAdmin = guild.getSelfMember().hasPermission(Permission.ADMINISTRATOR);
                
                DiscordServer server = new DiscordServer(
                    guild.getId(),
                    guild.getName(),
                    guild.getIconUrl(),
                    guild.getMemberCount(),
                    botHasAdmin
                );
                
                servers.add(server);
            }
            
            // Sort servers by music activity (most active servers first), then by name as tiebreaker
            final Map<String, Long> finalActivityCount = activityCount;
            servers.sort((s1, s2) -> {
                long activity1 = finalActivityCount.getOrDefault(s1.getId(), 0L);
                long activity2 = finalActivityCount.getOrDefault(s2.getId(), 0L);
                
                // Primary sort: by activity (descending)
                int activityCompare = Long.compare(activity2, activity1);
                if (activityCompare != 0) {
                    return activityCompare;
                }
                
                // Secondary sort: by name (alphabetically) for servers with same activity
                return s1.getName().compareToIgnoreCase(s2.getName());
            });
            
        } catch (Exception e) {
            log.error("Error fetching servers", e);
        }
        
        return servers;
    }

    /**
     * Get all channels for a specific server, sorted by category and position.
     */
    public List<DiscordChannel> getChannelsForServer(String serverId) {
        List<DiscordChannel> sortedChannels = new ArrayList<>();
        try {
            JDA jda = Bot.INSTANCE.getJDA();
            if (jda == null) {
                log.warn("JDA instance is null, cannot fetch channels for server {}", serverId);
                return null;
            }

            Guild guild = jda.getGuildById(serverId);
            if (guild == null) {
                log.warn("Guild with ID {} not found.", serverId);
                return null;
            }

            // Get and sort categories by position
            List<Category> categories = guild.getCategories().stream()
                    .sorted(Comparator.comparingInt(IPositionableChannel::getPosition))
                    .collect(Collectors.toList());

            // Add sorted categories and their channels
            for (Category category : categories) {
                sortedChannels.add(createDiscordChannelFromGuildChannel(category, guild));
                List<GuildChannel> channelsInCategory = category.getChannels().stream()
                        .filter(ch -> ch instanceof IPositionableChannel) // Ensure it's positionable
                        .map(ch -> (IPositionableChannel) ch) // Cast to IPositionableChannel
                        .sorted(Comparator.comparingInt(IPositionableChannel::getPosition))
                        .map(ch -> (GuildChannel) ch) // Cast back to GuildChannel
                        .collect(Collectors.toList());
                for (GuildChannel channel : channelsInCategory) {
                    sortedChannels.add(createDiscordChannelFromGuildChannel(channel, guild));
                }
            }

            // Add channels not in any category (orphaned), sorted by position
            List<GuildChannel> orphanedChannels = guild.getChannels().stream()
                    .filter(channel -> !(channel instanceof Category) && 
                                     (!(channel instanceof ICategorizableChannel) || ((ICategorizableChannel) channel).getParentCategory() == null))
                    .filter(ch -> ch instanceof IPositionableChannel) // Ensure it's positionable
                    .map(ch -> (IPositionableChannel) ch) // Cast to IPositionableChannel
                    .sorted(Comparator.comparingInt(IPositionableChannel::getPosition))
                    .map(ch -> (GuildChannel) ch) // Cast back to GuildChannel
                    .collect(Collectors.toList());

            for (GuildChannel channel : orphanedChannels) {
                sortedChannels.add(createDiscordChannelFromGuildChannel(channel, guild));
            }

        } catch (Exception e) {
            log.error("Error fetching channels for server " + serverId, e);
            return null; // Return null to indicate an error
        }
        return sortedChannels;
    }

    private DiscordChannel createDiscordChannelFromGuildChannel(GuildChannel channel, Guild guild) {
        boolean accessible = guild.getSelfMember().hasPermission(channel, Permission.VIEW_CHANNEL);
        String parentId = null;
        String topic = null;
        int position = -1; // Default if not positionable
        if (channel instanceof IPositionableChannel) {
             position = ((IPositionableChannel) channel).getPosition();
        }

        if (channel instanceof ICategorizableChannel) {
            Category parentCategory = ((ICategorizableChannel) channel).getParentCategory();
            if (parentCategory != null) {
                parentId = parentCategory.getId();
            }
        }
        if (channel instanceof TextChannel) {
            topic = ((TextChannel) channel).getTopic();
        }

        DiscordChannel discordChannel = new DiscordChannel(
                channel.getId(),
                channel.getName(),
                channel.getType().name().toLowerCase(),
                parentId,
                topic,
                position,
                accessible
        );

        // Populate connected users for voice channels
        if (channel.getType() == ChannelType.VOICE) {
            VoiceChannel voiceChannel = (VoiceChannel) channel;
            List<ChannelMember> connectedUsers = new ArrayList<>();
            for (Member member : voiceChannel.getMembers()) {
                User user = member.getUser();
                boolean isMuted = member.getVoiceState() != null && member.getVoiceState().isGuildMuted();
                boolean isDeafened = member.getVoiceState() != null && member.getVoiceState().isGuildDeafened();
                boolean isStreaming = member.getVoiceState() != null && member.getVoiceState().isSendingVideo(); // JDA uses isSendingVideo for streaming
                boolean isVideoEnabled = member.getVoiceState() != null && member.getVoiceState().isSendingVideo(); // Same as streaming for this basic impl.
                
                String userAvatar = avatarCacheService.getAvatarUrl(user.getId());
                if (userAvatar == null) {
                    userAvatar = user.getEffectiveAvatarUrl();
                }

                connectedUsers.add(new ChannelMember(
                    user.getId(), 
                    member.getEffectiveName(), 
                    userAvatar, 
                    user.isBot(),
                    isMuted, 
                    isDeafened,
                    isStreaming, 
                    isVideoEnabled 
                ));
            }
            discordChannel.setConnectedUsers(connectedUsers);
        }

        return discordChannel;
    }

    /**
     * Get all roles for a specific server
     */
    public List<DiscordRole> getRolesForServer(String serverId) {
        List<DiscordRole> roles = new ArrayList<>();
        try {
            JDA jda = Bot.INSTANCE.getJDA();
            if (jda == null) {
                log.warn("JDA instance is null, cannot fetch roles for server {}", serverId);
                return roles; // Return empty list
            }

            Guild guild = jda.getGuildById(serverId);
            if (guild == null) {
                log.warn("Guild with ID {} not found.", serverId);
                return roles; // Return empty list
            }

            for (Role role : guild.getRoles()) {
                String hexColor = "#FFFFFF"; // Default to white
                Color roleColor = role.getColor();
                if (roleColor != null) {
                    hexColor = String.format("#%02x%02x%02x", roleColor.getRed(), roleColor.getGreen(), roleColor.getBlue());
                }
                
                DiscordRole discordRole = new DiscordRole(
                    role.getId(),
                    role.getName(),
                    hexColor
                );
                roles.add(discordRole);
            }
            
            // Sort roles by position (descending, so higher roles appear first)
            roles.sort(Comparator.comparing((DiscordRole dr) -> {
                Role r = guild.getRoleById(dr.getId());
                return r != null ? r.getPosition() : -1;
            }).reversed());

        } catch (Exception e) {
            log.error("Error fetching roles for server " + serverId, e);
            // Return empty list on error
        }
        return roles;
    }

    /**
     * Get detailed information about a specific channel
     */
    public DiscordChannel getChannelDetails(String channelId) {
        try {
            JDA jda = Bot.INSTANCE.getJDA();
            if (jda == null) {
                log.warn("JDA instance is null, cannot fetch details for channel {}", channelId);
                return null;
            }

            GuildChannel guildChannel = jda.getGuildChannelById(channelId);
            if (guildChannel == null) {
                log.warn("Channel with ID {} not found.", channelId);
                return null;
            }

            Guild guild = guildChannel.getGuild();
            Member selfMember = guild.getSelfMember();

            String parentId = null;
            String topic = null;
            int position = -1; // Default position

            if (guildChannel instanceof IPositionableChannel) {
                position = ((IPositionableChannel) guildChannel).getPosition();
            }

            if (guildChannel instanceof ICategorizableChannel) {
                Category parentCategory = ((ICategorizableChannel) guildChannel).getParentCategory();
                if (parentCategory != null) {
                    parentId = parentCategory.getId();
                }
            }
            if (guildChannel instanceof TextChannel) {
                topic = ((TextChannel) guildChannel).getTopic();
            }

            // Check if bot can view the channel
            if (!selfMember.hasPermission(guildChannel, Permission.VIEW_CHANNEL)) {
                log.warn("Bot does not have permission to view channel {}", channelId);
                return new DiscordChannel(channelId, guildChannel.getName(), guildChannel.getType().name().toLowerCase(), parentId, topic, position, false);
            }

            // Basic channel info
            DiscordChannel details = new DiscordChannel(
                    guildChannel.getId(),
                    guildChannel.getName(),
                    guildChannel.getType().name().toLowerCase(),
                    parentId, 
                    topic,
                    position,
                    true // Accessible
            );
            
            // Add bot permissions for this channel
            List<ChannelPermission> permissions = new ArrayList<>();
            for (Permission p : Permission.values()) {
                if (p.isChannel()) { 
                    permissions.add(new ChannelPermission(p.getName(), selfMember.hasPermission(guildChannel, p), null)); // Passing null for description
                }
            }
            details.setPermissions(permissions);

            return details;

        } catch (Exception e) {
            log.error("Error fetching details for channel " + channelId, e);
            return null;
        }
    }

    /**
     * Get messages from a text channel
     * @param channelId The ID of the text channel
     * @param limit Maximum number of messages to retrieve (default 50)
     * @param before ID of message to get messages before (for pagination)
     * @param after ID of message to get messages after (for pagination)
     * @return List of Discord messages or null if error
     */
    public List<DiscordMessage> getChannelMessages(String channelId, Integer limit, String before, String after) {
        List<DiscordMessage> messages = new ArrayList<>();
        
        try {
            JDA jda = Bot.INSTANCE.getJDA();
            if (jda == null) {
                log.error("JDA instance is null");
                return null;
            }
            
            TextChannel channel = jda.getTextChannelById(channelId);
            if (channel == null) {
                log.error("Text channel not found: {}", channelId);
                return null;
            }
            
            Guild guild = channel.getGuild(); // Get guild for member information

            // Check if bot has permission to read message history
            if (!guild.getSelfMember().hasPermission(channel, Permission.MESSAGE_HISTORY)) {
                log.error("Bot doesn't have permission to read message history in channel: {}", channelId);
                return null;
            }
            
            // Set default limit if not specified
            int messageLimit = limit != null ? limit : 50;
            messageLimit = Math.min(messageLimit, 100); // Cap at 100 messages per request
            
            // Retrieve messages with pagination support
            List<Message> retrievedMessages;
            if (before != null) {
                retrievedMessages = channel.getHistoryBefore(before, messageLimit).complete().getRetrievedHistory();
            } else if (after != null) {
                retrievedMessages = channel.getHistoryAfter(after, messageLimit).complete().getRetrievedHistory();
            } else {
                retrievedMessages = channel.getHistory().retrievePast(messageLimit).complete();
            }
            
            // Convert JDA messages to our model
            for (Message message : retrievedMessages) {
                User authorUser = message.getAuthor();
                Member authorMember = guild.getMember(authorUser); // Get member object

                String highestRoleColor = "#FFFFFF"; // Default to white
                if (authorMember != null) {
                    List<Role> memberRoles = authorMember.getRoles();
                    if (!memberRoles.isEmpty()) {
                        Role highestRole = memberRoles.get(0); 
                        Color roleColor = highestRole.getColor();
                        if (roleColor != null) {
                            highestRoleColor = String.format("#%02x%02x%02x", roleColor.getRed(), roleColor.getGreen(), roleColor.getBlue());
                        }
                    }
                }

                // Process author
                String authorAvatar = avatarCacheService.getAvatarUrl(authorUser.getId());
                if (authorAvatar == null) {
                    authorAvatar = authorUser.getEffectiveAvatarUrl();
                }

                DiscordMessage.MessageAuthor discordAuthor = new DiscordMessage.MessageAuthor(
                    authorUser.getId(),
                    authorUser.getName(),
                    authorAvatar,
                    authorUser.isBot(),
                    highestRoleColor
                );
                
                // Resolve mentions in message content
                String resolvedContent = message.getContentRaw();
                for (User mentionedUser : message.getMentions().getUsers()) {
                    resolvedContent = resolvedContent.replace("<@" + mentionedUser.getId() + ">", "@" + mentionedUser.getName());
                    resolvedContent = resolvedContent.replace("<@!" + mentionedUser.getId() + ">", "@" + mentionedUser.getName());
                }
                for (GuildChannel mentionedChannel : message.getMentions().getChannels()) { // Use GuildChannel for broader type
                    resolvedContent = resolvedContent.replace("<#" + mentionedChannel.getId() + ">", "#" + mentionedChannel.getName());
                }
                for (Role mentionedRole : message.getMentions().getRoles()) {
                    resolvedContent = resolvedContent.replace("<@&" + mentionedRole.getId() + ">", "@" + mentionedRole.getName());
                }

                // Process attachments
                List<DiscordMessage.MessageAttachment> attachments = new ArrayList<>();
                for (net.dv8tion.jda.api.entities.Message.Attachment attachment : message.getAttachments()) {
                    attachments.add(new DiscordMessage.MessageAttachment(
                        attachment.getId(),
                        attachment.getUrl(),
                        attachment.getProxyUrl(),
                        attachment.getFileName(),
                        attachment.getSize(),
                        attachment.getContentType()
                    ));
                }
                
                // Process embeds
                List<DiscordMessage.MessageEmbed> embeds = new ArrayList<>();
                for (net.dv8tion.jda.api.entities.MessageEmbed embed : message.getEmbeds()) {
                    List<DiscordMessage.EmbedField> fields = new ArrayList<>();
                    if (embed.getFields() != null) {
                        for (net.dv8tion.jda.api.entities.MessageEmbed.Field field : embed.getFields()) {
                            fields.add(new DiscordMessage.EmbedField(
                                field.getName(),
                                field.getValue(),
                                field.isInline()
                            ));
                        }
                    }
                    
                    DiscordMessage.EmbedFooter footer = null;
                    if (embed.getFooter() != null) {
                        footer = new DiscordMessage.EmbedFooter(
                            embed.getFooter().getText(),
                            embed.getFooter().getIconUrl()
                        );
                    }
                    
                    DiscordMessage.EmbedImage image = null;
                    if (embed.getImage() != null) {
                        image = new DiscordMessage.EmbedImage(
                            embed.getImage().getUrl(),
                            embed.getImage().getWidth(),
                            embed.getImage().getHeight()
                        );
                    }
                    
                    DiscordMessage.EmbedThumbnail thumbnail = null;
                    if (embed.getThumbnail() != null) {
                        thumbnail = new DiscordMessage.EmbedThumbnail(
                            embed.getThumbnail().getUrl(),
                            embed.getThumbnail().getWidth(),
                            embed.getThumbnail().getHeight()
                        );
                    }
                    
                    DiscordMessage.EmbedAuthor embedAuthor = null;
                    if (embed.getAuthor() != null) {
                        embedAuthor = new DiscordMessage.EmbedAuthor(
                            embed.getAuthor().getName(),
                            embed.getAuthor().getUrl(),
                            embed.getAuthor().getIconUrl()
                        );
                    }
                    
                    String timestamp = embed.getTimestamp() != null ? embed.getTimestamp().toString() : null;
                    Integer color = embed.getColor() != null ? embed.getColor().getRGB() : null;
                    
                    embeds.add(new DiscordMessage.MessageEmbed(
                        embed.getTitle(),
                        embed.getDescription(),
                        embed.getUrl(),
                        timestamp,
                        color,
                        footer,
                        image,
                        thumbnail,
                        embedAuthor,
                        fields
                    ));
                }
                
                DiscordMessage discordMessage = new DiscordMessage(
                    message.getId(),
                    resolvedContent, // Use resolved content here
                    message.getTimeCreated(),
                    discordAuthor,
                    attachments,
                    embeds
                );
                
                messages.add(discordMessage);
            }
            
            Collections.reverse(messages);
            
        } catch (Exception e) {
            log.error("Error fetching messages for channel: {}", channelId, e);
            return null;
        }
        
        return messages;
    }

    /**
     * Send a message to a text channel
     * @param channelId The ID of the text channel
     * @param content The content of the message to send
     * @return true if the message was sent successfully, false otherwise
     */
    public boolean sendMessageToChannel(String channelId, String content) { // Renamed from sendChannelMessage
        try {
            JDA jda = Bot.INSTANCE.getJDA();
            if (jda == null) {
                log.error("JDA instance is null");
                return false;
            }
            
            Channel channel = jda.getChannelById(Channel.class, channelId);
            if (channel == null || !(channel instanceof TextChannel)) {
                log.error("Channel not found or not a text channel: {}", channelId);
                return false;
            }
            
            TextChannel textChannel = (TextChannel) channel;
            
            // Check if bot has permission to send messages
            if (!textChannel.getGuild().getSelfMember().hasPermission(textChannel, Permission.MESSAGE_SEND)) {
                log.error("Bot does not have permission to send messages in channel: {}", channelId);
                return false;
            }
            
            // Send the message
            textChannel.sendMessage(content).queue(
                message -> log.info("Message sent successfully to channel {}", channelId),
                error -> log.error("Error sending message to channel {}: {}", channelId, error.getMessage())
            );
            
            return true;
        } catch (Exception e) {
            log.error("Error sending message to channel {}: {}", channelId, e.getMessage());
            return false;
        }
    }

    /**
     * Get all roles for a specific member in a server
     */
    public List<DiscordRole> getMemberRoles(String serverId, String memberId) {
        List<DiscordRole> memberRolesList = new ArrayList<>();
        try {
            JDA jda = Bot.INSTANCE.getJDA();
            if (jda == null) {
                log.warn("JDA instance is null, cannot fetch member roles for server {} member {}", serverId, memberId);
                return memberRolesList; // Return empty list
            }

            Guild guild = jda.getGuildById(serverId);
            if (guild == null) {
                log.warn("Guild with ID {} not found while fetching member roles.", serverId);
                return memberRolesList; // Return empty list
            }

            Member member = guild.getMemberById(memberId);
            if (member == null) {
                log.warn("Member with ID {} not found in guild {} while fetching member roles.", memberId, serverId);
                return memberRolesList; // Return empty list
            }

            List<Role> roles = member.getRoles();
            // Roles from Member#getRoles() are already sorted by position (highest first)
            for (Role role : roles) {
                String hexColor = "#FFFFFF"; // Default to white
                Color roleColor = role.getColor();
                if (roleColor != null) {
                    hexColor = String.format("#%02x%02x%02x", roleColor.getRed(), roleColor.getGreen(), roleColor.getBlue());
                }
                memberRolesList.add(new DiscordRole(role.getId(), role.getName(), hexColor));
            }
            // No need to sort again as JDA provides them sorted by hierarchy

        } catch (Exception e) {
            log.error("Error fetching roles for member {} in server {}: {}", memberId, serverId, e.getMessage());
            // Return empty list on error
        }
        return memberRolesList;
    }

    /**
     * Get detailed profile information for a specific member in a server.
     * @param serverId The ID of the server.
     * @param memberId The ID of the member.
     * @return DiscordUserProfile object or null if not found or error.
     */
    public DiscordUserProfile getMemberProfile(String serverId, String memberId) {
        try {
            JDA jda = Bot.INSTANCE.getJDA();
            if (jda == null) {
                log.warn("JDA instance is null, cannot fetch profile for member {} in server {}", memberId, serverId);
                return null;
            }

            Guild guild = jda.getGuildById(serverId);
            if (guild == null) {
                log.warn("Guild with ID {} not found while fetching member profile.", serverId);
                return null;
            }

            Member member = guild.retrieveMemberById(memberId).complete(); // Retrieve fresh member data
            if (member == null) {
                log.warn("Member with ID {} not found in guild {} while fetching profile.", memberId, serverId);
                return null;
            }

            User user = member.getUser();
            Profile userProfile = user.retrieveProfile().complete(); // Retrieve user profile for banner, accent color

            DiscordUserProfile profile = new DiscordUserProfile();
            profile.setId(user.getId());
            profile.setUsername(user.getName());
            profile.setDiscriminator(user.getDiscriminator());
            profile.setEffectiveName(member.getEffectiveName());
            
            String userAvatar = avatarCacheService.getAvatarUrl(user.getId());
            if (userAvatar == null) {
                userAvatar = user.getEffectiveAvatarUrl();
            }
            profile.setAvatarUrl(userAvatar);
            
            profile.setBannerUrl(userProfile.getBannerUrl());
            if (userProfile.getAccentColor() != null) {
                profile.setAccentColorHex(String.format("#%06x", userProfile.getAccentColor().getRGB() & 0xFFFFFF));
            }
            profile.setBot(user.isBot());
            profile.setTimeCreated(user.getTimeCreated());
            profile.setTimeJoined(member.getTimeJoined());

            // Online Status
            OnlineStatus onlineStatus = member.getOnlineStatus();
            profile.setOnlineStatus(onlineStatus != null ? onlineStatus.getKey().toUpperCase() : "OFFLINE");

            // Activities
            List<DiscordUserProfile.ActivityInfo> activityInfos = new ArrayList<>();
            for (Activity activity : member.getActivities()) {
                String details = null;
                String state = null;
                String largeImageUrl = null;
                String smallImageUrl = null;
                String streamUrl = null;

                if (activity.isRich()) {
                    RichPresence richPresence = activity.asRichPresence();
                    if (richPresence != null) {
                        details = richPresence.getDetails();
                        state = richPresence.getState();
                        if (richPresence.getLargeImage() != null) {
                            largeImageUrl = richPresence.getLargeImage().getUrl();
                        }
                        if (richPresence.getSmallImage() != null) {
                            smallImageUrl = richPresence.getSmallImage().getUrl();
                        }
                    }
                }
                if (activity.getType() == Activity.ActivityType.STREAMING) {
                    streamUrl = activity.getUrl();
                }

                activityInfos.add(new DiscordUserProfile.ActivityInfo(
                        activity.getName(),
                        activity.getType().name(),
                        details,
                        state,
                        largeImageUrl,
                        smallImageUrl,
                        streamUrl
                ));
            }
            profile.setActivities(activityInfos);

            // Roles
            List<DiscordRole> memberRolesList = new ArrayList<>();
            List<Role> roles = member.getRoles(); // Roles are already sorted by position
            for (Role role : roles) {
                String hexColor = "#FFFFFF";
                Color roleColor = role.getColor();
                if (roleColor != null) {
                    hexColor = String.format("#%02x%02x%02x", roleColor.getRed(), roleColor.getGreen(), roleColor.getBlue());
                }
                memberRolesList.add(new DiscordRole(role.getId(), role.getName(), hexColor));
            }
            profile.setRoles(memberRolesList);

            // Mutual guilds/friends count are not directly available/easy to get via JDA for a specific user
            // and would require more complex logic or different API calls, so leaving as placeholders.
            profile.setMutualGuildsCount("N/A");
            profile.setMutualFriendsCount("N/A");

            return profile;

        } catch (Exception e) {
            log.error("Error fetching profile for member {} in server {}: {}", memberId, serverId, e.getMessage(), e);
            return null;
        }
    }
} 