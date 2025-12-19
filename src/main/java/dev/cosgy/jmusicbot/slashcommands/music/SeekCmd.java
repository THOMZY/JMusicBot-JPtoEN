/*
 * Copyright 2020 John Grosh <john.a.grosh@gmail.com>.
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
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.cosgy.jmusicbot.slashcommands.DJCommand;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import dev.cosgy.jmusicbot.util.TimeUtil;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Whew., Inc.
 */
public class SeekCmd extends MusicCommand {
    private final static Logger LOG = LoggerFactory.getLogger("Seeking");

    public SeekCmd(Bot bot) {
        super(bot);
        this.name = "seek";
        this.help = "Changes the playback position of the currently playing track.";
        this.arguments = "[+ | -] <HH:MM:SS | MM:SS | SS>|<0h0m0s | 0m0s | 0s>";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = true;

        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(OptionType.STRING, "time", "Format: `1:02:23` `+1:10` `-90`, `1h10m`, `+90s`", true));
        this.options = options;
    }

    @Override
    public void doCommand(CommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        AudioTrack playingTrack = handler.getPlayer().getPlayingTrack();
        if (!playingTrack.isSeekable()) {
            event.replyError("This track cannot be seeked.");
            return;
        }

        if (!DJCommand.checkDJPermission(event)) {
            RequestMetadata rm = playingTrack.getUserData(RequestMetadata.class);
            if (rm == null || rm.getOwner() != event.getAuthor().getIdLong()) {
                event.replyError("You cannot seek because you did not add **" + playingTrack.getInfo().title + "**!");
                return;
            }
        }

        String args = event.getArgs();
        TimeUtil.SeekTime seekTime = TimeUtil.parseTime(args);
        if (seekTime == null) {
            event.replyError("Invalid seek time! Expected format: " + arguments + "\nExamples: `1:02:23` `+1:10` `-90`, `1h10m`, `+90s`");
            return;
        }

        long currentPosition = playingTrack.getPosition();
        long trackDuration = playingTrack.getDuration();

        long seekMilliseconds = seekTime.relative ? currentPosition + seekTime.milliseconds : seekTime.milliseconds;
        if (seekMilliseconds > trackDuration) {
            event.replyError("The current track length is `" + TimeUtil.formatTime(trackDuration) + "` and cannot be seeked to `" + TimeUtil.formatTime(seekMilliseconds) + "`!");
            return;
        }

        try {
            playingTrack.setPosition(seekMilliseconds);
        } catch (Exception e) {
            event.replyError("An error occurred while seeking: " + e.getMessage());
            LOG.warn("Failed to seek track " + playingTrack.getIdentifier(), e);
            return;
        }
        event.replySuccess("Seeked to `" + TimeUtil.formatTime(playingTrack.getPosition()) + "/" + TimeUtil.formatTime(playingTrack.getDuration()) + "`!");
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        AudioTrack playingTrack = handler.getPlayer().getPlayingTrack();
        if (!playingTrack.isSeekable()) {
            event.reply("This track cannot be seeked.").queue();
            return;
        }

        if (!DJCommand.checkDJPermission(event.getClient(), event)) {
            RequestMetadata rm = playingTrack.getUserData(RequestMetadata.class);
            if (rm == null || rm.getOwner() != event.getUser().getIdLong()) {
                event.reply("You cannot seek because you did not add **" + playingTrack.getInfo().title + "**!").queue();
                return;
            }
        }

        String args = event.getOption("time").getAsString();
        TimeUtil.SeekTime seekTime = TimeUtil.parseTime(args);
        if (seekTime == null) {
            event.reply("Invalid seek time! Expected format: " + arguments + "\nExamples: `1:02:23` `+1:10` `-90`, `1h10m`, `+90s`").queue();
            return;
        }

        long currentPosition = playingTrack.getPosition();
        long trackDuration = playingTrack.getDuration();

        long seekMilliseconds = seekTime.relative ? currentPosition + seekTime.milliseconds : seekTime.milliseconds;
        if (seekMilliseconds > trackDuration) {
            event.reply("The current track length is `" + TimeUtil.formatTime(trackDuration) + "` and cannot be seeked to `" + TimeUtil.formatTime(seekMilliseconds) + "`!").queue();
            return;
        }

        try {
            playingTrack.setPosition(seekMilliseconds);
        } catch (Exception e) {
            event.reply("An error occurred while seeking: " + e.getMessage()).queue();
            LOG.warn("Failed to seek track {}", playingTrack.getIdentifier(), e);
            return;
        }
        event.reply("Seeked to `" + TimeUtil.formatTime(playingTrack.getPosition()) + "/" + TimeUtil.formatTime(playingTrack.getDuration()) + "`!").queue();
    }

}