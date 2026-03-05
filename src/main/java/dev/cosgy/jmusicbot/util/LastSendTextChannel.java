package dev.cosgy.jmusicbot.util;

import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandListener;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

public class LastSendTextChannel implements CommandListener {
    // Bring the text channel ID with the guild ID.
    private static final HashMap<Long, Long> textChannel = new HashMap<>();
    static Logger log = LoggerFactory.getLogger("LastSendTextChannel");

    public static void SetLastTextId(CommandEvent event) {
        textChannel.put(event.getGuild().getIdLong(), event.getTextChannel().getIdLong());
    }

    public static long GetLastTextId(long guildId) {
        long id;
        if (textChannel.containsKey(guildId)) {
            id = textChannel.get(guildId);
        } else {
            id = 0;
        }
        return id;
    }

    public static void SendMessage(Guild guild, String message) {
        log.debug("Sending message.");
        long textId = GetLastTextId(guild.getIdLong());
        if (textId == 0) {
            log.debug("Could not send message because the channel was not saved.");
            return;
        }
        MessageChannel channel = guild.getTextChannelById(textId);
        channel.sendMessage(message).queue();
    }
}
