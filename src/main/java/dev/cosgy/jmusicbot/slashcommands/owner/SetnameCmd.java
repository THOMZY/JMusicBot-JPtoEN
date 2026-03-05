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
package dev.cosgy.jmusicbot.slashcommands.owner;

import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import dev.cosgy.jmusicbot.slashcommands.OwnerCommand;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.ArrayList;
import java.util.List;

public class SetnameCmd extends OwnerCommand {
    protected Bot bot;

    public SetnameCmd(Bot bot) {
        this.bot = bot;
        this.name = "setname";
        this.help = "Sets the bot's name.";
        this.arguments = "<name>";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.guildOnly = false;

        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(OptionType.STRING, "name", "New bot name", true));
        this.options = options;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        try {
            String oldname = event.getJDA().getSelfUser().getName();
            event.getJDA().getSelfUser().getManager().setName(event.getOption("name").getAsString()).complete(false);
            event.reply(event.getClient().getSuccess() + "The bot's name has been changed from `" + oldname + "` to `" + event.getOption("name").getAsString() + "`.").queue();
        } catch (RateLimitedException e) {
            event.reply(event.getClient().getError() + "The name can only be changed twice per hour.").queue();
        } catch (Exception e) {
            event.reply(event.getClient().getError() + " That name is not available.").queue();
        }
    }
}