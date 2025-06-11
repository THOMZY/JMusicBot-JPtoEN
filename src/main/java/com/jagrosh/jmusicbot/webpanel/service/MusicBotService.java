/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.service;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.QueuedTrack;
import com.jagrosh.jmusicbot.audio.RequestMetadata;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

@Service
public class MusicBotService {
    
    private final Bot bot;
    private final MusicService musicService;
    
    public MusicBotService(Bot bot, MusicService musicService) {
        this.bot = bot;
        this.musicService = musicService;
    }

    /**
     * Executes a Discord command through the bot.
     * This simulates a user typing a command in a Discord text channel.
     *
     * @param command The command to execute (without the prefix)
     * @return Response message
     */
    public String executeDiscordCommand(String command) {
        try {
            // Get the currently selected guild
            String guildId = musicService.getSelectedGuildId();
            if (guildId == null) {
                return "No server selected. Please select a server first.";
            }
            
            // Get the guild
            Guild guild = bot.getJDA().getGuildById(guildId);
            if (guild == null) {
                return "The bot is not connected to the selected server.";
            }
            
            // Process different command types
            String result;
            
            // Volume command
            if (command.toLowerCase().startsWith("vol") || command.toLowerCase().startsWith("volume")) {
                result = handleVolumeCommand(command, guild);
            }
            // Play command
            else if (command.toLowerCase().startsWith("play")) {
                result = handlePlayCommand(command, guild);
            }
            // Skip command
            else if (command.toLowerCase().startsWith("skip")) {
                result = handleSkipCommand(guild);
            }
            // Stop command
            else if (command.toLowerCase().startsWith("stop")) {
                result = handleStopCommand(guild);
            }
            // Other commands
            else {
                // Handle other command types - just a basic response for now
                result = "Command executed: " + command;
            }
            
            return result;
        } catch (Exception e) {
            throw new RuntimeException("Failed to execute command: " + e.getMessage(), e);
        }
    }
    
    /**
     * Handles volume command
     */
    private String handleVolumeCommand(String command, Guild guild) {
        try {
            // Get the audio handler
            AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
            if (handler == null) {
                return "No audio playing in this server.";
            }
            
            // Extract volume value
            String[] parts = command.split("\\s+", 2);
            if (parts.length < 2) {
                return "Current volume: " + handler.getPlayer().getVolume() + "%";
            }
            
            int volume;
            try {
                volume = Integer.parseInt(parts[1]);
            } catch (NumberFormatException e) {
                return "Invalid volume value. Please provide a number between 0 and 100.";
            }
            
            // Validate volume
            if (volume < 0 || volume > 100) {
                return "Volume must be between 0 and 100.";
            }
            
            // Set volume
            handler.getPlayer().setVolume(volume);
            return "Volume set to " + volume + "%";
        } catch (Exception e) {
            return "Error executing volume command: " + e.getMessage();
        }
    }
    
    /**
     * Handles play command
     */
    private String handlePlayCommand(String command, Guild guild) {
        try {
            String[] parts = command.split("\\s+", 2);
            if (parts.length < 2) {
                return "Please provide a URL or search term to play.";
            }
            
            String query = parts[1];
            String url;
            
            // Check if the input is a URL or a search term
            if (query.startsWith("http://") || query.startsWith("https://")) {
                // It's a URL, use as is
                url = query;
            } else {
                // It's a search term, prefix with ytsearch:
                url = "ytsearch:" + query;
            }
            
            // Create a CompletableFuture to handle the async loading
            CompletableFuture<String> resultFuture = new CompletableFuture<>();
            
            // Load the item
            bot.getPlayerManager().loadItemOrdered(guild, url, new AudioLoadResultHandler() {
                @Override
                public void trackLoaded(AudioTrack track) {
                    // Always use the bot's self user for metadata
                    RequestMetadata metadata = new RequestMetadata(bot.getJDA().getSelfUser());
                    track.setUserData(metadata);
                    
                    // Get the handler
                    AudioHandler handler = bot.getPlayerManager().setUpHandler(guild);
                    
                    // Add to queue
                    int position = handler.addTrack(new QueuedTrack(track, metadata)) + 1;
                    
                    // Check if we need to establish a connection
                    if (!guild.getAudioManager().isConnected()) {
                        // Try to find a voice channel to join
                        guild.getVoiceChannels().stream()
                            .findFirst()
                            .ifPresent(vc -> guild.getAudioManager().openAudioConnection(vc));
                    }
                    
                    resultFuture.complete("Added to queue at position " + position + ": " + track.getInfo().title);
                }

                @Override
                public void playlistLoaded(AudioPlaylist playlist) {
                    // Handle playlist loading
                    if (playlist.getTracks().isEmpty()) {
                        resultFuture.complete("The playlist is empty");
                        return;
                    }
                    
                    // Get the handler
                    AudioHandler handler = bot.getPlayerManager().setUpHandler(guild);
                    
                    if (playlist.isSearchResult()) {
                        // Just load the first result
                        AudioTrack track = playlist.getTracks().get(0);
                        // Always use the bot's self user for metadata
                        RequestMetadata metadata = new RequestMetadata(bot.getJDA().getSelfUser());
                        track.setUserData(metadata);
                        
                        int position = handler.addTrack(new QueuedTrack(track, metadata)) + 1;
                        resultFuture.complete("Added to queue at position " + position + ": " + track.getInfo().title);
                    } else {
                        // Add all tracks from the playlist
                        playlist.getTracks().stream()
                            .limit(20) // Limit to 20 tracks for safety
                            .forEach(track -> {
                                // Always use the bot's self user for metadata
                                RequestMetadata metadata = new RequestMetadata(bot.getJDA().getSelfUser());
                                track.setUserData(metadata);
                                handler.addTrack(new QueuedTrack(track, metadata));
                            });
                        
                        resultFuture.complete("Added " + Math.min(playlist.getTracks().size(), 20) + 
                            " tracks from playlist: " + playlist.getName());
                    }
                    
                    // Check if we need to establish a connection
                    if (!guild.getAudioManager().isConnected()) {
                        // Try to find a voice channel to join
                        guild.getVoiceChannels().stream()
                            .findFirst()
                            .ifPresent(vc -> guild.getAudioManager().openAudioConnection(vc));
                    }
                }

                @Override
                public void noMatches() {
                    resultFuture.complete("No matches found for: " + url);
                }

                @Override
                public void loadFailed(FriendlyException exception) {
                    resultFuture.complete("Error loading track: " + exception.getMessage());
                }
            });
            
            // Wait for the result with a timeout
            try {
                return resultFuture.get(10, TimeUnit.SECONDS);
            } catch (Exception e) {
                return "Request timed out or was interrupted: " + e.getMessage();
            }
        } catch (Exception e) {
            return "Error executing play command: " + e.getMessage();
        }
    }
    
    /**
     * Handles skip command
     */
    private String handleSkipCommand(Guild guild) {
        try {
            AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
            if (handler == null) {
                return "No audio playing in this server.";
            }
            
            // Skip the current track
            String trackTitle = handler.getPlayer().getPlayingTrack() != null ? 
                handler.getPlayer().getPlayingTrack().getInfo().title : "Unknown track";
            
            handler.getPlayer().stopTrack();
            
            return "Skipped: " + trackTitle;
        } catch (Exception e) {
            return "Error executing skip command: " + e.getMessage();
        }
    }
    
    /**
     * Handles stop command
     */
    private String handleStopCommand(Guild guild) {
        try {
            AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
            if (handler == null) {
                return "No audio playing in this server.";
            }
            
            // Stop playback and clear the queue
            handler.stopAndClear();
            guild.getAudioManager().closeAudioConnection();
            
            return "Playback stopped and queue cleared.";
        } catch (Exception e) {
            return "Error executing stop command: " + e.getMessage();
        }
    }
} 