/*
 * Copyright 2018-2020 Cosgy Dev
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package dev.cosgy.jmusicbot.slashcommands.dj;


import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.queue.FairQueue;
import dev.cosgy.jmusicbot.slashcommands.DJCommand;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * A command that allows the user to move tracks within a playlist.
 */
public class MoveTrackCmd extends DJCommand {

    public MoveTrackCmd(Bot bot) {
        super(bot);
        this.name = "movetrack";
        this.help = "Changes the play order of songs in the queue";
        this.arguments = "<from> <to>";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = true;

        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(OptionType.INTEGER, "from", "From position", true));
        options.add(new OptionData(OptionType.INTEGER, "to", "To position", true));
        this.options = options;

    }

    private static boolean isUnavailablePosition(FairQueue<QueuedTrack> queue, int position) {
        return (position < 1 || position > queue.size());
    }

    @Override
    public void doCommand(CommandEvent event) {
        Logger log = LoggerFactory.getLogger("MoveTrack");
        int from;
        int to;

        String[] parts = event.getArgs().split("\\s+", 2);
        if (parts.length < 2) {
            event.replyError("Please include two valid numbers.");
            return;
        }

        try {
            // Validate the arguments
            from = Integer.parseInt(parts[0]);
            to = Integer.parseInt(parts[1]);
        } catch (NumberFormatException e) {
            event.replyError("Please include two valid numbers.");
            return;
        }

        if (from == to) {
            event.replyError("You cannot move the track to the same position.");
            return;
        }

        // Validate that 'from' and 'to' are valid positions
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        FairQueue<QueuedTrack> queue = handler.getQueue();
        if (isUnavailablePosition(queue, from)) {
            String reply = String.format("`%d` is not a valid position in the queue.", from);
            event.replyError(reply);
            return;
        }
        if (isUnavailablePosition(queue, to)) {
            String reply = String.format("`%d` is not a valid position in the queue.", to);
            event.replyError(reply);
            return;
        }

        // Move the track
        QueuedTrack track = queue.moveItem(from - 1, to - 1);
        String trackTitle = track.getTrack().getInfo().title;
        String reply = String.format("Moved **%s** from position `%d` to `%d`.", trackTitle, from, to);
        log.info("Moved **" + trackTitle + "** from position " + from + " to position " + to + " in " + event.getGuild().getName());
        event.replySuccess(reply);
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        if (!checkDJPermission(event.getClient(), event)) {
            event.reply(event.getClient().getWarning() + "You do not have permission to execute this command.").queue();
            return;
        }
        int from;
        int to;

        from = Integer.parseInt(event.getOption("from").getAsString());
        to = Integer.parseInt(event.getOption("to").getAsString());

        if (from == to) {
            event.reply(event.getClient().getError() + "You cannot move the track to the same position.").queue();
            return;
        }

        // Validate that 'from' and 'to' are valid positions
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        FairQueue<QueuedTrack> queue = handler.getQueue();
        if (isUnavailablePosition(queue, from)) {
            String reply = String.format("`%d` is not a valid position in the queue.", from);
            event.reply(event.getClient().getError() + reply).queue();
            return;
        }
        if (isUnavailablePosition(queue, to)) {
            String reply = String.format("`%d` is not a valid position in the queue.", to);
            event.reply(event.getClient().getError() + reply).queue();
            return;
        }

        // Move the track
        QueuedTrack track = queue.moveItem(from - 1, to - 1);
        String trackTitle = track.getTrack().getInfo().title;
        String reply = String.format("Moved **%s** from position `%d` to `%d`.", trackTitle, from, to);
        event.reply(event.getClient().getSuccess() + reply).queue();
    }
}
