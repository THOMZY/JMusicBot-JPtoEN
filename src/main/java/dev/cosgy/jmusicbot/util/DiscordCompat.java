package dev.cosgy.jmusicbot.util;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;

public final class DiscordCompat {
    private DiscordCompat() {
    }

    public static Member getSelfMember(Guild guild) {
        if (guild == null || guild.getJDA() == null || guild.getJDA().getSelfUser() == null) {
            return null;
        }
        return guild.getMember(guild.getJDA().getSelfUser());
    }
}