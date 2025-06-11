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
import com.jagrosh.jmusicbot.audio.MusicHistory;
import com.jagrosh.jmusicbot.audio.NowplayingHandler;
import com.jagrosh.jmusicbot.audio.PlayerManager;
import com.jagrosh.jmusicbot.audio.YouTubeChapterManager;
import com.jagrosh.jmusicbot.gui.GUI;
import com.jagrosh.jmusicbot.playlist.PlaylistLoader;
import com.jagrosh.jmusicbot.settings.SettingsManager;
import dev.cosgy.agent.GensokyoInfoAgent;
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

import java.io.File;
import java.io.IOException;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import javax.swing.SwingUtilities;

import static org.slf4j.LoggerFactory.getLogger;

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
    private final YouTubeChapterManager youtubeChapterManager;
    
    // Map to store local audio file metadata (LocalTrackInfo now contains artwork path)
    private final Map<String, LocalAudioMetadata.LocalTrackInfo> localMetadataCache;
    
    private boolean shuttingDown = false;
    private JDA jda;
    private GUI gui;
    private final MusicHistory musicHistory;

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
        this.youtubeChapterManager = new YouTubeChapterManager();
        
        // Initialize local metadata cache
        this.localMetadataCache = new ConcurrentHashMap<>();
        
        this.musicHistory = new MusicHistory(this);
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

    public YouTubeChapterManager getYoutubeChapterManager() {
        return youtubeChapterManager;
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
     * Processes a local audio file uploaded to Discord and extracts its metadata.
     * Artwork is now handled by LocalAudioMetadata.
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
            // Extract metadata using LocalAudioMetadata.extractMetadata
            // This method now handles artwork saving internally.
            // Pass the original trackId (Discord URL) as the identifier for caching.
            LocalAudioMetadata.LocalTrackInfo info = LocalAudioMetadata.extractMetadata(trackId, downloadedFile);
            
            if (info != null) {
                // Artwork saving and URL generation is now done within LocalAudioMetadata.extractMetadata
                // No need for specific artwork handling code here anymore.
                
                // Cache the metadata
                localMetadataCache.put(trackId, info);
                return info;
            } else {
                LoggerFactory.getLogger(Bot.class).warn("Failed to extract metadata from file: {}", fileUrl);
            }
        } catch (Exception e) {
            LoggerFactory.getLogger(Bot.class).error("Error processing local audio file: {}", e.getMessage(), e);
        } finally {
            // Delete the temporary downloaded file
            if (downloadedFile.exists()) {
                if (!downloadedFile.delete()) {
                     LoggerFactory.getLogger(Bot.class).warn("Failed to delete temporary file: {}", downloadedFile.getAbsolutePath());
                }
            }
        }
        
        return null;
    }
    
    /**
     * Gets the relative path for a track's artwork, suitable for web panel use.
     * This path is now directly retrieved from LocalTrackInfo.
     * @param trackId The track identifier
     * @return The relative path to the artwork (e.g., "local_artwork/hash.png") or null if no artwork.
     */
    public String getLocalArtworkPath(String trackId) {
        LocalAudioMetadata.LocalTrackInfo trackInfo = localMetadataCache.get(trackId);
        if (trackInfo != null && trackInfo.hasArtwork()) {
            return trackInfo.getArtworkPath();
        }
        return null;
    }
    
    /**
     * Cleans up temporary files created for local audio metadata (downloaded files).
     * Artwork files are now permanent and managed by LocalAudioMetadata, so no cleanup here.
     */
    private void cleanupLocalAudioFiles() {
        // This method is now primarily for cleaning up temporary downloaded files if any were missed.
        // The artworkDir and its contents are no longer managed/cleaned by this Bot class instance.
        // LocalAudioMetadata handles the `local_artwork` directory.
        // If there were other temporary files this Bot class created, they could be cleaned here.
        // For now, the main cleanup is the downloadedFile in processLocalAudioFile's finally block.
        LoggerFactory.getLogger(Bot.class).debug("cleanupLocalAudioFiles called. Note: Artwork cleanup is handled by LocalAudioMetadata.");
    }

    public void shutdown() {
        if (shuttingDown)
            return;
        shuttingDown = true;
        
        // Shutdown executor services first
        threadpool.shutdownNow();
        icyMetadataHandler.shutdown();
        youtubeChapterManager.shutdown();
        
        // Stop GensokyoInfoAgent if it's running
        dev.cosgy.agent.GensokyoInfoAgent.stopAgent();
        
        // Cancel all Gensokyo update tasks if any
        if (this.nowplaying != null) {
            this.nowplaying.cancelAllGensokyoUpdateTasks();
        }
        
        // Then close audio connections and cleanup audio resources
        if (jda != null && jda.getStatus() != JDA.Status.SHUTTING_DOWN) {
            jda.getGuilds().forEach((g) -> {
                g.getAudioManager().closeAudioConnection();
                AudioHandler ah = (AudioHandler) g.getAudioManager().getSendingHandler();
                if (ah != null) {
                    ah.stopAndClear();
                    ah.getPlayer().destroy();
                    nowplaying.updateTopic(g.getIdLong(), ah, true);
                }
            });
            
            // Finally shutdown JDA
            jda.shutdown();
        }
        
        // Clean up local audio files (temporary downloads, artwork is permanent)
        cleanupLocalAudioFiles(); // This call remains, but its scope is reduced.
        
        // Close the GUI last after everything else is done
        if (gui != null) {
            SwingUtilities.invokeLater(() -> {
                gui.dispose();
            });
        }
        
        // Schedule System.exit with a slight delay to ensure resources are released
        new Thread(() -> {
            try {
                Thread.sleep(500);
                System.exit(0);
            } catch (InterruptedException e) {
                System.exit(0);
            }
        }).start();
    }

    public void setGUI(GUI gui) {
        this.gui = gui;
    }

    /**
     * Gets the music history manager
     * @return The music history manager
     */
    public MusicHistory getMusicHistory() {
        return musicHistory;
    }
}
