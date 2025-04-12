/*
 * Copyright 2018 John Grosh (jagrosh).
 * Copyright 2025 THOMZY.
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
import com.jagrosh.jmusicbot.audio.AudioHandler;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.EmbedBuilder;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Consumer;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.JDA;
import com.jagrosh.jmusicbot.JMusicBot;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.audio.RequestMetadata;

/**
 * Command that displays the currently playing track
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class NowplayingCmd extends MusicCommand {

    public NowplayingCmd(Bot bot) {
        super(bot);
        this.name = "nowplaying";
        this.help = "Displays the currently playing track";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.botPermissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
    }

    @Override
    public void doCommand(CommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        if (handler == null || handler.getPlayer().getPlayingTrack() == null) {
            event.reply(handler != null ? handler.getNoMusicPlaying(event.getJDA()) : MessageCreateData.fromContent("No music is currently playing."));
            bot.getNowplayingHandler().clearLastNPMessage(event.getGuild());
            return;
        }

        // Get nowplaying information from AudioHandler
        MessageCreateData np;
        try {
            np = handler.getNowPlaying(event.getJDA());
        } catch (Exception e) {
            event.reply("Error getting now playing information: " + e.getMessage());
            return;
        }
        
        if (np == null) {
            event.reply(handler.getNoMusicPlaying(event.getJDA()));
            bot.getNowplayingHandler().clearLastNPMessage(event.getGuild());
        } else {
            event.reply(np, bot.getNowplayingHandler()::setLastNPMessage);
        }
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        if (handler == null || handler.getPlayer().getPlayingTrack() == null) {
            event.reply("No music is currently playing.").queue();
            bot.getNowplayingHandler().clearLastNPMessage(event.getGuild());
            return;
        }

        event.reply("Displaying the currently playing track...").queue(h -> h.deleteOriginal().queue());

        // Get nowplaying information from AudioHandler
        MessageCreateData np;
        try {
            np = handler.getNowPlaying(event.getJDA());
        } catch (Exception e) {
            event.reply("Error getting now playing information: " + e.getMessage()).queue();
            return;
        }
        
        if (np == null) {
            event.getChannel().sendMessage(handler.getNoMusicPlaying(event.getJDA())).queue();
            bot.getNowplayingHandler().clearLastNPMessage(event.getGuild());
        } else {
            event.getChannel().sendMessage(np).queue(m -> bot.getNowplayingHandler().setLastNPMessage(m));
        }
    }
}
