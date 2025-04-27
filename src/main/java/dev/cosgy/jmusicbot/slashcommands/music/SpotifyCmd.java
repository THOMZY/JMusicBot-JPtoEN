/*
 *  Copyright 2023 Cosgy Dev (info@cosgy.dev).
 * Edit 2025 THOMZY
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *        http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package dev.cosgy.jmusicbot.slashcommands.music;

import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.menu.ButtonMenu;
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
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.awt.*;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;
import java.util.stream.Collectors;

public class SpotifyCmd extends MusicCommand {

    Logger log = LoggerFactory.getLogger(this.name);
    private static final int CONNECTION_TIMEOUT = 5; // Connection timeout in seconds
    private static final int RESPONSE_TIMEOUT = 5;   // Response read timeout in seconds
    private static final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(CONNECTION_TIMEOUT))
            .build();
    private static final String SPOTIFY_TRACK_URL_PREFIX = "https://open.spotify.com/track/";
    private static final String SPOTIFY_AUTH_URL = "https://accounts.spotify.com/api/token";

    private final static String LOAD = "\uD83D\uDCE5"; // 📥
    private final static String CANCEL = "\uD83D\uDEAB"; // 🚫

    // Static variables to avoid double initialization
    private static String accessToken = null;
    private static long accessTokenExpirationTime;
    private static boolean initializationAttempted = false;

    // Maps to store Spotify track information
    public static final Map<String, String> lastTrackIds = new ConcurrentHashMap<>(); // guildId -> trackId
    public static final Map<String, String> trackNames = new ConcurrentHashMap<>(); // trackId -> trackName
    public static final Map<String, String> albumNames = new ConcurrentHashMap<>(); // trackId -> albumName
    public static final Map<String, String> artistNames = new ConcurrentHashMap<>(); // trackId -> artistName
    public static final Map<String, String> albumImageUrls = new ConcurrentHashMap<>(); // trackId -> albumImageUrl
    public static final Map<String, Color> trackColors = new ConcurrentHashMap<>(); // trackId -> color

    public SpotifyCmd(Bot bot) {
        super(bot);
        this.name = "spotify";
        this.arguments = "<title|URL|subcommand>";
        this.help = "Plays the specified track";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.beListening = true;
        this.bePlaying = false;

        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(OptionType.STRING, "tracklink", "URL of the Spotify track", true));
        this.options = options;

        // Initialize Spotify connection only if it hasn't been attempted yet
        if (!initializationAttempted) {
            initializationAttempted = true;
            try {
                String clientId = bot.getConfig().getSpotifyClientId();
                String clientSecret = bot.getConfig().getSpotifyClientSecret();

                if (clientId.isEmpty() || clientSecret.isEmpty()) {
                    log.info("Spotify feature disabled: Client ID or Client Secret not configured");
                    return;
                }

                // Single attempt to get access token with timeout
                accessToken = getAccessToken(clientId, clientSecret);
                if (accessToken == null) {
                    log.info("Spotify feature disabled: Failed to obtain access token");
                } else {
                    log.info("Successfully connected to Spotify API");
                }
            } catch (Exception e) {
                log.info("Failed to initialize Spotify connection", e);
                // Don't rethrow - let the bot continue without Spotify functionality
            }
        }
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        String trackUrl = event.getOption("tracklink").getAsString();

        if (accessToken == null) {
            event.reply("This command is not available. A configuration by the bot owner is required to activate this command.").queue();
            return;
        }

        // Renew the access token if it has expired
        if (System.currentTimeMillis() >= accessTokenExpirationTime) {
            String clientId = bot.getConfig().getSpotifyClientId();
            String clientSecret = bot.getConfig().getSpotifyClientSecret();
            accessToken = getAccessToken(clientId, clientSecret);
            if (accessToken == null) {
                event.reply("Failed to connect to Spotify API. Please try again later.").queue();
                return;
            }
        }

        if (!isSpotifyTrackUrl(trackUrl)) {
            event.reply("Error: The specified URL is not a Spotify track URL").queue();
            return;
        }

        String trackId = extractTrackIdFromUrl(trackUrl);
        String endpoint = "https://api.spotify.com/v1/tracks/" + trackId;

        HttpRequest request = HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept-Language", "en")
                .GET()
                .uri(URI.create(endpoint))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject json = new JSONObject(response.body());
            String trackName = json.getString("name");
            String albumName = json.getJSONObject("album").getString("name");
            String artistName = json.getJSONArray("artists").getJSONObject(0).getString("name");
            String albumImageUrl = json.getJSONObject("album").getJSONArray("images").getJSONObject(0).getString("url");

            // Use the Audio Features endpoint to retrieve track information
            endpoint = "https://api.spotify.com/v1/audio-features/" + trackId;
            request = HttpRequest.newBuilder()
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .uri(URI.create(endpoint))
                    .build();

            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            json = new JSONObject(response.body());
            // Use a default value when valence key does not exist
            double trackColor = json.has("valence") ? json.getDouble("valence") : 0.5;

            int hue = (int) (trackColor * 360);
            Color color = Color.getHSBColor((float) hue / 360, 1.0f, 1.0f);

            // Store track information for NowplayingCmd
            storeTrackInfo(event.getGuild().getId(), trackId, trackName, albumName, artistName, albumImageUrl, color);

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("Track Information");
            embed.addField("Title", trackName, true);
            embed.addField("Album", albumName, true);
            embed.addField("Artist", artistName, true);
            embed.setImage(albumImageUrl);
            embed.setColor(color);

            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();

            event.reply("Loading `[" + trackName + "]`...").queue(m -> bot.getPlayerManager().loadItemOrdered(event.getGuild(), "ytmsearch:" + trackName + " " + artistName, new SlashResultHandler(m, event)));
        } catch (Exception e) {
            event.reply("Error: " + e.getMessage()).queue();
        }
    }

    @Override
    public void doCommand(CommandEvent event) {
        if (event.getArgs().isEmpty()) {
            event.reply(event.getClient().getError() + " Please include the Spotify track URL.");
            return;
        }
        String trackUrl = event.getArgs();

        if (accessToken == null) {
            event.reply("This command is not available. A configuration by the bot owner is required to activate this command.");
            return;
        }

        // Renew the access token if it has expired
        if (System.currentTimeMillis() >= accessTokenExpirationTime) {
            String clientId = bot.getConfig().getSpotifyClientId();
            String clientSecret = bot.getConfig().getSpotifyClientSecret();
            accessToken = getAccessToken(clientId, clientSecret);
            if (accessToken == null) {
                event.reply("Failed to connect to Spotify API. Please try again later.");
                return;
            }
        }

        if (!isSpotifyTrackUrl(trackUrl)) {
            event.reply("Error: The specified URL is not a Spotify track URL");
            return;
        }

        String trackId = extractTrackIdFromUrl(trackUrl);
        String endpoint = "https://api.spotify.com/v1/tracks/" + trackId;

        HttpRequest request = HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept-Language", "en")
                .GET()
                .uri(URI.create(endpoint))
                .build();

        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            JSONObject json = new JSONObject(response.body());
            String trackName = json.getString("name");
            String albumName = json.getJSONObject("album").getString("name");
            String artistName = json.getJSONArray("artists").getJSONObject(0).getString("name");
            String albumImageUrl = json.getJSONObject("album").getJSONArray("images").getJSONObject(0).getString("url");

            // Use the Audio Features endpoint to retrieve track information
            endpoint = "https://api.spotify.com/v1/audio-features/" + trackId;
            request = HttpRequest.newBuilder()
                    .header("Authorization", "Bearer " + accessToken)
                    .GET()
                    .uri(URI.create(endpoint))
                    .build();

            response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            json = new JSONObject(response.body());
            // Use a default value when valence key does not exist
            double trackColor = json.has("valence") ? json.getDouble("valence") : 0.5;

            int hue = (int) (trackColor * 360);
            Color color = Color.getHSBColor((float) hue / 360, 1.0f, 1.0f);
            
            // Store track information for NowplayingCmd
            storeTrackInfo(event.getGuild().getId(), trackId, trackName, albumName, artistName, albumImageUrl, color);

            EmbedBuilder embed = new EmbedBuilder();
            embed.setTitle("Track Information");
            embed.addField("Title", trackName, true);
            embed.addField("Album", albumName, true);
            embed.addField("Artist", artistName, true);
            embed.setImage(albumImageUrl);
            embed.setColor(color);

            event.getTextChannel().sendMessageEmbeds(embed.build()).queue();

            event.reply("Loading `[" + trackName + "]`...", m -> bot.getPlayerManager().loadItemOrdered(event.getGuild(), "ytmsearch:" + trackName + " " + artistName, new ResultHandler(m, event)));
        } catch (Exception e) {
            event.reply("Error: " + e.getMessage());
        }
    }

    public static String extractTrackIdFromUrl(String url) {
        String trackId = null;

        Pattern pattern = Pattern.compile("track/(\\w+)");
        Matcher matcher = pattern.matcher(url);

        if (matcher.find()) {
            trackId = matcher.group(1);
        }

        return trackId;
    }

    public boolean isSpotifyTrackUrl(String url) {
        Pattern pattern = Pattern.compile("https://open\\.spotify\\.com/(intl-ja/)?track/\\w+");
        Matcher matcher = pattern.matcher(url.split("\\?")[0]);

        return matcher.matches();
    }

    private static String getAccessToken(String clientId, String clientSecret) {
        if (clientId.isEmpty() || clientSecret.isEmpty()) {
            LoggerFactory.getLogger("SpotifyCmd").warn("Spotify credentials are not configured");
            return null;
        }

        try {
            String encodedCredentials = Base64.getEncoder().encodeToString((clientId + ":" + clientSecret).getBytes());

            HttpRequest request = HttpRequest.newBuilder()
                    .header("Authorization", "Basic " + encodedCredentials)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString("grant_type=client_credentials"))
                    .uri(URI.create(SPOTIFY_AUTH_URL))
                    .timeout(Duration.ofSeconds(RESPONSE_TIMEOUT))
                    .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            
            // Check if response is successful (2xx status code)
            if (response.statusCode() / 100 != 2) {
                LoggerFactory.getLogger("SpotifyCmd").warn("Failed to get Spotify access token. Status code: " + response.statusCode());
                return null;
            }

            // Check if response body is empty
            if (response.body() == null || response.body().trim().isEmpty()) {
                LoggerFactory.getLogger("SpotifyCmd").warn("Empty response from Spotify API");
                return null;
            }

            // Validate JSON response
            try {
                JSONObject json = new JSONObject(response.body());
                if (!json.has("access_token") || !json.has("expires_in")) {
                    LoggerFactory.getLogger("SpotifyCmd").warn("Invalid response format from Spotify API");
                    return null;
                }
                accessTokenExpirationTime = System.currentTimeMillis() + json.getInt("expires_in") * 1000L;
                return json.getString("access_token");
            } catch (org.json.JSONException e) {
                LoggerFactory.getLogger("SpotifyCmd").warn("Failed to parse Spotify API response: " + e.getMessage());
                return null;
            }
        } catch (IOException e) {
            LoggerFactory.getLogger("SpotifyCmd").warn("Network error while connecting to Spotify API: " + e.getMessage());
            return null;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            LoggerFactory.getLogger("SpotifyCmd").warn("Interrupted while connecting to Spotify API");
            return null;
        } catch (Exception e) {
            LoggerFactory.getLogger("SpotifyCmd").warn("Unexpected error while connecting to Spotify API: " + e.getMessage());
            return null;
        }
    }

    // Check if a track is from Spotify (based on the guild ID)
    public static boolean isSpotifyTrack(String guildId) {
        return lastTrackIds.containsKey(guildId);
    }
    
    // Get Spotify track information for a guild
    public static class SpotifyTrackInfo {
        public final String trackId;
        public final String trackName;
        public final String albumName;
        public final String artistName;
        public final String albumImageUrl;
        public final Color color;
        
        public SpotifyTrackInfo(String trackId, String trackName, String albumName, String artistName, String albumImageUrl, Color color) {
            this.trackId = trackId;
            this.trackName = trackName;
            this.albumName = albumName;
            this.artistName = artistName;
            this.albumImageUrl = albumImageUrl;
            this.color = color;
        }
    }
    
    // Get Spotify track info for a specific guild
    public static SpotifyTrackInfo getTrackInfo(String guildId) {
        String trackId = lastTrackIds.get(guildId);
        if (trackId == null) {
            return null;
        }
        
        return new SpotifyTrackInfo(
            trackId,
            trackNames.getOrDefault(trackId, "Unknown Track"),
            albumNames.getOrDefault(trackId, "Unknown Album"),
            artistNames.getOrDefault(trackId, "Unknown Artist"),
            albumImageUrls.getOrDefault(trackId, ""),
            trackColors.getOrDefault(trackId, Color.GREEN)
        );
    }
    
    // Store track info in the maps
    private void storeTrackInfo(String guildId, String trackId, String trackName, String albumName, String artistName, String albumImageUrl, Color color) {
        lastTrackIds.put(guildId, trackId);
        trackNames.put(trackId, trackName);
        albumNames.put(trackId, albumName);
        artistNames.put(trackId, artistName);
        albumImageUrls.put(trackId, albumImageUrl);
        trackColors.put(trackId, color);
    }

    private class SlashResultHandler implements AudioLoadResultHandler {
        private final InteractionHook m;
        private final SlashCommandEvent event;
        private final String trackId;

        private SlashResultHandler(InteractionHook m, SlashCommandEvent event) {
            this.m = m;
            this.event = event;
            this.trackId = lastTrackIds.get(event.getGuild().getId());
        }

        @Override
        public void trackLoaded(AudioTrack track) {
            if (bot.getConfig().isTooLong(track)) {
                event.getHook().sendMessage(FormatUtil.filter(event.getClient().getWarning() + "**" + track.getInfo().title + "**` exceeds the allowed maximum length. "
                        + FormatUtil.formatTime(track.getDuration()) + "` > `" + bot.getConfig().getMaxTime() + "`")).queue();
                return;
            }
            
            // Create RequestMetadata and attach the Spotify trackId to it
            com.jagrosh.jmusicbot.audio.RequestMetadata rm = new com.jagrosh.jmusicbot.audio.RequestMetadata(event.getUser());
            if (trackId != null) {
                rm.setSpotifyTrackId(trackId);
            }
            
            // Create QueuedTrack with the RequestMetadata containing Spotify information
            com.jagrosh.jmusicbot.audio.QueuedTrack qtrack = new com.jagrosh.jmusicbot.audio.QueuedTrack(track, rm);
            
            // Add the track to the queue
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            int pos = handler.addTrack(qtrack) + 1;
            
            // Update the lastTrackIds map immediately, even if the track is queued
            // This ensures we remember this is a Spotify track when it eventually plays
            if (trackId != null) {
                // Always update the lastTrackIds map to remember this track was loaded from Spotify
                lastTrackIds.put(event.getGuild().getId(), trackId);
            }
            
            event.getHook().sendMessage(FormatUtil.filter(event.getClient().getSuccess() + "**" + track.getInfo().title
                    + "**(`" + FormatUtil.formatTime(track.getDuration()) + "`) " + (pos == 0 ? "has been added."
                    : " has been added to the queue at position " + pos + "."))).queue();
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {
            // Play the first result directly instead of showing a list
            AudioTrack track = playlist.getTracks().get(0);
            
            if (bot.getConfig().isTooLong(track)) {
                event.getHook().sendMessage(FormatUtil.filter(event.getClient().getWarning() + "**" + track.getInfo().title + "**` exceeds the allowed maximum length. "
                        + FormatUtil.formatTime(track.getDuration()) + "` > `" + bot.getConfig().getMaxTime() + "`")).queue();
                return;
            }
            
            // Create RequestMetadata and attach the Spotify trackId to it
            com.jagrosh.jmusicbot.audio.RequestMetadata rm = new com.jagrosh.jmusicbot.audio.RequestMetadata(event.getUser());
            if (trackId != null) {
                rm.setSpotifyTrackId(trackId);
            }
            
            // Create QueuedTrack with the RequestMetadata containing Spotify information
            com.jagrosh.jmusicbot.audio.QueuedTrack qtrack = new com.jagrosh.jmusicbot.audio.QueuedTrack(track, rm);
            
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            int pos = handler.addTrack(qtrack) + 1;
            
            // Update the lastTrackIds map immediately, even if the track is queued
            if (trackId != null) {
                // Always update the lastTrackIds map to remember this track was loaded from Spotify
                lastTrackIds.put(event.getGuild().getId(), trackId);
            }
            
            event.getHook().sendMessage(FormatUtil.filter(event.getClient().getSuccess() + "**" + track.getInfo().title
                    + "**(`" + FormatUtil.formatTime(track.getDuration()) + "`) " + (pos == 0 ? "has been added."
                    : " has been added to the queue at position " + pos + "."))).queue();
        }

        @Override
        public void noMatches() {
            m.sendMessage(FormatUtil.filter(event.getClient().getWarning() + " Cannot find the searched track.")).queue();
        }

        @Override
        public void loadFailed(FriendlyException throwable) {
            if (throwable.severity == FriendlyException.Severity.COMMON)
                event.getHook().sendMessage(event.getClient().getError() + " An error occurred while loading: " + throwable.getMessage()).queue();
            else
                event.getHook().sendMessage(event.getClient().getError() + " An error occurred while loading").queue();
        }
    }

    private class ResultHandler implements AudioLoadResultHandler {
        private final Message m;
        private final CommandEvent event;
        private final String trackId;

        private ResultHandler(Message m, CommandEvent event) {
            this.m = m;
            this.event = event;
            this.trackId = lastTrackIds.get(event.getGuild().getId());
        }

        @Override
        public void trackLoaded(AudioTrack track) {
            if (bot.getConfig().isTooLong(track)) {
                m.editMessage(FormatUtil.filter(event.getClient().getWarning() + "**" + track.getInfo().title + "**` exceeds the allowed maximum length. "
                        + FormatUtil.formatTime(track.getDuration()) + "` > `" + bot.getConfig().getMaxTime() + "`")).queue();
                return;
            }
            
            // Create RequestMetadata and attach the Spotify trackId to it
            com.jagrosh.jmusicbot.audio.RequestMetadata rm = new com.jagrosh.jmusicbot.audio.RequestMetadata(event.getAuthor());
            if (trackId != null) {
                rm.setSpotifyTrackId(trackId);
            }
            
            // Create QueuedTrack with the RequestMetadata containing Spotify information
            com.jagrosh.jmusicbot.audio.QueuedTrack qtrack = new com.jagrosh.jmusicbot.audio.QueuedTrack(track, rm);
            
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            int pos = handler.addTrack(qtrack) + 1;
            
            // Update the lastTrackIds map immediately, even if the track is queued
            if (trackId != null) {
                // Always update the lastTrackIds map to remember this track was loaded from Spotify
                lastTrackIds.put(event.getGuild().getId(), trackId);
            }
            
            m.editMessage(FormatUtil.filter(event.getClient().getSuccess() + "**" + track.getInfo().title
                    + "**(`" + FormatUtil.formatTime(track.getDuration()) + "`) " + (pos == 0 ? "has been added."
                    : " has been added to the queue at position " + pos + "."))).queue();
        }

        @Override
        public void playlistLoaded(AudioPlaylist playlist) {
            // Play the first result directly instead of showing a list
            AudioTrack track = playlist.getTracks().get(0);
            
            if (bot.getConfig().isTooLong(track)) {
                m.editMessage(FormatUtil.filter(event.getClient().getWarning() + "**" + track.getInfo().title + "**` exceeds the allowed maximum length. "
                        + FormatUtil.formatTime(track.getDuration()) + "` > `" + bot.getConfig().getMaxTime() + "`")).queue();
                return;
            }
            
            // Create RequestMetadata and attach the Spotify trackId to it
            com.jagrosh.jmusicbot.audio.RequestMetadata rm = new com.jagrosh.jmusicbot.audio.RequestMetadata(event.getAuthor());
            if (trackId != null) {
                rm.setSpotifyTrackId(trackId);
            }
            
            // Create QueuedTrack with the RequestMetadata containing Spotify information
            com.jagrosh.jmusicbot.audio.QueuedTrack qtrack = new com.jagrosh.jmusicbot.audio.QueuedTrack(track, rm);
            
            AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
            int pos = handler.addTrack(qtrack) + 1;
            
            // Update the lastTrackIds map immediately, even if the track is queued
            if (trackId != null) {
                // Always update the lastTrackIds map to remember this track was loaded from Spotify
                lastTrackIds.put(event.getGuild().getId(), trackId);
            }
            
            m.editMessage(FormatUtil.filter(event.getClient().getSuccess() + "**" + track.getInfo().title
                    + "**(`" + FormatUtil.formatTime(track.getDuration()) + "`) " + (pos == 0 ? "has been added."
                    : " has been added to the queue at position " + pos + "."))).queue();
        }

        @Override
        public void noMatches() {
            m.editMessage(FormatUtil.filter(event.getClient().getWarning() + " No matches found for the searched track.")).queue();
        }

        @Override
        public void loadFailed(FriendlyException throwable) {
            if (throwable.severity == FriendlyException.Severity.COMMON)
                m.editMessage(event.getClient().getError() + " An error occurred while loading: " + throwable.getMessage()).queue();
            else
                m.editMessage(event.getClient().getError() + " An error occurred while loading").queue();
        }
    }

    // Check if Spotify API credentials are properly configured
    public boolean isConfigured() {
        return accessToken != null;
    }

    // Handle Spotify track processing for SlashCommands (called from PlayCmd)
    public void handleSpotifyTrack(String trackId, SlashCommandEvent event, InteractionHook hook) throws Exception {
        if (accessToken == null) {
            hook.editOriginal("This command is unavailable. Configuration by the bot owner is required to enable this command.").queue();
            return;
        }

        // Renew the access token if it has expired
        if (System.currentTimeMillis() >= accessTokenExpirationTime) {
            String clientId = bot.getConfig().getSpotifyClientId();
            String clientSecret = bot.getConfig().getSpotifyClientSecret();
            accessToken = getAccessToken(clientId, clientSecret);
            
            if (accessToken == null) {
                hook.editOriginal("Failed to authenticate with Spotify. Please try again later.").queue();
                return;
            }
        }

        String endpoint = "https://api.spotify.com/v1/tracks/" + trackId;

        HttpRequest request = HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept-Language", "en")
                .GET()
                .uri(URI.create(endpoint))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject json = new JSONObject(response.body());
        String trackName = json.getString("name");
        String albumName = json.getJSONObject("album").getString("name");
        String artistName = json.getJSONArray("artists").getJSONObject(0).getString("name");
        String albumImageUrl = json.getJSONObject("album").getJSONArray("images").getJSONObject(0).getString("url");

        // Use the Audio Features endpoint to retrieve track information
        endpoint = "https://api.spotify.com/v1/audio-features/" + trackId;
        request = HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .uri(URI.create(endpoint))
                .build();

        response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        json = new JSONObject(response.body());
        // Use a default value when valence key does not exist
        double trackColor = json.has("valence") ? json.getDouble("valence") : 0.5;

        int hue = (int) (trackColor * 360);
        Color color = Color.getHSBColor((float) hue / 360, 1.0f, 1.0f);

        // Store track information for NowplayingCmd
        storeTrackInfo(event.getGuild().getId(), trackId, trackName, albumName, artistName, albumImageUrl, color);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Track Information");
        embed.addField("Title", trackName, true);
        embed.addField("Album", albumName, true);
        embed.addField("Artist", artistName, true);
        embed.setImage(albumImageUrl);
        embed.setColor(color);

        event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
        
        // Update the hook with the track name being loaded
        hook.editOriginal("Loading `[" + trackName + "]`...").queue();
        
        // Create a custom AudioLoadResultHandler that will store the Spotify track ID
        bot.getPlayerManager().loadItemOrdered(event.getGuild(), "ytmsearch:" + trackName + " " + artistName, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                // Create RequestMetadata with the Spotify track ID
                com.jagrosh.jmusicbot.audio.RequestMetadata rm = new com.jagrosh.jmusicbot.audio.RequestMetadata(event.getUser());
                rm.setSpotifyTrackId(trackId);
                
                // Create QueuedTrack with the RequestMetadata
                com.jagrosh.jmusicbot.audio.QueuedTrack qtrack = new com.jagrosh.jmusicbot.audio.QueuedTrack(track, rm);
                
                // Process the track as usual
                AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                int pos = handler.addTrack(qtrack) + 1;
                
                hook.editOriginal(FormatUtil.filter(event.getClient().getSuccess() + "**" + track.getInfo().title
                        + "**(`" + FormatUtil.formatTime(track.getDuration()) + "`) " + (pos == 0 ? "has been added."
                        : " has been added to the queue at position " + pos + "."))).queue();
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack track = playlist.getTracks().get(0);
                
                // Create RequestMetadata with the Spotify track ID
                com.jagrosh.jmusicbot.audio.RequestMetadata rm = new com.jagrosh.jmusicbot.audio.RequestMetadata(event.getUser());
                rm.setSpotifyTrackId(trackId);
                
                // Create QueuedTrack with the RequestMetadata
                com.jagrosh.jmusicbot.audio.QueuedTrack qtrack = new com.jagrosh.jmusicbot.audio.QueuedTrack(track, rm);
                
                // Process the track as usual
                AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                int pos = handler.addTrack(qtrack) + 1;
                
                hook.editOriginal(FormatUtil.filter(event.getClient().getSuccess() + "**" + track.getInfo().title
                        + "**(`" + FormatUtil.formatTime(track.getDuration()) + "`) " + (pos == 0 ? "has been added."
                        : " has been added to the queue at position " + pos + "."))).queue();
            }

            @Override
            public void noMatches() {
                hook.editOriginal(event.getClient().getWarning() + " No matches found for: " + trackName).queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                hook.editOriginal(event.getClient().getError() + " Error loading track: " + exception.getMessage()).queue();
            }
        });
    }
    
    // Handle Spotify track processing for regular Commands (called from PlayCmd)
    public void handleSpotifyTrack(String trackId, CommandEvent event, Message message) throws Exception {
        if (accessToken == null) {
            message.editMessage("This command is unavailable. Configuration by the bot owner is required to enable this command.").queue();
            return;
        }

        // Renew the access token if it has expired
        if (System.currentTimeMillis() >= accessTokenExpirationTime) {
            String clientId = bot.getConfig().getSpotifyClientId();
            String clientSecret = bot.getConfig().getSpotifyClientSecret();
            accessToken = getAccessToken(clientId, clientSecret);
            
            if (accessToken == null) {
                message.editMessage("Failed to authenticate with Spotify. Please try again later.").queue();
                return;
            }
        }

        String endpoint = "https://api.spotify.com/v1/tracks/" + trackId;

        HttpRequest request = HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept-Language", "en")
                .GET()
                .uri(URI.create(endpoint))
                .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        JSONObject json = new JSONObject(response.body());
        String trackName = json.getString("name");
        String albumName = json.getJSONObject("album").getString("name");
        String artistName = json.getJSONArray("artists").getJSONObject(0).getString("name");
        String albumImageUrl = json.getJSONObject("album").getJSONArray("images").getJSONObject(0).getString("url");

        // Use the Audio Features endpoint to retrieve track information
        endpoint = "https://api.spotify.com/v1/audio-features/" + trackId;
        request = HttpRequest.newBuilder()
                .header("Authorization", "Bearer " + accessToken)
                .GET()
                .uri(URI.create(endpoint))
                .build();

        response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        json = new JSONObject(response.body());
        // Use a default value when valence key does not exist
        double trackColor = json.has("valence") ? json.getDouble("valence") : 0.5;

        int hue = (int) (trackColor * 360);
        Color color = Color.getHSBColor((float) hue / 360, 1.0f, 1.0f);
        
        // Store track information for NowplayingCmd
        storeTrackInfo(event.getGuild().getId(), trackId, trackName, albumName, artistName, albumImageUrl, color);

        EmbedBuilder embed = new EmbedBuilder();
        embed.setTitle("Track Information");
        embed.addField("Title", trackName, true);
        embed.addField("Album", albumName, true);
        embed.addField("Artist", artistName, true);
        embed.setImage(albumImageUrl);
        embed.setColor(color);

        event.getTextChannel().sendMessageEmbeds(embed.build()).queue();
        
        // Update the message with the track name being loaded
        message.editMessage("Loading `[" + trackName + "]`...").queue();
        
        // Create a custom AudioLoadResultHandler that will store the Spotify track ID
        bot.getPlayerManager().loadItemOrdered(event.getGuild(), "ytmsearch:" + trackName + " " + artistName, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                // Create RequestMetadata with the Spotify track ID
                com.jagrosh.jmusicbot.audio.RequestMetadata rm = new com.jagrosh.jmusicbot.audio.RequestMetadata(event.getAuthor());
                rm.setSpotifyTrackId(trackId);
                
                // Create QueuedTrack with the RequestMetadata
                com.jagrosh.jmusicbot.audio.QueuedTrack qtrack = new com.jagrosh.jmusicbot.audio.QueuedTrack(track, rm);
                
                // Process the track as usual
                AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                int pos = handler.addTrack(qtrack) + 1;
                
                message.editMessage(FormatUtil.filter(event.getClient().getSuccess() + "**" + track.getInfo().title
                        + "**(`" + FormatUtil.formatTime(track.getDuration()) + "`) " + (pos == 0 ? "has been added."
                        : " has been added to the queue at position " + pos + "."))).queue();
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                AudioTrack track = playlist.getTracks().get(0);
                
                // Create RequestMetadata with the Spotify track ID
                com.jagrosh.jmusicbot.audio.RequestMetadata rm = new com.jagrosh.jmusicbot.audio.RequestMetadata(event.getAuthor());
                rm.setSpotifyTrackId(trackId);
                
                // Create QueuedTrack with the RequestMetadata
                com.jagrosh.jmusicbot.audio.QueuedTrack qtrack = new com.jagrosh.jmusicbot.audio.QueuedTrack(track, rm);
                
                // Process the track as usual
                AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                int pos = handler.addTrack(qtrack) + 1;
                
                message.editMessage(FormatUtil.filter(event.getClient().getSuccess() + "**" + track.getInfo().title
                        + "**(`" + FormatUtil.formatTime(track.getDuration()) + "`) " + (pos == 0 ? "has been added."
                        : " has been added to the queue at position " + pos + "."))).queue();
            }

            @Override
            public void noMatches() {
                message.editMessage(event.getClient().getWarning() + " No matches found for: " + trackName).queue();
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                message.editMessage(event.getClient().getError() + " Error loading track: " + exception.getMessage()).queue();
            }
        });
    }
}

