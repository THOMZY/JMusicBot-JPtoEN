package dev.cosgy.jmusicbot.slashcommands.owner;

import dev.cosgy.jmusicbot.framework.jdautilities.command.Command;
import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import dev.cosgy.jmusicbot.playlist.MylistLoader;
import dev.cosgy.jmusicbot.playlist.PubliclistLoader;
import dev.cosgy.jmusicbot.playlist.PubliclistLoader.Playlist;
import dev.cosgy.jmusicbot.slashcommands.OwnerCommand;
import dev.cosgy.jmusicbot.slashcommands.admin.AutoplaylistCmd;
import dev.cosgy.jmusicbot.slashcommands.music.MylistCmd;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * @author kosugikun
 */
public class PublistCmd extends OwnerCommand {
    private final Bot bot;

    public PublistCmd(Bot bot) {
        this.bot = bot;
        this.guildOnly = false;
        this.name = "publist";
        this.arguments = "<append|delete|make|all|show>";
        this.help = "Playlist Management";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.children = new OwnerCommand[]{
                new ListCmd(),
                new AppendlistCmd(),
                new DeletelistCmd(),
                new MakelistCmd(),
                new ShowTracksCmd()
        };
    }

    @Override
    protected void execute(SlashCommandEvent slashCommandEvent) {

    }

    @Override
    public void execute(CommandEvent event) {
        StringBuilder builder = new StringBuilder(event.getClient().getWarning() + " Playlist management commands:\n");
        for (Command cmd : this.children)
            builder.append("\n`").append(event.getClient().getPrefix()).append(name).append(" ").append(cmd.getName())
                    .append(" ").append(cmd.getArguments() == null ? "" : cmd.getArguments()).append("` - ").append(cmd.getHelp());
        event.reply(builder.toString());
    }

    public static class DefaultlistCmd extends AutoplaylistCmd {
        public DefaultlistCmd(Bot bot) {
            super(bot);
            this.name = "setdefault";
            this.aliases = new String[]{"default"};
            this.arguments = "<playlistname|NONE>";
            this.guildOnly = true;
        }
    }

    public class ShowTracksCmd extends OwnerCommand {
        public ShowTracksCmd() {
            this.name = "show";
            this.help = "Displays the tracks in the specified playlist";
            this.arguments = "<name>";
            this.guildOnly = false;
            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "name", "Playlist name", true));
            this.options = options;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            String playlistName = event.getOption("name").getAsString();
            PubliclistLoader.Playlist playlist = bot.getPublistLoader().getPlaylist(playlistName);
            if (playlist == null) {
                event.reply(event.getClient().getError() + " The playlist `" + playlistName + "` could not be found.").queue();
                return;
            }
            if (playlist.getItems().isEmpty()) {
                event.reply(event.getClient().getWarning() + " There are no tracks in the playlist `" + playlistName + "`").queue();
                return;
            }
            StringBuilder builder = new StringBuilder(event.getClient().getSuccess() + " Tracks in the playlist `" + playlistName + "`:\n");
            for (int i = 0; i < playlist.getItems().size(); i++) {
                builder.append(i + 1).append(". ").append(playlist.getItems().get(i)).append("\n");
            }
            if (builder.length() > 2000) {
                builder.setLength(1997); // Adjust for Discord's message limit
                builder.append("...");
            }
            event.reply(builder.toString()).queue();
        }

        @Override
        protected void execute(CommandEvent event) {
            String playlistName = event.getArgs().trim();
            if (playlistName.isEmpty()) {
                event.reply(event.getClient().getError() + " Please specify the playlist name.");
                return;
            }
            PubliclistLoader.Playlist playlist = bot.getPublistLoader().getPlaylist(playlistName);
            if (playlist == null) {
                event.reply(event.getClient().getError() + " The playlist `" + playlistName + "` could not be found.");
                return;
            }
            if (playlist.getItems().isEmpty()) {
                event.reply(event.getClient().getWarning() + " There are no tracks in the playlist `" + playlistName + "`.");
                return;
            }
            StringBuilder builder = new StringBuilder(event.getClient().getSuccess() + " Tracks in the playlist `" + playlistName + "`:\n");
            for (int i = 0; i < playlist.getItems().size(); i++) {
                builder.append(i + 1).append(". ").append(playlist.getItems().get(i)).append("\n");
            }
            if (builder.length() > 2000) {
                builder.setLength(1997); // Adjust for Discord's message limit
                builder.append("...");
            }
            event.reply(builder.toString());
        }
    }

    public class MakelistCmd extends OwnerCommand {
        public MakelistCmd() {
            this.name = "make";
            this.aliases = new String[]{"create"};
            this.help = "Creates a new playlist";
            this.arguments = "<name>";
            this.guildOnly = false;

            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "name", "Playlist name", true));
            this.options = options;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            String pname = event.getOption("name").getAsString().replaceAll("\\s+", "_");
            if (bot.getPublistLoader().getPlaylist(pname) == null) {
                try {
                    bot.getPublistLoader().createPlaylist(pname);
                    event.reply(event.getClient().getSuccess() + " Created a playlist named `" + pname + "`!").queue();
                } catch (IOException e) {
                    event.reply(event.getClient().getError() + " Could not create the playlist: " + e.getLocalizedMessage()).queue();
                }
            } else
                event.reply(event.getClient().getError() + " The playlist `" + pname + "` already exists!").queue();
        }

        @Override
        protected void execute(CommandEvent event) {
            String pname = event.getArgs().replaceAll("\\s+", "_");
            if (bot.getPublistLoader().getPlaylist(pname) == null) {
                try {
                    bot.getPublistLoader().createPlaylist(pname);
                    event.reply(event.getClient().getSuccess() + " Created a playlist named `" + pname + "`!");
                } catch (IOException e) {
                    event.reply(event.getClient().getError() + " Could not create the playlist: " + e.getLocalizedMessage());
                }
            } else
                event.reply(event.getClient().getError() + " The playlist `" + pname + "` already exists!");
        }
    }

    public class DeletelistCmd extends OwnerCommand {
        public DeletelistCmd() {
            this.name = "delete";
            this.aliases = new String[]{"remove"};
            this.help = "Deletes an existing playlist";
            this.arguments = "<name>";
            this.guildOnly = false;

            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "name", "Playlist name", true));
            this.options = options;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            String pname = event.getOption("name").getAsString().replaceAll("\\s+", "_");
            if (bot.getPublistLoader().getPlaylist(pname) == null)
                event.reply(event.getClient().getError() + " The playlist `" + pname + "` does not exist!").queue();
            else {
                try {
                    bot.getPublistLoader().deletePlaylist(pname);
                    event.reply(event.getClient().getSuccess() + " Deleted the playlist `" + pname + "`.").queue();
                } catch (IOException e) {
                    event.reply(event.getClient().getError() + " Could not delete the playlist: " + e.getLocalizedMessage()).queue();
                }
            }
        }

        @Override
        protected void execute(CommandEvent event) {
            String pname = event.getArgs().replaceAll("\\s+", "_");
            if (bot.getPublistLoader().getPlaylist(pname) == null)
                event.reply(event.getClient().getError() + " The playlist `" + pname + "` does not exist!");
            else {
                try {
                    bot.getPublistLoader().deletePlaylist(pname);
                    event.reply(event.getClient().getSuccess() + " Deleted the playlist `" + pname + "`.");
                } catch (IOException e) {
                    event.reply(event.getClient().getError() + " Could not delete the playlist: " + e.getLocalizedMessage());
                }
            }
        }
    }

    public class AppendlistCmd extends OwnerCommand {
        public AppendlistCmd() {
            this.name = "append";
            this.aliases = new String[]{"add"};
            this.help = "Adds tracks to an existing playlist";
            this.arguments = "<name> <URL> | <URL> | ...";
            this.guildOnly = false;
            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "name", "Playlist name", true));
            options.add(new OptionData(OptionType.STRING, "url", "URL", true));
            this.options = options;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            String pname = event.getOption("name").getAsString();
            Playlist playlist = bot.getPublistLoader().getPlaylist(pname);
            if (playlist == null)
                event.reply(event.getClient().getError() + " The playlist `" + pname + "` does not exist!").queue();
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
                    bot.getPublistLoader().writePlaylist(pname, builder.toString());
                    event.reply(event.getClient().getSuccess() + " Added " + urls.length + " items to the playlist `" + pname + "`!").queue();
                } catch (IOException e) {
                    event.reply(event.getClient().getError() + " Could not add to the playlist: " + e.getLocalizedMessage()).queue();
                }
            }
        }

        @Override
        protected void execute(CommandEvent event) {
            String[] parts = event.getArgs().split("\\s+", 2);
            if (parts.length < 2) {
                event.reply(event.getClient().getError() + " Please include the name of the playlist to append to and URLs.");
                return;
            }
            String pname = parts[0];
            Playlist playlist = bot.getPublistLoader().getPlaylist(pname);
            if (playlist == null)
                event.reply(event.getClient().getError() + " The playlist `" + pname + "` does not exist!");
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
                    bot.getPublistLoader().writePlaylist(pname, builder.toString());
                    event.reply(event.getClient().getSuccess() + " Added " + urls.length + " items to the playlist `" + pname + "`!");
                } catch (IOException e) {
                    event.reply(event.getClient().getError() + " Could not add to the playlist: " + e.getLocalizedMessage());
                }
            }
        }
    }

    public class ListCmd extends OwnerCommand {
        public ListCmd() {
            this.name = "all";
            this.aliases = new String[]{"available", "list"};
            this.help = "Displays all available playlists.";
            this.guildOnly = true;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            if (!bot.getPublistLoader().folderExists())
                bot.getPublistLoader().createFolder();
            if (!bot.getPublistLoader().folderExists()) {
                event.reply(event.getClient().getWarning() + " Could not create because the playlist folder does not exist.").queue();
                return;
            }
            List<String> list = bot.getPublistLoader().getPlaylistNames();
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

        @Override
        protected void execute(CommandEvent event) {
            if (!bot.getPublistLoader().folderExists())
                bot.getPublistLoader().createFolder();
            if (!bot.getPublistLoader().folderExists()) {
                event.reply(event.getClient().getWarning() + " Could not create because the playlist folder does not exist.");
                return;
            }
            List<String> list = bot.getPublistLoader().getPlaylistNames();
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
    }
}
