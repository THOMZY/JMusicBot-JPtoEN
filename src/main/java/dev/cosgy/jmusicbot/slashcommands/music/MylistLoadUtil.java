package dev.cosgy.jmusicbot.slashcommands.music;

import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import dev.cosgy.jmusicbot.playlist.MylistLoader;

public final class MylistLoadUtil {
    private MylistLoadUtil() {
    }

    public static void loadMylistForCommand(Bot bot, CommandEvent event, MylistLoader.Playlist playlist, String playlistName) {
        event.getChannel().sendMessage(":calling: Loading mylist **" + playlistName + "**... (" + playlist.getItems().size() + " tracks)")
                .queue(m -> {
                    AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                    playlist.loadTracks(bot.getPlayerManager(), at -> handler.addTrack(new QueuedTrack(at, event.getAuthor())), () -> {
                        String str = buildMylistLoadResult(event.getClient().getWarning(), event.getClient().getSuccess(), playlist);
                        m.editMessage(FormatUtil.filter(str)).queue();
                    });
                });
    }

    public static void loadMylistForSlash(Bot bot, SlashCommandEvent event, MylistLoader.Playlist playlist, String playlistName) {
        event.reply(":calling: Loading mylist **" + playlistName + "**... (" + playlist.getItems().size() + " tracks)")
                .queue(m -> {
                    AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                    playlist.loadTracks(bot.getPlayerManager(), at -> handler.addTrack(new QueuedTrack(at, event.getUser())), () -> {
                        String str = buildMylistLoadResult(event.getClient().getWarning(), event.getClient().getSuccess(), playlist);
                        m.editOriginal(FormatUtil.filter(str)).queue();
                    });
                });
    }

    public static String buildMylistLoadResult(String warningPrefix, String successPrefix, MylistLoader.Playlist playlist) {
        StringBuilder builder = new StringBuilder(playlist.getTracks().isEmpty()
                ? warningPrefix + " No tracks were loaded."
                : successPrefix + " Loaded **" + playlist.getTracks().size() + "** tracks.");
        if (!playlist.getErrors().isEmpty()) {
            builder.append("\nThe following tracks could not be loaded:");
        }
        playlist.getErrors().forEach(err -> builder.append("\n`[").append(err.getIndex() + 1)
                .append("]` **").append(err.getItem()).append("**: ").append(err.getReason()));
        String str = builder.toString();
        if (str.length() > 2000) {
            str = str.substring(0, 1994) + " (truncated)";
        }
        return str;
    }
}
