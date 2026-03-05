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

import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.playlist.PlaylistLoader;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import dev.cosgy.jmusicbot.util.StackTraceUtil;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static dev.cosgy.jmusicbot.slashcommands.DJCommand.checkDJPermission;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class PlaylistsCmd extends MusicCommand {

    public PlaylistsCmd(Bot bot) {
        super(bot);
        this.name = "playlists";
        this.help = "Displays available playlists";
        this.arguments = "<play|append|delete|make|show>";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.guildOnly = true;
        this.beListening = false;
        this.children = new MusicCommand[]{
                new PlayCmd(bot),
                new ListCmd(bot),
                new AppendlistCmd(bot),
                new DeletelistCmd(bot),
                new MakelistCmd(bot),
                new ShowTracksCmd(bot)};
    }

    @Override
    public void doCommand(CommandEvent event) {
        handlePlaylistsCommand(event.getGuild().getId(), event, null);
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        handlePlaylistsCommand(event.getGuild().getId(), null, event);
    }

    private void handlePlaylistsCommand(String guildID, CommandEvent cmdEvent, SlashCommandEvent slashEvent) {
        ensureFoldersExist(guildID);

        List<String> playlists = bot.getPlaylistLoader().getPlaylistNames(guildID);

        String prefix = (cmdEvent != null ? cmdEvent.getClient().getTextualPrefix() : slashEvent.getClient().getTextualPrefix());
        String message;
        if (playlists == null) {
            message = (cmdEvent != null ? cmdEvent.getClient().getError() : slashEvent.getClient().getError()) + " Could not load available playlists.";
        } else if (playlists.isEmpty()) {
            message = (cmdEvent != null ? cmdEvent.getClient().getWarning() : slashEvent.getClient().getWarning()) + " There are no playlists in the playlist folder.";
        } else {
            StringBuilder builder = new StringBuilder((cmdEvent != null ? cmdEvent.getClient().getSuccess() : slashEvent.getClient().getSuccess()) + " Available playlists:\n");
            playlists.forEach(name -> builder.append("`").append(name).append("` "));
            builder.append("\nYou can play a playlist by typing `").append(prefix).append("playlists play <name>`. ");
            message = builder.toString();
        }

        reply(cmdEvent, slashEvent, message);
    }

    private void ensureFoldersExist(String guildID) {
        if (!bot.getPlaylistLoader().folderExists()) {
            bot.getPlaylistLoader().createFolder();
        }
        if (!bot.getPlaylistLoader().folderGuildExists(guildID)) {
            bot.getPlaylistLoader().createGuildFolder(guildID);
        }
    }

    private void reply(CommandEvent cmdEvent, SlashCommandEvent slashEvent, String message) {
        if (cmdEvent != null) {
            cmdEvent.reply(message); // Do not add queue() to CommandEvent
        } else if (slashEvent != null) {
            slashEvent.reply(message).queue(); // Add queue() to SlashCommandEvent
        }
    }

    public class PlayCmd extends MusicCommand {
        public PlayCmd(Bot bot) {
            super(bot);
            this.name = "play";
            this.aliases = new String[]{"play"};
            this.arguments = "<name>";
            this.help = "Plays the specified playlist";
            this.beListening = true;
            this.bePlaying = false;

            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "name", "Mylist name", true));
            this.options = options;
        }

        @Override
        public void doCommand(CommandEvent event) {
            playPlaylist(event.getGuild().getId(), event.getArgs(), event, null);
        }

        @Override
        public void doCommand(SlashCommandEvent event) {
            String playlistName = event.getOption("name").getAsString();
            playPlaylist(event.getGuild().getId(), playlistName, null, event);
        }

        private void playPlaylist(String guildID, String playlistName, CommandEvent cmdEvent, SlashCommandEvent slashEvent) {
            if (playlistName == null || playlistName.isEmpty()) {
                reply(cmdEvent, slashEvent, (cmdEvent != null ? cmdEvent.getClient().getError() : slashEvent.getClient().getError()) + " Please specify a playlist name.");
                return;
            }

            PlaylistLoader.Playlist playlist = bot.getPlaylistLoader().getPlaylist(guildID, playlistName);
            if (playlist == null) {
                reply(cmdEvent, slashEvent, (cmdEvent != null ? cmdEvent.getClient().getError() : slashEvent.getClient().getError()) + " Playlist `" + playlistName + "` not found.");
                return;
            }

            reply(cmdEvent, slashEvent, (cmdEvent != null ? cmdEvent.getClient().getSuccess() : slashEvent.getClient().getSuccess()) + ":calling: Loading playlist **" + playlistName + "**... (" + playlist.getItems().size() + " tracks)");

            AudioHandler handler = (AudioHandler) (cmdEvent != null
                    ? cmdEvent.getGuild().getAudioManager().getSendingHandler()
                    : slashEvent.getGuild().getAudioManager().getSendingHandler());

            playlist.loadTracks(bot.getPlayerManager(), track -> handler.addTrack(new QueuedTrack(track, cmdEvent != null ? cmdEvent.getAuthor() : slashEvent.getUser())), () -> {
                StringBuilder builder = new StringBuilder();
                if (playlist.getTracks().isEmpty()) {
                    builder.append((cmdEvent != null ? cmdEvent.getClient().getWarning() : slashEvent.getClient().getWarning())).append(" No tracks loaded.");
                } else {
                    builder.append((cmdEvent != null ? cmdEvent.getClient().getSuccess() : slashEvent.getClient().getSuccess())).append(" Loaded **").append(playlist.getTracks().size()).append("** tracks.");
                }
                if (!playlist.getErrors().isEmpty()) {
                    builder.append("\nCould not load the following tracks:");
                    playlist.getErrors().forEach(error -> builder.append("\n`[").append(error.getIndex() + 1).append("]` **").append(error.getItem()).append("**: ").append(error.getReason()));
                }

                String result = FormatUtil.filter(builder.toString());
                if (result.length() > 2000) {
                    result = result.substring(0, 1994) + " (truncated)";
                }

                reply(cmdEvent, slashEvent, result);
            });
        }
    }

    public class ShowTracksCmd extends MusicCommand {
        public ShowTracksCmd(Bot bot) {
            super(bot);
            this.name = "show";
            this.help = "Shows the songs in the specified playlist";
            this.arguments = "<name>";
            this.guildOnly = true;

            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "name", "Playlist name", true));
            this.options = options;
        }

        @Override
        public void doCommand(CommandEvent event) {
            String guildId = event.getGuild().getId();
            String playlistName = event.getArgs().trim();

            if (playlistName.isEmpty()) {
                event.reply(event.getClient().getError() + " Please specify a playlist name.");
                return;
            }

            PlaylistLoader.Playlist playlist = bot.getPlaylistLoader().getPlaylist(guildId, playlistName);
            if (playlist == null) {
                event.reply(event.getClient().getError() + " Playlist `" + playlistName + "` not found.");
                return;
            }

            if (playlist.getItems().isEmpty()) {
                event.reply(event.getClient().getWarning() + " There are no songs in the playlist `" + playlistName + "`.");
                return;
            }

            StringBuilder builder = new StringBuilder(event.getClient().getSuccess() + " Songs in playlist `" + playlistName + "`:\n");
            for (int i = 0; i < playlist.getItems().size(); i++) {
                builder.append(i + 1).append(". ").append(playlist.getItems().get(i)).append("\n");
            }

            if (builder.length() > 2000) {
                builder.setLength(1997); // Corresponds to Discord message limit
                builder.append("...");
            }

            event.reply(builder.toString());
        }

            @Override
            public void doCommand(SlashCommandEvent event) {
                String guildId = event.getGuild().getId();
                String playlistName = event.getOption("name").getAsString();

                PlaylistLoader.Playlist playlist = bot.getPlaylistLoader().getPlaylist(guildId, playlistName);
                if (playlist == null) {
                    event.reply(event.getClient().getError() + " Playlist `" + playlistName + "` not found.").queue();
                    return;
                }

                if (playlist.getItems().isEmpty()) {
                    event.reply(event.getClient().getWarning() + " There are no songs in the playlist `" + playlistName + "`.").queue();
                    return;
                }

                StringBuilder builder = new StringBuilder(event.getClient().getSuccess() + " Songs in playlist `" + playlistName + "`:\n");
                for (int i = 0; i < playlist.getItems().size(); i++) {
                    builder.append(i + 1).append(". ").append(playlist.getItems().get(i)).append("\n");
                }

                if (builder.length() > 2000) {
                    builder.setLength(1997); // Corresponds to Discord message limit
                    builder.append("...");
                }

                event.reply(builder.toString()).queue();
            }
        }

        public class MakelistCmd extends MusicCommand {
            public MakelistCmd(Bot bot) {
                super(bot);
                this.name = "make";
                this.aliases = new String[]{"create"};
                this.help = "Creates a new playlist";
                this.arguments = "<name>";
                this.guildOnly = true;
                this.ownerCommand = false;

                List<OptionData> options = new ArrayList<>();
                options.add(new OptionData(OptionType.STRING, "name", "Playlist name", true));
                this.options = options;
            }

            @Override
            public void doCommand(CommandEvent event) {

                String pName = event.getArgs().replaceAll("\\s+", "_");
                String guildId = event.getGuild().getId();

                if (pName == null || pName.isEmpty()) {
                    event.replyError("Please enter the name of the playlist.");
                } else if (bot.getPlaylistLoader().getPlaylist(guildId, pName) == null) {
                    try {
                        bot.getPlaylistLoader().createPlaylist(guildId, pName);
                        event.reply(event.getClient().getSuccess() + " Created playlist `" + pName + "`");
                    } catch (IOException e) {
                        if (event.isOwner() || event.getMember().isOwner()) {
                            event.replyError("An error occurred while loading the song.\n" +
                                    "**Error details: " + e.getLocalizedMessage() + "**");
                            StackTraceUtil.sendStackTrace(event.getTextChannel(), e);
                            return;
                        }

                        event.reply(event.getClient().getError() + " Could not create playlist.:" + e.getLocalizedMessage());
                    }
                } else {
                    event.reply(event.getClient().getError() + " Playlist `" + pName + "` already exists");
                }
            }

            @Override
            public void doCommand(SlashCommandEvent event) {
                if (!checkDJPermission(event.getClient(), event)) {
                    event.reply(event.getClient().getWarning() + "Cannot execute because you do not have permission.").queue();
                    return;
                }
                String pname = event.getOption("name").getAsString();
                String guildId = event.getGuild().getId();
                if (pname == null || pname.isEmpty()) {
                    event.reply(event.getClient().getError() + "Please enter the name of the playlist.").queue();
                } else if (bot.getPlaylistLoader().getPlaylist(guildId, pname) == null) {
                    try {
                        bot.getPlaylistLoader().createPlaylist(guildId, pname);
                        event.reply(event.getClient().getSuccess() + " Created playlist `" + pname + "`").queue();
                    } catch (IOException e) {
                        if (event.getClient().getOwnerId() == event.getMember().getId() || event.getMember().isOwner()) {
                            event.reply(event.getClient().getError() + "An error occurred while loading the song.\n" +
                                    "**Error details: " + e.getLocalizedMessage() + "**").queue();
                            StackTraceUtil.sendStackTrace(event.getTextChannel(), e);
                            return;
                        }

                        event.reply(event.getClient().getError() + " Could not create playlist.:" + e.getLocalizedMessage()).queue();
                    }
                } else {
                    event.reply(event.getClient().getError() + " Playlist `" + pname + "` already exists").queue();
                }
            }
        }

        public class DeletelistCmd extends MusicCommand {
            public DeletelistCmd(Bot bot) {
                super(bot);
                this.name = "delete";
                this.aliases = new String[]{"remove"};
                this.help = "Deletes an existing playlist";
                this.arguments = "<name>";
                this.guildOnly = true;
                this.ownerCommand = false;
                List<OptionData> options = new ArrayList<>();
                options.add(new OptionData(OptionType.STRING, "name", "Playlist name", true));
                this.options = options;
            }

            @Override
            public void doCommand(CommandEvent event) {

                String pname = event.getArgs().replaceAll("\\s+", "_");
                String guildid = event.getGuild().getId();
                if (!pname.equals("")) {
                    if (bot.getPlaylistLoader().getPlaylist(guildid, pname) == null)
                        event.reply(event.getClient().getError() + " Playlist does not exist:`" + pname + "`");
                    else {
                        try {
                            bot.getPlaylistLoader().deletePlaylist(guildid, pname);
                            event.reply(event.getClient().getSuccess() + " Deleted playlist:`" + pname + "`");
                        } catch (IOException e) {
                            event.reply(event.getClient().getError() + " Could not delete playlist: " + e.getLocalizedMessage());
                        }
                    }
                } else {
                    event.reply(event.getClient().getError() + "Please include the name of the playlist");
                }
            }

            @Override
            public void doCommand(SlashCommandEvent event) {
                if (!checkDJPermission(event.getClient(), event)) {
                    event.reply(event.getClient().getWarning() + "Cannot execute because you do not have permission.").queue();
                    return;
                }
                String pname = event.getOption("name").getAsString();
                String guildid = event.getGuild().getId();
                if (bot.getPlaylistLoader().getPlaylist(guildid, pname) == null)
                    event.reply(event.getClient().getError() + " Playlist does not exist:`" + pname + "`").queue();
                else {
                    try {
                        bot.getPlaylistLoader().deletePlaylist(guildid, pname);
                        event.reply(event.getClient().getSuccess() + " Deleted playlist:`" + pname + "`").queue();
                    } catch (IOException e) {
                        event.reply(event.getClient().getError() + " Could not delete playlist: " + e.getLocalizedMessage()).queue();
                    }
                }
            }
        }

        public class AppendlistCmd extends MusicCommand {
            public AppendlistCmd(Bot bot) {
                super(bot);
                this.name = "append";
                this.aliases = new String[]{"add"};
                this.help = "Adds a song to an existing playlist";
                this.arguments = "<name> <URL>| <URL> | ...";
                this.guildOnly = true;
                this.ownerCommand = false;
                List<OptionData> options = new ArrayList<>();
                options.add(new OptionData(OptionType.STRING, "name", "Playlist name", true));
                options.add(new OptionData(OptionType.STRING, "url", "URL", true));
                this.options = options;
            }

            @Override
            public void doCommand(CommandEvent event) {

                String[] parts = event.getArgs().split("\\s+", 2);
                String guildid = event.getGuild().getId();
                if (parts.length < 2) {
                    event.reply(event.getClient().getError() + " Please include the playlist name and URL to add to.");
                    return;
                }
                String pname = parts[0];
                PlaylistLoader.Playlist playlist = bot.getPlaylistLoader().getPlaylist(guildid, pname);
                if (playlist == null)
                    event.reply(event.getClient().getError() + " Playlist does not exist:`" + pname + "`");
                else {
                    StringBuilder builder = new StringBuilder();
                    playlist.getItems().forEach(item -> builder.append("\r\n").append(item));
                    String[] urls = parts[1].split("\\|");
                    for (String url : urls) {
                        String u = url.trim();
                        if (u.startsWith("<") && u.endsWith(">"))
                            u = u.substring(1, u.length() - 1);
                        builder.append("\r\n").append(u);
                    }
                    try {
                        bot.getPlaylistLoader().writePlaylist(guildid, pname, builder.toString());
                        event.reply(event.getClient().getSuccess() + urls.length + " items added to playlist:`" + pname + "`");
                    } catch (IOException e) {
                        event.reply(event.getClient().getError() + " Could not add to playlist: " + e.getLocalizedMessage());
                    }
                }
            }

            @Override
            public void doCommand(SlashCommandEvent event) {
                if (!checkDJPermission(event.getClient(), event)) {
                    event.reply(event.getClient().getWarning() + "Cannot execute because you do not have permission.").queue();
                    return;
                }

                String guildid = event.getGuild().getId();
                String pname = event.getOption("name").getAsString();
                PlaylistLoader.Playlist playlist = bot.getPlaylistLoader().getPlaylist(guildid, pname);
                if (playlist == null)
                    event.reply(event.getClient().getError() + " Playlist does not exist:`" + pname + "`").queue();
                else {
                    StringBuilder builder = new StringBuilder();
                    playlist.getItems().forEach(item -> builder.append("\r\n").append(item));
                    String[] urls = event.getOption("url").getAsString().split("\\|");
                    for (String url : urls) {
                        String u = url.trim();
                        if (u.startsWith("<") && u.endsWith(">"))
                            u = u.substring(1, u.length() - 1);
                        builder.append("\r\n").append(u);
                    }
                    try {
                        bot.getPlaylistLoader().writePlaylist(guildid, pname, builder.toString());
                        event.reply(event.getClient().getSuccess() + urls.length + " items added to playlist:`" + pname + "`").queue();
                    } catch (IOException e) {
                        event.reply(event.getClient().getError() + " Could not add to playlist: " + e.getLocalizedMessage()).queue();
                    }
                }
            }
        }

        public class ListCmd extends MusicCommand {
            public ListCmd(Bot bot) {
                super(bot);
                this.name = "all";
                this.aliases = new String[]{"available", "list"};
                this.help = "Displays all available playlists";
                this.guildOnly = true;
                this.ownerCommand = false;
            }

            @Override
            public void doCommand(CommandEvent event) {
                String guildId = event.getGuild().getId();

                if (!bot.getPlaylistLoader().folderGuildExists(guildId))
                    bot.getPlaylistLoader().createGuildFolder(guildId);
                if (!bot.getPlaylistLoader().folderGuildExists(guildId)) {
                    event.reply(event.getClient().getWarning() + " Could not create playlist folder because it does not exist.");
                    return;
                }
                List<String> list = bot.getPlaylistLoader().getPlaylistNames(guildId);
                if (list == null)
                    event.reply(event.getClient().getError() + " Could not load available playlists.");
                else if (list.isEmpty())
                    event.reply(event.getClient().getWarning() + " There are no playlists in the playlist folder.");
                else {
                    StringBuilder builder = new StringBuilder(event.getClient().getSuccess() + " Available playlists:\n");
                    list.forEach(str -> builder.append("`").append(str).append("` "));
                    event.reply(builder.toString());
                }
            }

            @Override
            public void doCommand(SlashCommandEvent event) {
                if (!checkDJPermission(event.getClient(), event)) {
                    event.reply(event.getClient().getWarning() + "Cannot execute because you do not have permission.").queue();
                    return;
                }
                String guildId = event.getGuild().getId();
                if (!bot.getPlaylistLoader().folderGuildExists(guildId))
                    bot.getPlaylistLoader().createGuildFolder(guildId);
                if (!bot.getPlaylistLoader().folderGuildExists(guildId)) {
                    event.reply(event.getClient().getWarning() + " Could not create playlist folder because it does not exist.").queue();
                    return;
                }
                List<String> list = bot.getPlaylistLoader().getPlaylistNames(guildId);
                if (list == null)
                    event.reply(event.getClient().getError() + " Could not load available playlists.").queue();
                else if (list.isEmpty())
                    event.reply(event.getClient().getWarning() + " There are no playlists in the playlist folder.").queue();
                else {
                    StringBuilder builder = new StringBuilder(event.getClient().getSuccess() + " Available playlists:\n");
                    list.forEach(str -> builder.append("`").append(str).append("` "));
                    event.reply(builder.toString()).queue();
                }
            }
        }
    }
