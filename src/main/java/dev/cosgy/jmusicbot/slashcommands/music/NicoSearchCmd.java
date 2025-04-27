/*
 *  Copyright 2021 Cosgy Dev (info@cosgy.dev).
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
import com.jagrosh.jdautilities.menu.OrderedMenu;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import dev.cosgy.niconicoSearchAPI.nicoSearchAPI;
import dev.cosgy.niconicoSearchAPI.nicoVideoSearchResult;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.ActionRow;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class NicoSearchCmd extends MusicCommand {
    public static final nicoSearchAPI niconicoAPI = new nicoSearchAPI(true, 100);

    public NicoSearchCmd(Bot bot) {
        super(bot);
        this.name = "ncsearch";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = false;
        this.arguments = "<search term>";
        this.help = "Searches for videos on Nico Nico Douga using the specified search term.";
        this.botPermissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS};

        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(OptionType.STRING, "input", "Search term", true));
        this.options = options;
    }

    @Override
    public void doCommand(CommandEvent event) {
        boolean isOwner = event.getAuthor().getIdLong() == bot.getConfig().getOwnerId();
        if (!bot.getConfig().isNicoNicoEnabled()) {
            event.reply("Nico Nico Douga feature is not enabled.\n" +
                    (isOwner ? "" : "Please ask the bot creator to change `useniconico = false` to `useniconico = true` in config.txt. "));
            return;
        }

        if (event.getArgs().isEmpty()) {
            event.reply("Usage: **`" + event.getClient().getPrefix() + this.name + " " + this.arguments + "`**");
        } else {
            Message m = event.getChannel().sendMessage(bot.getConfig().getSearching() + " Searching for " + event.getArgs() + " on Nico Nico Douga\n" +
                    "**(Note: Some videos may not be playable.)**").complete();
            LinkedList<nicoVideoSearchResult> results = niconicoAPI.searchVideo(event.getArgs(), 5, true);
            if (results.size() == 0) {
                m.editMessage(event.getArgs() + " No results found.").queue();
                return;
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(event.getSelfMember().getColor())
                    .setTitle(FormatUtil.filter(event.getClient().getSuccess() + "`" + event.getArgs() + "` search results:"));
                    
            StringBuilder description = new StringBuilder();
            description.append("Choose a track to play :\n\n");

            // Create a list for the buttons and choices
            List<Button> buttons = new ArrayList<>();
            List<String> choices = new ArrayList<>();
            
            for (int i = 0; i < results.size(); i++) {
                nicoVideoSearchResult result = results.get(i);
                String choice = "`[" + result.getInfo().getLengthFormatted() + "]` [**" + result.getTitle() + "**](" + result.getWatchUrl() + ")";
                choices.add(choice);
                buttons.add(Button.primary("nico:" + i, String.valueOf(i + 1)));
            }
            
            // Add all choices to description
            for (int i = 0; i < choices.size(); i++) {
                description.append("**").append(i + 1).append(".** ").append(choices.get(i)).append("\n\n");
            }
            
            embed.setDescription(description.toString());
            
            // Add cancel button
            buttons.add(Button.danger("nico:cancel", "❌ Cancel"));
            
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
                                
                                if (buttonId.equals("nico:cancel")) {
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
                                } else if (buttonId.startsWith("nico:")) {
                                    // Extract the track index
                                    int index = Integer.parseInt(buttonId.substring(5));
                                    
                                    // Select video
                                    nicoVideoSearchResult selectedResultVideo = results.get(index);
                                    
                                    // Disable all buttons and indicate selection
                                    e.editMessage("Video **" + selectedResultVideo.getTitle() + "** selected!")
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
                                    
                                    System.out.println("URL = " + selectedResultVideo.getWatchUrl() + ", title = " + selectedResultVideo.getTitle());
                                    bot.getPlayerManager().loadItemOrdered(event.getGuild(), selectedResultVideo.getWatchUrl(), new ResultHandler(m, event, bot));
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
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        boolean isOwner = event.getUser().getIdLong() == bot.getConfig().getOwnerId();
        if (!bot.getConfig().isNicoNicoEnabled()) {
            event.reply("Nico Nico Douga feature is not enabled.\n" +
                    (isOwner ? "" : "Please ask the bot creator to change `useniconico = false` to `useniconico = true` in config.txt.")).queue();
            return;
        }

        String input = event.getOption("input").getAsString();

        event.reply(bot.getConfig().getSearching() + " Searching for " + input + " on Nico Nico Douga\n" +
                "**(Note: Some videos may not be playable.)**").queue(m -> {
            LinkedList<nicoVideoSearchResult> results = niconicoAPI.searchVideo(input, 5, true);
            if (results.size() == 0) {
                m.editOriginal(input + " No results found.").queue();
                return;
            }

            EmbedBuilder embed = new EmbedBuilder()
                    .setColor(event.getGuild().getSelfMember().getColor())
                    .setTitle(FormatUtil.filter(event.getClient().getSuccess() + "`" + input + "` search results:"));
                    
            StringBuilder description = new StringBuilder();
            description.append("Choose a track to play :\n\n");

            // Create a list for the buttons and choices
            List<Button> buttons = new ArrayList<>();
            List<String> choices = new ArrayList<>();
            
            for (int i = 0; i < results.size(); i++) {
                nicoVideoSearchResult result = results.get(i);
                String choice = "`[" + result.getInfo().getLengthFormatted() + "]` [**" + result.getTitle() + "**](" + result.getWatchUrl() + ")";
                choices.add(choice);
                buttons.add(Button.primary("nico:" + i, String.valueOf(i + 1)));
            }
            
            // Add all choices to description
            for (int i = 0; i < choices.size(); i++) {
                description.append("**").append(i + 1).append(".** ").append(choices.get(i)).append("\n\n");
            }
            
            embed.setDescription(description.toString());
            
            // Add cancel button
            buttons.add(Button.danger("nico:cancel", "❌ Cancel"));
            
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
                                
                                if (buttonId.equals("nico:cancel")) {
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
                                } else if (buttonId.startsWith("nico:")) {
                                    // Extract the track index
                                    int index = Integer.parseInt(buttonId.substring(5));
                                    
                                    // Select video
                                    nicoVideoSearchResult selectedResultVideo = results.get(index);
                                    
                                    // Disable all buttons and indicate selection
                                    e.editMessage("Video **" + selectedResultVideo.getTitle() + "** selected!")
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
                                    
                                    System.out.println("URL = " + selectedResultVideo.getWatchUrl() + ", title = " + selectedResultVideo.getTitle());
                                    bot.getPlayerManager().loadItemOrdered(event.getGuild(), selectedResultVideo.getWatchUrl(), new SlashResultHandler(m, event, bot));
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
        });
    }

    private static class ResultHandler implements AudioLoadResultHandler {
        private final CommandEvent event;
        private final Bot bot;

        private ResultHandler(Message m, CommandEvent event, Bot bot) {
            this.bot = bot;
            this.event = event;
        }

        /**
         * Called when the requested item is a track and it was successfully loaded.
         *
         * @param track The loaded track
         */
        @Override
        public void trackLoaded(AudioTrack track) {
            if (bot.getConfig().isTooLong(track)) {
                event.reply(FormatUtil.filter(event.getClient().getWarning() + " Track (**" + track.getInfo().title + "**) exceeds the allowed video length: `"
                        + FormatUtil.formatTime(track.getDuration()) + "` > `" + bot.getConfig().getMaxTime() + "`"));
                return;
            }

            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            int pos = handler.addTrack(new QueuedTrack(track, event.getAuthor())) + 1;

            event.reply(FormatUtil.filter(String.format("%s %s **%s** (`%s`) added to queue", event.getClient().getSuccess(), (pos == 0 ? "added to queue" : "added to queue #" + pos), track.getInfo().title, FormatUtil.formatTime(track.getDuration()))));
        }

        /**
         * Called when the requested item is a playlist and it was successfully loaded.
         *
         * @param playlist The loaded playlist
         */
        @Override
        public void playlistLoaded(AudioPlaylist playlist) {

        }

        /**
         * Called when there were no items found by the specified identifier.
         */
        @Override
        public void noMatches() {
            event.reply(FormatUtil.filter(event.getClient().getWarning() + " No results found for `" + event.getArgs() + "`."));
        }

        /**
         * Called when loading an item failed with an exception.
         *
         * @param exception The exception that was thrown
         */
        @Override
        public void loadFailed(FriendlyException exception) {
            if (exception.severity == FriendlyException.Severity.COMMON)
                event.reply(event.getClient().getError() + " Error occurred while loading: " + exception.getMessage());
            else
                event.reply(event.getClient().getError() + " Error occurred while loading track.");
        }
    }

    private class SlashResultHandler implements AudioLoadResultHandler {
        private final InteractionHook m;
        private final SlashCommandEvent event;
        private final Bot bot;

        private SlashResultHandler(InteractionHook m, SlashCommandEvent event, Bot bot) {
            this.m = m;
            this.bot = bot;
            this.event = event;
        }

        /**
         * Called when the requested item is a track and it was successfully loaded.
         *
         * @param track The loaded track
         */
        @Override
        public void trackLoaded(AudioTrack track) {
            if (bot.getConfig().isTooLong(track)) {
                m.editOriginal(FormatUtil.filter(event.getClient().getWarning() + " Track (**" + track.getInfo().title + "**) exceeds the allowed video length: `"
                        + FormatUtil.formatTime(track.getDuration()) + "` > `" + bot.getConfig().getMaxTime() + "`")).queue();
                return;
            }

            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            int pos = handler.addTrack(new QueuedTrack(track, event.getUser())) + 1;

            m.editOriginal(FormatUtil.filter(String.format("%s %s **%s** (`%s`) added to queue", event.getClient().getSuccess(), (pos == 0 ? "added to queue" : "added to queue #" + pos), track.getInfo().title, FormatUtil.formatTime(track.getDuration())))).queue();
        }

        /**
         * Called when the requested item is a playlist and it was successfully loaded.
         *
         * @param playlist The loaded playlist
         */
        @Override
        public void playlistLoaded(AudioPlaylist playlist) {

        }

        /**
         * Called when there were no items found by the specified identifier.
         */
        @Override
        public void noMatches() {
            m.editOriginal(FormatUtil.filter(event.getClient().getWarning() + " No results found for `" + event.getOption("input").getAsString() + "`")).queue();
        }

        /**
         * Called when loading an item failed with an exception.
         *
         * @param exception The exception that was thrown
         */
        @Override
        public void loadFailed(FriendlyException exception) {
            if (exception.severity == FriendlyException.Severity.COMMON)
                m.editOriginal(event.getClient().getError() + " Error occurred while loading: " + exception.getMessage()).queue();
            else
                m.editOriginal(event.getClient().getError() + " Error occurred while loading track.").queue();
        }
    }
}
