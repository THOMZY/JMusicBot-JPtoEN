package dev.cosgy.jmusicbot.slashcommands.general;

import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommand;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.EmbedBuilder;

import java.time.format.DateTimeFormatter;
import java.util.Objects;

public class ServerInfo extends SlashCommand {
    public ServerInfo(Bot bot) {
        this.name = "serverinfo";
        this.help = "Displays information about the server";
        this.guildOnly = true;
        this.category = new Category("General");
        this.aliases = bot.getConfig().getAliases(this.name);
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.replyEmbeds(buildServerInfoEmbed(event.getGuild()).build()).queue();
    }

    @Override
    public void execute(CommandEvent event) {
        event.getChannel().sendMessageEmbeds(buildServerInfoEmbed(event.getGuild()).build()).queue();
    }

    private EmbedBuilder buildServerInfoEmbed(Guild guild) {
        String guildName = guild.getName();
        String guildIconURL = guild.getIconUrl();
        String guildId = guild.getId();
        String guildOwner = Objects.requireNonNull(guild.getOwner()).getUser().getName() + "#" + guild.getOwner().getUser().getDiscriminator();
        String guildCreatedDate = guild.getTimeCreated().format(DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss"));

        String guildRolesCount = String.valueOf(guild.getRoles().size());
        String guildMember = String.valueOf(guild.getMembers().size());
        String guildCategoryCount = String.valueOf(guild.getCategories().size());
        String guildTextChannelCount = String.valueOf(guild.getTextChannels().size());
        String guildVoiceChannelCount = String.valueOf(guild.getVoiceChannels().size());
        String guildStageChannelCount = String.valueOf(guild.getStageChannels().size());
        String guildForumChannelCount = String.valueOf(guild.getForumChannels().size());
        String guildLocation = guild.getLocale().getNativeName();

        EmbedBuilder eb = new EmbedBuilder();
        eb.setAuthor("Information about server " + guildName, null, guildIconURL);
        eb.addField("Server ID", guildId, true);
        eb.addField("Primary Language of the Server", guildLocation, true);
        eb.addField("Server Owner", guildOwner, true);
        eb.addField("Member Count", guildMember, true);
        eb.addField("Role Count", guildRolesCount, true);
        eb.addField("Category Count", guildCategoryCount, true);
        eb.addField("Text Channel Count", guildTextChannelCount, true);
        eb.addField("Voice Channel Count", guildVoiceChannelCount, true);
        eb.addField("Stage Channel Count", guildStageChannelCount, true);
        eb.addField("Forum Channel Count", guildForumChannelCount, true);
        eb.setFooter("Server Creation Date: " + guildCreatedDate, null);
        return eb;
    }
}
