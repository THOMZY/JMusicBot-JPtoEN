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

import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import dev.cosgy.jmusicbot.slashcommands.OwnerCommand;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class ShutdownCmd extends OwnerCommand {
    private final Bot bot;

    public ShutdownCmd(Bot bot) {
        this.bot = bot;
        this.name = "shutdown";
        this.help = "Shutdown safely";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.guildOnly = false;
    }

    @Override
    protected void execute(SlashCommandEvent event) {
        event.reply(event.getClient().getWarning() + "Shutting down...\nThere may be issues that prevent a proper shutdown. If that occurs, please forcibly stop the bot.").queue();
        bot.shutdown();
    }

    @Override
    protected void execute(CommandEvent event) {
        event.replyWarning("Shutting down...\nThere may be issues that prevent a proper shutdown. If that occurs, please forcibly stop the bot.");
        bot.shutdown();
    }
}