/*
 * Copyright 2018 John Grosh (jagrosh).
 * Edit 2025 THOMZY
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

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.YouTubeChapterManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.utils.messages.MessageEditData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.function.Consumer;
import java.util.concurrent.TimeUnit;

/**
 * Command that displays the currently playing track
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class NowplayingCmd extends MusicCommand {
    private static final Logger log = LoggerFactory.getLogger(NowplayingCmd.class);
    private static final int CHAPTER_REFRESH_MAX_ATTEMPTS = 20;
    private static final long CHAPTER_REFRESH_DELAY_SECONDS = 1;

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
            AudioTrack initialTrack = handler.getPlayer().getPlayingTrack();
            event.reply(np, message -> {
                bot.getNowplayingHandler().setLastNPMessage(message);
                scheduleChapterRefreshForMessage(event.getGuild(), handler, initialTrack, message);
            });
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

        MessageCreateData np;
        try {
            np = handler.getNowPlaying(event.getJDA());
        } catch (Exception e) {
            event.reply("Error getting now playing information: " + e.getMessage()).queue();
            return;
        }

        if (np == null) {
            event.reply(handler.getNoMusicPlaying(event.getJDA())).queue();
            bot.getNowplayingHandler().clearLastNPMessage(event.getGuild());
            return;
        }

        AudioTrack initialTrack = handler.getPlayer().getPlayingTrack();
        event.reply(np).queue(hook -> {
            hook.retrieveOriginal().queue(bot.getNowplayingHandler()::setLastNPMessage, t -> {
            });
            scheduleChapterRefreshForHook(event.getGuild(), handler, initialTrack, hook);
        });
    }

    private void scheduleChapterRefreshForMessage(Guild guild, AudioHandler handler, AudioTrack initialTrack, Message message) {
        scheduleChapterRefresh(guild, handler, initialTrack, editData ->
                message.editMessage(editData).queue(bot.getNowplayingHandler()::setLastNPMessage, t -> {
                }));
    }

    private void scheduleChapterRefreshForHook(Guild guild, AudioHandler handler, AudioTrack initialTrack, InteractionHook hook) {
        scheduleChapterRefresh(guild, handler, initialTrack, editData -> hook.editOriginal(editData).queue());
    }

    private void scheduleChapterRefresh(Guild guild, AudioHandler handler, AudioTrack initialTrack, Consumer<MessageEditData> editor) {
        if (handler == null || initialTrack == null) {
            return;
        }

        YouTubeChapterManager chapterManager = bot.getYoutubeChapterManager();
        if (chapterManager == null) {
            return;
        }

        if (!chapterManager.getCachedChapters(initialTrack).isEmpty()) {
            return;
        }

        chapterManager.prefetchChapters(initialTrack);
        waitForChaptersAndEdit(guild, handler, initialTrack, chapterManager, editor, 0);
    }

    private void waitForChaptersAndEdit(
            Guild guild,
            AudioHandler handler,
            AudioTrack initialTrack,
            YouTubeChapterManager chapterManager,
            Consumer<MessageEditData> editor,
            int attempt
    ) {
        if (attempt >= CHAPTER_REFRESH_MAX_ATTEMPTS) {
            return;
        }

        bot.getThreadpool().schedule(() -> {
            try {
                AudioTrack currentTrack = handler.getPlayer().getPlayingTrack();
                if (currentTrack == null || currentTrack != initialTrack) {
                    return;
                }

                if (chapterManager.getCachedChapters(initialTrack).isEmpty()) {
                    waitForChaptersAndEdit(guild, handler, initialTrack, chapterManager, editor, attempt + 1);
                    return;
                }

                MessageCreateData refreshed = handler.getNowPlaying(bot.getJDA());
                if (refreshed == null) {
                    return;
                }

                editor.accept(MessageEditData.fromCreateData(refreshed));
            } catch (Exception e) {
                log.debug("Failed to refresh nowplaying with chapters for guild {}: {}", guild.getIdLong(), e.toString());
            }
        }, CHAPTER_REFRESH_DELAY_SECONDS, TimeUnit.SECONDS);
    }
}
