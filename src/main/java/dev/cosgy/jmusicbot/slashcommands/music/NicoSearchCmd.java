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

import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import dev.cosgy.jmusicbot.util.DiscordCompat;
import dev.cosgy.niconicoSearchAPI.nicoSearchAPI;
import dev.cosgy.niconicoSearchAPI.nicoVideoSearchResult;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
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
            return;
        }

        Message loadingMessage = event.getChannel().sendMessage(buildSearchingMessage(event.getArgs())).complete();
        LinkedList<nicoVideoSearchResult> results = niconicoAPI.searchVideo(event.getArgs(), 5, true);
        if (results.isEmpty()) {
            loadingMessage.editMessage(event.getArgs() + " No results found.").queue();
            return;
        }

        SearchResultUi ui = buildSearchResultUi(event.getClient().getSuccess(), event.getArgs(),
                DiscordCompat.getMemberColorRaw(DiscordCompat.getSelfMember(event.getGuild())), results);

        loadingMessage.editMessageEmbeds(ui.embed.build())
                .setComponents(ui.actionRow)
                .queue(message -> setupSelectionWaiter(message, event.getAuthor(), results,
                        selectedResult -> bot.getPlayerManager().loadItemOrdered(
                                event.getGuild(),
                                selectedResult.getWatchUrl(),
                                new ResultHandler(loadingMessage, event, bot))));
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

        event.reply(buildSearchingMessage(input)).queue(m -> {
            LinkedList<nicoVideoSearchResult> results = niconicoAPI.searchVideo(input, 5, true);
            if (results.isEmpty()) {
                m.editOriginal(input + " No results found.").queue();
                return;
            }

            SearchResultUi ui = buildSearchResultUi(event.getClient().getSuccess(), input,
                    DiscordCompat.getMemberColorRaw(DiscordCompat.getSelfMember(event.getGuild())), results);

            event.getChannel().sendMessageEmbeds(ui.embed.build())
                    .setComponents(ui.actionRow)
                    .queue(message -> setupSelectionWaiter(message, event.getUser(), results,
                            selectedResult -> bot.getPlayerManager().loadItemOrdered(
                                    event.getGuild(),
                                    selectedResult.getWatchUrl(),
                                    new SlashResultHandler(m, event, bot))));
        });
    }

    private String buildSearchingMessage(String input) {
        return bot.getConfig().getSearching() + " Searching for " + input + " on Nico Nico Douga\n"
                + "**(Note: Some videos may not be playable.)**";
    }

    private SearchResultUi buildSearchResultUi(String successEmoji, String input, int colorRaw,
                                                LinkedList<nicoVideoSearchResult> results) {
        EmbedBuilder embed = new EmbedBuilder()
                .setColor(colorRaw)
                .setTitle(FormatUtil.filter(successEmoji + "`" + input + "` search results:"));

        StringBuilder description = new StringBuilder("Choose a track to play :\n\n");
        List<Button> buttons = new ArrayList<>();

        for (int i = 0; i < results.size(); i++) {
            nicoVideoSearchResult result = results.get(i);
            String choice = "`[" + result.getInfo().getLengthFormatted() + "]` [**" + result.getTitle() + "**](" + result.getWatchUrl() + ")";
            description.append("**").append(i + 1).append(".** ").append(choice).append("\n\n");
            buttons.add(Button.primary("nico:" + i, String.valueOf(i + 1)));
        }

        buttons.add(Button.danger("nico:cancel", "❌ Cancel"));
        embed.setDescription(description.toString());
        return new SearchResultUi(embed, ActionRow.of(buttons));
    }

    private void setupSelectionWaiter(Message message, User allowedUser, LinkedList<nicoVideoSearchResult> results,
                                      Consumer<nicoVideoSearchResult> onSelection) {
        bot.getWaiter().waitForEvent(
                net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent.class,
                e -> e.getMessage().equals(message) && e.getUser().equals(allowedUser),
            e -> handleSelectionInteraction(e, results, onSelection),
                1, TimeUnit.MINUTES,
                () -> message.editMessageComponents(disabledRows(message)).queue());
    }

        private void handleSelectionInteraction(net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent interaction,
                            LinkedList<nicoVideoSearchResult> results,
                                            Consumer<nicoVideoSearchResult> onSelection) {
        String buttonId = interaction.getComponentId();
        Message message = interaction.getMessage();
        if ("nico:cancel".equals(buttonId)) {
            interaction.editMessage("Selection canceled.")
                    .setComponents(disabledRows(message))
                    .queue();
            return;
        }

        if (!buttonId.startsWith("nico:")) {
            return;
        }

        int index = Integer.parseInt(buttonId.substring(5));
        nicoVideoSearchResult selectedResultVideo = results.get(index);
        interaction.editMessage("Video **" + selectedResultVideo.getTitle() + "** selected!")
                .setComponents(disabledRows(message))
                .queue();
        System.out.println("URL = " + selectedResultVideo.getWatchUrl() + ", title = " + selectedResultVideo.getTitle());
        onSelection.accept(selectedResultVideo);
    }

    private List<ActionRow> disabledRows(Message message) {
        return message.getComponents().stream()
                .map(component -> component.asActionRow())
                .map(row -> ActionRow.of(
                        row.getButtons().stream()
                                .map(Button::asDisabled)
                                .collect(Collectors.toList())
                ))
                .collect(Collectors.toList());
    }

    private static class SearchResultUi {
        private final EmbedBuilder embed;
        private final ActionRow actionRow;

        private SearchResultUi(EmbedBuilder embed, ActionRow actionRow) {
            this.embed = embed;
            this.actionRow = actionRow;
        }
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
