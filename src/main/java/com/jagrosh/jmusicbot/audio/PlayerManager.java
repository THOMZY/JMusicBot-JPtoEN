/*
 * Copyright 2018-2020 Cosgy Dev
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.jagrosh.jmusicbot.audio;

import com.jagrosh.jmusicbot.Bot;
import com.sedmelluq.discord.lavaplayer.player.AudioConfiguration;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManagers;
import com.sedmelluq.discord.lavaplayer.source.nico.NicoAudioSourceManager;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import dev.lavalink.youtube.YoutubeSourceOptions;
import dev.lavalink.youtube.clients.*;
import dev.lavalink.youtube.clients.skeleton.Client;
import net.dv8tion.jda.api.entities.Guild;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class PlayerManager extends DefaultAudioPlayerManager {
    private final Bot bot;
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    public PlayerManager(Bot bot) {
        this.bot = bot;
    }

    public void init() {
        if (bot.getConfig().isNicoNicoEnabled()) {
            registerSourceManager(
                    new NicoAudioSourceManager(
                            bot.getConfig().getNicoNicoEmailAddress(),
                            bot.getConfig().getNicoNicoPassword())
            );
        }

        // Prepare YouTube source options (e.g., remote cipher server)
        YoutubeSourceOptions ytOptions = new YoutubeSourceOptions();
        String cipherUrl = bot.getConfig().getYouTubeCipherUrl();
        String cipherPassword = bot.getConfig().getYouTubeCipherPassword();
        String cipherUserAgent = bot.getConfig().getYouTubeCipherUserAgent();
        if (cipherUrl != null && !cipherUrl.isEmpty()) {
            // Configure remote cipher server compatible with yt-cipher API
            String ua = (cipherUserAgent != null && !cipherUserAgent.isBlank()) ? cipherUserAgent : null;
            ytOptions.setRemoteCipher(cipherUrl, cipherPassword, ua);
            if (ua == null) {
                logger.info("Enabled remote cipher server for YouTube at {}", cipherUrl);
            } else {
                logger.info("Enabled remote cipher server for YouTube at {} using UA {}", cipherUrl, ua);
            }
        }

        registerSourceManager(new YoutubeAudioSourceManager(ytOptions, new Client[] { new Music(),
                new TvHtml5Embedded(),
                new AndroidMusic(),
                new Web(),
                new WebEmbedded(),
                new Android(),
                new Ios()
        }));

        TransformativeAudioSourceManager.createTransforms(bot.getConfig().getTransforms()).forEach(this::registerSourceManager);
        AudioSourceManagers.registerRemoteSources(this);
        AudioSourceManagers.registerLocalSource(this);

        source(YoutubeAudioSourceManager.class).setPlaylistPageCount(10);

        // YouTube OAuth2 integration with persistent refresh token
        String ytRefreshToken = bot.getConfig().getYouTubeRefreshToken();
        if (ytRefreshToken != null && !ytRefreshToken.isEmpty()) {
            source(YoutubeAudioSourceManager.class).useOauth2(ytRefreshToken, true);
        } else {
            source(YoutubeAudioSourceManager.class).useOauth2(null, false);
        }

        if (getConfiguration().getOpusEncodingQuality() != 10) {
            logger.debug("OpusEncodingQuality is {}, setting quality to 10.", getConfiguration().getOpusEncodingQuality());
            getConfiguration().setOpusEncodingQuality(10);
        }

        if (getConfiguration().getResamplingQuality() != AudioConfiguration.ResamplingQuality.HIGH) {
            logger.debug("ResamplingQuality is {} (not HIGH), setting quality to HIGH.", getConfiguration().getResamplingQuality().name());
            getConfiguration().setResamplingQuality(AudioConfiguration.ResamplingQuality.HIGH);
        }
    }

    public Bot getBot() {
        return bot;
    }

    public boolean hasHandler(Guild guild) {
        return guild.getAudioManager().getSendingHandler() != null;
    }

    public AudioHandler setUpHandler(Guild guild) {
        AudioHandler handler;
        if (guild.getAudioManager().getSendingHandler() == null) {
            AudioPlayer player = createPlayer();
            player.setVolume(bot.getSettingsManager().getSettings(guild).getVolume());
            handler = new AudioHandler(this, guild, player);
            player.addListener(handler);
            guild.getAudioManager().setSendingHandler(handler);
        } else
            handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
        return handler;
    }
}
