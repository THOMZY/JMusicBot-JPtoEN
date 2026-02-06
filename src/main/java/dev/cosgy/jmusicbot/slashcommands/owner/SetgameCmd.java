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

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import dev.cosgy.jmusicbot.slashcommands.OwnerCommand;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.util.ArrayList;
import java.util.List;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class SetgameCmd extends OwnerCommand {
    public SetgameCmd(Bot bot) {
        this.name = "setgame";
        this.help = "Sets the game the bot is playing";
        this.arguments = "[action] [game]";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.guildOnly = false;
        this.children = new OwnerCommand[]{
                new PlayingCmd(),
                new SetlistenCmd(),
                new SetstreamCmd(),
                new SetwatchCmd(),
                new SetCompetingCmd(),
                new NoneCmd()
        };
    }

    @Override
    protected void execute(SlashCommandEvent slashCommandEvent) {
    }

    @Override
    protected void execute(CommandEvent event) {
        String title = event.getArgs().toLowerCase().startsWith("playing") ? event.getArgs().substring(7).trim() : event.getArgs();
        try {
            event.getJDA().getPresence().setActivity(title.isEmpty() ? null : Activity.playing(title));
            event.reply(event.getClient().getSuccess() + " **" + event.getSelfUser().getName()
                    + "** is now " + (title.isEmpty() ? "doing nothing." : "playing `" + title + "`."));
        } catch (Exception e) {
            event.reply(event.getClient().getError() + " Could not set the status.");
        }
    }

    private class NoneCmd extends OwnerCommand {
        private NoneCmd() {
            this.name = "none";
            this.aliases = new String[]{"none"};
            this.help = "Resets the status.";
            this.guildOnly = false;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            event.getJDA().getPresence().setActivity(null);
            event.reply("Status has been reset.").queue();
        }

        @Override
        protected void execute(CommandEvent event) {
            event.getJDA().getPresence().setActivity(null);
            event.reply("Status has been reset.");
        }
    }

    private class PlayingCmd extends OwnerCommand {
        private PlayingCmd() {
            this.name = "playing";
            this.aliases = new String[]{"twitch", "streaming"};
            this.help = "Sets the game the bot is playing.";
            this.arguments = "<title>";
            this.guildOnly = false;
            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "title", "Title of the game", true));
            this.options = options;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            String title = event.getOption("title").getAsString();
            try {
                event.getJDA().getPresence().setActivity(Activity.playing(title));
                event.reply(event.getClient().getSuccess() + " **" + event.getJDA().getSelfUser().getName()
                        + "** is now playing `" + title + "`.");
            } catch (Exception e) {
                event.reply(event.getClient().getError() + " Could not set the status.").queue();
            }
        }

        @Override
        protected void execute(CommandEvent event) {
        }
    }

    private class SetstreamCmd extends OwnerCommand {
        private SetstreamCmd() {
            this.name = "stream";
            this.aliases = new String[]{"twitch", "streaming"};
            this.help = "Sets the game the bot is streaming.";
            this.arguments = "<username> <game>";
            this.guildOnly = false;
            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "user", "Username", true));
            options.add(new OptionData(OptionType.STRING, "game", "Game title", true));
            this.options = options;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            try {
                event.getJDA().getPresence().setActivity(Activity.streaming(event.getOption("game").getAsString(), "https://twitch.tv/" + event.getOption("user").getAsString()));
                event.reply(event.getClient().getSuccess() + "**" + event.getJDA().getSelfUser().getName()
                        + "** is now streaming `" + event.getOption("game").getAsString() + "`.").queue();
            } catch (Exception e) {
                event.reply(event.getClient().getError() + " Could not set the game.").queue();
            }
        }

        @Override
        protected void execute(CommandEvent event) {
            String[] parts = event.getArgs().split("\\s+", 2);
            if (parts.length < 2) {
                event.replyError("Please enter a username and the name of the game to stream.");
                return;
            }
            try {
                event.getJDA().getPresence().setActivity(Activity.streaming(parts[1], "https://twitch.tv/" + parts[0]));
                event.replySuccess("**" + event.getSelfUser().getName()
                        + "** is now streaming `" + parts[1] + "`.");
            } catch (Exception e) {
                event.reply(event.getClient().getError() + " Could not set the game.");
            }
        }
    }

    private class SetlistenCmd extends OwnerCommand {
        private SetlistenCmd() {
            this.name = "listen";
            this.aliases = new String[]{"listening"};
            this.help = "Sets the game the bot is listening to.";
            this.arguments = "<title>";
            this.guildOnly = false;
            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "title", "Title", true));
            this.options = options;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            String title = event.getOption("title").getAsString();
            if(title.length() > 128) {
                title = title.substring(0, 128);
            }
            try {
                event.getJDA().getPresence().setActivity(Activity.listening(title));
                event.reply(event.getClient().getSuccess() + "**" + event.getJDA().getSelfUser().getName() + "** is now listening to `" + title + "`.").queue();
            } catch (Exception e) {
                event.reply(event.getClient().getError() + " Could not set the game.").queue();
            }
        }

        @Override
        protected void execute(CommandEvent event) {
            if (event.getArgs().isEmpty()) {
                event.replyError("Please include a title to listen to!");
                return;
            }
            String title = event.getArgs().toLowerCase().startsWith("to") ? event.getArgs().substring(2).trim() : event.getArgs();
            if(title.length() > 128) {
                title = title.substring(0, 128);
            }
            try {
                event.getJDA().getPresence().setActivity(Activity.listening(title));
                event.replySuccess("**" + event.getSelfUser().getName() + "** is now listening to `" + title + "`.");
            } catch (Exception e) {
                event.reply(event.getClient().getError() + " Could not set the game.");
            }
        }
    }

    private class SetwatchCmd extends OwnerCommand {
        private SetwatchCmd() {
            this.name = "watch";
            this.aliases = new String[]{"watching"};
            this.help = "Sets the game the bot is watching.";
            this.arguments = "<title>";
            this.guildOnly = false;
            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "title", "Title", true));
            this.options = options;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            String title = event.getOption("title").getAsString();
            try {
                event.getJDA().getPresence().setActivity(Activity.watching(title));
                event.reply(event.getClient().getSuccess() + "**" + event.getJDA().getSelfUser().getName() + "** is now watching `" + title + "`.").queue();
            } catch (Exception e) {
                event.reply(event.getClient().getError() + " Could not set the game.").queue();
            }
        }

        @Override
        protected void execute(CommandEvent event) {
            if (event.getArgs().isEmpty()) {
                event.replyError("Please enter a title to watch.");
                return;
            }
            String title = event.getArgs();
            try {
                event.getJDA().getPresence().setActivity(Activity.watching(title));
                event.replySuccess("**" + event.getSelfUser().getName() + "** is now watching `" + title + "`.");
            } catch (Exception e) {
                event.reply(event.getClient().getError() + " Could not set the game.");
            }
        }
    }

    private class SetCompetingCmd extends OwnerCommand {
        private SetCompetingCmd() {
            this.name = "competing";
            this.help = "Sets the game the bot is competing in.";
            this.arguments = "<title>";
            this.guildOnly = false;
            List<OptionData> options = new ArrayList<>();
            options.add(new OptionData(OptionType.STRING, "title", "Game title", true));
            this.options = options;
        }

        @Override
        protected void execute(SlashCommandEvent event) {
            String title = event.getOption("title").getAsString();
            try {
                event.getJDA().getPresence().setActivity(Activity.competing(title));
                event.reply(event.getClient().getSuccess() + "**" + event.getJDA().getSelfUser().getName() + "** is now competing in `" + title + "`.").queue();
            } catch (Exception e) {
                event.reply(event.getClient().getError() + " Could not set the game.").queue();
            }
        }

        @Override
        protected void execute(CommandEvent event) {
            if (event.getArgs().isEmpty()) {
                event.replyError("Please enter a title to compete in.");
                return;
            }
            String title = event.getArgs();
            try {
                event.getJDA().getPresence().setActivity(Activity.watching(title));
                event.replySuccess("**" + event.getSelfUser().getName() + "** is now competing in `" + title + "`.");
            } catch (Exception e) {
                event.reply(event.getClient().getError() + " Could not set the game.");
            }
        }
    }
}
