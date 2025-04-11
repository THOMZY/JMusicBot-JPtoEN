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
package dev.cosgy.jmusicbot.slashcommands.music;

import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.menu.ButtonMenu;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.PlayStatus;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.cosgy.jmusicbot.playlist.CacheLoader;
import dev.cosgy.jmusicbot.playlist.MylistLoader;
import dev.cosgy.jmusicbot.playlist.PubliclistLoader;
import dev.cosgy.jmusicbot.slashcommands.DJCommand;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import dev.cosgy.jmusicbot.util.Cache;
import dev.cosgy.jmusicbot.util.StackTraceUtil;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.regex.Matcher;


/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class PlayCmd extends MusicCommand {
    private final static String LOAD = "\uD83D\uDCE5"; // 📥
    private final static String CANCEL = "\uD83D\uDEAB"; // 🚫

    private final String loadingEmoji;
    private SpotifyCmd spotifyCmd;

    public PlayCmd(Bot bot) {
        super(bot);
        this.loadingEmoji = bot.getConfig().getLoading();
        this.name = "play";
        this.arguments = "<title|URL|subcommand>";
        this.help = "Plays the specified song";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = false;

        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(OptionType.STRING, "input", "URL or song name", false));
        this.options = options;

        // Initialize SpotifyCmd for later use
        this.spotifyCmd = new SpotifyCmd(bot);

        //this.children = new SlashCommand[]{new PlaylistCmd(bot), new MylistCmd(bot), new PublistCmd(bot), new RequestCmd(bot)};
    }

    // Check if URL is a Spotify track
    private boolean isSpotifyUrl(String url) {
        if (url == null || url.isEmpty()) {
            return false;
        }
        
        Pattern pattern = Pattern.compile("https://open\\.spotify\\.com/(intl-[a-z]+/)?track/\\w+");
        Matcher matcher = pattern.matcher(url.split("\\?")[0]);
        return matcher.matches();
    }

    @Override
    public void doCommand(CommandEvent event) {

        if (event.getArgs().isEmpty() && event.getMessage().getAttachments().isEmpty()) {
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            if (handler.getPlayer().getPlayingTrack() != null && handler.getPlayer().isPaused()) {
                if (DJCommand.checkDJPermission(event)) {
                    handler.getPlayer().setPaused(false);
                    event.replySuccess("Resumed playing **" + handler.getPlayer().getPlayingTrack().getInfo().title + "**.");

                    Bot.updatePlayStatus(event.getGuild(), event.getGuild().getSelfMember(), PlayStatus.PLAYING);
                } else
                    event.replyError("Only DJs can resume playback!");
                return;
            }

            // Cache loading mechanism
            if (bot.getCacheLoader().cacheExists(event.getGuild().getId())) {
                List<Cache> data = bot.getCacheLoader().GetCache(event.getGuild().getId());

                AtomicInteger count = new AtomicInteger();
                CacheLoader.CacheResult cache = bot.getCacheLoader().ConvertCache(data);
                event.getChannel().sendMessage(":calling: Loading cache file... (" + cache.getItems().size() + " songs)").queue(m -> {
                    cache.loadTracks(bot.getPlayerManager(), (at) -> {
                        handler.addTrack(new QueuedTrack(at, (User) User.fromId(data.get(count.get()).getUserId())));
                        count.getAndIncrement();
                    }, () -> {
                        StringBuilder builder = new StringBuilder(cache.getTracks().isEmpty()
                                ? event.getClient().getWarning() + " No songs were loaded."
                                : event.getClient().getSuccess() + " From the cache file, **" + cache.getTracks().size() + "** songs were loaded.");
                        if (!cache.getErrors().isEmpty())
                            builder.append("\nThe following songs could not be loaded:");
                        cache.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1).append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                        String str = builder.toString();
                        if (str.length() > 2000)
                            str = str.substring(0, 1994) + " (truncated)";
                        m.editMessage(FormatUtil.filter(str)).queue();
                    });
                });
                try {
                    bot.getCacheLoader().deleteCache(event.getGuild().getId());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return;
            }

            if (handler.playFromDefault()) {
                Settings settings = event.getClient().getSettingsFor(event.getGuild());
                handler.stopAndClear();
                Playlist playlist = bot.getPlaylistLoader().getPlaylist(event.getGuild().getId(), settings.getDefaultPlaylist());
                if (playlist == null) {
                    event.replyError("The playlist file `" + event.getArgs() + ".txt` could not be found in the playlist folder.");
                    return;
                }
                event.getChannel().sendMessage(loadingEmoji + " Loading playlist **" + settings.getDefaultPlaylist() + "** ... ( " + playlist.getItems().size() + " songs)").queue(m -> {

                    playlist.loadTracks(bot.getPlayerManager(), (at) -> handler.addTrack(new QueuedTrack(at, event.getAuthor())), () -> {
                        StringBuilder builder = new StringBuilder(playlist.getTracks().isEmpty()
                                ? event.getClient().getWarning() + " No songs were loaded!"
                                : event.getClient().getSuccess() + " **" + playlist.getTracks().size() + "** songs were loaded!");
                        if (!playlist.getErrors().isEmpty())
                            builder.append("\nThe following songs could not be loaded:");
                        playlist.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1).append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                        String str = builder.toString();
                        if (str.length() > 2000)
                            str = str.substring(0, 1994) + " (...)";
                        m.editMessage(FormatUtil.filter(str)).queue();
                    });
                });
                return;
            }

            StringBuilder builder = new StringBuilder(event.getClient().getWarning() + " Play command:\n");
            builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" <song name>` - Plays the first result from YouTube");
            builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" <URL>` - Plays the specified song, playlist, or stream");
            for (Command cmd : children)
                builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" ").append(cmd.getName()).append(" ").append(cmd.getArguments()).append("` - ").append(cmd.getHelp());
            event.reply(builder.toString());
            return;
        }
//THOMZY edit, queue multiple local files in a single message//
			List<String> urls = new ArrayList<>();
			if (!event.getMessage().getAttachments().isEmpty()) {
				Set<String> supportedExtensions = new HashSet<>(Arrays.asList("mp3", "flac", "wav", "webm", "mp4", "m4a"));
				event.getMessage().getAttachments().forEach(attachment -> {
					if (attachment.getFileExtension() != null && 
							supportedExtensions.contains(attachment.getFileExtension())) {
						urls.add(attachment.getUrl());
					}
				});
			}

			if (!urls.isEmpty()) {
				for (String url : urls) {
					event.reply(loadingEmoji + "`Loading file...`", m -> {
						bot.getPlayerManager().loadItemOrdered(event.getGuild(), url, new ResultHandler(m, event, false));
					});
				}
			} else {
				String args = event.getArgs().startsWith("<") && event.getArgs().endsWith(">")
					? event.getArgs().substring(1, event.getArgs().length() - 1)
					: event.getArgs();
                
                // Check if the URL is a Spotify link
                if (isSpotifyUrl(args)) {
                    // For Spotify URLs, use SpotifyCmd's functionality
                    if (spotifyCmd.isConfigured()) {
                        // Process the Spotify URL
                        String trackId = SpotifyCmd.extractTrackIdFromUrl(args);
                        if (trackId != null) {
                            // Handle loading message and processing
                            event.getChannel().sendMessage(loadingEmoji + " Loading Spotify track...").queue(message -> {
                                try {
                                    // Call SpotifyCmd to handle the track
                                    spotifyCmd.handleSpotifyTrack(trackId, event, message);
                                } catch (Exception e) {
                                    message.editMessage("Error processing Spotify track: " + e.getMessage()).queue();
                                }
                            });
                        } else {
                            event.reply("Error: Invalid Spotify track URL format");
                        }
                    } else {
                        // Spotify functionality is not configured
                        event.reply("Spotify support is not configured on this bot. Please contact the bot owner.");
                    }
                    return;
                }
                
                // Normal track loading
                event.reply(loadingEmoji + " Loading `[" + args + "]`...", m -> 
                        bot.getPlayerManager().loadItemOrdered(event.getGuild(), args, new ResultHandler(m, event, false)));
			} //end of edit//
    }

    @Override
    public void doCommand(SlashCommandEvent event) {

        if (event.getOption("input") == null) {
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            if (handler.getPlayer().getPlayingTrack() != null && handler.getPlayer().isPaused()) {
                if (DJCommand.checkDJPermission(event.getClient(), event)) {

                    handler.getPlayer().setPaused(false);
                    event.reply(event.getClient().getSuccess() + "Resumed playing **" + handler.getPlayer().getPlayingTrack().getInfo().title + "**.").queue();

                    Bot.updatePlayStatus(event.getGuild(), event.getGuild().getSelfMember(), PlayStatus.PLAYING);
                } else
                    event.reply(event.getClient().getError() + "Only DJs can resume playback!").queue();
                return;
            }

            // Cache loading mechanism
            if (bot.getCacheLoader().cacheExists(event.getGuild().getId())) {
                List<Cache> data = bot.getCacheLoader().GetCache(event.getGuild().getId());

                AtomicInteger count = new AtomicInteger();
                CacheLoader.CacheResult cache = bot.getCacheLoader().ConvertCache(data);
                event.reply(":calling: Loading cache file... (" + cache.getItems().size() + " songs)").queue(m -> {
                    cache.loadTracks(bot.getPlayerManager(), (at) -> {
                        handler.addTrack(new QueuedTrack(at, event.getUser()));
                        count.getAndIncrement();
                    }, () -> {
                        StringBuilder builder = new StringBuilder(cache.getTracks().isEmpty()
                                ? event.getClient().getWarning() + " No songs have been loaded."
                                : event.getClient().getSuccess() + " Loaded **" + cache.getTracks().size() + "** songs from cache file.");
                        if (!cache.getErrors().isEmpty())
                            builder.append("\nThe following songs could not be loaded:");
                        cache.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1).append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                        String str = builder.toString();
                        if (str.length() > 2000)
                            str = str.substring(0, 1994) + " (truncated)";
                        m.editOriginal(FormatUtil.filter(str)).queue();
                    });
                });
                try {
                    bot.getCacheLoader().deleteCache(event.getGuild().getId());
                } catch (IOException e) {
                    e.printStackTrace();
                }
                return;
            }

            if (handler.playFromDefault()) {
                Settings settings = event.getClient().getSettingsFor(event.getGuild());
                handler.stopAndClear();
                Playlist playlist = bot.getPlaylistLoader().getPlaylist(event.getGuild().getId(), settings.getDefaultPlaylist());
                if (playlist == null) {
                    event.reply("`" + event.getOption("input").getAsString() + ".txt` was not found in the playlist folder.").queue();
                    return;
                }
                event.reply(loadingEmoji + " Loading playlist **" + settings.getDefaultPlaylist() + " ** ... (" + playlist.getItems().size() + " songs)").queue(m -> {

                    playlist.loadTracks(bot.getPlayerManager(), (at) -> handler.addTrack(new QueuedTrack(at, event.getUser())), () -> {
                        StringBuilder builder = new StringBuilder(playlist.getTracks().isEmpty()
                                ? event.getClient().getWarning() + " No songs have been loaded!"
                                : event.getClient().getSuccess() + " Loaded ** " + playlist.getTracks().size() + " ** songs!");
                        if (!playlist.getErrors().isEmpty())
                            builder.append("\nThe following songs could not be loaded:");
                        playlist.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1).append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                        String str = builder.toString();
                        if (str.length() > 2000)
                            str = str.substring(0, 1994) + " (...)";
                        m.editOriginal(FormatUtil.filter(str)).queue();
                    });
                });
                return;

            }

            StringBuilder builder = new StringBuilder(event.getClient().getWarning() + " Play command:\n");
            builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" <song name>` - Play the first result from YouTube");
            builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" <URL>` - Play the specified song, playlist, or stream");
            for (Command cmd : children)
                builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" ").append(cmd.getName()).append(" ").append(cmd.getArguments()).append("` - ").append(cmd.getHelp());
            event.reply(builder.toString()).queue();
            return;
        }
        
        String input = event.getOption("input").getAsString();
        
        // Check if the URL is a Spotify link
        if (isSpotifyUrl(input)) {
            // For Spotify URLs, handle separately using SpotifyCmd
            if (spotifyCmd.isConfigured()) {
                // Get the track ID from the URL
                String trackId = SpotifyCmd.extractTrackIdFromUrl(input);
                if (trackId != null) {
                    // Send initial loading message and process with SpotifyCmd
                    event.deferReply().queue(hook -> {
                        try {
                            // Use SpotifyCmd to handle the track
                            spotifyCmd.handleSpotifyTrack(trackId, event, hook);
                        } catch (Exception e) {
                            hook.editOriginal("Error processing Spotify track: " + e.getMessage()).queue();
                        }
                    });
                } else {
                    event.reply("Error: Invalid Spotify track URL format").queue();
                }
            } else {
                // Spotify functionality is not configured
                event.reply("Spotify support is not configured on this bot. Please contact the bot owner.").queue();
            }
            return;
        }
        
        // Normal track loading
        event.reply(loadingEmoji + " Loading `[" + input + "]`...").queue(m -> 
            bot.getPlayerManager().loadItemOrdered(event.getGuild(), input, new SlashResultHandler(m, event, false)));
    }

    public class SlashResultHandler implements AudioLoadResultHandler {
        private final InteractionHook m;
        private final SlashCommandEvent event;
        private final boolean ytsearch;

        SlashResultHandler(InteractionHook m, SlashCommandEvent event, boolean ytsearch) {
            this.m = m;
            this.event = event;
            this.ytsearch = ytsearch;
        }

        private void loadSingle(AudioTrack track, AudioPlaylist playlist) {
            if (bot.getConfig().isTooLong(track)) {
                m.editOriginal(FormatUtil.filter(event.getClient().getWarning() +
                        " **" + (track.getInfo().uri.matches(".*stream.gensokyoradio.net/.*") ? "Gensokyo Radio" : track.getInfo().title) + "**`(" + FormatUtil.formatTime(track.getDuration()) + ")` exceeds the set length `(" + FormatUtil.formatTime(bot.getConfig().getMaxSeconds() * 1000) + ")`.")).queue();
                return;
            }
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            int pos = handler.addTrack(new QueuedTrack(track, event.getUser())) + 1;

            // Output MSG ex:
            // Added <title><(length)>.
            // Added <title><(length)> to <playback queue number> in the queue.
            String addMsg = FormatUtil.filter(event.getClient().getSuccess() + " **" + (track.getInfo().uri.matches(".*stream.gensokyoradio.net/.*") ? "Gensokyo Radio" : track.getInfo().title)
                    + "** (`" + FormatUtil.formatTime(track.getDuration()) + "`) " + (pos == 0 ? "has been added." : "has been added at position " + pos + " in the queue."));
            if (playlist == null || !event.getGuild().getSelfMember().hasPermission(event.getTextChannel(), Permission.MESSAGE_ADD_REACTION)) {
                m.editOriginal(addMsg).queue();
            } else {
                new ButtonMenu.Builder()
                        .setText(addMsg + "\n" + event.getClient().getWarning() + " This playlist has **" + playlist.getTracks().size() + "** additional tracks. Select " + LOAD + " to load the tracks.")
                        .setChoices(LOAD, CANCEL)
                        .setEventWaiter(bot.getWaiter())
                        .setTimeout(30, TimeUnit.SECONDS)
                        .setAction(re -> {
                            if (re.getName().equals(LOAD))
                                m.editOriginal(addMsg + "\n" + event.getClient().getSuccess() + "Added **" + loadPlaylist(playlist, track) + "** tracks to the queue!").queue();
                            else
                                m.editOriginal(addMsg).queue();
                        }).setFinalAction(m -> {
                            try {
                                m.clearReactions().queue();
                                m.delete().queue();
                            } catch (PermissionException ignore) {
                            }
                        }).build().display(event.getChannel());
            }
        }

        private int loadPlaylist(AudioPlaylist playlist, AudioTrack exclude) {
            int[] count = {0};
            playlist.getTracks().forEach((track) -> {
                if (!bot.getConfig().isTooLong(track) && !track.equals(exclude)) {
                    AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                    handler.addTrack(new QueuedTrack(track, event.getUser()));
                    count[0]++;
                }
            });
            return count[0];
        }

        @Override
        public void trackLoaded(AudioTrack track) {
            loadSingle(track, null);
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {
            if (playlist.getTracks().size() == 1 || playlist.isSearchResult()) {
                AudioTrack single = playlist.getSelectedTrack() == null ? playlist.getTracks().get(0) : playlist.getSelectedTrack();
                loadSingle(single, null);
            } else if (playlist.getSelectedTrack() != null) {
                AudioTrack single = playlist.getSelectedTrack();
                loadSingle(single, playlist);
            } else {
                int count = loadPlaylist(playlist, null);
                if (count == 0) {
                    m.editOriginal(FormatUtil.filter(event.getClient().getWarning() + " This playlist " + (playlist.getName() == null ? "" : "(**" + playlist.getName()
                            + "**)") + " exceeds the allowed maximum length. (`" + bot.getConfig().getMaxTime() + "`)")).queue();
                } else {
                    m.editOriginal(FormatUtil.filter(event.getClient().getSuccess()
                            + (playlist.getName() == null ? "The playlist" : "Playlist **" + playlist.getName() + "**") + " has added `"
                            + playlist.getTracks().size() + "` tracks to the queue."
                            + (count < playlist.getTracks().size() ? "\n" + event.getClient().getWarning() + " Some tracks longer than the allowed maximum length (`"
                            + bot.getConfig().getMaxTime() + "`) have been omitted." : ""))).queue();
                }
            }
        }

        @Override
        public void noMatches() {
            if (ytsearch)
                m.editOriginal(FormatUtil.filter(event.getClient().getWarning() + " No search results for `" + event.getOption("input").getAsString() + "`.")).queue();
            else
                bot.getPlayerManager().loadItemOrdered(event.getGuild(), "ytsearch:" + event.getOption("input").getAsString(), new SlashResultHandler(m, event, true));
        }

        @Override
        public void loadFailed(FriendlyException throwable) {
            if (throwable.severity == Severity.COMMON) {
                m.editOriginal(event.getClient().getError() + " An error occurred while loading: " + throwable.getMessage()).queue();
            } else {
                m.editOriginal(event.getClient().getError() + " An error occurred while loading the track.").queue();
            }
        }
    }

    private class ResultHandler implements AudioLoadResultHandler {
        private final Message m;
        private final CommandEvent event;
        private final boolean ytsearch;

        private ResultHandler(Message m, CommandEvent event, boolean ytsearch) {
            this.m = m;
            this.event = event;
            this.ytsearch = ytsearch;
        }

        private void loadSingle(AudioTrack track, AudioPlaylist playlist) {
            if (bot.getConfig().isTooLong(track)) {
                m.editMessage(FormatUtil.filter(event.getClient().getWarning() +
                        " **" + track.getInfo().title + "**`(" + FormatUtil.formatTime(track.getDuration()) + ")` exceeds the set length `(" + FormatUtil.formatTime(bot.getConfig().getMaxSeconds() * 1000) + ")`.")).queue();
                return;
            }
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            int pos = handler.addTrack(new QueuedTrack(track, event.getAuthor())) + 1;

            // Output MSG ex:
            // Added <title><(length)>.
            // Added <title><(length)> to <playback queue number> in the queue.
            String addMsg = FormatUtil.filter(event.getClient().getSuccess() + " **" + (track.getInfo().uri.contains("https://stream.gensokyoradio.net/") ? "Gensokyo Radio" : track.getInfo().title)
                    + "** (`" + FormatUtil.formatTime(track.getDuration()) + "`) " + (pos == 0 ? "has been added." : "has been added at position " + pos + " in the queue. "));
            if (playlist == null || !event.getSelfMember().hasPermission(event.getTextChannel(), Permission.MESSAGE_ADD_REACTION))
                m.editMessage(addMsg).queue();
            else {
                new ButtonMenu.Builder()
                        .setText(addMsg + "\n" + event.getClient().getWarning() + " This playlist includes **" + playlist.getTracks().size() + "** additional tracks. Select " + LOAD + " to load the tracks.")
                        .setChoices(LOAD, CANCEL)
                        .setEventWaiter(bot.getWaiter())
                        .setTimeout(30, TimeUnit.SECONDS)
                        .setAction(re ->
                        {
                            if (re.getName().equals(LOAD))
                                m.editMessage(addMsg + "\n" + event.getClient().getSuccess() + "Added **" + loadPlaylist(playlist, track) + "** tracks to the queue!").queue();
                            else
                                m.editMessage(addMsg).queue();
                        }).setFinalAction(m ->
                        {
                            try {
                                m.clearReactions().queue();
                            } catch (PermissionException ignore) {
                            }
                        }).build().display(m);
            }
        }

        private int loadPlaylist(AudioPlaylist playlist, AudioTrack exclude) {
            int[] count = {0};
            playlist.getTracks().forEach((track) -> {
                if (!bot.getConfig().isTooLong(track) && !track.equals(exclude)) {
                    AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                    handler.addTrack(new QueuedTrack(track, event.getAuthor()));
                    count[0]++;
                }
            });
            return count[0];
        }

        @Override
        public void trackLoaded(AudioTrack track) {
            loadSingle(track, null);
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {
            if (playlist.getTracks().size() == 1 || playlist.isSearchResult()) {
                AudioTrack single = playlist.getSelectedTrack() == null ? playlist.getTracks().get(0) : playlist.getSelectedTrack();
                loadSingle(single, null);
            } else if (playlist.getSelectedTrack() != null) {
                AudioTrack single = playlist.getSelectedTrack();
                loadSingle(single, playlist);
            } else {
                int count = loadPlaylist(playlist, null);
                if (count == 0) {
                    m.editMessage(FormatUtil.filter(event.getClient().getWarning() + " This playlist " + (playlist.getName() == null ? "" : "(**" + playlist.getName()
                            + "**) ") + " exceeds the allowed maximum length. (`" + bot.getConfig().getMaxTime() + "`)")).queue();
                } else {
                    m.editMessage(FormatUtil.filter(event.getClient().getSuccess()
                            + (playlist.getName() == null ? "The playlist" : "Playlist **" + playlist.getName() + "**") + " has added `"
                            + playlist.getTracks().size() + "` tracks to the queue."
                            + (count < playlist.getTracks().size() ? "\n" + event.getClient().getWarning() + " Some tracks longer than the allowed maximum length (`"
                            + bot.getConfig().getMaxTime() + "`) have been omitted." : ""))).queue();
                }
            }
        }

        @Override
        public void noMatches() {
            if (ytsearch)
                m.editMessage(FormatUtil.filter(event.getClient().getWarning() + " No search results for `" + event.getArgs() + "`.")).queue();
            else
                bot.getPlayerManager().loadItemOrdered(event.getGuild(), "ytsearch:" + event.getArgs(), new ResultHandler(m, event, true));
        }

        @Override
        public void loadFailed(FriendlyException throwable) {
            if (throwable.severity == Severity.COMMON) {
                m.editMessage(event.getClient().getError() + " An error occurred while loading: " + throwable.getMessage()).queue();
            } else {
                if (m.getAuthor().getIdLong() == bot.getConfig().getOwnerId() || m.getMember().isOwner()) {
                    m.editMessage(event.getClient().getError() + " An error occurred while loading the track.\n" +
                            "**Error details: " + throwable.getLocalizedMessage() + "**").queue();
                    StackTraceUtil.sendStackTrace(event.getTextChannel(), throwable);
                    return;
                }

                m.editMessage(event.getClient().getError() + " An error occurred while loading the track.").queue();
            }
        }
    }

    public class RequestCmd extends MusicCommand {
        private final static String LOAD = "\uD83D\uDCE5"; // 📥
        private final static String CANCEL = "\uD83D\uDEAB"; // 🚫

        private final String loadingEmoji;
        private final JDA jda;

        public RequestCmd(Bot bot) {
            super(bot);
            this.jda = bot.getJDA();
            this.loadingEmoji = bot.getConfig().getLoading();
            this.name = "request";
            this.arguments = "<title|URL>";
            this.help = "Requests a song.";
            this.aliases = bot.getConfig().getAliases(this.name);
            this.beListening = true;
            this.bePlaying = false;

            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "input", "URL or song title", false));
			this.options = options;

        }

        @Override
        public void doCommand(CommandEvent event) {
        }

        @Override
        public void doCommand(SlashCommandEvent event) {

            if (event.getOption("input") == null) {
                AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                if (handler.getPlayer().getPlayingTrack() != null && handler.getPlayer().isPaused()) {
                    if (DJCommand.checkDJPermission(event.getClient(), event)) {

                        handler.getPlayer().setPaused(false);
                        event.reply(event.getClient().getSuccess() + "**Resumed playing " + handler.getPlayer().getPlayingTrack().getInfo().title + "**.").queue();

                        Bot.updatePlayStatus(event.getGuild(), event.getGuild().getSelfMember(), PlayStatus.PLAYING);
                    } else
                        event.reply(event.getClient().getError() + "Only the DJ can resume playback!").queue();
                    return;
                }

                // Cache loading mechanism
                if (bot.getCacheLoader().cacheExists(event.getGuild().getId())) {
                    List<Cache> data = bot.getCacheLoader().GetCache(event.getGuild().getId());

                    AtomicInteger count = new AtomicInteger();
                    CacheLoader.CacheResult cache = bot.getCacheLoader().ConvertCache(data);
                    event.reply(":calling: Loading cache file... (" + cache.getItems().size() + " tracks)").queue(m -> {
                        cache.loadTracks(bot.getPlayerManager(), (at) -> {
                            // TODO: Use user ID stored in cache.
                            handler.addTrack(new QueuedTrack(at, event.getUser()));
                            count.getAndIncrement();
                        }, () -> {
                            StringBuilder builder = new StringBuilder(cache.getTracks().isEmpty()
                                    ? event.getClient().getWarning() + " No tracks were loaded."
                                    : event.getClient().getSuccess() + " Loaded **" + cache.getTracks().size() + "** tracks from the cache file.");
                            if (!cache.getErrors().isEmpty())
                                builder.append("\nThe following tracks could not be loaded:");
                            cache.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1).append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                            String str = builder.toString();
                            if (str.length() > 2000)
                                str = str.substring(0, 1994) + " (truncated)";
                            m.editOriginal(FormatUtil.filter(str)).queue();
                        });
                    });
                    try {
                        bot.getCacheLoader().deleteCache(event.getGuild().getId());
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    return;
                }

                if (handler.playFromDefault()) {
                    Settings settings = event.getClient().getSettingsFor(event.getGuild());
                    handler.stopAndClear();
                    Playlist playlist = bot.getPlaylistLoader().getPlaylist(event.getGuild().getId(), settings.getDefaultPlaylist());
                    if (playlist == null) {
                        event.reply("Could not find `" + event.getOption("input").getAsString() + ".txt` in the playlist folder.").queue();
                        return;
                    }
                    event.reply(loadingEmoji + " Loading playlist **" + settings.getDefaultPlaylist() + "** ... (" + playlist.getItems().size() + " tracks)").queue(m ->
                    {

                        playlist.loadTracks(bot.getPlayerManager(), (at) -> handler.addTrack(new QueuedTrack(at, event.getUser())), () -> {
                            StringBuilder builder = new StringBuilder(playlist.getTracks().isEmpty()
                                    ? event.getClient().getWarning() + " No tracks were loaded!"
                                    : event.getClient().getSuccess() + " Loaded **" + playlist.getTracks().size() + "** tracks!");
                            if (!playlist.getErrors().isEmpty())
                                builder.append("\nFailed to load the following tracks:");
                            playlist.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1).append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                            String str = builder.toString();
                            if (str.length() > 2000)
                                str = str.substring(0, 1994) + " (...)";
                            m.editOriginal(FormatUtil.filter(str)).queue();
                        });
                    });
                    return;

                }

                StringBuilder builder = new StringBuilder(event.getClient().getWarning() + " Play command:\n");
                builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" <song name>` - Play the first result from YouTube");
                builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" <URL>` - Play the specified song, playlist, or stream");
                for (Command cmd : children)
                    builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" ").append(cmd.getName()).append(" ").append(cmd.getArguments()).append("` - ").append(cmd.getHelp());
                event.reply(builder.toString()).queue();
                return;
            }
            event.reply(loadingEmoji + "Loading `[" + event.getOption("input").getAsString() + "]`...").queue(m -> bot.getPlayerManager().loadItemOrdered(event.getGuild(), event.getOption("input").getAsString(), new SlashResultHandler(m, event, false)));
        }


    }


    public class PlaylistCmd extends MusicCommand {
        public PlaylistCmd(Bot bot) {
            super(bot);
            this.name = "playlist";
            this.aliases = new String[]{"pl"};
            this.arguments = "<name>";
            this.help = "Play the provided playlist";
            this.beListening = true;
            this.bePlaying = false;

            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "name", "Playlist name", true));
            this.options = options;
        }

        @Override
        public void doCommand(CommandEvent event) {
            String guildId = event.getGuild().getId();
            if (event.getArgs().isEmpty()) {
                event.reply(event.getClient().getError() + "Please include the playlist name.");
                return;
            }
            Playlist playlist = bot.getPlaylistLoader().getPlaylist(guildId, event.getArgs());
            if (playlist == null) {
                event.replyError("Could not find `" + event.getArgs() + ".txt`");
                return;
            }
            event.getChannel().sendMessage(":calling: Loading playlist **" + event.getArgs() + "**... (" + playlist.getItems().size() + " tracks)").queue(m ->
            {
                AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                playlist.loadTracks(bot.getPlayerManager(), (at) -> handler.addTrack(new QueuedTrack(at, event.getAuthor())), () -> {
                    StringBuilder builder = new StringBuilder(playlist.getTracks().isEmpty()
                            ? event.getClient().getWarning() + " No tracks were loaded."
                            : event.getClient().getSuccess() + " Loaded **" + playlist.getTracks().size() + "** tracks.");
                    if (!playlist.getErrors().isEmpty())
                        builder.append("\nThe following tracks could not be loaded:");
                    playlist.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1).append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                    String str = builder.toString();
                    if (str.length() > 2000)
                        str = str.substring(0, 1994) + " (truncated)";
                    m.editMessage(FormatUtil.filter(str)).queue();
                });
            });
        }

        @Override
        public void doCommand(SlashCommandEvent event) {
            String guildId = event.getGuild().getId();

            String name = event.getOption("name").getAsString();

            Playlist playlist = bot.getPlaylistLoader().getPlaylist(guildId, name);
            if (playlist == null) {
                event.reply(event.getClient().getError() + "Could not find `" + name + ".txt`").queue();
                return;
            }
            event.reply(":calling: Loading playlist **" + name + "**... (" + playlist.getItems().size() + " tracks)").queue(m ->
            {
                AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                playlist.loadTracks(bot.getPlayerManager(), (at) -> handler.addTrack(new QueuedTrack(at, event.getUser())), () -> {
                    StringBuilder builder = new StringBuilder(playlist.getTracks().isEmpty()
                            ? event.getClient().getWarning() + " No tracks were loaded."
                            : event.getClient().getSuccess() + " Loaded **" + playlist.getTracks().size() + "** tracks.");
                    if (!playlist.getErrors().isEmpty())
                        builder.append("\nThe following tracks could not be loaded:");
                    playlist.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1)
                            .append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                    String str = builder.toString();
                    if (str.length() > 2000)
                        str = str.substring(0, 1994) + " (truncated)";
                    m.editOriginal(FormatUtil.filter(str)).queue();
                });
            });
        }
    }

    public class MylistCmd extends MusicCommand {
        public MylistCmd(Bot bot) {
            super(bot);
            this.name = "mylist";
            this.aliases = new String[]{"ml"};
            this.arguments = "<name>";
            this.help = "マイリストを再生します";
            this.beListening = true;
            this.bePlaying = false;

            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "name", "マイリスト名", true));
            this.options = options;
        }

        @Override
        public void doCommand(CommandEvent event) {
            String userId = event.getAuthor().getId();
            if (event.getArgs().isEmpty()) {
                event.reply(event.getClient().getError() + " Please include the mylist name.");
                return;
            }
            MylistLoader.Playlist playlist = bot.getMylistLoader().getPlaylist(userId, event.getArgs());
            if (playlist == null) {
                event.replyError("Could not find `" + event.getArgs() + ".txt`");
                return;
            }
            event.getChannel().sendMessage(":calling: Loading mylist **" + event.getArgs() + "**... (" + playlist.getItems().size() + " tracks)").queue(m ->
            {
                AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                playlist.loadTracks(bot.getPlayerManager(), (at) -> handler.addTrack(new QueuedTrack(at, event.getAuthor())), () -> {
                    StringBuilder builder = new StringBuilder(playlist.getTracks().isEmpty()
                            ? event.getClient().getWarning() + " No tracks were loaded."
                            : event.getClient().getSuccess() + " Loaded **" + playlist.getTracks().size() + "** tracks.");
                    if (!playlist.getErrors().isEmpty())
                        builder.append("\nThe following tracks could not be loaded:");
                    playlist.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1)
                            .append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                    String str = builder.toString();
                    if (str.length() > 2000)
                        str = str.substring(0, 1994) + " (truncated)";
                    m.editMessage(FormatUtil.filter(str)).queue();
                });
            });
        }

        @Override
        public void doCommand(SlashCommandEvent event) {
            String userId = event.getUser().getId();

            String name = event.getOption("name").getAsString();

            MylistLoader.Playlist playlist = bot.getMylistLoader().getPlaylist(userId, name);
            if (playlist == null) {
                event.reply(event.getClient().getError() + "Could not find `" + name + ".txt`").queue();
                return;
            }
            event.reply(":calling: Loading mylist **" + name + "**... (" + playlist.getItems().size() + " tracks)").queue(m ->
            {
                AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                playlist.loadTracks(bot.getPlayerManager(), (at) -> handler.addTrack(new QueuedTrack(at, event.getUser())), () -> {
                    StringBuilder builder = new StringBuilder(playlist.getTracks().isEmpty()
                            ? event.getClient().getWarning() + " No tracks were loaded."
                            : event.getClient().getSuccess() + " Loaded **" + playlist.getTracks().size() + "** tracks.");
                    if (!playlist.getErrors().isEmpty())
                        builder.append("\nThe following tracks could not be loaded:");
                    playlist.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1)
                            .append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                    String str = builder.toString();
                    if (str.length() > 2000)
                        str = str.substring(0, 1994) + " (truncated)";
                    m.editOriginal(FormatUtil.filter(str)).queue();
                });
            });
        }
    }

    public class PublistCmd extends MusicCommand {
        public PublistCmd(Bot bot) {
            super(bot);
            this.name = "publist";
            this.aliases = new String[]{"pul"};
            this.arguments = "<name>";
            this.help = "Play a public list";
            this.beListening = true;
            this.bePlaying = false;

            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "name", "Public list name", true));
            this.options = options;
        }

        @Override
        public void doCommand(CommandEvent event) {
            if (event.getArgs().isEmpty()) {
                event.reply(event.getClient().getError() + " Please include the playlist name.");
                return;
            }
            PubliclistLoader.Playlist playlist = bot.getPublistLoader().getPlaylist(event.getArgs());
            if (playlist == null) {
                event.replyError("Could not find `" + event.getArgs() + ".txt`");
                return;
            }
            event.getChannel().sendMessage(":calling: Loading playlist **" + event.getArgs() + "**... (" + playlist.getItems().size() + " tracks)").queue(m ->
            {
                AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                playlist.loadTracks(bot.getPlayerManager(), (at) -> handler.addTrack(new QueuedTrack(at, event.getAuthor())), () -> {
                    StringBuilder builder = new StringBuilder(playlist.getTracks().isEmpty()
                            ? event.getClient().getWarning() + " No tracks were loaded."
                            : event.getClient().getSuccess() + " Loaded **" + playlist.getTracks().size() + "** tracks.");
                    if (!playlist.getErrors().isEmpty())
                        builder.append("\nThe following tracks could not be loaded:");
                    playlist.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1)
                            .append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                    String str = builder.toString();
                    if (str.length() > 2000)
                        str = str.substring(0, 1994) + " (truncated)";
                    m.editMessage(FormatUtil.filter(str)).queue();
                });
            });
        }

        @Override
        public void doCommand(SlashCommandEvent event) {
            String name = event.getOption("name").getAsString();
            PubliclistLoader.Playlist playlist = bot.getPublistLoader().getPlaylist(name);
            if (playlist == null) {
                event.reply(event.getClient().getError() + "Could not find `" + name + ".txt`").queue();
                return;
            }
            event.reply(":calling: Loading playlist **" + name + "**... (" + playlist.getItems().size() + " tracks)").queue(m ->
            {
                AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                playlist.loadTracks(bot.getPlayerManager(), (at) -> handler.addTrack(new QueuedTrack(at, event.getUser())), () -> {
                    StringBuilder builder = new StringBuilder(playlist.getTracks().isEmpty()
                            ? event.getClient().getWarning() + " No tracks were loaded."
                            : event.getClient().getSuccess() + " Loaded **" + playlist.getTracks().size() + "** tracks.");
                    if (!playlist.getErrors().isEmpty())
                        builder.append("\nThe following tracks could not be loaded:");
                    playlist.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1)
                            .append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
                    String str = builder.toString();
                    if (str.length() > 2000)
                        str = str.substring(0, 1994) + " (truncated)";
                    m.editOriginal(FormatUtil.filter(str)).queue();
                });
            });
        }
    }
}
