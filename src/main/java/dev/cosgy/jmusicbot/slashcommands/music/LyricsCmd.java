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

import dev.cosgy.jlyrics.LyricsClient;
import dev.cosgy.jlyrics.Lyrics;
import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandEvent;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import dev.cosgy.jmusicbot.util.DiscordCompat;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class LyricsCmd extends MusicCommand {
    private final LyricsClient lClient = new LyricsClient();

    public LyricsCmd(Bot bot) {
        super(bot);
        this.name = "lyrics";
        this.arguments = "[song name]";
        this.help = "Displays the lyrics of a song";
        this.botPermissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
        this.aliases = bot.getConfig().getAliases(this.name);
        this.bePlaying = true;

        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(OptionType.STRING, "name", "Song name", false));
        this.options = options;
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        event.getChannel().sendTyping().queue();
        boolean hasManualTitle = event.getOption("name") != null && !event.getOption("name").getAsString().isEmpty();
        String title = resolveSlashTitle(event, hasManualTitle);
        if (title == null) {
            return;
        }
        handleSlashLyricsLookup(event, title, hasManualTitle);
    }

    private String resolveSlashTitle(SlashCommandEvent event, boolean hasManualTitle) {
        if (hasManualTitle) {
            return cleanupTitle(event.getOption("name").getAsString());
        }

        AudioHandler sendingHandler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        if (sendingHandler.isMusicPlaying(event.getJDA())) {
            return cleanupTitle(sendingHandler.getPlayer().getPlayingTrack().getInfo().title);
        }

        event.reply(event.getClient().getError() + "The command can't be used as no song is currently playing.").queue();
        return null;
    }

    private void handleSlashLyricsLookup(SlashCommandEvent event, String title, boolean hasManualTitle) {
        lClient.getLyrics(title).thenAccept(lyrics -> {
            if (lyrics != null) {
                sendLyricsEmbed(event, lyrics, title);
                return;
            }

            if (replyWithOvhLyricsForSlash(event, title, title)) {
                return;
            }

            if (title.contains("-")) {
                String simplifiedTitle = title.substring(title.indexOf("-") + 1).trim();
                if (replyWithOvhLyricsForSlash(event, simplifiedTitle, title)) {
                    return;
                }
            }

            event.reply(event.getClient().getError() + "No lyrics found for `" + title + "`."
                    + (hasManualTitle ? "" : " Try manually entering the song name (`lyrics [song name]`).")).queue();
        });
    }

    private boolean replyWithOvhLyricsForSlash(SlashCommandEvent event, String lookupTitle, String displayTitle) {
        String lyricsText = searchLyricsOvh(lookupTitle);
        if (lyricsText == null || lyricsText.isEmpty()) {
            return false;
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setAuthor("Lyrics for: " + displayTitle)
                .setColor(event.getMember().getColor())
                .setTitle(displayTitle, null);
        sendChunkedSlashLyrics(event, eb, lyricsText);
        return true;
    }

    private void sendChunkedSlashLyrics(SlashCommandEvent event, EmbedBuilder eb, String lyricsText) {
        if (lyricsText.length() <= 2000) {
            event.replyEmbeds(eb.setDescription(lyricsText).build()).queue();
            return;
        }

        String content = lyricsText.trim();
        while (content.length() > 2000) {
            int index = findLyricsSplitIndex(content);
            event.replyEmbeds(eb.setDescription(content.substring(0, index).trim()).build()).queue();
            content = content.substring(index).trim();
            eb.setAuthor(null).setTitle(null, null);
        }
        event.replyEmbeds(eb.setDescription(content).build()).queue();
    }
    
    private String searchLyricsOvh(String title) {
        try {
            // Try to separate artist and title if they're in format "Artist - Title"
            String artist = "";
            String songTitle = title;
            
            if (title.contains("-")) {
                String[] parts = title.split("-", 2);
                if (parts.length == 2) {
                    artist = parts[0].trim();
                    songTitle = parts[1].trim();
                }
            }
            
            // Encode the parameters for the URL
            String encodedArtist = URLEncoder.encode(artist, StandardCharsets.UTF_8.toString());
            String encodedTitle = URLEncoder.encode(songTitle, StandardCharsets.UTF_8.toString());
            
            // Form the URL
            String apiUrl = "https://api.lyrics.ovh/v1/" + encodedArtist + "/" + encodedTitle;
            
            // Make the request
            URL url = new URL(apiUrl);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            
            // Check the response code
            int responseCode = connection.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                // Read the response
                try (InputStream is = connection.getInputStream();
                     Scanner scanner = new Scanner(is, StandardCharsets.UTF_8.name())) {
                    scanner.useDelimiter("\\A");
                    String response = scanner.hasNext() ? scanner.next() : "";
                    
                    // Parse the JSON response
                    JSONObject jsonObject = new JSONObject(response);
                    if (jsonObject.has("lyrics")) {
                        return jsonObject.getString("lyrics");
                    }
                }
            }
        } catch (IOException | JSONException e) {
            // Just ignore exceptions and return null
        }
        
        return null;
    }
    
    private String cleanupTitle(String title) {
        return title.replaceAll("\\(Official (Music )?Video\\)", "")
                    .replaceAll("\\(Official (Audio|Lyric) Video\\)", "")
                    .replaceAll("\\(Lyric Video\\)", "")
                    .replaceAll("\\(Audio\\)", "")
                    .replaceAll("\\[Official (Music )?Video\\]", "")
                    .replaceAll("\\(\\d{2}:\\d{2}\\)", "")
                    .trim();
    }
    
    private void sendLyricsEmbed(SlashCommandEvent event, Lyrics lyrics, String title) {
        EmbedBuilder eb = new EmbedBuilder()
                .setAuthor(lyrics.getAuthor())
                .setColor(event.getMember().getColor())
                .setTitle(lyrics.getTitle(), lyrics.getURL());
        if (lyrics.getContent().length() > 15000) {
            event.reply(event.getClient().getWarning() + " Lyrics found for `" + title + "` but they might be incorrect: " + lyrics.getURL()).queue();
        } else if (lyrics.getContent().length() > 2000) {
            String content = lyrics.getContent().trim();
            while (content.length() > 2000) {
                int index = content.lastIndexOf("\n\n", 2000);
                if (index == -1)
                    index = content.lastIndexOf("\n", 2000);
                if (index == -1)
                    index = content.lastIndexOf(" ", 2000);
                if (index == -1)
                    index = 2000;
                event.replyEmbeds(eb.setDescription(content.substring(0, index).trim()).build()).queue();
                content = content.substring(index).trim();
                eb.setAuthor(null).setTitle(null, null);
            }
            event.replyEmbeds(eb.setDescription(content).build()).queue();
        } else
            event.replyEmbeds(eb.setDescription(lyrics.getContent()).build()).queue();
    }

    @Override
    public void doCommand(CommandEvent event) {
        event.getChannel().sendTyping().queue();
        boolean hasManualTitle = !event.getArgs().isEmpty();
        String title = resolveCommandTitle(event, hasManualTitle);
        if (title == null) {
            return;
        }
        handleCommandLyricsLookup(event, title, hasManualTitle);
    }

    private String resolveCommandTitle(CommandEvent event, boolean hasManualTitle) {
        if (hasManualTitle) {
            return cleanupTitle(event.getArgs());
        }

        AudioHandler sendingHandler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        if (sendingHandler.isMusicPlaying(event.getJDA())) {
            return cleanupTitle(sendingHandler.getPlayer().getPlayingTrack().getInfo().title);
        }

        event.replyError("The command can't be used as no song is currently playing.");
        return null;
    }

    private void handleCommandLyricsLookup(CommandEvent event, String title, boolean hasManualTitle) {
        lClient.getLyrics(title).thenAccept(lyrics -> {
            if (lyrics != null) {
                sendLyricsEmbed(event, lyrics, title);
                return;
            }

            if (replyWithOvhLyricsForCommand(event, title, title)) {
                return;
            }

            if (title.contains("-")) {
                String simplifiedTitle = title.substring(title.indexOf("-") + 1).trim();
                if (replyWithOvhLyricsForCommand(event, simplifiedTitle, title)) {
                    return;
                }
            }

            event.replyError("No lyrics found for `" + title + "`."
                    + (hasManualTitle ? "" : " Try manually entering the song name (`lyrics [song name]`)."));
        });
    }

    private boolean replyWithOvhLyricsForCommand(CommandEvent event, String lookupTitle, String displayTitle) {
        String lyricsText = searchLyricsOvh(lookupTitle);
        if (lyricsText == null || lyricsText.isEmpty()) {
            return false;
        }

        EmbedBuilder eb = new EmbedBuilder()
                .setAuthor("Lyrics for: " + displayTitle)
                .setColor(DiscordCompat.getSelfMember(event.getGuild()).getColor())
                .setTitle(displayTitle, null);
        sendChunkedCommandLyrics(event, eb, lyricsText);
        return true;
    }

    private void sendChunkedCommandLyrics(CommandEvent event, EmbedBuilder eb, String lyricsText) {
        if (lyricsText.length() <= 2000) {
            event.reply(eb.setDescription(lyricsText).build());
            return;
        }

        String content = lyricsText.trim();
        while (content.length() > 2000) {
            int index = findLyricsSplitIndex(content);
            event.reply(eb.setDescription(content.substring(0, index).trim()).build());
            content = content.substring(index).trim();
            eb.setAuthor(null).setTitle(null, null);
        }
        event.reply(eb.setDescription(content).build());
    }

    private int findLyricsSplitIndex(String content) {
        int index = content.lastIndexOf("\n\n", 2000);
        if (index == -1)
            index = content.lastIndexOf("\n", 2000);
        if (index == -1)
            index = content.lastIndexOf(" ", 2000);
        if (index == -1)
            index = 2000;
        return index;
    }
    
    private void sendLyricsEmbed(CommandEvent event, Lyrics lyrics, String title) {
        EmbedBuilder eb = new EmbedBuilder()
                .setAuthor(lyrics.getAuthor())
            .setColor(DiscordCompat.getSelfMember(event.getGuild()).getColor())
                .setTitle(lyrics.getTitle(), lyrics.getURL());
        if (lyrics.getContent().length() > 15000) {
            event.replyWarning(" Lyrics found for `" + title + "` but they might be incorrect: " + lyrics.getURL());
        } else if (lyrics.getContent().length() > 2000) {
            String content = lyrics.getContent().trim();
            while (content.length() > 2000) {
                int index = content.lastIndexOf("\n\n", 2000);
                if (index == -1)
                    index = content.lastIndexOf("\n", 2000);
                if (index == -1)
                    index = content.lastIndexOf(" ", 2000);
                if (index == -1)
                    index = 2000;
                event.reply(eb.setDescription(content.substring(0, index).trim()).build());
                content = content.substring(index).trim();
                eb.setAuthor(null).setTitle(null, null);
            }
            event.reply(eb.setDescription(content).build());
        } else
            event.reply(eb.setDescription(lyrics.getContent()).build());
    }
}