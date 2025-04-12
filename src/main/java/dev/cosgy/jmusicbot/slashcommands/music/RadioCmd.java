/*
 * Copyright 2025 THOMZY
*/
package dev.cosgy.jmusicbot.slashcommands.music;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagrosh.jdautilities.command.CommandEvent;
import com.jagrosh.jdautilities.command.SlashCommandEvent;
import com.jagrosh.jdautilities.menu.OrderedMenu;
import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException.Severity;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import dev.cosgy.jmusicbot.slashcommands.MusicCommand;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.interactions.InteractionHook;
import net.dv8tion.jda.api.interactions.commands.OptionType;
import net.dv8tion.jda.api.interactions.commands.build.OptionData;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.ActionRow;
import java.util.stream.Collectors;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.dv8tion.jda.api.entities.Activity;
import com.jagrosh.jmusicbot.settings.Settings;

/**
 * Command to search and play radio stations from onlineradiobox.com
 * 
 * @author THOMZY
 */
public class RadioCmd extends MusicCommand {
    private final String searchingEmoji;
    private final ObjectMapper mapper = new ObjectMapper();
    
    // Store timers by guild
    private static final Map<String, Timer> activeTimers = new ConcurrentHashMap<>();
    
    // Store the last radio message for each guild to update when track changes
    private static final Map<String, Long> lastRadioMessageIds = new ConcurrentHashMap<>();
    public static final Map<String, String> lastStationPaths = new ConcurrentHashMap<>();
    public static final Map<String, String> lastStationLogos = new ConcurrentHashMap<>();

    public RadioCmd(Bot bot) {
        super(bot);
        this.searchingEmoji = bot.getConfig().getSearching();
        this.name = "radio";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.arguments = "<station name>";
        this.help = "Searches for radio stations on onlineradiobox.com and plays them";
        this.beListening = true;
        this.bePlaying = false;
        this.botPermissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS};

        List<OptionData> options = new ArrayList<>();
        options.add(new OptionData(OptionType.STRING, "input", "Radio station to search for", true));
        this.options = options;
    }

    @Override
    public void doCommand(CommandEvent event) {
        if (event.getArgs().isEmpty()) {
            event.replyError("Please specify a radio station to search for.");
            return;
        }
        
        event.reply(searchingEmoji + "Searching for radio station `[" + event.getArgs() + "]`... ",
                m -> searchRadioStations(event.getArgs(), m, event, null, null));
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        String query = event.getOption("input").getAsString();
        event.reply(searchingEmoji + "Searching for radio station `[" + query + "]`... ").queue(
                hook -> searchRadioStations(query, null, null, hook, event));
    }

    private void searchRadioStations(String query, Message message, CommandEvent cmdEvent, InteractionHook hook, SlashCommandEvent slashEvent) {
        try {
            // Encode the query for the URL
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
            
            // Create URL for the search
            URL url = new URL("https://onlineradiobox.com/search?q=" + encodedQuery);
            
            // Setup connection
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            // Set language to English
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            
            // Read the response
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            // Parse HTML to find radio stations (initial 20 results)
            String html = response.toString();
            List<RadioStation> stations = parseRadioStations(html, 20);
            
            // Check for "Show more" button and get its URL and offset
            String moreResultsUrl = null;
            int moreResultsOffset = 0;
            
            int moreResultsDiv = html.indexOf("<div class=\"search-result__more\"");
            if (moreResultsDiv != -1) {
                // Get the data-url attribute
                int dataUrlStart = html.indexOf("data-url=\"", moreResultsDiv);
                if (dataUrlStart != -1) {
                    dataUrlStart += 10; // Length of "data-url=\""
                    int dataUrlEnd = html.indexOf("\"", dataUrlStart);
                    if (dataUrlEnd != -1) {
                        moreResultsUrl = html.substring(dataUrlStart, dataUrlEnd);
                        
                        // The URL might already contain the offset parameter
                        if (moreResultsUrl.contains("offset=")) {
                            int offsetParamStart = moreResultsUrl.indexOf("offset=") + 7;
                            int offsetParamEnd = moreResultsUrl.indexOf("&", offsetParamStart);
                            if (offsetParamEnd == -1) {
                                offsetParamEnd = moreResultsUrl.length();
                            }
                            try {
                                moreResultsOffset = Integer.parseInt(moreResultsUrl.substring(offsetParamStart, offsetParamEnd));
                            } catch (NumberFormatException e) {
                                // Default to 20 if parsing fails
                                moreResultsOffset = 20;
                            }
                        }
                    }
                }
                
                // Get the offset attribute directly
                int offsetStart = html.indexOf("offset=\"", moreResultsDiv);
                if (offsetStart != -1 && moreResultsOffset == 0) {
                    offsetStart += 8; // Length of "offset=\""
                    int offsetEnd = html.indexOf("\"", offsetStart);
                    if (offsetEnd != -1) {
                        try {
                            moreResultsOffset = Integer.parseInt(html.substring(offsetStart, offsetEnd));
                        } catch (NumberFormatException e) {
                            // Default to 20 if parsing fails
                            moreResultsOffset = 20;
                        }
                    }
                }
                
                // If we still haven't found an offset, default to 20 as that's typically the first increment
                if (moreResultsOffset == 0) {
                    moreResultsOffset = 20;
                }
            }
            
            // Store the pagination info for later use
            final String finalMoreResultsUrl = moreResultsUrl != null ? moreResultsUrl : "https://onlineradiobox.com/search?part=1&q=" + encodedQuery;
            final int finalMoreResultsOffset = moreResultsOffset;
            
            if (stations.isEmpty()) {
                if (message != null) {
                    message.editMessage(FormatUtil.filter(cmdEvent.getClient().getWarning() + " No radio stations found for `" + query + "`. Please try another search term.")).queue();
                } else if (hook != null && slashEvent != null) {
                    hook.editOriginal(FormatUtil.filter(slashEvent.getClient().getWarning() + " No radio stations found for `" + query + "`. Please try another search term.")).queue();
                }
                return;
            }
            
            // Pre-load descriptions for the first page of stations (synchronously)
            preloadStationDescriptions(stations, 0, Math.min(5, stations.size()));
            
            // Display search results - first page (0)
            if (cmdEvent != null) {
                displaySearchResults(stations, message, cmdEvent, null, null, 0, query, finalMoreResultsUrl, finalMoreResultsOffset);
            } else if (hook != null && slashEvent != null) {
                displaySearchResults(stations, null, null, hook, slashEvent, 0, query, finalMoreResultsUrl, finalMoreResultsOffset);
            }
            
            // Continue loading descriptions for the rest of the stations asynchronously
            if (stations.size() > 5) {
                new Thread(() -> {
                    preloadStationDescriptions(stations, 5, stations.size());
                }).start();
            }
            
        } catch (Exception e) {
            String errorMsg = "Error searching for radio stations: " + e.getMessage();
            if (e.getCause() != null) {
                errorMsg += " Cause: " + e.getCause().getMessage();
            }
            
            if (message != null) {
                message.editMessage(errorMsg).queue();
            } else if (hook != null) {
                hook.editOriginal(errorMsg).queue();
            }
            
            // Print stack trace for debugging
            e.printStackTrace();
        }
    }

    private List<RadioStation> parseRadioStations(String html, int maxResults) {
        List<RadioStation> stations = new ArrayList<>();
        
        // Extract radio stations from the HTML
        // The stations in OnlineRadioBox are in <li class="stations__station"> elements
        int startIdx = 0;
        while (true) {
            // Find the start of a station block
            int stationBlockStart = html.indexOf("<li class=\"stations__station\">", startIdx);
            if (stationBlockStart == -1) break;
            
            // Find the station URL and title
            int urlStart = html.indexOf("href=\"/", stationBlockStart);
            if (urlStart == -1) break;
            urlStart += 7; // Length of "href=\"/"
            
            int urlEnd = html.indexOf("/\"", urlStart);
            if (urlEnd == -1) break;
            
            // Find station name/title
            int titleStart = html.indexOf("<figcaption class=\"station__title__name\">", stationBlockStart);
            if (titleStart == -1) break;
            titleStart += 40; // Length of "<figcaption class=\"station__title__name\">"
            
            int titleEnd = html.indexOf("</figcaption>", titleStart);
            if (titleEnd == -1) break;
            
            // Find station logo URL
            String logoUrl = "";
            int imgTagStart = html.indexOf("<img class=\"station__title__logo\"", stationBlockStart);
            if (imgTagStart != -1 && imgTagStart < titleStart) {
                int logoStart = html.indexOf("src=\"", imgTagStart);
                if (logoStart != -1) {
                    logoStart += 5; // Length of "src=\""
                    int logoEnd = html.indexOf("\"", logoStart);
                    if (logoEnd != -1) {
                        logoUrl = html.substring(logoStart, logoEnd);
                        // Make sure URL is absolute
                        if (logoUrl.startsWith("//")) {
                            logoUrl = "https:" + logoUrl;
                        }
                        // If the URL doesn't start with http or https, add https:
                        else if (!logoUrl.startsWith("http://") && !logoUrl.startsWith("https://")) {
                            logoUrl = "https://" + logoUrl;
                        }
                    }
                }
            }
            
            // Find country and location
            String country = "";
            int countryStart = html.indexOf("class=\"i-flag ", stationBlockStart);
            if (countryStart != -1) {
                countryStart += 14; // Length of "class=\"i-flag "
                int countryEnd = html.indexOf(" ", countryStart);
                if (countryEnd == -1) {
                    countryEnd = html.indexOf("\"", countryStart);
                }
                if (countryEnd != -1) {
                    country = html.substring(countryStart, countryEnd);
                }
            }
            
            // Find genres/tags
            List<String> genres = new ArrayList<>();
            int genresStart = html.indexOf("<ul class=\"stations__station__tags\"", stationBlockStart);
            int genresEnd = html.indexOf("</ul>", genresStart);
            
            if (genresStart != -1 && genresEnd != -1) {
                String genresHtml = html.substring(genresStart, genresEnd);
                int tagIdx = 0;
                while (true) {
                    int tagStart = genresHtml.indexOf("class=\"ajax\">", tagIdx);
                    if (tagStart == -1) break;
                    tagStart += 13; // Length of "class=\"ajax\">"
                    
                    int tagEnd = genresHtml.indexOf("</a>", tagStart);
                    if (tagEnd == -1) break;
                    
                    genres.add(genresHtml.substring(tagStart, tagEnd));
                    tagIdx = tagEnd;
                }
            }
            
            // Extract title and path
            String path = html.substring(urlStart, urlEnd);
            String title = cleanStationTitle(html.substring(titleStart, titleEnd));
            
            // Stream URL is in a button element with class "b-play station_play"
            int streamStart = html.indexOf("stream=\"", stationBlockStart);
            String streamUrl = "";
            if (streamStart != -1) {
                streamStart += 8; // Length of "stream=\""
                int streamEnd = html.indexOf("\"", streamStart);
                if (streamEnd != -1) {
                    streamUrl = html.substring(streamStart, streamEnd);
                }
            }
            
            // Add the radio station to the list
            RadioStation station;
            if (!streamUrl.isEmpty()) {
                // If we have a direct stream URL, use it
                station = new RadioStation(title, path, streamUrl, logoUrl, country, genres);
            } else {
                station = new RadioStation(title, path, logoUrl, country, genres);
            }
            
            // We'll fetch descriptions later in bulk for better performance
            stations.add(station);
            
            // Move search position past this station
            startIdx = titleEnd;
            
            // Limit to maxResults results to avoid overloading
            if (stations.size() >= maxResults) break;
        }
        
        return stations;
    }
    
    // Method to preload descriptions for a range of stations
    private void preloadStationDescriptions(List<RadioStation> stations, int startIndex, int endIndex) {
        for (int i = startIndex; i < endIndex; i++) {
            try {
                RadioStation station = stations.get(i);
                
                // Get station details from the JSON endpoint
                URL url = new URL("https://onlineradiobox.com/json/" + station.path);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // Parse the JSON response to get the description
                JsonNode stationData = mapper.readTree(response.toString());
                if (stationData.has("station") && stationData.get("station").has("description")) {
                    station.description = stationData.get("station").get("description").asText("");
                }
            } catch (Exception e) {
                // Don't crash if we can't get the description
                System.err.println("Error fetching station description for station " + i + ": " + e.getMessage());
            }
        }
    }

    private void displaySearchResults(
            List<RadioStation> stations, 
            Message message, 
            CommandEvent cmdEvent, 
            InteractionHook hook, 
            SlashCommandEvent slashEvent, 
            int page, 
            String query,
            String moreResultsUrl,
            int moreResultsOffset) {
        
        int stationsPerPage = 5;
        int startIndex = page * stationsPerPage;
        int endIndex = Math.min(startIndex + stationsPerPage, stations.size());
        
        // Check if we have a valid page
        if (startIndex >= stations.size() || startIndex < 0) {
            if (message != null) {
                message.editMessage("No more stations to display.").queue();
            } else if (hook != null) {
                hook.editOriginal("No more stations to display.").queue();
            }
            return;
        }
        
        // Get the stations for this page
        List<RadioStation> pageStations = stations.subList(startIndex, endIndex);
        
        // Create a simple text description without duplicating the list
        StringBuilder description = new StringBuilder();
        description.append("📻 Click a button to select a station to play.\n\n");
        
        // Add page information
        int totalPages = (stations.size() + stationsPerPage - 1) / stationsPerPage;
        description.append("**Page ").append(page + 1).append(" of ").append(totalPages).append("**\n\n");
        
        // Prepare the list of stations to display
        List<String> stationDisplays = new ArrayList<>();
        for (int i = 0; i < pageStations.size(); i++) {
            RadioStation station = pageStations.get(i);
            String stationDisplay = "";
            
            // Create a link to the radio page
            String stationUrl = "https://onlineradiobox.com/" + station.path;
            if (!stationUrl.endsWith("/")) {
                stationUrl += "/";
            }
            stationDisplay = "**" + (startIndex + i + 1) + ".** ";
            
            // Add the link to the radio page with the station name
            stationDisplay += "[" + FormatUtil.filter(station.title) + "](" + stationUrl + ")";
            
            // Add genres if available
            if (!station.genres.isEmpty()) {
                stationDisplay += " - ";
                List<String> decodedGenres = new ArrayList<>();
                for (String genre : station.genres) {
                    decodedGenres.add(decodeHtmlEntities(genre));
                }
                stationDisplay += String.join(", ", decodedGenres);
            }
            
            // Add country flag if available
            if (!station.country.isEmpty()) {
                String flagEmoji = countryCodeToFlagEmoji(station.country);
                if (!flagEmoji.isEmpty()) {
                    stationDisplay += " | " + flagEmoji;
                }
            }

            // Add description if available
            if (station.description != null && !station.description.isEmpty()) {
                // Truncate description if it's too long (more than 150 characters)
                String desc = station.description;
                if (desc.length() > 150) {
                    desc = desc.substring(0, 147) + "...";
                }
                stationDisplay += "\n" + "*" + desc + "*";
            }
            
            stationDisplays.add(stationDisplay);
        }
        
        // Add stations to the description
        for (String stationDisplay : stationDisplays) {
            description.append(stationDisplay).append("\n\n");
        }

        // Create the embed with results
        EmbedBuilder embed = new EmbedBuilder();
        if (cmdEvent != null) {
            embed.setColor(cmdEvent.getSelfMember().getColor());
            embed.setTitle(FormatUtil.filter(cmdEvent.getClient().getSuccess() + " Radio station search results:"));
        } else if (hook != null && slashEvent != null) {
            embed.setColor(slashEvent.getGuild().getSelfMember().getColor());
            embed.setTitle(FormatUtil.filter(slashEvent.getClient().getSuccess() + " Radio station search results:"));
        }
        embed.setDescription(description.toString());
        
        // Create selection buttons
        List<Button> buttons = new ArrayList<>();
        for (int i = 0; i < pageStations.size(); i++) {
            buttons.add(Button.primary("radio:" + (startIndex + i), String.valueOf(startIndex + i + 1)));
        }
        
        // Add navigation buttons
        if (page > 0) {
            buttons.add(Button.secondary("radio:prev:" + page + ":" + query, "⬅️ Previous"));
        }
        
        if (endIndex < stations.size()) {
            buttons.add(Button.secondary("radio:next:" + page + ":" + query, "Next ➡️"));
        }
        
        // Add "Load More" button if we're on the last page and there are more results available
        boolean isLastPage = (page == totalPages - 1);
        if (moreResultsOffset > 0 && isLastPage) {
            buttons.add(Button.success("radio:more:" + query + ":" + moreResultsOffset, "Load More Results"));
        }
        
        buttons.add(Button.danger("radio:cancel", "❌ Cancel"));
        
        // Distribute buttons in rows (max 5 buttons per row)
        List<ActionRow> actionRows = new ArrayList<>();
        
        // Selection buttons in first row
        List<Button> selectionButtons = new ArrayList<>();
        for (int i = 0; i < Math.min(5, pageStations.size()); i++) {
            selectionButtons.add(buttons.get(i));
        }
        actionRows.add(ActionRow.of(selectionButtons));
        
        // Navigation buttons in second row
        List<Button> navigationButtons = new ArrayList<>();
        for (int i = pageStations.size(); i < buttons.size(); i++) {
            navigationButtons.add(buttons.get(i));
        }
        
        if (!navigationButtons.isEmpty()) {
            actionRows.add(ActionRow.of(navigationButtons));
        }
        
        // Send the embed with buttons
        if (cmdEvent != null) {
            // For classic commands
            if (message != null) {
                message.editMessageEmbeds(embed.build())
                        .setComponents(actionRows)
                        .queue(m -> setupButtonListener(m, stations, cmdEvent, null, page, query, moreResultsUrl, moreResultsOffset));
            } else {
                cmdEvent.getChannel().sendMessageEmbeds(embed.build())
                        .setComponents(actionRows)
                        .queue(m -> setupButtonListener(m, stations, cmdEvent, null, page, query, moreResultsUrl, moreResultsOffset));
            }
        } else if (hook != null && slashEvent != null) {
            // For slash commands
            hook.editOriginalEmbeds(embed.build())
                    .setComponents(actionRows)
                    .queue(m -> setupButtonListener(m, stations, null, slashEvent, page, query, moreResultsUrl, moreResultsOffset));
        }
    }

    // Method to configure the button listener
    private void setupButtonListener(
            Message message, 
            List<RadioStation> stations, 
            CommandEvent cmdEvent, 
            SlashCommandEvent slashEvent, 
            int currentPage, 
            String query,
            String moreResultsUrl,
            int moreResultsOffset) {
            
        // Configure the button listener for responses
        bot.getWaiter().waitForEvent(
            net.dv8tion.jda.api.events.interaction.component.ButtonInteractionEvent.class,
            e -> {
                // Check if it's the correct message and the user is authorized
                if (!e.getMessage().equals(message)) {
                    return false;
                }
                
                if (cmdEvent != null) {
                    return e.getUser().equals(cmdEvent.getAuthor());
                } else if (slashEvent != null) {
                    return e.getUser().equals(slashEvent.getUser());
                }
                
                return false;
            },
            e -> {
                // Handle button interaction
                String buttonId = e.getComponentId();
                
                if (buttonId.equals("radio:cancel")) {
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
                } else if (buttonId.startsWith("radio:next:")) {
                    // Next page button
                    String[] parts = buttonId.split(":");
                    int page = Integer.parseInt(parts[2]);
                    String searchQuery = parts.length > 3 ? parts[3] : query;
                    
                    // Acknowledge the button click
                    e.deferEdit().queue();
                    
                    // Display the next page
                    displaySearchResults(stations, e.getMessage(), cmdEvent, null, slashEvent, page + 1, searchQuery, moreResultsUrl, moreResultsOffset);
                } else if (buttonId.startsWith("radio:prev:")) {
                    // Previous page button
                    String[] parts = buttonId.split(":");
                    int page = Integer.parseInt(parts[2]);
                    String searchQuery = parts.length > 3 ? parts[3] : query;
                    
                    // Acknowledge the button click
                    e.deferEdit().queue();
                    
                    // Display the previous page
                    displaySearchResults(stations, e.getMessage(), cmdEvent, null, slashEvent, page - 1, searchQuery, moreResultsUrl, moreResultsOffset);
                } else if (buttonId.startsWith("radio:more:")) {
                    // Load more results button
                    String[] parts = buttonId.split(":");
                    String searchQuery = parts.length > 2 ? parts[2] : query;
                    int offset = parts.length > 3 ? Integer.parseInt(parts[3]) : 20;
                    
                    // Acknowledge the button click and show loading message
                    e.editMessage("Loading more results...").queue();
                    
                    // Load more results in a new thread to avoid blocking
                    new Thread(() -> {
                        try {
                            // Load additional stations
                            List<RadioStation> moreStations = loadMoreSearchResults(searchQuery, offset, 20);
                            
                            // If we got more stations, add them to our list
                            if (!moreStations.isEmpty()) {
                                // Add the new stations to the existing list
                                stations.addAll(moreStations);
                                
                                // Pre-load descriptions for the new stations
                                preloadStationDescriptions(stations, stations.size() - moreStations.size(), stations.size());
                                
                                // Check for next "Show more" button and get its offset
                                String newMoreResultsUrl = moreResultsUrl;
                                int newMoreResultsOffset = offset + 20;  // Increase by 20 for the next batch
                                
                                // Show the current page with the new stations
                                int currentPageToShow = currentPage;  // Stay on the same page
                                
                                // If we were on the last page and added more stations, we might need to show a new page
                                int totalPages = (stations.size() + 4) / 5;  // Calculate total pages (5 stations per page)
                                if (currentPage >= totalPages - 1) {
                                    // We've reached the end, show the last page
                                    currentPageToShow = totalPages - 1;
                                }
                                
                                displaySearchResults(
                                    stations, 
                                    e.getMessage(), 
                                    cmdEvent, 
                                    null, 
                                    slashEvent, 
                                    currentPageToShow, 
                                    searchQuery,
                                    newMoreResultsUrl,
                                    newMoreResultsOffset
                                );
                            } else {
                                // No more stations found
                                e.getMessage().editMessage("No more stations found.")
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
                            }
                        } catch (Exception ex) {
                            // Handle errors
                            e.getMessage().editMessage("Error loading more results: " + ex.getMessage())
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
                        }
                    }).start();
                } else if (buttonId.startsWith("radio:")) {
                    // Extract the station index
                    int index = Integer.parseInt(buttonId.substring(6));
                    
                    // Select station
                    RadioStation station = stations.get(index);
                    
                    // Disable all buttons and indicate selection
                    e.editMessage("Station **" + station.title + "** selected!")
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
                    
                    // Load and play radio
                    if (cmdEvent != null) {
                        loadAndPlayRadio(new RadioStation(cleanStationTitle(station.title), station.path, station.directStreamUrl, station.logoUrl, station.country, station.genres), cmdEvent, null);
                    } else if (slashEvent != null) {
                        loadAndPlayRadio(new RadioStation(cleanStationTitle(station.title), station.path, station.directStreamUrl, station.logoUrl, station.country, station.genres), null, slashEvent.getUser());
                    }
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
    }

    private void loadAndPlayRadio(RadioStation station, CommandEvent cmdEvent, net.dv8tion.jda.api.entities.User slashUser) {
        try {
            // Get the guild ID
            final String guildId;
            if (cmdEvent != null) {
                guildId = cmdEvent.getGuild().getId();
            } else if (slashUser != null) {
                Guild guild = slashUser.getJDA().getGuildById(slashUser.getJDA().getSelfUser().getId());
                if (guild != null) {
                    guildId = guild.getId();
                } else {
                    guildId = null;
                }
            } else {
                guildId = null;
            }
            
            // Cancel any existing timer for this guild
            if (guildId != null) {
                cancelExistingTimer(guildId);
            }
            
            String streamUrl = null;
            
            // Use direct stream URL if available
            if (station.directStreamUrl != null && !station.directStreamUrl.isEmpty()) {
                streamUrl = station.directStreamUrl;
            } else {
                // Get station details
                URL url = new URL("https://onlineradiobox.com/json/" + station.path);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
                connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                
                BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                // Get stream URL from widget data
                URL widgetUrl = new URL("https://onlineradiobox.com/json/" + station.path + "/widget/");
                HttpURLConnection widgetConnection = (HttpURLConnection) widgetUrl.openConnection();
                widgetConnection.setRequestMethod("GET");
                widgetConnection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
                widgetConnection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
                
                reader = new BufferedReader(new InputStreamReader(widgetConnection.getInputStream(), StandardCharsets.UTF_8));
                response = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();
                
                JsonNode widgetData = mapper.readTree(response.toString());
                streamUrl = widgetData.path("streamURL").asText();
            }
            
            if (streamUrl == null || streamUrl.isEmpty()) {
                String errorMsg = "Could not find stream URL for the selected radio station.";
                if (cmdEvent != null) {
                    cmdEvent.replyError(errorMsg);
                }
                return;
            }
            
            // Create handlers with the station logo
            RadioLoadHandler handler;
            if (cmdEvent != null) {
                handler = new RadioLoadHandler(cmdEvent, station.title, station.path);
                handler.logoUrl = station.logoUrl;
                bot.getPlayerManager().loadItemOrdered(cmdEvent.getGuild(), streamUrl, handler);
            } else if (slashUser != null) {
                Guild guild = slashUser.getJDA().getGuildById(slashUser.getJDA().getSelfUser().getId());
                if (guild != null) {
                    handler = new RadioLoadHandler(null, station.title, slashUser, station.path);
                    handler.logoUrl = station.logoUrl;
                    bot.getPlayerManager().loadItemOrdered(guild, streamUrl, handler);
                    
                    // For slash commands, we can also display an embed with the logo
                    if (station.logoUrl != null && !station.logoUrl.isEmpty()) {
                        // Implementation for slash commands
                        net.dv8tion.jda.api.entities.channel.middleman.MessageChannel channel = guild.getTextChannelById(guild.getTextChannelCache().asList().get(0).getId());
                        if (channel != null) {
                            EmbedBuilder embed = new EmbedBuilder();
                            embed.setColor(guild.getSelfMember().getColor());
                            embed.setTitle("Radio added to queue");
                            embed.setDescription("**" + station.title + "** has been added to the queue!");
                            embed.setThumbnail(station.logoUrl);
                            channel.sendMessageEmbeds(embed.build()).queue();
                        }
                    }
                }
            }
            
            // Save the station path for later updates
            if (guildId != null) {
                lastStationPaths.put(guildId, station.path);
                if (station.logoUrl != null && !station.logoUrl.isEmpty()) {
                    lastStationLogos.put(guildId, station.logoUrl);
                }
            }
            
        } catch (Exception e) {
            String errorMsg = "Error loading radio station: " + e.getMessage();
            if (e.getCause() != null) {
                errorMsg += " Cause: " + e.getCause().getMessage();
            }
            
            if (cmdEvent != null) {
                cmdEvent.replyError(errorMsg);
            }
        }
    }
    
    private String getCurrentTrackInfo(String stationPath) {
        try {
            // Get current track info from OnlineRadioBox scraper API
            String scraperId = stationPath.replace("/", ".");
            URL scrapeUrl = new URL("http://scraper.onlineradiobox.com/" + scraperId);
            
            HttpURLConnection connection = (HttpURLConnection) scrapeUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            JsonNode trackInfo = mapper.readTree(response.toString());
            if (trackInfo.has("updated") && trackInfo.path("updated").asInt() > 0) {
                return trackInfo.path("title").asText();
            }
            
            return null;
        } catch (Exception e) {
            // Just return null on error, don't crash
            return null;
        }
    }
    
    // Class to store detailed information about a track
    public static class TrackInfo {
        public String title = ""; // Full title (Artist - Song name)
        public String artist = ""; // Artist
        public String songName = ""; // Song name
        public String imageUrl = ""; // Album cover URL
        
        // Returns a formatted title for display
        public String getFormattedTitle() {
            if (!artist.isEmpty() && !songName.isEmpty()) {
                return artist + " - " + songName;
            } else if (!title.isEmpty()) {
                return title;
            } else {
                return "Unknown";
            }
        }
        
        // Returns a formatted title with the station name
        public String getFormattedTitleWithStation(String stationName) {
            String baseTitle = getFormattedTitle();
            String cleanStationName = stationName; // Use already cleaned station name
            
            if (baseTitle.equals("Unknown")) {
                return cleanStationName;
            } else {
                // Format: "Artist - Song | Station Name" 
                return baseTitle + " | " + cleanStationName;
            }
        }
        
        // Returns a formatted title without the station name (for "Radio added to queue" message)
        public String getFormattedTitleWithoutStation() {
            return getFormattedTitle();
        }
    }
    
    // New method that returns an object with more information about the current song
    public TrackInfo getDetailedTrackInfo(String stationPath) {
        try {
            // Get current track info from OnlineRadioBox scraper API
            String scraperId = stationPath.replace("/", ".");
            URL scrapeUrl = new URL("http://scraper.onlineradiobox.com/" + scraperId);
            
            HttpURLConnection connection = (HttpURLConnection) scrapeUrl.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            JsonNode trackInfo = mapper.readTree(response.toString());
            if (trackInfo.has("updated") && trackInfo.path("updated").asInt() > 0) {
                TrackInfo info = new TrackInfo();
                info.title = trackInfo.path("title").asText("");
                info.artist = trackInfo.path("iArtist").asText("");
                info.songName = trackInfo.path("iName").asText("");
                info.imageUrl = trackInfo.path("iImg").asText("");
                
                return info;
            }
            
            return null;
        } catch (Exception e) {
            // Just return null on error, don't crash
            System.err.println("Error getting track info: " + e.getMessage());
            return null;
        }
    }
    
    private class RadioStation {
        final String title;
        final String path;
        final String directStreamUrl;
        final String logoUrl;
        final String country;
        final List<String> genres;
        String description; // Added description field
        
        RadioStation(String title, String path) {
            this.title = title;
            this.path = path;
            this.directStreamUrl = null;
            this.logoUrl = "";
            this.country = "";
            this.genres = new ArrayList<>();
            this.description = "";
        }
        
        RadioStation(String title, String path, String logoUrl, String country, List<String> genres) {
            this.title = title;
            this.path = path;
            this.directStreamUrl = null;
            this.logoUrl = logoUrl;
            this.country = country;
            this.genres = genres;
            this.description = "";
        }
        
        RadioStation(String title, String path, String directStreamUrl, String logoUrl, String country, List<String> genres) {
            this.title = title;
            this.path = path;
            this.directStreamUrl = directStreamUrl;
            this.logoUrl = logoUrl;
            this.country = country;
            this.genres = genres;
            this.description = "";
        }
    }
    
    private class RadioLoadHandler implements AudioLoadResultHandler {
        private final CommandEvent event;
        private final String stationTitle;
        private final net.dv8tion.jda.api.entities.User slashUser;
        private final String stationPath;
        private String logoUrl; // Field to store the logo URL
        
        private RadioLoadHandler(CommandEvent event, String stationTitle, String stationPath) {
            this.event = event;
            this.stationTitle = cleanStationTitle(stationTitle);
            this.slashUser = null;
            this.stationPath = stationPath;
            this.logoUrl = null;
        }
        
        private RadioLoadHandler(CommandEvent event, String stationTitle, net.dv8tion.jda.api.entities.User slashUser, String stationPath) {
            this.event = event;
            this.stationTitle = cleanStationTitle(stationTitle);
            this.slashUser = slashUser;
            this.stationPath = stationPath;
            this.logoUrl = null;
        }
        
        @Override
        public void trackLoaded(AudioTrack track) {
            try {
                // Get the guild ID
                final String guildId;
                if (event != null) {
                    guildId = event.getGuild().getId();
                } else if (slashUser != null) {
                    Guild guild = slashUser.getJDA().getGuildById(slashUser.getJDA().getSelfUser().getId());
                    if (guild != null) {
                        guildId = guild.getId();
                    } else {
                        guildId = null;
                    }
                } else {
                    guildId = null;
                }
                
                // Cancel any existing timer for this guild
                if (guildId != null) {
                    cancelExistingTimer(guildId);
                }
                
                // Get detailed information about the current track
                TrackInfo trackInfo = getDetailedTrackInfo(stationPath);
                String title;
                
                if (trackInfo != null && !trackInfo.getFormattedTitle().isEmpty() && !trackInfo.getFormattedTitle().equals("Unknown")) {
                    title = trackInfo.getFormattedTitleWithStation(stationTitle);
                } else {
                    // Fallback title if no track info is available
                    title = stationTitle;
                }
                
                // Force update of track title for all cases, not just when title is null or empty
                // This ensures the title is always set correctly
                java.lang.reflect.Field titleField = track.getInfo().getClass().getDeclaredField("title");
                titleField.setAccessible(true);
                titleField.set(track.getInfo(), title);
                
                // Also set the user data as a backup
                track.setUserData(title);
                
                // Save the station path for later updates
                if (guildId != null) {
                    lastStationPaths.put(guildId, stationPath);
                    if (logoUrl != null && !logoUrl.isEmpty()) {
                        lastStationLogos.put(guildId, logoUrl);
                    }
                }
                
                // Setup a timer to update the track title periodically (every minute)
                if (guildId != null) {
                    Timer timer = new Timer();
                    
                    // Store the timer
                    activeTimers.put(guildId, timer);
                    
                    // Capture the guildId as final for the timerTask
                    final String finalGuildId = guildId;
                    
                    timer.scheduleAtFixedRate(
                        new java.util.TimerTask() {
                            @Override
                            public void run() {
                                try {
                                    // Check if we're still playing the radio
                                    Guild currentGuild = bot.getJDA().getGuildById(finalGuildId);
                                    if (currentGuild == null) {
                                        this.cancel();
                                        activeTimers.remove(finalGuildId);
                                        return;
                                    }
                                    
                                    AudioHandler handler = (AudioHandler) currentGuild.getAudioManager().getSendingHandler();
                                    if (handler == null || handler.getPlayer().getPlayingTrack() == null || 
                                        !handler.getPlayer().getPlayingTrack().equals(track) ||
                                        !lastStationPaths.containsKey(finalGuildId) ||
                                        !lastStationPaths.get(finalGuildId).equals(stationPath)) {
                                        // We're no longer playing this radio track, cancel the timer
                                        this.cancel();
                                        activeTimers.remove(finalGuildId);
                                        lastStationPaths.remove(finalGuildId);
                                        return;
                                    }
                                    
                                    // Get the latest track info
                                    TrackInfo latestInfo = getDetailedTrackInfo(stationPath);
                                    if (latestInfo != null && !latestInfo.getFormattedTitle().isEmpty() && !latestInfo.getFormattedTitle().equals("Unknown")) {
                                        String newTitle = latestInfo.getFormattedTitleWithStation(stationTitle);
                                        
                                        // Update the title
                                        titleField.set(track.getInfo(), newTitle);
                                        track.setUserData(newTitle);
                                        
                                        // Also update the Discord status/topic and bot nickname
                                        Guild guild = bot.getJDA().getGuildById(finalGuildId);
                                        if (guild != null) {
                                            // Update channel topic through NowplayingHandler
                                            bot.getNowplayingHandler().onTrackUpdate(guild.getIdLong(), track, 
                                                (AudioHandler) guild.getAudioManager().getSendingHandler());
                                            
                                            // Update bot activity status if enabled
                                            if (bot.getConfig().getSongInStatus() && 
                                                    bot.getJDA().getGuilds().stream()
                                                    .filter(g -> g.getSelfMember().getVoiceState() != null && 
                                                            g.getSelfMember().getVoiceState().inAudioChannel())
                                                    .count() <= 1) {
                                                bot.getJDA().getPresence().setActivity(Activity.listening(newTitle));
                                            }
                                            
                                            // Update the last radio message if it exists
                                            Long messageId = lastRadioMessageIds.get(finalGuildId);
                                            if (messageId != null) {
                                                // Find the text channel to use
                                                Settings settings = bot.getSettingsManager().getSettings(guild.getIdLong());
                                                TextChannel tchan = settings.getTextChannel(guild);
                                                
                                                if (tchan != null) {
                                                    tchan.retrieveMessageById(messageId).queue(message -> {
                                                        // Create a new embed with updated info
                                                        EmbedBuilder embed = new EmbedBuilder();
                                                        embed.setColor(guild.getSelfMember().getColor());
                                                        embed.setTitle("Now playing on " + stationTitle);
                                                        
                                                        // Description with updated information
                                                        StringBuilder description = new StringBuilder();
                                                        description.append("**Now playing:**\n").append(newTitle);
                                                        
                                                        embed.setDescription(description.toString());
                                                        
                                                        // Keep original thumbnail (station logo)
                                                        if (logoUrl != null && !logoUrl.isEmpty()) {
                                                            embed.setThumbnail(logoUrl);
                                                        }
                                                        
                                                        // Update album cover if available
                                                        if (latestInfo != null && !latestInfo.imageUrl.isEmpty()) {
                                                            embed.setImage(latestInfo.imageUrl);
                                                        }
                                                        
                                                        // Update the message
                                                        message.editMessageEmbeds(embed.build()).queue(
                                                            success -> {}, 
                                                            error -> lastRadioMessageIds.remove(finalGuildId) // Remove from map if message is gone
                                                        );
                                                    }, error -> lastRadioMessageIds.remove(finalGuildId)); // Remove from map if message is gone
                                                }
                                            }
                                        }
                                    }
                                } catch (Exception e) {
                                    System.err.println("Error updating radio track title: " + e.getMessage());
                                    // Cancel the timer if we encounter an error
                                    this.cancel();
                                    // Remove the timer from the map
                                    activeTimers.remove(finalGuildId);
                                }
                            }
                        },
                        30000, // Delay before first update (every 30s)
                        30000  // Period between updates (every 30s)
                    );
                }
                
            } catch (Exception e) {
                // If reflection fails, at least set the user data and log the error
                track.setUserData(stationTitle);
                System.err.println("Failed to update track title: " + e.getMessage());
            }
            
            if (event != null) {
                AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
                handler.addTrack(new QueuedTrack(track, event.getAuthor()));
                
                // Get detailed information for the embed
                TrackInfo trackInfo = getDetailedTrackInfo(stationPath);
                
                // Create an embed to display the radio addition with its logo
                if (logoUrl != null && !logoUrl.isEmpty()) {
                    EmbedBuilder embed = new EmbedBuilder();
                    embed.setColor(event.getSelfMember().getColor());
                    embed.setTitle(FormatUtil.filter(event.getClient().getSuccess() + " Radio added to queue"));
                    
                    // Description with more information
                    StringBuilder description = new StringBuilder();
                    description.append("**").append(stationTitle).append("** has been added to the queue!");
                    
                    // Add information about the currently playing song if available
                    if (trackInfo != null && !trackInfo.getFormattedTitle().isEmpty() && !trackInfo.getFormattedTitle().equals("Unknown")) {
                        description.append("\n\n**Now playing:**\n").append(trackInfo.getFormattedTitleWithoutStation());
                    }
                    
                    embed.setDescription(description.toString());
                    embed.setThumbnail(logoUrl);
                    
                    // Add album cover if available
                    if (trackInfo != null && !trackInfo.imageUrl.isEmpty()) {
                        embed.setImage(trackInfo.imageUrl);
                    }
                    
                    // Send the message and save the ID for later updates
                    String guildId = event.getGuild().getId();
                    event.reply(embed.build(), message -> {
                        // Store message ID for later updates
                        lastRadioMessageIds.put(guildId, message.getIdLong());
                    });
                } else {
                    // Simple text version with song information
                    String replyMessage = "Added radio station **" + stationTitle + "** to the queue!";
                    
                    if (trackInfo != null && !trackInfo.getFormattedTitle().isEmpty() && !trackInfo.getFormattedTitle().equals("Unknown")) {
                        replyMessage += "\nCurrently playing: " + trackInfo.getFormattedTitleWithoutStation();
                    }
                    
                    event.replySuccess(replyMessage);
                }
            } else if (slashUser != null) {
                Guild guild = slashUser.getJDA().getGuildById(slashUser.getJDA().getSelfUser().getId());
                if (guild != null) {
                    AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
                    handler.addTrack(new QueuedTrack(track, slashUser));
                    
                    // For slash commands, also get detailed information
                    TrackInfo trackInfo = getDetailedTrackInfo(stationPath);
                    
                    // For slash commands, we can also display an embed with the logo
                    if (logoUrl != null && !logoUrl.isEmpty()) {
                        // Implementation for slash commands
                        MessageChannel channel = guild.getTextChannelById(guild.getTextChannelCache().asList().get(0).getId());
                        if (channel != null) {
                            EmbedBuilder embed = new EmbedBuilder();
                            embed.setColor(guild.getSelfMember().getColor());
                            embed.setTitle("Radio added to queue");
                            
                            // Description with more information
                            StringBuilder description = new StringBuilder();
                            description.append("**").append(stationTitle).append("** has been added to the queue!");
                            if (trackInfo != null && !trackInfo.getFormattedTitle().isEmpty() && !trackInfo.getFormattedTitle().equals("Unknown")) {
                                description.append("\n\n**Now playing:**\n").append(trackInfo.getFormattedTitleWithoutStation());
                            }
                            
                            embed.setDescription(description.toString());
                            embed.setThumbnail(logoUrl);
                            
                            // Add album cover if available
                            if (trackInfo != null && !trackInfo.imageUrl.isEmpty()) {
                                embed.setImage(trackInfo.imageUrl);
                            }
                            
                            final String guildId = guild.getId();
                            channel.sendMessageEmbeds(embed.build()).queue(message -> {
                                // Store message ID for later updates
                                lastRadioMessageIds.put(guildId, message.getIdLong());
                            });
                        }
                    }
                }
            }
        }
        
        @Override
        public void playlistLoaded(AudioPlaylist playlist) {
            // Should not happen with radio stations
            if (!playlist.getTracks().isEmpty()) {
                trackLoaded(playlist.getTracks().get(0));
            }
        }
        
        @Override
        public void noMatches() {
            if (event != null) {
                event.replyError("Could not find the radio station stream. Please try another station.");
            }
        }
        
        @Override
        public void loadFailed(FriendlyException throwable) {
            if (event != null) {
                if (throwable.severity == Severity.COMMON)
                    event.replyError("Error loading radio station: " + throwable.getMessage());
                else
                    event.replyError("Error loading radio station.");
            }
        }
    }
    
    /**
     * Convert a country code to a flag emoji
     * This uses the Unicode regional indicator symbols
     */
    private String countryCodeToFlagEmoji(String countryCode) {
        // Country codes are ISO 3166-1 alpha-2, which we can convert to flag emoji
        if (countryCode.length() != 2) {
            return "";
        }
        
        // Check if countryCode contains only lowercase letters a-z
        if (!countryCode.matches("[a-z]{2}")) {
            return "";
        }
        
        // Convert country code to uppercase and then to regional indicator symbols
        // Regional indicator symbols are Unicode characters in the range U+1F1E6 to U+1F1FF
        StringBuilder emoji = new StringBuilder();
        for (char c : countryCode.toUpperCase().toCharArray()) {
            emoji.append(Character.toChars(0x1F1E6 + (c - 'A')));
        }
        
        return emoji.toString();
    }
    
    /**
     * Decode HTML entities in a string
     * This handles common entities like &amp;, &lt;, &gt;, &quot;, &#39; etc.
     */
    private String decodeHtmlEntities(String input) {
        if (input == null || input.isEmpty()) {
            return input;
        }
        
        // Replace common HTML entities
        return input.replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&nbsp;", " ");
    }
    
    /**
     * Clean station title by removing '>' characters at the beginning
     * and trimming whitespace
     */
    private String cleanStationTitle(String title) {
        if (title == null || title.isEmpty()) {
            return title;
        }
        
        // Remove leading '>' characters
        while (title.startsWith(">")) {
            title = title.substring(1);
        }
        
        // Trim whitespace and decode common HTML entities
        return decodeHtmlEntities(title.trim());
    }

    // Method to cancel an existing timer for a guild
    private void cancelExistingTimer(String guildId) {
        Timer existingTimer = activeTimers.get(guildId);
        if (existingTimer != null) {
            existingTimer.cancel();
            existingTimer.purge();
            activeTimers.remove(guildId);
            System.out.println("Cancelled existing timer for guild: " + guildId);
        }
    }

    // Method to load more search results using the "Show more" button
    private List<RadioStation> loadMoreSearchResults(String query, int offset, int maxResults) {
        try {
            // Encode the query for the URL
            String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8.toString());
            
            // Create URL for the search with pagination parameters
            // The URL format is: https://onlineradiobox.com/search?part=1&q=[query]&offset=[offset]
            URL url = new URL("https://onlineradiobox.com/search?part=1&q=" + encodedQuery + "&offset=" + offset);
            
            // Setup connection
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/91.0.4472.124 Safari/537.36");
            connection.setRequestProperty("Accept-Language", "en-US,en;q=0.9");
            
            // Read the response
            BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8));
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                response.append(line);
            }
            reader.close();
            
            // Parse HTML to find radio stations
            String html = response.toString();
            
            // Get stations from the HTML response
            List<RadioStation> moreStations = parseRadioStations(html, maxResults);
            
            // Check if there's another "Show more" button to get the next offset
            int moreResultsDiv = html.indexOf("<div class=\"search-result__more\"");
            if (moreResultsDiv != -1) {
                int offsetStart = html.indexOf("offset=\"", moreResultsDiv);
                if (offsetStart != -1) {
                    offsetStart += 8; // Length of "offset=\""
                    int offsetEnd = html.indexOf("\"", offsetStart);
                    if (offsetEnd != -1) {
                        try {
                            int newOffset = Integer.parseInt(html.substring(offsetStart, offsetEnd));
                            // Save the new offset for the next load if needed
                            System.out.println("Next offset available: " + newOffset);
                        } catch (NumberFormatException e) {
                            // Just log the error and continue
                            System.err.println("Error parsing next offset: " + e.getMessage());
                        }
                    }
                }
            }
            
            return moreStations;
            
        } catch (Exception e) {
            System.err.println("Error loading more search results: " + e.getMessage());
            e.printStackTrace(); // Print stack trace for more detailed error information
            return new ArrayList<>();
        }
    }
} 