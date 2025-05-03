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
import com.jagrosh.jlyrics.LyricsClient;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
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
        String title;
        if (event.getOption("name") == null || event.getOption("name").getAsString().isEmpty()) {
            AudioHandler sendingHandler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            if (sendingHandler.isMusicPlaying(event.getJDA()))
                title = sendingHandler.getPlayer().getPlayingTrack().getInfo().title;
            else {
                event.reply(event.getClient().getError() + "The command can't be used as no song is currently playing.").queue();
                return;
            }
        } else
            title = event.getOption("name").getAsString();
            
        // Clean up the title to improve search results
        title = cleanupTitle(title);
        
        // First try with the JLyrics library
        final String finalTitle = title;
        lClient.getLyrics(finalTitle).thenAccept(lyrics -> {
            if (lyrics != null) {
                sendLyricsEmbed(event, lyrics, finalTitle);
                return;
            }
            
            // If JLyrics fails, try with lyrics.ovh API
            String lyricsText = searchLyricsOvh(finalTitle);
            if (lyricsText != null && !lyricsText.isEmpty()) {
                EmbedBuilder eb = new EmbedBuilder()
                        .setAuthor("Lyrics for: " + finalTitle)
                        .setColor(event.getMember().getColor())
                        .setTitle(finalTitle, null);
                
                if (lyricsText.length() > 2000) {
                    String content = lyricsText.trim();
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
                    event.replyEmbeds(eb.setDescription(lyricsText).build()).queue();
            } else {
                // Try once more with a simplified title if it contains a dash
                if (finalTitle.contains("-")) {
                    String simplifiedTitle = finalTitle.substring(finalTitle.indexOf("-") + 1).trim();
                    String simplifiedLyrics = searchLyricsOvh(simplifiedTitle);
                    
                    if (simplifiedLyrics != null && !simplifiedLyrics.isEmpty()) {
                        EmbedBuilder eb = new EmbedBuilder()
                                .setAuthor("Lyrics for: " + finalTitle)
                                .setColor(event.getMember().getColor())
                                .setTitle(finalTitle, null);
                                
                        if (simplifiedLyrics.length() > 2000) {
                            String content = simplifiedLyrics.trim();
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
                            event.replyEmbeds(eb.setDescription(simplifiedLyrics).build()).queue();
                    } else {
                        event.reply(event.getClient().getError() + "No lyrics found for `" + finalTitle + "`." + 
                                 (event.getOption("name") == null || event.getOption("name").getAsString().isEmpty() ? 
                                  " Try manually entering the song name (`lyrics [song name]`)." : "")).queue();
                    }
                } else {
                    event.reply(event.getClient().getError() + "No lyrics found for `" + finalTitle + "`." + 
                             (event.getOption("name") == null || event.getOption("name").getAsString().isEmpty() ? 
                              " Try manually entering the song name (`lyrics [song name]`)." : "")).queue();
                }
            }
        });
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
    
    private void sendLyricsEmbed(SlashCommandEvent event, com.jagrosh.jlyrics.Lyrics lyrics, String title) {
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
        String title;
        if (event.getArgs().isEmpty()) {
            AudioHandler sendingHandler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            if (sendingHandler.isMusicPlaying(event.getJDA()))
                title = sendingHandler.getPlayer().getPlayingTrack().getInfo().title;
            else {
                event.replyError("The command can't be used as no song is currently playing.");
                return;
            }
        } else
            title = event.getArgs();
            
        // Clean up the title to improve search results
        title = cleanupTitle(title);
                     
        final String finalTitle = title;
        lClient.getLyrics(finalTitle).thenAccept(lyrics -> {
            if (lyrics != null) {
                sendLyricsEmbed(event, lyrics, finalTitle);
                return;
            }
            
            // If JLyrics fails, try with lyrics.ovh API
            String lyricsText = searchLyricsOvh(finalTitle);
            if (lyricsText != null && !lyricsText.isEmpty()) {
                EmbedBuilder eb = new EmbedBuilder()
                        .setAuthor("Lyrics for: " + finalTitle)
                        .setColor(event.getSelfMember().getColor())
                        .setTitle(finalTitle, null);
                
                if (lyricsText.length() > 2000) {
                    String content = lyricsText.trim();
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
                    event.reply(eb.setDescription(lyricsText).build());
            } else {
                // Try once more with a simplified title if it contains a dash
                if (finalTitle.contains("-")) {
                    String simplifiedTitle = finalTitle.substring(finalTitle.indexOf("-") + 1).trim();
                    String simplifiedLyrics = searchLyricsOvh(simplifiedTitle);
                    
                    if (simplifiedLyrics != null && !simplifiedLyrics.isEmpty()) {
                        EmbedBuilder eb = new EmbedBuilder()
                                .setAuthor("Lyrics for: " + finalTitle)
                                .setColor(event.getSelfMember().getColor())
                                .setTitle(finalTitle, null);
                                
                        if (simplifiedLyrics.length() > 2000) {
                            String content = simplifiedLyrics.trim();
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
                            event.reply(eb.setDescription(simplifiedLyrics).build());
                    } else {
                        event.replyError("No lyrics found for `" + finalTitle + "`." + 
                                    (event.getArgs().isEmpty() ? " Try manually entering the song name (`lyrics [song name]`)." : ""));
                    }
                } else {
                    event.replyError("No lyrics found for `" + finalTitle + "`." + 
                                (event.getArgs().isEmpty() ? " Try manually entering the song name (`lyrics [song name]`)." : ""));
                }
            }
        });
    }
    
    private void sendLyricsEmbed(CommandEvent event, com.jagrosh.jlyrics.Lyrics lyrics, String title) {
        EmbedBuilder eb = new EmbedBuilder()
                .setAuthor(lyrics.getAuthor())
                .setColor(event.getSelfMember().getColor())
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