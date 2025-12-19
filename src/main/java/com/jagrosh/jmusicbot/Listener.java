/*
 * Copyright 2016 John Grosh (jagrosh).
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
package com.jagrosh.jmusicbot;

import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.events.guild.voice.GuildVoiceUpdateEvent;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.events.message.MessageDeleteEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.events.session.ShutdownEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class Listener extends ListenerAdapter {
    private final Bot bot;

    public Listener(Bot bot) {
        this.bot = bot;
    }

    @Override
    public void onReady(ReadyEvent event) {
        if (event.getJDA().getGuilds().isEmpty()) {
            Logger log = LoggerFactory.getLogger("MusicBot");
            log.warn("This bot is not in the group! Use the link below to add the bot to your group.");
            log.warn(event.getJDA().getInviteUrl(JMusicBot.RECOMMENDED_PERMS));
        }
        event.getJDA().getGuilds().forEach((guild) ->
        {
            try {
                String defpl = Objects.requireNonNull(bot.getSettingsManager().getSettings(guild)).getDefaultPlaylist();
                VoiceChannel vc = Objects.requireNonNull(bot.getSettingsManager().getSettings(guild)).getVoiceChannel(guild);
                if (defpl != null && vc != null && bot.getPlayerManager().setUpHandler(guild).playFromDefault()) {
                    guild.getAudioManager().openAudioConnection(vc);
                }
            } catch (Exception ignore) {
            }
        });
        if (bot.getConfig().useUpdateAlerts()) {
            bot.getThreadpool().scheduleWithFixedDelay(() ->
            {
                User owner = bot.getJDA().getUserById(bot.getConfig().getOwnerId());
                if (owner != null) {
                    String currentVersion = OtherUtil.getCurrentVersion();
                    String latestVersion = OtherUtil.getLatestVersion();
                    if (latestVersion != null && !currentVersion.equalsIgnoreCase(latestVersion) && JMusicBot.CHECK_UPDATE) {
                        String msg = String.format(OtherUtil.NEW_VERSION_AVAILABLE, currentVersion, latestVersion);
                        owner.openPrivateChannel().queue(pc -> pc.sendMessage(msg).queue());
                    }
                }
            }, 0, 24, TimeUnit.HOURS);
        }
    }

    @Override
    public void onMessageDelete(@NotNull MessageDeleteEvent event) {
        bot.getNowplayingHandler().onMessageDelete(event.getGuild(), event.getMessageIdLong());
    }

    @Override
    public void onGuildVoiceUpdate(@NotNull GuildVoiceUpdateEvent event) {
        Logger log = LoggerFactory.getLogger("onGuildVoiceUpdate");

        Member self = event.getGuild().getSelfMember();
        if (event.getMember().equals(self)
                && event.getChannelLeft() != null
                && event.getChannelJoined() == null) {
            bot.getPlayerManager().clearYtDlpCache();
        }

        bot.getAloneInVoiceHandler().onVoiceUpdate(event);

        // Event on exit
        log.debug("onGuildVoiceLeave Start");
        onGuildVoiceLeave(event);
        log.debug("onGuildVoiceLeave End");
        // Event ends on exit

        // Events when participating
        log.debug("onGuildVoiceJoin Start");
        onGuildVoiceJoin(event);
        log.debug("onGuildVoiceJoin End");
        // Event ends when participating
    }


    public void onGuildVoiceLeave(@NotNull GuildVoiceUpdateEvent event) {
        if (event.getChannelLeft() == null) return;

        //NUP = false -> NUS = false -> return
        //NUP = false -> NUS = true -> GO
        //NUP = true -> GO
        if (!bot.getConfig().getNoUserPause())
            if (!bot.getConfig().getNoUserStop()) return;
        Member botMember = event.getGuild().getSelfMember();
        // Is there one person on the voice chat and a bot on the voice chat?
        if (event.getChannelLeft().getMembers().size() == 1 && event.getChannelLeft().getMembers().contains(botMember)) {
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();

            // If nouserpause in config.txt is true
            if (bot.getConfig().getNoUserPause()) {
                //⏸
                // pause the player
                Objects.requireNonNull(handler).getPlayer().setPaused(true);

                Bot.updatePlayStatus(event.getGuild(), event.getGuild().getSelfMember(), PlayStatus.PAUSED);

                return;
            }

            if (bot.getConfig().getNoUserStop()) {
                //⏹
                if (bot.getConfig().getAutoStopQueueSave()) {
                    bot.getCacheLoader().Save(event.getGuild().toString(), handler.getQueue());
                }
                Objects.requireNonNull(handler).stopAndClear();
                event.getGuild().getAudioManager().closeAudioConnection();
            }
        }
    }


    public void onGuildVoiceJoin(@NotNull GuildVoiceUpdateEvent event) {
        if (event.getChannelJoined() == null) return;

        Logger log = LoggerFactory.getLogger("onGuildVoiceJoin");
        if (!bot.getConfig().getResumeJoined()) return;
        //▶
        Member botMember = event.getGuild().getSelfMember();
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();

        log.debug("Playback resume check {}", ((event.getChannelJoined().getMembers().size() > 1 && event.getChannelJoined().getMembers().contains(botMember)) && Objects.requireNonNull(handler).getPlayer().isPaused()));
        //The number of people in the voice chat is one or more, the bot is in the voice chat, or playback is paused.
        if ((event.getChannelJoined().getMembers().size() > 1 && event.getChannelJoined().getMembers().contains(botMember)) && Objects.requireNonNull(handler).getPlayer().isPaused()) {
            handler.getPlayer().setPaused(false);
            log.debug("Playback resumed.");

            Bot.updatePlayStatus(event.getGuild(), event.getGuild().getSelfMember(), PlayStatus.PLAYING);
        }
    }

    @Override
    public void onButtonInteraction(@NotNull ButtonInteractionEvent event) {

    }

    @Override
    public void onShutdown(@NotNull ShutdownEvent event) {
        bot.shutdown();
    }
}
