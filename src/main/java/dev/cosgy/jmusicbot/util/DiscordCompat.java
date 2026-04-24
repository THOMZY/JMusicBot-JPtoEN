package dev.cosgy.jmusicbot.util;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.awt.Color;

public final class DiscordCompat {
    private DiscordCompat() {
    }

    public static Member getSelfMember(Guild guild) {
        if (guild == null || guild.getJDA() == null || guild.getJDA().getSelfUser() == null) {
            return null;
        }
        return guild.getMember(guild.getJDA().getSelfUser());
    }

    /**
     * Replacement for the deprecated {@link Member#getColor()}. Returns the color of the highest
     * role that has a custom color, or {@code null} if the member has none.
     */
    @SuppressWarnings("deprecation")
    public static Color getMemberColor(Member member) {
        if (member == null) {
            return null;
        }
        for (Role role : member.getRoles()) {
            int raw = role.getColorRaw();
            if (raw != Role.DEFAULT_COLOR_RAW) {
                return new Color(raw);
            }
        }
        return null;
    }

    /**
     * Replacement for the deprecated {@link Role#getColor()}. Returns the role's color or
     * {@code null} when the role uses the default color.
     */
    @SuppressWarnings("deprecation")
    public static Color getRoleColor(Role role) {
        if (role == null) {
            return null;
        }
        int raw = role.getColorRaw();
        return raw == Role.DEFAULT_COLOR_RAW ? null : new Color(raw);
    }

    /**
     * Returns the raw color int of the highest custom-colored role for the member, or
     * {@link Role#DEFAULT_COLOR_RAW} when the member has none.
     */
    @SuppressWarnings("deprecation")
    public static int getMemberColorRaw(Member member) {
        if (member == null) {
            return Role.DEFAULT_COLOR_RAW;
        }
        for (Role role : member.getRoles()) {
            int raw = role.getColorRaw();
            if (raw != Role.DEFAULT_COLOR_RAW) {
                return raw;
            }
        }
        return Role.DEFAULT_COLOR_RAW;
    }
}
