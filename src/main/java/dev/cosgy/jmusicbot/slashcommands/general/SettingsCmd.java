/*
 * Copyright 2018 John Grosh (jagrosh).
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
package dev.cosgy.jmusicbot.slashcommands.general;

import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommand;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import dev.cosgy.jmusicbot.settings.RepeatMode;
import dev.cosgy.jmusicbot.util.DiscordCompat;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;


/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class SettingsCmd extends SlashCommand {
    private final static String EMOJI = "\uD83C\uDFA7"; // 🎧

    public SettingsCmd(Bot bot) {
        this.name = "settings";
        this.help = "Displays the bot's settings";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.guildOnly = true;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        Settings s = event.getClient().getSettingsFor(event.getGuild());
        MessageCreateBuilder builder = new MessageCreateBuilder()
                .addContent(EMOJI + " **")
                .addContent(FormatUtil.filter(event.getJDA().getSelfUser().getName()))
                .addContent("**'s settings:");
        TextChannel tChan = s.getTextChannel(event.getGuild());
        VoiceChannel vChan = s.getVoiceChannel(event.getGuild());
        Role role = s.getRole(event.getGuild());
        EmbedBuilder ebuilder = new EmbedBuilder()
                .setDescription("Command channel: " + (tChan == null ? "None" : "**#" + tChan.getName() + "**")
                        + "\nDedicated voice channel: " + (vChan == null ? "None" : "**" + vChan.getAsMention() + "**")
                        + "\nDJ role: " + (role == null ? "Not set" : "**" + role.getName() + "**")
                        + "\nRepeat: **" + (s.getRepeatMode() == RepeatMode.ALL ? "Enabled (repeat all songs)" : (s.getRepeatMode() == RepeatMode.SINGLE ? "Enabled (repeat one song)" : "Disabled")) + "**"
                        + "\nVolume: **" + (s.getVolume()) + "**"
                        + "\nQueue addition mode: **" + (s.isForceToEndQue() ? "Normal mode" : "Fair mode") + "**"
                        + "\nDefault playlist: " + (s.getDefaultPlaylist() == null ? "None" : "**" + s.getDefaultPlaylist() + "**")
                )
                .setFooter(String.format(
                                "%s servers | %s voice channels connected",
                                event.getJDA().getGuilds().size(),
                                                                event.getJDA().getGuilds().stream().filter(g -> {
                                                                        var selfMember = DiscordCompat.getSelfMember(g);
                                                                        return selfMember != null && selfMember.getVoiceState() != null && selfMember.getVoiceState().inAudioChannel();
                                                                }).count()),
                        null);
        event.reply(builder.addEmbeds(ebuilder.build()).build()).queue();
    }

    @Override
    protected void execute(CommandEvent event) {
        Settings s = event.getClient().getSettingsFor(event.getGuild());
        MessageCreateBuilder builder = new MessageCreateBuilder()
                .addContent(EMOJI + " **")
                .addContent(FormatUtil.filter(event.getSelfUser().getName()))
                .addContent("**'s settings:");
        TextChannel tChan = s.getTextChannel(event.getGuild());
        VoiceChannel vChan = s.getVoiceChannel(event.getGuild());
        Role role = s.getRole(event.getGuild());
        EmbedBuilder ebuilder = new EmbedBuilder()
                .setColor(DiscordCompat.getMemberColor(DiscordCompat.getSelfMember(event.getGuild())))
                .setDescription("Command channel: " + (tChan == null ? "None" : "**#" + tChan.getName() + "**")
                        + "\nDedicated voice channel: " + (vChan == null ? "None" : "**" + vChan.getName() + "**")
                        + "\nDJ role: " + (role == null ? "Not set" : "**" + role.getName() + "**")
                        + "\nRepeat: **" + (s.getRepeatMode() == RepeatMode.ALL ? "Enabled (repeat all songs)" : (s.getRepeatMode() == RepeatMode.SINGLE ? "Enabled (repeat one song)" : "Disabled")) + "**"
                        + "\nQueue addition mode: **" + (s.isForceToEndQue() ? "Normal mode" : "Fair mode") + "**"
                        + "\nDefault playlist: " + (s.getDefaultPlaylist() == null ? "None" : "**" + s.getDefaultPlaylist() + "**")
                )
                .setFooter(String.format(
                                "%s servers | %s voice channels connected",
                                event.getJDA().getGuilds().size(),
                                                                event.getJDA().getGuilds().stream().filter(g -> {
                                                                        var selfMember = DiscordCompat.getSelfMember(g);
                                                                        return selfMember != null && selfMember.getVoiceState() != null && selfMember.getVoiceState().inAudioChannel();
                                                                }).count()),
                        null);
        event.getChannel().sendMessage(builder.addEmbeds(ebuilder.build()).build()).queue();
    }
}
