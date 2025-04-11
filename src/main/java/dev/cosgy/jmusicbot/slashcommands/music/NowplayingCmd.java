/*
 * Copyright 2018 John Grosh (jagrosh).
 * Copyright 2025 THOMZY.
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
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;
import net.dv8tion.jda.api.EmbedBuilder;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import java.net.HttpURLConnection;
import java.net.URL;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Consumer;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.channel.middleman.MessageChannel;
import net.dv8tion.jda.api.JDA;
import com.jagrosh.jmusicbot.JMusicBot;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.audio.RequestMetadata;

/**
 * @author John Grosh <john.a.grosh@gmail.com> & THOMZY ( edit radio)  
 */
public class NowplayingCmd extends MusicCommand {
    private final RadioCmd radioCmd;
    private final ObjectMapper mapper = new ObjectMapper();

    public NowplayingCmd(Bot bot) {
        super(bot);
        this.name = "nowplaying";
        this.help = "Displays the currently playing track";
        this.aliases = bot.getConfig().getAliases(this.name);
        this.botPermissions = new Permission[]{Permission.MESSAGE_EMBED_LINKS};
        this.radioCmd = new RadioCmd(bot);
    }

    @Override
    public void doCommand(CommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        if (handler.getPlayer().getPlayingTrack() == null) {
            event.reply(handler.getNoMusicPlaying(event.getJDA()));
            bot.getNowplayingHandler().clearLastNPMessage(event.getGuild());
            return;
        }

        processNowPlaying(event.getGuild(), event.getJDA(), event.getSelfMember(), event.getChannel(), true, 
            (m) -> event.reply(m, bot.getNowplayingHandler()::setLastNPMessage),
            (s) -> event.reply(s));
    }

    @Override
    public void doCommand(SlashCommandEvent event) {
        AudioHandler handler = (AudioHandler) event.getGuild().getAudioManager().getSendingHandler();
        if (handler.getPlayer().getPlayingTrack() == null) {
            event.reply("No music is currently playing.").queue();
            bot.getNowplayingHandler().clearLastNPMessage(event.getGuild());
            return;
        }

        event.reply("Displaying the currently playing track...").queue(h -> h.deleteOriginal().queue());

        processNowPlaying(event.getGuild(), event.getJDA(), event.getGuild().getSelfMember(), event.getChannel(), false,
            null,
            (s) -> event.reply(s).queue());
    }

    /**
     * Process now playing information for both command types
     * 
     * @param guild The guild where the command is executed
     * @param jda JDA instance
     * @param selfMember Bot's member instance
     * @param channel Channel where the command was used
     * @param isStandardCommand Whether this is a standard command (true) or slash command (false)
     * @param standardReplyFunction Function to handle replies for standard commands
     * @param errorReplyFunction Function to handle error replies
     */
    private void processNowPlaying(Guild guild, JDA jda, Member selfMember, MessageChannel channel, boolean isStandardCommand,
                                  Consumer<MessageCreateData> standardReplyFunction,
                                  Consumer<String> errorReplyFunction) {
        
        AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        String guildId = guild.getId();
        AudioTrack track = handler.getPlayer().getPlayingTrack();
        
        // Check if this is a Spotify track based on current track data
        boolean isSpotifyTrack = track != null && SpotifyCmd.lastTrackIds.containsKey(guildId);
        
        if (isSpotifyTrack && SpotifyCmd.lastTrackIds.containsKey(guildId)) {
            // Get Spotify track info
            SpotifyCmd.SpotifyTrackInfo trackInfo = SpotifyCmd.getTrackInfo(guildId);
            
            if (trackInfo != null) {
                // Create Spotify-style embed
                EmbedBuilder embed = new EmbedBuilder();
                embed.setColor(trackInfo.color);
                embed.setTitle("~ Now Playing Spotify Track ~");
                
                // Get request metadata for the user who requested the track
                RequestMetadata rm = handler.getRequestMetadata();
                if (rm.getOwner() != 0L) {
                    try {
                        net.dv8tion.jda.api.entities.User u = guild.getJDA().getUserById(rm.user.id);
                        if (u == null)
                            embed.setAuthor(rm.user.username, null, rm.user.avatar);
                        else
                            embed.setAuthor(u.getName(), null, u.getEffectiveAvatarUrl());
                    } catch (Exception e) {
                        // Ignore errors with getting user info
                    }
                }
                
                // Build rich description with track details
                StringBuilder description = new StringBuilder();
                description.append("**Track:** ").append(trackInfo.trackName);
                description.append("\n**Album:** ").append(trackInfo.albumName);
                description.append("\n**Artist:** ").append(trackInfo.artistName);
                
                // Add progress bar
                double progress = (double) handler.getPlayer().getPlayingTrack().getPosition() / track.getDuration();
                description.append("\n\n");
                description.append((handler.getPlayer().isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI))
                        .append(" ")
                        .append(FormatUtil.progressBar(progress))
                        .append(" `[")
                        .append(FormatUtil.formatTime(track.getPosition()))
                        .append("/")
                        .append(FormatUtil.formatTime(track.getDuration()))
                        .append("]` ")
                        .append(FormatUtil.volumeIcon(handler.getPlayer().getVolume()));
                
                embed.setDescription(description.toString());
                
                // Add album art as image if npimages is enabled
                if (bot.getConfig().useNPImages() && trackInfo.albumImageUrl != null && !trackInfo.albumImageUrl.isEmpty()) {
                    embed.setImage(trackInfo.albumImageUrl);
                }
                
                // Set footer to show platform
                embed.setFooter("Source: Spotify", null);
                
                // Send the embed
                if (isStandardCommand) {
                    standardReplyFunction.accept(MessageCreateData.fromEmbeds(embed.build()));
                } else {
                    channel.sendMessageEmbeds(embed.build()).queue(m -> bot.getNowplayingHandler().setLastNPMessage(m));
                }
                return;
            }
        }
        
        // Check if the currently playing track is a radio station
        boolean isRadioTrack = isRadioTrack(track, guildId);
        
        if (isRadioTrack && RadioCmd.lastStationPaths.containsKey(guildId)) {
            // Get radio station info
            String stationPath = RadioCmd.lastStationPaths.get(guildId);
            
            // Get track info using RadioCmd's methods
            RadioCmd.TrackInfo trackInfo = radioCmd.getDetailedTrackInfo(stationPath);
            
            // Get station logo URL from stored data
            String logoUrl = RadioCmd.lastStationLogos.get(guildId);
            
            // Create radio-style embed
            EmbedBuilder embed = new EmbedBuilder();
            embed.setColor(selfMember.getColor());
            embed.setTitle("~ Now Playing Radio ~");
            
            // Get station title from track info
            String stationTitle = track.getInfo().title;
            if (stationTitle.contains(" | ")) {
                stationTitle = stationTitle.substring(stationTitle.lastIndexOf(" | ") + 3);
            }
            if (stationTitle.endsWith(" Radio")) {
                stationTitle = stationTitle.substring(0, stationTitle.length() - 6);
            }
            
            // Create OnlineRadioBox URL for the station
            String stationUrl = "https://onlineradiobox.com/" + stationPath;
            
            // Description with current track info - make station name a clickable link
            StringBuilder description = new StringBuilder();
            description.append("**Station:** [").append(stationTitle).append("](").append(stationUrl).append(")");
            
            if (trackInfo != null && !trackInfo.getFormattedTitle().isEmpty() && !trackInfo.getFormattedTitle().equals("Unknown")) {
                description.append("\n\n**Now playing:**\n").append(trackInfo.getFormattedTitle());
            }
            
            embed.setDescription(description.toString());
            
            // Add station logo as thumbnail and album art only if npimages is enabled
            if (bot.getConfig().useNPImages()) {
                // Add station logo as thumbnail
                if (logoUrl != null && !logoUrl.isEmpty()) {
                    embed.setThumbnail(logoUrl);
                }
                
                // Add album art if available
                if (trackInfo != null && !trackInfo.imageUrl.isEmpty()) {
                    embed.setImage(trackInfo.imageUrl);
                }
            }
            
            if (isStandardCommand) {
                standardReplyFunction.accept(MessageCreateData.fromEmbeds(embed.build()));
            } else {
                channel.sendMessageEmbeds(embed.build()).queue(m -> bot.getNowplayingHandler().setLastNPMessage(m));
            }
        } else {
            // Remove radio station data if this is not a radio track
            if (!isRadioTrack) {
                RadioCmd.lastStationPaths.remove(guildId);
                RadioCmd.lastStationLogos.remove(guildId);
            }
            
            // Use default nowplaying display for non-radio tracks
            MessageCreateData m;
            try {
                m = handler.getNowPlaying(jda);
            } catch (Exception e) {
                errorReplyFunction.accept("Error getting now playing information: " + e.getMessage());
                return;
            }
            if (m == null) {
                if (isStandardCommand) {
                    standardReplyFunction.accept(handler.getNoMusicPlaying(jda));
                } else {
                    channel.sendMessage(handler.getNoMusicPlaying(jda)).queue();
                }
                bot.getNowplayingHandler().clearLastNPMessage(guild);
            } else {
                if (isStandardCommand) {
                    standardReplyFunction.accept(m);
                } else {
                    channel.sendMessage(m).queue(bot.getNowplayingHandler()::setLastNPMessage);
                }
            }
        }
    }
    
    /**
     * Determines if a track is a radio station track
     * This method checks the track properties to identify radio tracks
     */
    private boolean isRadioTrack(AudioTrack track, String guildId) {
        // First check if we have a record of this guild playing a radio station
        if (RadioCmd.lastStationPaths.containsKey(guildId)) {
            // For tracks coming from a stream source that's a radio station
            if (track.getInfo().isStream) {
                // Check if the URL matches the radio station pattern
                String trackUrl = track.getInfo().uri;
                if (trackUrl != null && 
                    (trackUrl.contains("onlineradiobox.com") || 
                     trackUrl.contains("listen.") || 
                     trackUrl.contains(".stream") || 
                     trackUrl.contains("ice") || 
                     trackUrl.contains(".mp3") || 
                     trackUrl.contains(".aac"))) {
                    return true;
                }
            }
        }
        
        // If we're not sure, assume it's not a radio track
        return false;
    }
}
