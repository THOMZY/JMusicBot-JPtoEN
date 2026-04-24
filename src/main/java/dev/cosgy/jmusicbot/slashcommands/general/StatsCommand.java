/*
 * Copyright 2025 THOMZY.
 */

package dev.cosgy.jmusicbot.slashcommands.general;

import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommand;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.doc.standard.CommandInfo;
import dev.cosgy.jmusicbot.framework.jdautilities.examples.doc.Author;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.settings.Settings;
import dev.cosgy.jmusicbot.util.DiscordCompat;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Guild;

import java.util.concurrent.TimeUnit;

@CommandInfo(
        name = "Stats",
        description = "Shows music bot statistics for this server"
)
@Author("THOMZY")
public class StatsCommand extends SlashCommand {
    private final Bot bot;

    public StatsCommand(Bot bot) {
        this.bot = bot;
        this.name = "stats";
        this.help = "Shows music bot statistics for this server";
        this.guildOnly = true;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        EmbedBuilder eb = createStatsEmbed(event.getGuild());
        event.replyEmbeds(eb.build()).queue();
    }

    @Override
    protected void execute(CommandEvent event) {
        EmbedBuilder eb = createStatsEmbed(event.getGuild());
        event.reply(eb.build());
    }
    
    /**
     * Creates the stats embed with server statistics
     * @param guild The guild to get stats for
     * @return The built embed with stats information
     */
    private EmbedBuilder createStatsEmbed(Guild guild) {
        Settings settings = bot.getSettingsManager().getSettings(guild);
        
        // Get stats
        int songsPlayed = settings.getSongsPlayed();
        long playTimeMillis = settings.getPlayTimeMillis();
        
        // Format time
        long hours = TimeUnit.MILLISECONDS.toHours(playTimeMillis);
        long minutes = TimeUnit.MILLISECONDS.toMinutes(playTimeMillis) % 60;
        long seconds = TimeUnit.MILLISECONDS.toSeconds(playTimeMillis) % 60;
        String timeStr = String.format("%d hours, %d minutes and %d seconds", hours, minutes, seconds);
        
        // Create a more stylish embed
        EmbedBuilder eb = new EmbedBuilder();
        eb.setColor(DiscordCompat.getMemberColor(DiscordCompat.getSelfMember(guild)));
        eb.setTitle("📊 Music Statistics for :");
        eb.setDescription("`" + guild.getName() + "`" + "\n\n▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬");
        
        // Add thumbnail if available
        if (guild.getIconUrl() != null) {
            eb.setThumbnail(guild.getIconUrl());
        }

        // Add fields in vertical layout (one per line) with emojis for better styling
        eb.addField("🎵 Total Songs Played :","> " + "```" + songsPlayed + "```", false);
        eb.addField("⏱️ Total Play Time :", "> " + "```" + timeStr + "```", false);
        
        // Add timestamp for freshness
        eb.setTimestamp(java.time.Instant.now());
        
        return eb;
    }
} 