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
package com.jagrosh.jmusicbot;

import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.jagrosh.jmusicbot.audio.AloneInVoiceHandler;
import com.jagrosh.jmusicbot.audio.AudioHandler;
import com.jagrosh.jmusicbot.audio.IcyMetadataHandler;
import com.jagrosh.jmusicbot.audio.NowplayingHandler;
import com.jagrosh.jmusicbot.audio.PlayerManager;
import com.jagrosh.jmusicbot.gui.GUI;
import com.jagrosh.jmusicbot.playlist.PlaylistLoader;
import com.jagrosh.jmusicbot.settings.SettingsManager;
import dev.cosgy.jmusicbot.playlist.CacheLoader;
import dev.cosgy.jmusicbot.playlist.MylistLoader;
import dev.cosgy.jmusicbot.playlist.PubliclistLoader;
import dev.cosgy.jmusicbot.util.LocalAudioMetadata;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

/**
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class Bot {
    public static Bot INSTANCE;
    private final EventWaiter waiter;
    private final ScheduledExecutorService threadpool;
    private final BotConfig config;
    private final SettingsManager settings;
    private final PlayerManager players;
    private final PlaylistLoader playlists;
    private final MylistLoader mylists;
    private final PubliclistLoader publist;
    private final CacheLoader cache;
    private final NowplayingHandler nowplaying;
    private final AloneInVoiceHandler aloneInVoiceHandler;
    private final IcyMetadataHandler icyMetadataHandler;
    
    // Map to store local audio file metadata
    private final Map<String, LocalAudioMetadata.LocalTrackInfo> localMetadataCache;
    
    // Directory for storing temporary artwork files
    private final Path artworkDir;
    
    // Map to store artwork URLs for local audio files
    private final Map<String, String> localArtworkUrls;

    private boolean shuttingDown = false;
    private JDA jda;
    private GUI gui;

    public Bot(EventWaiter waiter, BotConfig config, SettingsManager settings) {
        this.waiter = waiter;
        this.config = config;
        this.settings = settings;
        this.playlists = new PlaylistLoader(config);
        this.mylists = new MylistLoader(config);
        this.publist = new PubliclistLoader(config);
        this.cache = new CacheLoader(config);
        this.threadpool = Executors.newSingleThreadScheduledExecutor();
        this.players = new PlayerManager(this);
        this.players.init();
        this.nowplaying = new NowplayingHandler(this);
        this.nowplaying.init();
        this.aloneInVoiceHandler = new AloneInVoiceHandler(this);
        this.aloneInVoiceHandler.init();
        this.icyMetadataHandler = new IcyMetadataHandler(this);
        
        // Initialize local metadata cache
        this.localMetadataCache = new ConcurrentHashMap<>();
        
        // Create directory for temporary artwork files
        this.artworkDir = Paths.get(System.getProperty("java.io.tmpdir"), "jmusicbot_artwork");
        this.localArtworkUrls = new ConcurrentHashMap<>();
        
        try {
            if (!Files.exists(artworkDir)) {
                Files.createDirectories(artworkDir);
            }
        } catch (IOException e) {
            LoggerFactory.getLogger(Bot.class).error("Failed to create artwork directory", e);
        }
    }

    public static void updatePlayStatus(Guild guild, Member selfMember, PlayStatus status) {
        if (!INSTANCE.getConfig().getChangeNickName()) return;
        if (!selfMember.hasPermission(Permission.NICKNAME_CHANGE)) {
            LoggerFactory.getLogger("UpdName").error("Failed to change nickname: Insufficient permissions.");
            return;
        }

        String name = selfMember.getEffectiveName().replaceAll("[⏯⏸⏹] ", "");
        switch (status) {
            case PLAYING:
                name = "⏯ " + name;
                break;
            case PAUSED:
                name = "⏸ " + name;
                break;
            case STOPPED:
                name = "⏹ " + name;
                break;
            default:
        }

        guild.modifyNickname(selfMember, name).queue();
    }

    public BotConfig getConfig() {
        return config;
    }

    public SettingsManager getSettingsManager() {
        return settings;
    }

    public EventWaiter getWaiter() {
        return waiter;
    }

    public ScheduledExecutorService getThreadpool() {
        return threadpool;
    }

    public PlayerManager getPlayerManager() {
        return players;
    }

    public PlaylistLoader getPlaylistLoader() {
        return playlists;
    }

    public MylistLoader getMylistLoader() {
        return mylists;
    }

    public PubliclistLoader getPublistLoader() {
        return publist;
    }

    public CacheLoader getCacheLoader() {
        return cache;
    }

    public NowplayingHandler getNowplayingHandler() {
        return nowplaying;
    }

    public AloneInVoiceHandler getAloneInVoiceHandler() {
        return aloneInVoiceHandler;
    }

    public IcyMetadataHandler getIcyMetadataHandler() {
        return icyMetadataHandler;
    }

    public JDA getJDA() {
        return jda;
    }

    public void setJDA(JDA jda) {
        this.jda = jda;
    }

    public void closeAudioConnection(long guildId) {
        Guild guild = jda.getGuildById(guildId);
        if (guild != null)
            threadpool.submit(() -> guild.getAudioManager().closeAudioConnection());
    }

    public void resetGame() {
        Activity game = config.getGame() == null || config.getGame().getName().toLowerCase().matches("(none|なし)") ? null : config.getGame();
        if (!Objects.equals(jda.getPresence().getActivity(), game))
            jda.getPresence().setActivity(game);
    }

    /**
     * Gets the cached metadata for a local audio track
     * @return The map of track IDs to LocalTrackInfo objects
     */
    public Map<String, LocalAudioMetadata.LocalTrackInfo> getLocalMetadataCache() {
        return localMetadataCache;
    }
    
    /**
     * Processes a local audio file uploaded to Discord and extracts its metadata
     * @param trackId The track identifier
     * @param fileUrl The URL of the file to download and process
     * @return The LocalTrackInfo with extracted metadata, or null on failure
     */
    public LocalAudioMetadata.LocalTrackInfo processLocalAudioFile(String trackId, String fileUrl) {
        // Skip processing if we already have metadata for this track
        if (localMetadataCache.containsKey(trackId)) {
            return localMetadataCache.get(trackId);
        }
        
        // Download the file
        File downloadedFile = LocalAudioMetadata.downloadFile(fileUrl);
        if (downloadedFile == null) {
            LoggerFactory.getLogger(Bot.class).warn("Failed to download file: {}", fileUrl);
            return null;
        }
        
        try {
            // Extract metadata using just the file, track is reconstructed inside if needed
            LocalAudioMetadata.LocalTrackInfo info = LocalAudioMetadata.extractMetadata(null, downloadedFile);
            
            if (info != null) {
                // If the track has artwork, save it as a temporary file and create a data URL
                if (info.hasArtwork()) {
                    try {
                        byte[] artworkData = info.getArtworkData();
                        
                        // Create a safe filename using hash of trackId rather than full URL
                        String safeFilename = String.format("artwork_%d.%s", 
                                              Math.abs(trackId.hashCode()), 
                                              determineImageExtension(artworkData));
                        
                        Path artworkFile = artworkDir.resolve(safeFilename);
                        Files.write(artworkFile, artworkData);
                        
                        // Store the file URI as the artwork URL
                        String artworkUrl = artworkFile.toUri().toString();
                        localArtworkUrls.put(trackId, artworkUrl);
                    } catch (Exception e) {
                        LoggerFactory.getLogger(Bot.class).error("Failed to generate artwork URL", e);
                        // Don't fail the whole process if artwork processing fails
                    }
                }
                
                // Cache the metadata
                localMetadataCache.put(trackId, info);
                return info;
            } else {
                LoggerFactory.getLogger(Bot.class).warn("Failed to extract metadata from file: {}", fileUrl);
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(Bot.class).error("Error processing local audio file: {}", e.getMessage(), e);
        } finally {
            // Delete the temporary file
            if (downloadedFile.exists()) {
                downloadedFile.delete();
            }
        }
        
        return null;
    }
    
    /**
     * Determines the image format from raw image data
     * @param data The image data
     * @return The file extension (jpg, png, etc.)
     */
    private String determineImageExtension(byte[] data) {
        if (data == null || data.length < 4) {
            return "jpg"; // Default to JPG
        }
        
        // Check PNG signature
        if (data[0] == (byte) 0x89 && data[1] == (byte) 0x50 && data[2] == (byte) 0x4E && data[3] == (byte) 0x47) {
            return "png";
        }
        
        // Check JPEG signature
        if (data[0] == (byte) 0xFF && data[1] == (byte) 0xD8 && data[2] == (byte) 0xFF) {
            return "jpg";
        }
        
        // Check GIF signature
        if (data[0] == (byte) 0x47 && data[1] == (byte) 0x49 && data[2] == (byte) 0x46) {
            return "gif";
        }
        
        return "jpg"; // Default to JPG if unknown
    }
    
    /**
     * Gets the URL for a track's artwork
     * @param trackId The track identifier
     * @return The URL to use in embeds
     */
    public String getLocalArtworkUrl(String trackId) {
        // Get the file path from the map
        String fileUri = localArtworkUrls.get(trackId);
        
        if (fileUri == null || fileUri.isEmpty()) {
            return null;
        }
        
        // Check if it's a file URI that needs to be converted
        if (fileUri.startsWith("file:")) {
            try {
                // Extract the file path from the URI
                Path artworkFile = Paths.get(new URI(fileUri));
                
                // Check if the file exists
                if (Files.exists(artworkFile)) {
                    // Return the path for the file to be loaded and attached
                    return artworkFile.toString();
                } else {
                    LoggerFactory.getLogger(Bot.class).warn("Artwork file does not exist: {}", artworkFile);
                    return null;
                }
            } catch (Exception e) {
                LoggerFactory.getLogger(Bot.class).error("Failed to convert file URI to path", e);
                return null;
            }
        }
        
        // If it's already a proper URL (not a file:// URL), return it directly
        return fileUri;
    }
    
    /**
     * Cleans up temporary files created for local audio metadata
     */
    private void cleanupLocalAudioFiles() {
        try {
            if (Files.exists(artworkDir)) {
                Files.list(artworkDir).forEach(file -> {
                    try {
                        Files.delete(file);
                    } catch (IOException e) {
                        // Ignore deletion failures
                    }
                });
            }
        } catch (IOException e) {
            LoggerFactory.getLogger(Bot.class).warn("Failed to clean up artwork files", e);
        }
    }

    public void shutdown() {
        if (shuttingDown)
            return;
        shuttingDown = true;
        threadpool.shutdownNow();
        
        // Clean up local audio files
        cleanupLocalAudioFiles();
        
        // Shut down the ICY metadata handler
        icyMetadataHandler.shutdown();
        
        if (jda.getStatus() != JDA.Status.SHUTTING_DOWN) {
            jda.getGuilds().forEach(g ->
            {
                g.getAudioManager().closeAudioConnection();
                AudioHandler ah = (AudioHandler) g.getAudioManager().getSendingHandler();
                if (ah != null) {
                    ah.stopAndClear();
                    ah.getPlayer().destroy();
                    nowplaying.updateTopic(g.getIdLong(), ah, true);
                }
            });
            jda.shutdown();
        }
        if (gui != null)
            gui.dispose();
        System.exit(0);
    }

    public void setGUI(GUI gui) {
        this.gui = gui;
    }
}
