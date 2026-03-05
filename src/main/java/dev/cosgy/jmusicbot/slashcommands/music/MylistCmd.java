package dev.cosgy.jmusicbot.slashcommands.music;

import dev.cosgy.jmusicbot.framework.jdautilities.command.Command;
import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import dev.cosgy.jmusicbot.playlist.MylistLoader;
import dev.cosgy.jmusicbot.slashcommands.DJCommand;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import dev.cosgy.jmusicbot.util.StackTraceUtil;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author kosugikun
 */
public class MylistCmd extends MusicCommand {

    public MylistCmd(Bot bot) {
        super(bot);
        this.guildOnly = false;
        this.name = "mylist";
        this.arguments = "<append|delete|make|all|show>";
        this.help = "Manage your personal playlist";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.children = new MusicCommand[]{
                new PlayCmd(bot),
                new MakelistCmd(bot),
                new DeletelistCmd(bot),
                new AppendlistCmd(bot),
                new ListCmd(bot),
                new ShowTracksCmd(bot)
        };
    }

    @Override
    public void doCommand(CommandEvent event) {

        StringBuilder builder = new StringBuilder(event.getClient().getWarning() + " Mylist Management Commands:\n");
        for (Command cmd : this.children)
            builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" ").append(cmd.getName())
                    .append(" ").append(cmd.getArguments() == null ? "" : cmd.getArguments()).append("` - ").append(cmd.getHelp());
        event.reply(builder.toString());
    }

    @Override
    public void doCommand(SlashCommandEvent slashCommandEvent) {
    }

    public static class ShowTracksCmd extends MusicCommand {
        public ShowTracksCmd(Bot bot) {
            super(bot);
            this.name = "show";
            this.help = "Displays the tracks in the specified playlist";
            this.arguments = "<name>";
            this.guildOnly = false;
            this.ownerCommand = false;
            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "name", "Playlist name", true));
            this.options = options;
        }

        @Override
        public void doCommand(CommandEvent event) {
            String userId = event.getAuthor().getId();
            String playlistName = event.getArgs().trim();
            if (playlistName.isEmpty()) {
                event.reply(event.getClient().getError() + " Please specify the playlist name.");
                return;
            }
            MylistLoader.Playlist playlist = bot.getMylistLoader().getPlaylist(userId, playlistName);
            if (playlist == null) {
                event.reply(event.getClient().getError() + " The playlist `" + playlistName + "` could not be found.");
                return;
            }
            if (playlist.getTracks().isEmpty()) {
                event.reply(event.getClient().getWarning() + " There are no tracks in the playlist `" + playlistName + "`.");
                return;
            }
            StringBuilder builder = new StringBuilder(event.getClient().getSuccess() + " Tracks in the playlist `" + playlistName + "`:\n");
            for (int i = 0; i < playlist.getTracks().size(); i++) {
                builder.append(i + 1).append(". ").append(playlist.getTracks().get(i).getInfo().title).append("\n");
            }
            if (builder.length() > 2000) {
                builder.setLength(1997); // Ensure it does not exceed Discord's message limit
                builder.append("...");
            }
            event.reply(builder.toString());
        }

        @Override
        public void doCommand(SlashCommandEvent event) {
            String userId = event.getUser().getId();
            String playlistName = event.getOption("name").getAsString();
            MylistLoader.Playlist playlist = bot.getMylistLoader().getPlaylist(userId, playlistName);
            if (playlist == null) {
                event.reply(event.getClient().getError() + " The playlist `" + playlistName + "` could not be found.").queue();
                return;
            }
            if (playlist.getTracks().isEmpty()) {
                event.reply(event.getClient().getWarning() + " There are no tracks in the playlist `" + playlistName + "`").queue();
                return;
            }
            StringBuilder builder = new StringBuilder(event.getClient().getSuccess() + " Tracks in the playlist `" + playlistName + "`:\n");
            for (int i = 0; i < playlist.getTracks().size(); i++) {
                builder.append(i + 1).append(". ").append(playlist.getTracks().get(i).getInfo().title).append("\n");
            }
            if (builder.length() > 2000) {
                builder.setLength(1997); // Ensure it does not exceed Discord's message limit
                builder.append("...");
            }
            event.reply(builder.toString()).queue();
        }
    }

    public static class PlayCmd extends MusicCommand {
        public PlayCmd(Bot bot) {
            super(bot);
            this.name = "play";
            this.aliases = new String[]{"pl"};
            this.arguments = "<name>";
            this.help = "Play a mylist";
            this.beListening = true;
            this.bePlaying = false;

            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "name", "Mylist name", true));
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
            MylistLoadUtil.loadMylistForCommand(bot, event, playlist, event.getArgs());
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
            MylistLoadUtil.loadMylistForSlash(bot, event, playlist, name);
        }
    }


    public static class MakelistCmd extends DJCommand {
        public MakelistCmd(Bot bot) {
            super(bot);
            this.name = "make";
            this.aliases = new String[]{"create"};
            this.help = "Create a new playlist";
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
            String userId = event.getAuthor().getId();

            if (pName.isEmpty()) {
                event.replyError("Please specify a playlist name.");
                return;
            }

            if (bot.getMylistLoader().getPlaylist(userId, pName) == null) {
                try {
                    bot.getMylistLoader().createPlaylist(userId, pName);
                    event.reply(event.getClient().getSuccess() + "Created mylist `" + pName + "`");
                } catch (IOException e) {
                    if (event.isOwner() || event.getMember().isOwner()) {
                        event.replyError("An error occurred while loading the songs.\n" +
                                "**Error details: " + e.getLocalizedMessage() + "**");
                        StackTraceUtil.sendStackTrace(event.getTextChannel(), e);
                        return;
                    }

                    event.reply(event.getClient().getError() + " Could not create the mylist: " + e.getLocalizedMessage());
                }
            } else {
                event.reply(event.getClient().getError() + " Mylist `" + pName + "` already exists");
            }
        }

        @Override
        public void doCommand(SlashCommandEvent event) {
            String pName = event.getOption("name").getAsString().replaceAll("\\s+", "_");
            String userId = event.getUser().getId();

            if (pName.isEmpty()) {
                event.reply(event.getClient().getError() + "Please specify a playlist name.").queue();
                return;
            }

            if (bot.getMylistLoader().getPlaylist(userId, pName) == null) {
                try {
                    bot.getMylistLoader().createPlaylist(userId, pName);
                    event.reply(event.getClient().getSuccess() + "Created mylist `" + pName + "`").queue();
                } catch (IOException e) {
                    if (event.getClient().getOwnerId() == event.getMember().getId() || event.getMember().isOwner()) {
                        event.reply(event.getClient().getError() + "An error occurred while loading the songs.\n" +
                                "**Error details: " + e.getLocalizedMessage() + "**").queue();
                        StackTraceUtil.sendStackTrace(event.getTextChannel(), e);
                        return;
                    }

                    event.reply(event.getClient().getError() + " Could not create the mylist: " + e.getLocalizedMessage()).queue();
                }
            } else {
                event.reply(event.getClient().getError() + " Mylist `" + pName + "` already exists").queue();
            }
        }
    }

    public static class DeletelistCmd extends MusicCommand {
        public DeletelistCmd(Bot bot) {
            super(bot);
            this.name = "delete";
            this.aliases = new String[]{"remove"};
            this.help = "Delete an existing mylist";
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
            String userId = event.getAuthor().getId();
            if (!pName.equals("")) {
                if (bot.getMylistLoader().getPlaylist(userId, pName) == null)
                    event.reply(event.getClient().getError() + " Mylist does not exist: `" + pName + "`");
                else {
                    try {
                        bot.getMylistLoader().deletePlaylist(userId, pName);
                        event.reply(event.getClient().getSuccess() + " Deleted mylist: `" + pName + "`");
                    } catch (IOException e) {
                        event.reply(event.getClient().getError() + " Could not delete the mylist: " + e.getLocalizedMessage());
                    }
                }
            } else {
                event.reply(event.getClient().getError() + "Please include the mylist name");
            }
        }

        @Override
        public void doCommand(SlashCommandEvent event) {
            String pName = event.getOption("name").getAsString().replaceAll("\\s+", "_");
            String userId = event.getUser().getId();

            if (bot.getMylistLoader().getPlaylist(userId, pName) == null)
                event.reply(event.getClient().getError() + " Mylist does not exist: `" + pName + "`").queue();
            else {
                try {
                    bot.getMylistLoader().deletePlaylist(userId, pName);
                    event.reply(event.getClient().getSuccess() + " Deleted mylist: `" + pName + "`").queue();
                } catch (IOException e) {
                    event.reply(event.getClient().getError() + " Could not delete the mylist: " + e.getLocalizedMessage()).queue();
                }
            }
        }
    }

    public static class AppendlistCmd extends MusicCommand {
        public AppendlistCmd(Bot bot) {
            super(bot);
            this.name = "append";
            this.aliases = new String[]{"add"};
            this.help = "Add songs to an existing mylist";
            this.arguments = "<name> <URL> | <URL> | ...";
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
            String userId = event.getAuthor().getId();
            if (parts.length < 2) {
                event.reply(event.getClient().getError() + " Please include the mylist name and URLs to append.");
                return;
            }
            String pName = parts[0];
            MylistLoader.Playlist playlist = bot.getMylistLoader().getPlaylist(userId, pName);
            if (playlist == null)
                event.reply(event.getClient().getError() + " Mylist does not exist: `" + pName + "`");
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
                    bot.getMylistLoader().writePlaylist(userId, pName, builder.toString());
                    event.reply(event.getClient().getSuccess() + urls.length + " items added to mylist: `" + pName + "`");
                } catch (IOException e) {
                    event.reply(event.getClient().getError() + " Could not append to the mylist: " + e.getLocalizedMessage());
                }
            }
        }

        @Override
        public void doCommand(SlashCommandEvent event) {
            String userId = event.getUser().getId();
            String pname = event.getOption("name").getAsString();
            MylistLoader.Playlist playlist = bot.getMylistLoader().getPlaylist(userId, pname);
            if (playlist == null)
                event.reply(event.getClient().getError() + " Mylist does not exist: `" + pname + "`").queue();
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
                    bot.getMylistLoader().writePlaylist(userId, pname, builder.toString());
                    event.reply(event.getClient().getSuccess() + urls.length + " items added to mylist: `" + pname + "`").queue();
                } catch (IOException e) {
                    event.reply(event.getClient().getError() + " Could not append to the mylist: " + e.getLocalizedMessage()).queue();
                }
            }
        }
    }

    public static class ListCmd extends MusicCommand {
        public ListCmd(Bot bot) {
            super(bot);
            this.name = "all";
            this.aliases = new String[]{"available", "list"};
            this.help = "Display all available mylists";
            this.guildOnly = true;
            this.ownerCommand = false;
        }

        @Override
        public void doCommand(CommandEvent event) {
            String userId = event.getAuthor().getId();

            if (!bot.getMylistLoader().folderUserExists(userId))
                bot.getMylistLoader().createUserFolder(userId);
            if (!bot.getMylistLoader().folderUserExists(userId)) {
                event.reply(event.getClient().getWarning() + " Could not create mylist folder because it doesn't exist.");
                return;
            }
            List<String> list = bot.getMylistLoader().getPlaylistNames(userId);
            if (list == null)
                event.reply(event.getClient().getError() + " Could not load available mylists.");
            else if (list.isEmpty())
                event.reply(event.getClient().getWarning() + " There are no playlists in your mylist folder.");
            else {
                StringBuilder builder = new StringBuilder(event.getClient().getSuccess() + " Available mylists:\n");
                list.forEach(str -> builder.append("`").append(str).append("` "));
                event.reply(builder.toString());
            }
        }

        @Override
        public void doCommand(SlashCommandEvent event) {
            String userId = event.getUser().getId();

            if (!bot.getMylistLoader().folderUserExists(userId))
                bot.getMylistLoader().createUserFolder(userId);
            if (!bot.getMylistLoader().folderUserExists(userId)) {
                event.reply(event.getClient().getWarning() + " Could not create mylist folder because it doesn't exist.").queue();
                return;
            }
            List<String> list = bot.getMylistLoader().getPlaylistNames(userId);
            if (list == null)
                event.reply(event.getClient().getError() + " Could not load available mylists.").queue();
            else if (list.isEmpty())
                event.reply(event.getClient().getWarning() + " There are no playlists in your mylist folder.").queue();
            else {
                StringBuilder builder = new StringBuilder(event.getClient().getSuccess() + " Available mylists:\n");
                list.forEach(str -> builder.append("`").append(str).append("` "));
                event.reply(builder.toString()).queue();
            }
        }
    }
}
