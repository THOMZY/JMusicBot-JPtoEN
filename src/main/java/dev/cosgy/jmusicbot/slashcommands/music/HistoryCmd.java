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

import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.MusicHistory;
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
        
        count = parseCountArg(event.getArgs(), count);
        
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
        List<MusicHistory.PlayRecord> filteredHistory = filterGuildHistory(fullHistory, guildId);
        List<MusicHistory.PlayRecord> history = limitHistory(filteredHistory, count);

        if (history.isEmpty()) {
            replyNoHistory(event, slashEvent);
            return;
        }

        EmbedBuilder builder = new EmbedBuilder();
        builder.setTitle("Music Playback History");
        builder.setColor(new Color(114, 137, 218)); // Discord blue

        builder.setDescription(buildHistoryDescription(history));
        applyThumbnail(builder, history);
        builder.setFooter(buildFooterText(history.size(), filteredHistory.size(), guildId));

        replyHistory(event, slashEvent, builder);
    }

    private int parseCountArg(String args, int fallback) {
        String[] parts = args.split("\\s+");
        if (parts.length == 0 || parts[0].isEmpty()) {
            return fallback;
        }
        try {
            int parsed = Integer.parseInt(parts[0]);
            return Math.max(1, Math.min(50, parsed));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private List<MusicHistory.PlayRecord> filterGuildHistory(List<MusicHistory.PlayRecord> history, String guildId) {
        return history.stream()
                .filter(record -> guildId.equals(record.getGuildId()))
                .collect(Collectors.toList());
    }

    private List<MusicHistory.PlayRecord> limitHistory(List<MusicHistory.PlayRecord> history, int count) {
        int size = Math.min(count, history.size());
        return size > 0 ? history.subList(0, size) : history;
    }

    private void replyNoHistory(CommandEvent event, SlashCommandEvent slashEvent) {
        if (event != null) {
            event.replyWarning("No music history available for this server.");
            return;
        }
        slashEvent.reply("No music history available for this server.").setEphemeral(true).queue();
    }

    private String buildHistoryDescription(List<MusicHistory.PlayRecord> history) {
        StringBuilder description = new StringBuilder();
        for (int i = 0; i < history.size(); i++) {
            appendRecord(description, history.get(i), i + 1);
        }
        return description.toString();
    }

    private void appendRecord(StringBuilder description, MusicHistory.PlayRecord record, int index) {
        String title = record.getTitle();
        String artist = record.getArtist();

        description.append("`").append(index).append(".` ");
        description.append(getSourceIcon(record));
        if (record.hasStreamData() && record.isLiveStream()) {
            description.append("**LIVE** - ");
        }

        if (!artist.isEmpty() && !"Unknown artist".equals(artist)) {
            description.append("**").append(artist).append("** - ");
        }
        description.append("**").append(title).append("**");

        if (record.hasStreamData() && !record.getStreamName().equals(title)) {
            description.append(" | ").append(record.getStreamName());
        }

        description.append(formatDurationTag(record));
        description.append("\n    Requested by: ").append(record.getRequesterName())
                .append(" | ").append(record.getFormattedPlayedAt());

        appendMetadata(description, record);
        description.append("\n\n");
    }

    private String getSourceIcon(MusicHistory.PlayRecord record) {
        if (record.hasSpotifyData()) {
            return "🎧 ";
        }
        if (record.hasRadioData()) {
            return "📻 ";
        }
        if (record.hasYoutubeData()) {
            return "▶️ ";
        }
        if (record.hasStreamData()) {
            return "🔴 ";
        }
        if (record.hasLocalData()) {
            return "💿 ";
        }
        if (record.getUrl() != null && record.getUrl().contains("soundcloud.com")) {
            return "☁️ ";
        }
        return "";
    }

    private String formatDurationTag(MusicHistory.PlayRecord record) {
        if (record.hasStreamData() && record.isLiveStream()) {
            return " `[STREAM]`";
        }
        if (record.hasRadioData()) {
            return " `[LIVE]`";
        }
        return " `[" + record.getFormattedDuration() + "]`";
    }

    private void appendMetadata(StringBuilder description, MusicHistory.PlayRecord record) {
        if (record.hasSpotifyData()) {
            description.append(" | Album: ").append(record.getSpotifyAlbumName());
            return;
        }
        if (record.hasRadioData()) {
            description.append(" | Source: Radio");
            return;
        }
        if (record.hasStreamData() && !record.getStreamGenre().isEmpty()) {
            description.append(" | Genre: ").append(record.getStreamGenre());
            return;
        }
        if (record.hasLocalData() && record.getLocalAlbum() != null && !record.getLocalAlbum().isEmpty()) {
            description.append(" | Album: ").append(record.getLocalAlbum());
            if (record.getLocalGenre() != null && !record.getLocalGenre().isEmpty()) {
                description.append(" | Genre: ").append(record.getLocalGenre());
            }
        }
    }

    private void applyThumbnail(EmbedBuilder builder, List<MusicHistory.PlayRecord> history) {
        MusicHistory.PlayRecord latestRecord = history.get(0);

        if (latestRecord.hasRadioData() && latestRecord.getRadioLogoUrl() != null && !latestRecord.getRadioLogoUrl().isEmpty()) {
            builder.setThumbnail(latestRecord.getRadioLogoUrl());
            if (latestRecord.getRadioSongImageUrl() != null && !latestRecord.getRadioSongImageUrl().isEmpty()) {
                builder.setImage(latestRecord.getRadioSongImageUrl());
            }
            return;
        }
        if (latestRecord.hasSpotifyData() && latestRecord.getSpotifyAlbumImageUrl() != null) {
            builder.setThumbnail(latestRecord.getSpotifyAlbumImageUrl());
            return;
        }
        if (latestRecord.hasYoutubeData()) {
            builder.setThumbnail("https://img.youtube.com/vi/" + latestRecord.getYoutubeVideoId() + "/hqdefault.jpg");
            return;
        }
        if (latestRecord.hasStreamData() && latestRecord.getStreamLogo() != null && !latestRecord.getStreamLogo().isEmpty()) {
            builder.setThumbnail(latestRecord.getStreamLogo());
        }
    }

    private String buildFooterText(int shownCount, int totalRecords, String guildId) {
        String serverName = "this server";
        Guild guild = bot.getJDA().getGuildById(guildId);
        if (guild != null) {
            serverName = guild.getName();
        }
        return "Showing " + shownCount + " of " + totalRecords + " entries from " + serverName;
    }

    private void replyHistory(CommandEvent event, SlashCommandEvent slashEvent, EmbedBuilder builder) {
        if (event != null) {
            event.reply(builder.build());
            return;
        }
        slashEvent.replyEmbeds(builder.build()).queue();
    }
} 