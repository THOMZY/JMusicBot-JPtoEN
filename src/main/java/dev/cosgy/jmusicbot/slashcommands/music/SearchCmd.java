/*
 * Copyright 2018 John Grosh (jagrosh).
 * Edit 2025 THOMZY
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
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.ActionRow;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class SearchCmd extends MusicCommand {
    private final String searchingEmoji;
    protected String searchPrefix = "ytsearch:";

    public SearchCmd(Bot bot) {
        super(bot);
        this.searchingEmoji = bot.getConfig().getSearching();
        this.name = "search";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.arguments = "<query>";
        this.help = "Searches YouTube for videos using the specified string.";
        this.beListening = true;
        this.bePlaying = false;
        this.botPermissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS};

        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(OptionType.STRING, "input", "Search keyword", true));
        this.options = options;
    }

    @Override
    public void doCommand(CommandEvent event) {
        if (event.getArgs().isEmpty()) {
            event.replyError("Please specify a string.");
            return;
        }
        event.reply(searchingEmoji + "Searching for `[" + event.getArgs() + "]`... ",
                m -> bot.getPlayerManager().loadItemOrdered(event.getGuild(), searchPrefix + event.getArgs(), new ResultHandler(m, event)));
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        event.reply(searchingEmoji + "Searching for `[" + event.getOption("input").getAsString() + "]`... ").queue(
                m -> bot.getPlayerManager().loadItemOrdered(event.getGuild(), searchPrefix + event.getOption("input").getAsString(), new SlashResultHandler(m, event)));
    }

    private class SlashResultHandler implements AudioLoadResultHandler {
        private final InteractionHook m;
        private final SlashCommandEvent event;

        private SlashResultHandler(InteractionHook m, SlashCommandEvent event) {
            this.m = m;
            this.event = event;
        }

        @Override
        public void trackLoaded(AudioTrack track) {
            if (bot.getConfig().isTooLong(track)) {
                m.editOriginal(FormatUtil.filter(event.getClient().getWarning() + "**" + track.getInfo().title + "**` is longer than the allowed maximum length. "
                        + FormatUtil.formatTime(track.getDuration()) + "` > `" + bot.getConfig().getMaxTime() + "`")).queue();
                return;
            }
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            int pos = handler.addTrack(new QueuedTrack(track, event.getUser())) + 1;
            m.editOriginal(FormatUtil.filter(event.getClient().getSuccess() + "Added **" + track.getInfo().title
                    + "**(`" + FormatUtil.formatTime(track.getDuration()) + "`) to the queue" + (pos == 0 ? "."
                    : " at position " + pos))).queue();
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(event.getGuild().getSelfMember().getColor())
                    .setTitle(FormatUtil.filter(event.getClient().getSuccess() + " Search results for `" + event.getOption("input").getAsString() + "`:"));
                    
            StringBuilder description = new StringBuilder();
            description.append("Choose a track to play :\n\n");

            // Create a list for the buttons and choices
            List<Button> buttons = new ArrayList<>();
            List<String> choices = new ArrayList<>();
            
            for (int i = 0; i < 4 && i < playlist.getTracks().size(); i++) {
                AudioTrack track = playlist.getTracks().get(i);
                String choice = "`[" + FormatUtil.formatTime(track.getDuration()) + "]` [**" + track.getInfo().title + "**](" + track.getInfo().uri + ")";
                choices.add(choice);
                buttons.add(Button.primary("search:" + i, String.valueOf(i + 1)));
            }
            
            // Add all choices to description
            for (int i = 0; i < choices.size(); i++) {
                description.append("**").append(i + 1).append(".** ").append(choices.get(i)).append("\n\n");
            }
            
            embed.setDescription(description.toString());
            
            // Add cancel button
            buttons.add(Button.danger("search:cancel", "❌ Cancel"));
            
            // Create the action row with buttons
            ActionRow actionRow = ActionRow.of(buttons);
            
            // Send the message with buttons
            event.getChannel().sendMessageEmbeds(embed.build())
                    .setComponents(actionRow)
                    .queue(message -> {
                        // Set up button listener
                        bot.getWaiter().waitForEvent(
                            net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent.class,
                            e -> {
                                // Check if it's the correct message and the user is authorized
                                return e.getMessage().equals(message) && e.getUser().equals(event.getUser());
                            },
                            e -> {
                                // Handle button interaction
                                String buttonId = e.getComponentId();
                                
                                if (buttonId.equals("search:cancel")) {
                                    // Disable all buttons and indicate cancellation
                                    e.editMessage("Selection canceled.")
                                        .setComponents(
                                            e.getMessage().getActionRows().stream()
                                                .map(row -> ActionRow.of(
                                                    row.getButtons().stream()
                                                        .map(Button::asDisabled)
                                                        .collect(Collectors.toList())
                                                ))
                                                .collect(Collectors.toList())
                                        )
                                        .queue();
                                } else if (buttonId.startsWith("search:")) {
                                    // Extract the track index
                                    int index = Integer.parseInt(buttonId.substring(7));
                                    
                                    // Select track
                                    AudioTrack track = playlist.getTracks().get(index);
                                    
                                    if (bot.getConfig().isTooLong(track)) {
                                        e.reply(event.getClient().getWarning() + "**" + track.getInfo().title + "** is longer than the allowed maximum length. "
                                                + FormatUtil.formatTime(track.getDuration()) + " > " + bot.getConfig().getMaxTime()).queue();
                                        return;
                                    }
                                    
                                    // Disable all buttons and indicate selection
                                    e.editMessage("Track **" + track.getInfo().title + "** selected!")
                                        .setComponents(
                                            e.getMessage().getActionRows().stream()
                                                .map(row -> ActionRow.of(
                                                    row.getButtons().stream()
                                                        .map(Button::asDisabled)
                                                        .collect(Collectors.toList())
                                                ))
                                                .collect(Collectors.toList())
                                        )
                                        .queue();
                                    
                                    AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                                    int pos = handler.addTrack(new QueuedTrack(track, event.getUser())) + 1;
                                    
                                    e.getHook().sendMessage(event.getClient().getSuccess() + "**" + track.getInfo().title
                                            + "** (`" + FormatUtil.formatTime(track.getDuration()) + "`) " + (pos == 0 ? "added to the queue."
                                            : " added to position " + pos + " in the queue.")).queue();
                                }
                            },
                            1, TimeUnit.MINUTES,
                            () -> {
                                // If the wait timeout expires, disable all buttons
                                message.editMessageComponents(
                                    message.getActionRows().stream()
                                        .map(row -> ActionRow.of(
                                            row.getButtons().stream()
                                                .map(Button::asDisabled)
                                                .collect(Collectors.toList())
                                        ))
                                        .collect(Collectors.toList())
                                ).queue();
                            }
                        );
                    });
        }

        @Override
        public void noMatches() {
            m.editOriginal(FormatUtil.filter(event.getClient().getWarning() + " No search results for `" + event.getOption("input").getAsString() + "`.")).queue();
        }

        @Override
        public void loadFailed(FriendlyException throwable) {

            if (throwable.severity == Severity.COMMON)
                m.editOriginal(event.getClient().getError() + " An error occurred during loading: " + throwable.getMessage()).queue();
            else
                m.editOriginal(event.getClient().getError() + " An error occurred during loading").queue();
        }
    }

    private class ResultHandler implements AudioLoadResultHandler {
        private final Message m;
        private final CommandEvent event;

        private ResultHandler(Message m, CommandEvent event) {
            this.m = m;
            this.event = event;
        }

        @Override
        public void trackLoaded(AudioTrack track) {
            if (bot.getConfig().isTooLong(track)) {
                m.editMessage(FormatUtil.filter(event.getClient().getWarning() + " This track (**" + track.getInfo().title + "**) is longer than the allowed maximum length. `"
                        + FormatUtil.formatTime(track.getDuration()) + "` > `" + bot.getConfig().getMaxTime() + "`")).queue();
                return;
            }
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            int pos = handler.addTrack(new QueuedTrack(track, event.getAuthor())) + 1;
            m.editMessage(FormatUtil.filter(event.getClient().getSuccess() + " **" + track.getInfo().title
                    + "** (`" + FormatUtil.formatTime(track.getDuration()) + "`) " + (pos == 0 ? "will start playing now."
                    : " added to position " + pos + " in the play queue."))).queue();
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {
            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(event.getSelfMember().getColor())
                    .setTitle(FormatUtil.filter(event.getClient().getSuccess() + " Search results for `" + event.getArgs() + "`:"));
                    
            StringBuilder description = new StringBuilder();
            description.append("Choose a track to play :\n\n");

            // Create a list for the buttons and choices
            List<Button> buttons = new ArrayList<>();
            List<String> choices = new ArrayList<>();
            
            for (int i = 0; i < 4 && i < playlist.getTracks().size(); i++) {
                AudioTrack track = playlist.getTracks().get(i);
                String choice = "`[" + FormatUtil.formatTime(track.getDuration()) + "]` [**" + track.getInfo().title + "**](" + track.getInfo().uri + ")";
                choices.add(choice);
                buttons.add(Button.primary("search:" + i, String.valueOf(i + 1)));
            }
            
            // Add all choices to description
            for (int i = 0; i < choices.size(); i++) {
                description.append("**").append(i + 1).append(".** ").append(choices.get(i)).append("\n\n");
            }
            
            embed.setDescription(description.toString());
            
            // Add cancel button
            buttons.add(Button.danger("search:cancel", "❌ Cancel"));
            
            // Create the action row with buttons
            ActionRow actionRow = ActionRow.of(buttons);
            
            // Send the message with buttons
            m.editMessageEmbeds(embed.build())
                    .setComponents(actionRow)
                    .queue(message -> {
                        // Set up button listener
                        bot.getWaiter().waitForEvent(
                            net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent.class,
                            e -> {
                                // Check if it's the correct message and the user is authorized
                                return e.getMessage().equals(message) && e.getUser().equals(event.getAuthor());
                            },
                            e -> {
                                // Handle button interaction
                                String buttonId = e.getComponentId();
                                
                                if (buttonId.equals("search:cancel")) {
                                    // Disable all buttons and indicate cancellation
                                    e.editMessage("Selection canceled.")
                                        .setComponents(
                                            e.getMessage().getActionRows().stream()
                                                .map(row -> ActionRow.of(
                                                    row.getButtons().stream()
                                                        .map(Button::asDisabled)
                                                        .collect(Collectors.toList())
                                                ))
                                                .collect(Collectors.toList())
                                        )
                                        .queue();
                                } else if (buttonId.startsWith("search:")) {
                                    // Extract the track index
                                    int index = Integer.parseInt(buttonId.substring(7));
                                    
                                    // Select track
                                    AudioTrack track = playlist.getTracks().get(index);
                                    
                                    if (bot.getConfig().isTooLong(track)) {
                                        e.reply(event.getClient().getWarning() + "This track (**" + track.getInfo().title + "**) exceeds the allowed maximum length: `"
                                                + FormatUtil.formatTime(track.getDuration()) + "` > `" + bot.getConfig().getMaxTime() + "`").queue();
                                        return;
                                    }
                                    
                                    // Disable all buttons and indicate selection
                                    e.editMessage("Track **" + track.getInfo().title + "** selected!")
                                        .setComponents(
                                            e.getMessage().getActionRows().stream()
                                                .map(row -> ActionRow.of(
                                                    row.getButtons().stream()
                                                        .map(Button::asDisabled)
                                                        .collect(Collectors.toList())
                                                ))
                                                .collect(Collectors.toList())
                                        )
                                        .queue();
                                    
                                    AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                                    int pos = handler.addTrack(new QueuedTrack(track, event.getAuthor())) + 1;
                                    
                                    event.replySuccess("**" + FormatUtil.filter(track.getInfo().title)
                                            + "** (`" + FormatUtil.formatTime(track.getDuration()) + "`) " + (pos == 0 ? "will start playing now."
                                            : " added to position " + pos + " in the play queue."));
                                }
                            },
                            1, TimeUnit.MINUTES,
                            () -> {
                                // If the wait timeout expires, disable all buttons
                                message.editMessageComponents(
                                    message.getActionRows().stream()
                                        .map(row -> ActionRow.of(
                                            row.getButtons().stream()
                                                .map(Button::asDisabled)
                                                .collect(Collectors.toList())
                                        ))
                                        .collect(Collectors.toList())
                                ).queue();
                            }
                        );
                    });
        }

        @Override
        public void noMatches() {
            m.editMessage(FormatUtil.filter(event.getClient().getWarning() + " No results found for `" + event.getArgs() + "`.")).queue();
        }

        @Override
        public void loadFailed(FriendlyException throwable) {
            if (throwable.severity == Severity.COMMON)
                m.editMessage(event.getClient().getError() + " Loading error: " + throwable.getMessage()).queue();
            else
                m.editMessage(event.getClient().getError() + " Failed to load track.").queue();
        }
    }
}
