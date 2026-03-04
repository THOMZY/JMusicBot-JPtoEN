/*
 * Copyright 2018 John Grosh <john.a.grosh@gmail.com>.
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
package dev.cosgy.jmusicbot.slashcommands.dj;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.PlayStatus;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import dev.cosgy.jmusicbot.slashcommands.DJCommand;
import dev.cosgy.jmusicbot.util.DiscordCompat;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class PauseCmd extends DJCommand {
    Logger log = LoggerFactory.getLogger("Pause");

    public PauseCmd(Bot bot) {
        super(bot);
        this.name = "pause";
        this.help = "Pauses the current track.";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = true;
    }

    @Override
    public void doCommand(CommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        if (handler.getPlayer().isPaused()) {
            event.replyWarning("The track is already paused. You can use `" + event.getClient().getPrefix() + " play` to resume it.");
            return;
        }
        handler.getPlayer().setPaused(true);
        log.info("Paused **" + handler.getPlayer().getPlayingTrack().getInfo().title + "** in " + event.getGuild().getName());
        event.replySuccess("Paused **" + handler.getPlayer().getPlayingTrack().getInfo().title + "**. Use `" + event.getClient().getPrefix() + " play` to resume it.");
        bot.getNowplayingHandler().onTrackUpdate(event.getGuild().getIdLong(), handler.getPlayer().getPlayingTrack(), handler);

        Bot.updatePlayStatus(event.getGuild(), DiscordCompat.getSelfMember(event.getGuild()), PlayStatus.PAUSED);
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        if (!checkDJPermission(event.getClient(), event)) {
            event.reply(event.getClient().getWarning() + "You do not have permission to execute this command.").queue();
            return;
        }
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        if (handler.getPlayer().isPaused()) {
            event.reply(event.getClient().getWarning() + "The track is already paused. You can use `" + event.getClient().getPrefix() + " play` to resume it.").queue();
            return;
        }
        handler.getPlayer().setPaused(true);
        log.info("Paused **" + handler.getPlayer().getPlayingTrack().getInfo().title + "** in " + event.getGuild().getName());
        event.reply(event.getClient().getSuccess() + "Paused **" + handler.getPlayer().getPlayingTrack().getInfo().title + "**. Use `" + event.getClient().getPrefix() + " play` to resume it.").queue();
        bot.getNowplayingHandler().onTrackUpdate(event.getGuild().getIdLong(), handler.getPlayer().getPlayingTrack(), handler);

        Bot.updatePlayStatus(event.getGuild(), DiscordCompat.getSelfMember(event.getGuild()), PlayStatus.PAUSED);
    }
}
