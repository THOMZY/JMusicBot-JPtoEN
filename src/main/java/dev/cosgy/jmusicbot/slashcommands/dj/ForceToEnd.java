/*
 *  Copyright 2025 THOMZY
 *
 * Translated from ForceToEnd.kt
 */

package dev.cosgy.jmusicbot.slashcommands.dj;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import dev.cosgy.jmusicbot.slashcommands.DJCommand;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.Collections;

public class ForceToEnd extends DJCommand {
    
    public ForceToEnd(Bot bot) {
        super(bot);
        this.name = "forcetoend";
        this.help = "Toggles between fair and normal song addition modes. `TRUE` enables normal mode.";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.options = Collections.singletonList(new OptionData(OptionType.BOOLEAN, "value", "Whether to use normal addition mode", true));
    }

    @Override
    public void doCommand(CommandEvent event) {
        Boolean nowSetting = bot.getSettingsManager().getSettings(event.getGuild()).isForceToEndQue();
        boolean newSetting = false;

        if (event.getArgs().isEmpty()) {
            newSetting = !nowSetting;
        } else if (event.getArgs().equalsIgnoreCase("true") || event.getArgs().equalsIgnoreCase("on") || event.getArgs().equalsIgnoreCase("enabled")) {
            newSetting = true;
        } else if (event.getArgs().equalsIgnoreCase("false") || event.getArgs().equalsIgnoreCase("off") || event.getArgs().equalsIgnoreCase("disabled")) {
            newSetting = false;
        }

        bot.getSettingsManager().getSettings(event.getGuild()).setForceToEndQue(newSetting);

        String msg = "Changed the way songs are added to the queue.\nSetting:";
        if (newSetting) {
            msg += "Normal addition mode\nSongs will be added to the end of the queue.";
        } else {
            msg += "Fair addition mode\nSongs will be added to the queue in a fair order.";
        }

        event.replySuccess(msg);
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        Boolean nowSetting = bot.getSettingsManager().getSettings(event.getGuild()).isForceToEndQue();
        boolean newSetting;

        newSetting = event.getOption("value").getAsBoolean();

        bot.getSettingsManager().getSettings(event.getGuild()).setForceToEndQue(newSetting);

        String msg = "Changed the way songs are added to the queue.\nSetting:";
        if (newSetting) {
            msg += "Normal addition mode\nSongs will be added to the end of the queue.";
        } else {
            msg += "Fair addition mode\nSongs will be added to the queue in a fair order.";
        }

        event.reply(msg).queue();
    }
} 