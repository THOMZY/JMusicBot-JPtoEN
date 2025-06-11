/*
 * Copyright 2025 THOMZY
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package dev.cosgy.jmusicbot.slashcommands.music;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.MusicHistory;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;

import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Command to display the music playback history
 */
public class HistoryCmd extends MusicCommand {

    public HistoryCmd(Bot bot) {
        super(bot);
        this.name = "history";
        this.help = "Displays the music playback history";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.botPermissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
        
        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(OptionType.INTEGER, "count", "Number of history entries to display", false)
                .setMinValue(1)
                .setMaxValue(50));
        this.options = options;
    }

    @Override
    public void doCommand(CommandEvent event) {
        // Check if history is enabled
        if (!bot.getConfig().isHistoryEnabled()) {
            event.replyError("Music history feature is disabled in the configuration.");
            return;
        }
        
        int count = 10; // Default count
        String guildId = event.getGuild().getId(); // Current guild only
        
        String[] args = event.getArgs().split("\\s+");
        if (args.length > 0 && !args[0].isEmpty()) {
            try {
                count = Integer.parseInt(args[0]);
                if (count < 1) count = 1;
                if (count > 50) count = 50;
            } catch (NumberFormatException ex) {
                // Invalid number, use default
            }
        }
        
        // Display the history for the current guild only
        displayHistory(event, null, count, guildId);
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        // Check if history is enabled
        if (!bot.getConfig().isHistoryEnabled()) {
            event.reply("Music history feature is disabled in the configuration.").setEphemeral(true).queue();
            return;
        }
        
        int count = 10; // Default count
        String guildId = event.getGuild().getId(); // Current guild only
        
        if (event.getOption("count") != null) {
            count = (int) event.getOption("count").getAsLong();
        }
        
        // Display the history for the current guild only
        displayHistory(null, event, count, guildId);
    }
    
    /**
     * Display the history in an embed
     * @param event Command event (can be null if slash command)
     * @param slashEvent Slash command event (can be null if regular command)
     * @param count Number of entries to display
     * @param guildId Guild ID to filter by
     */
    private void displayHistory(CommandEvent event, SlashCommandEvent slashEvent, int count, String guildId) {
        List<MusicHistory.PlayRecord> fullHistory = bot.getMusicHistory().getHistory();
        
        // Filter by the current guild
        List<MusicHistory.PlayRecord> filteredHistory = fullHistory.stream()
            .filter(record -> guildId.equals(record.getGuildId()))
            .collect(Collectors.toList());
        
        // Limit the number of entries
        int size = Math.min(count, filteredHistory.size());
        List<MusicHistory.PlayRecord> history = size > 0 ? filteredHistory.subList(0, size) : filteredHistory;
        
        if (history.isEmpty()) {
            if (event != null) {
                event.replyWarning("No music history available for this server.");
            } else {
                slashEvent.reply("No music history available for this server.").setEphemeral(true).queue();
            }
            return;
        }
        
        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("Music Playback History");
        builder.setColor(new Color(114, 137, 218)); // Discord blue
        
        StringBuilder description = new StringBuilder();
        
        for (int i = 0; i < history.size(); i++) {
            MusicHistory.PlayRecord record = history.get(i);
            String title = record.getTitle();
            String artist = record.getArtist();
            String duration = record.getFormattedDuration();
            String requester = record.getRequesterName();
            String timestamp = record.getFormattedPlayedAt();
            
            description.append("`").append(i + 1).append(".` ");
            
            // Add different icons based on source type
            if (record.hasSpotifyData()) {
                description.append("🎧 ");
            } else if (record.hasRadioData()) {
                description.append("📻 ");
            } else if (record.hasYoutubeData()) {
                description.append("▶️ ");
            } else if (record.hasStreamData()) {
                description.append("🔴 ");
                if (record.isLiveStream()) {
                    description.append("**LIVE** - ");
                }
            } else if (record.hasLocalData()) {
                description.append("💿 ");
            } else if (record.getUrl() != null && record.getUrl().contains("soundcloud.com")) {
                description.append("☁️ "); // SoundCloud icon
            }
            
            if (!artist.isEmpty() && !artist.equals("Unknown artist")) {
                description.append("**").append(artist).append("** - ");
            }
            description.append("**").append(title).append("**");
            
            // For streams, add stream name if available and different from title
            if (record.hasStreamData() && !record.getStreamName().equals(title)) {
                description.append(" | ").append(record.getStreamName());
            }
            
            // Show duration with formatting for streams and radio
            if (record.hasStreamData() && record.isLiveStream()) {
                description.append(" `[STREAM]`");
            } else if (record.hasRadioData()) {
                description.append(" `[LIVE]`");
            } else {
                description.append(" `[").append(duration).append("]`");
            }
            
            description.append("\n");
            
            // Add requester and timestamp on the next line
            description.append("    ").append("Requested by: ").append(requester);
            description.append(" | ").append(timestamp);
            
            // Add additional metadata based on type
            if (record.hasSpotifyData()) {
                description.append(" | Album: ").append(record.getSpotifyAlbumName());
            } else if (record.hasRadioData()) {
                description.append(" | Source: ").append("Radio");
                // Don't display URLs in text, but we'll use them for the embed thumbnails
            } else if (record.hasStreamData() && !record.getStreamGenre().isEmpty()) {
                description.append(" | Genre: ").append(record.getStreamGenre());
            } else if (record.hasLocalData() && record.getLocalAlbum() != null && !record.getLocalAlbum().isEmpty()) {
                description.append(" | Album: ").append(record.getLocalAlbum());
                if (record.getLocalGenre() != null && !record.getLocalGenre().isEmpty()) {
                    description.append(" | Genre: ").append(record.getLocalGenre());
                }
            }
            
            description.append("\n\n");
        }
        
        builder.setDescription(description.toString());
        
        // Set thumbnail if available based on source type
        // Check if the most recent entry has an image we can use
        if (!history.isEmpty()) {
            MusicHistory.PlayRecord latestRecord = history.get(0);
            
            if (latestRecord.hasRadioData() && latestRecord.getRadioLogoUrl() != null && !latestRecord.getRadioLogoUrl().isEmpty()) {
                // For radio tracks, prioritize using the station logo as the thumbnail
                builder.setThumbnail(latestRecord.getRadioLogoUrl());
                
                // If we also have a song image, add it as a separate image
                if (latestRecord.getRadioSongImageUrl() != null && !latestRecord.getRadioSongImageUrl().isEmpty()) {
                    builder.setImage(latestRecord.getRadioSongImageUrl());
                }
            } else if (latestRecord.hasSpotifyData() && latestRecord.getSpotifyAlbumImageUrl() != null) {
                builder.setThumbnail(latestRecord.getSpotifyAlbumImageUrl());
            } else if (latestRecord.hasYoutubeData()) {
                builder.setThumbnail("https://img.youtube.com/vi/" + latestRecord.getYoutubeVideoId() + "/hqdefault.jpg");
            } else if (latestRecord.hasStreamData() && latestRecord.getStreamLogo() != null && !latestRecord.getStreamLogo().isEmpty()) {
                builder.setThumbnail(latestRecord.getStreamLogo());
            }
        }
        
        int totalRecords = filteredHistory.size();
        
        // Get the current guild name
        String serverName = "this server";
        Guild guild = bot.getJDA().getGuildById(guildId);
        if (guild != null) {
            serverName = guild.getName();
        }
        
        builder.setFooter("Showing " + history.size() + " of " + totalRecords + " entries from " + serverName);
        
        if (event != null) {
            event.reply(builder.build());
        } else {
            slashEvent.replyEmbeds(builder.build()).queue();
        }
    }
} 