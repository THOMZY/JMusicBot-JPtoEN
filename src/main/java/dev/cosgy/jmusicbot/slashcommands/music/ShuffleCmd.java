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
package dev.cosgy.jmusicbot.slashcommands.music;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.commands.Command;

import java.util.ArrayList;
import java.util.List;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class ShuffleCmd extends MusicCommand {
    public ShuffleCmd(Bot bot) {
        super(bot);
        this.name = "shuffle";
        this.help = "Shuffle the added tracks. Usage: shuffle [all|mytracks]";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = true;
        this.arguments = "[all|mytracks]";
        
        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(OptionType.STRING, "mode", "Choose shuffle mode")
            .setRequired(true)
            .addChoices(
                new Command.Choice("All tracks", "all"),
                new Command.Choice("My tracks only", "mytracks")
            ));
        this.options = options;
    }

    @Override
    public void doCommand(CommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        String args = event.getArgs().trim().toLowerCase();
        
        // Check if argument is provided
        if (args.isEmpty()) {
            event.replyError("Please specify a shuffle mode:\n" +
                "• `" + event.getClient().getPrefix() + "shuffle all` - Shuffle all tracks in the queue\n" +
                "• `" + event.getClient().getPrefix() + "shuffle mytracks` - Shuffle only your tracks");
            return;
        }
        
        boolean shuffleAll = args.equals("all");
        boolean myTracks = args.equals("mytracks");
        
        // Validate argument
        if (!shuffleAll && !myTracks) {
            event.replyError("Invalid argument. Use `all` to shuffle all tracks or `mytracks` to shuffle only your tracks.");
            return;
        }
        
        int s;
        if (shuffleAll) {
            s = handler.getQueue().shuffleAll();
        } else {
            s = handler.getQueue().shuffle(event.getAuthor().getIdLong());
        }
        
        switch (s) {
            case 0:
                event.replyError("There are no tracks in the queue!");
                break;
            case 1:
                event.replyWarning("There is currently only one track in the queue!");
                break;
            default:
                String message = shuffleAll ? "Shuffled all " + s + " tracks." : "Shuffled " + s + " of your tracks.";
                event.replySuccess(message);
                break;
        }
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        // Mode is now required, so it should always be present
        String mode = event.getOption("mode").getAsString();
        
        boolean shuffleAll = mode.equals("all");
        
        int s;
        if (shuffleAll) {
            s = handler.getQueue().shuffleAll();
        } else {
            s = handler.getQueue().shuffle(event.getUser().getIdLong());
        }
        
        switch (s) {
            case 0:
                event.reply(event.getClient().getError() + "There are no tracks in the queue!").queue();
                break;
            case 1:
                event.reply(event.getClient().getWarning() + "There is currently only one track in the queue!").queue();
                break;
            default:
                String message = shuffleAll ? "Shuffled all " + s + " tracks." : "Shuffled " + s + " of your tracks.";
                event.reply(event.getClient().getSuccess() + message).queue();
                break;
        }
    }
}
