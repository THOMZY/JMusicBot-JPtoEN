/*
 * Copyright 2018-2020 Cosgy Dev
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package dev.cosgy.jmusicbot.slashcommands.general;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.commons.JDAUtilitiesInfo;
import com.jagrosh.jdautilities.doc.standard.CommandInfo;
import com.jagrosh.jdautilities.examples.doc.Author;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDAInfo;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.ApplicationInfo;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.util.Objects;

/**
 * @author Cosgy Dev
 */
@CommandInfo(
        name = "About",
        description = "Displays information about the bot."
)
@Author("Cosgy Dev")
public class AboutCommand extends SlashCommand {
    private final Color color;
    private final String description;
    private final Permission[] perms;
    private final String[] features;
    private boolean IS_AUTHOR = true;
    private String REPLACEMENT_ICON = "+";
    private String oauthLink;

    public AboutCommand(Color color, String description, String[] features, Permission... perms) {
        this.color = color;
        this.description = description;
        this.features = features;
        this.name = "about";
        this.help = "Displays information about the bot.";
        this.aliases = new String[]{"botinfo", "info"};
        this.guildOnly = false;
        this.perms = perms;
        this.botPermissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
    }

    public void setIsAuthor(boolean value) {
        this.IS_AUTHOR = value;
    }

    public void setReplacementCharacter(String value) {
        this.REPLACEMENT_ICON = value;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        if (oauthLink == null) {
            try {
                ApplicationInfo info = event.getJDA().retrieveApplicationInfo().complete();
                oauthLink = info.isBotPublic() ? info.getInviteUrl(0L, perms) : "";
            } catch (Exception e) {
                Logger log = LoggerFactory.getLogger("OAuth2");
                log.error("Failed to generate invite link ", e);
                oauthLink = "";
            }
        }
        EmbedBuilder builder = new EmbedBuilder();
        builder.setColor(event.getGuild() == null ? color : event.getGuild().getSelfMember().getColor());
        builder.setAuthor("" + event.getJDA().getSelfUser().getName() + " Information", null, event.getJDA().getSelfUser().getAvatarUrl());
        String CosgyOwner = "Operated and developed by THOMZY.";
        String author = event.getJDA().getUserById(event.getClient().getOwnerId()) == null ? "<@" + event.getClient().getOwnerId() + ">"
                : Objects.requireNonNull(event.getJDA().getUserById(event.getClient().getOwnerId())).getName();

        StringBuilder descr = new StringBuilder().append("Hello! **").append(event.getJDA().getSelfUser().getName()).append("** here. ")
                .append(description).append(" uses [" + JDAUtilitiesInfo.AUTHOR + "](https://github.com/JDA-Applications)'s [Commands Extension](" + JDAUtilitiesInfo.GITHUB + ") (")
                .append(JDAUtilitiesInfo.VERSION).append(") and the [JDA library](https://github.com/DV8FromTheWorld/JDA) (")
                .append(JDAInfo.VERSION).append("), and is owned by ").append((IS_AUTHOR ? CosgyOwner : author + ". "))
                .append("\nRelated questions about ").append(event.getJDA().getSelfUser().getName()).append(" can be directed to the [GitHub](https://github.com/THOMZY/JMusicBot-JPtoEN).")
                .append("\nTo check the bot's usage, type `").append("/help")
                .append("`.").append("\n\nFeatures: ```css");
        for (String feature : features)
            descr.append("\n").append(event.getClient().getSuccess().startsWith("<") ? REPLACEMENT_ICON : event.getClient().getSuccess()).append(" ").append(feature);
        descr.append(" ```");
        builder.setDescription(descr);

        if (event.getJDA().getShardInfo().getShardTotal() == 1) {
            builder.addField("Status", event.getJDA().getGuilds().size() + " servers\n1 shard", true);
            builder.addField("Users", event.getJDA().getUsers().size() + " unique\n" + event.getJDA().getGuilds().stream().mapToInt(g -> g.getMembers().size()).sum() + " total", true);
            builder.addField("Channels", event.getJDA().getTextChannels().size() + " text\n" + event.getJDA().getVoiceChannels().size() + " voice", true);
        } else {
            builder.addField("Status", (event.getClient()).getTotalGuilds() + " servers\nShard " + (event.getJDA().getShardInfo().getShardId() + 1)
                    + "/" + event.getJDA().getShardInfo().getShardTotal(), true);
            builder.addField("", event.getJDA().getUsers().size() + " users in shard\n" + event.getJDA().getGuilds().size() + " servers", true);
            builder.addField("", event.getJDA().getTextChannels().size() + " text channels\n" + event.getJDA().getVoiceChannels().size() + " voice channels", true);
        }
        builder.setFooter("Time when the bot was last restarted", "https://cdn-icons-png.flaticon.com/512/3357/3357329.png");
        builder.setTimestamp(event.getClient().getStartTime());
        event.replyEmbeds(builder.build()).queue();
    }

    @Override
    protected void execute(CommandEvent event) {
        if (oauthLink == null) {
            try {
                ApplicationInfo info = event.getJDA().retrieveApplicationInfo().complete();
                oauthLink = info.isBotPublic() ? info.getInviteUrl(0L, perms) : "";
            } catch (Exception e) {
                Logger log = LoggerFactory.getLogger("OAuth2");
                log.error("Failed to generate invite link ", e);
                oauthLink = "";
            }
        }
        EmbedBuilder builder = new EmbedBuilder();
        builder.setColor(event.isFromType(ChannelType.TEXT) ? event.getGuild().getSelfMember().getColor() : color);
        builder.setAuthor("" + event.getSelfUser().getName() + " Information", null, event.getSelfUser().getAvatarUrl());
        String CosgyOwner = "Operated and developed by THOMZY.";
        String author = event.getJDA().getUserById(event.getClient().getOwnerId()) == null ? "<@" + event.getClient().getOwnerId() + ">"
                : Objects.requireNonNull(event.getJDA().getUserById(event.getClient().getOwnerId())).getName();

        StringBuilder descr = new StringBuilder().append("Hello! **").append(event.getSelfUser().getName()).append("** here. ")
                .append(description).append(" uses [" + JDAUtilitiesInfo.AUTHOR + "]'s [Commands Extension](" + JDAUtilitiesInfo.GITHUB + ") (")
                .append(JDAUtilitiesInfo.VERSION).append(") and the [JDA library](https://github.com/DV8FromTheWorld/JDA) (")
                .append(JDAInfo.VERSION).append("), and is ").append((IS_AUTHOR ? CosgyOwner : author + " owns it."))
                .append(event.getSelfUser().getName()).append(" related questions can be directed to the [Cosgy Dev Official Channel](https://discord.gg/RBpkHxf).")
                .append("\nTo check the bot's usage, type `").append(event.getClient().getTextualPrefix()).append(event.getClient().getHelpWord())
                .append("`.").append("\n\nFeatures: ```css");
        for (String feature : features)
            descr.append("\n").append(event.getClient().getSuccess().startsWith("<") ? REPLACEMENT_ICON : event.getClient().getSuccess()).append(" ").append(feature);
        descr.append(" ```");
        builder.setDescription(descr);

        if (event.getJDA().getShardInfo().getShardTotal() == 1) {
            builder.addField("Status", event.getJDA().getGuilds().size() + " servers\n1 shard", true);
            builder.addField("Users", event.getJDA().getUsers().size() + " unique\n" + event.getJDA().getGuilds().stream().mapToInt(g -> g.getMembers().size()).sum() + " total", true);
            builder.addField("Channels", event.getJDA().getTextChannels().size() + " text\n" + event.getJDA().getVoiceChannels().size() + " voice", true);
        } else {
            builder.addField("Status", (event.getClient()).getTotalGuilds() + " servers\nShard " + (event.getJDA().getShardInfo().getShardId() + 1)
                    + "/" + event.getJDA().getShardInfo().getShardTotal(), true);
            builder.addField("", event.getJDA().getUsers().size() + " users in shard\n" + event.getJDA().getGuilds().size() + " servers", true);
            builder.addField("", event.getJDA().getTextChannels().size() + " text channels\n" + event.getJDA().getVoiceChannels().size() + " voice channels", true);
        }
        builder.setFooter("Time when the bot was last restarted", "https://www.cosgy.dev/wp-content/uploads/2020/03/restart.jpg");
        builder.setTimestamp(event.getClient().getStartTime());
        event.reply(builder.build());
    }

}
