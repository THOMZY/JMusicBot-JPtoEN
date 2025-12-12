/*
 *  Copyright 2025 THOMZY.
 */

package dev.cosgy.jmusicbot.slashcommands.admin;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.settings.Settings;
import dev.cosgy.jmusicbot.slashcommands.AdminCommand;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.List;
import java.util.Objects;

public class SettopicStatusCmd extends AdminCommand {
    public SettopicStatusCmd(Bot bot) {
        this.name = "settopicstatus";
        this.help = "Set whether or not to display 'Playing' in the text channel topic.";
        this.arguments = "<true|false>";
        this.aliases = bot.getConfig().getAliases(this.name);

        this.options = List.of(
            new OptionData(OptionType.BOOLEAN, "status", "Enabled: true / Disabled: false", true)
        );
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        if (checkAdminPermission(event.getClient(), event)) {
            event.reply(event.getClient().getWarning() + "You do not have permission to run this command.").queue();
            return;
        }

        boolean status = Objects.requireNonNull(event.getOption("status")).getAsBoolean();
        Settings s = event.getClient().getSettingsFor(event.getGuild());

        s.setTopicStatus(status);
        StringBuilder response = new StringBuilder(event.getClient().getSuccess())
                .append("Automatic text channel topic updates are now `").append(status).append("`.");
        if (s.getTextChannel(event.getGuild()) == null) {
            response.append(" You haven't set a text channel yet; use /settc to choose where the status is shown.");
        }
        event.reply(response.toString()).queue();
    }

    @Override
    protected void execute(CommandEvent event) {
        if (event.getArgs().isEmpty()) {
            event.reply(event.getClient().getError() + "Please specify true or false.");
            return;
        }

        Settings s = event.getClient().getSettingsFor(event.getGuild());
        String args = event.getArgs().toLowerCase();

        if (args.matches("(false|disabled)")) {
            s.setTopicStatus(false);
            String msg = event.getClient().getSuccess() + "Automatic topic updates are disabled.";
            if (s.getTextChannel(event.getGuild()) == null) {
                msg += " You haven't set a text channel yet; use /settc to choose where the status is shown.";
            }
            event.reply(msg);
        } else if (args.matches("(true|enabled)")) {
            s.setTopicStatus(true);
            String msg = event.getClient().getSuccess() + "Automatic topic updates are enabled.";
            if (s.getTextChannel(event.getGuild()) == null) {
                msg += " You haven't set a text channel yet; use /settc to choose where the status is shown.";
            }
            event.reply(msg);
        } else {
            event.reply(event.getClient().getError() + "Please specify true or false.");
        }
    }
}
