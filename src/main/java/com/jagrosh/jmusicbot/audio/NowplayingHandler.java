/*
 * Copyright 2018-2020 Cosgy Dev
 * Edit 2025 THOMZY
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
import com.jagrosh.jmusicbot.entities.Pair;
import com.jagrosh.jmusicbot.settings.Settings;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.jagrosh.jmusicbot.audio.IcyMetadataHandler;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.GuildVoiceState;
import net.dv8tion.jda.api.entities.Message;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.entities.channel.concrete.VoiceChannel;
import net.dv8tion.jda.api.entities.channel.unions.AudioChannelUnion;
import net.dv8tion.jda.api.exceptions.PermissionException;
import net.dv8tion.jda.api.exceptions.RateLimitedException;
import net.dv8tion.jda.api.utils.messages.MessageEditData;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class NowplayingHandler {
    private final Bot bot;
    private final HashMap<Long, Pair<Long, Long>> lastNP; // guild -> channel,message

    public NowplayingHandler(Bot bot) {
        this.bot = bot;
        this.lastNP = new HashMap<>();
    }

    public void init() {
        if (!bot.getConfig().useNPImages())
            bot.getThreadpool().scheduleWithFixedDelay(this::updateAll, 0, 10, TimeUnit.SECONDS);
    }

    public void setLastNPMessage(Message m) {
        lastNP.put(m.getGuild().getIdLong(), new Pair<>(m.getChannel().getIdLong(), m.getIdLong()));
    }

    public void clearLastNPMessage(Guild guild) {
        lastNP.remove(guild.getIdLong());
    }

    private void updateAll() {
        Set<Long> toRemove = new HashSet<>();
        for (long guildId : lastNP.keySet()) {
            Guild guild = bot.getJDA().getGuildById(guildId);
            if (guild == null) {
                toRemove.add(guildId);
                continue;
            }
            Pair<Long, Long> pair = lastNP.get(guildId);
            TextChannel tc = guild.getTextChannelById(pair.getKey());
            if (tc == null) {
                toRemove.add(guildId);
                continue;
            }
            AudioHandler handler = (AudioHandler) guild.getAudioManager().getSendingHandler();
            MessageEditData msg = null;
            try {
                msg = MessageEditData.fromCreateData(Objects.requireNonNull(handler).getNowPlaying(bot.getJDA()));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            if (msg == null) {
                msg = MessageEditData.fromCreateData(handler.getNoMusicPlaying(bot.getJDA()));
                toRemove.add(guildId);
            }
            try {
                tc.editMessageById(pair.getValue(), msg).queue(m -> {
                }, t -> lastNP.remove(guildId));
            } catch (Exception e) {
                toRemove.add(guildId);
            }
        }
        toRemove.forEach(lastNP::remove);
    }

    public void updateTopic(long guildId, AudioHandler handler, boolean wait) {
        Guild guild = bot.getJDA().getGuildById(guildId);
        if (guild == null)
            return;
        Settings settings = bot.getSettingsManager().getSettings(guildId);
        TextChannel tchan = settings.getTextChannel(guild);
        if (tchan != null && guild.getSelfMember().hasPermission(tchan, Permission.MANAGE_CHANNEL)) {
            String otherText;
            String topic = tchan.getTopic();
            if (topic == null || topic.isEmpty())
                otherText = "\u200B";
            else if (topic.contains("\u200B"))
                otherText = topic.substring(topic.lastIndexOf("\u200B"));
            else
                otherText = "\u200B\n " + topic;
            String text = handler.getTopicFormat(bot.getJDA()) + otherText;
            if (!text.equals(tchan.getTopic())) {
                try {
                    tchan.getManager().setTopic(text).complete(wait);
                } catch (PermissionException | RateLimitedException ignore) {
                }
            }
        }

        // Voice channel status updates
        GuildVoiceState vChan = guild.getSelfMember().getVoiceState();

        if(vChan == null || !vChan.inAudioChannel()){
            return;
        }

        AudioChannelUnion chan = vChan.getChannel();
        if (!(chan instanceof VoiceChannel)) {
            return;
        }

        VoiceChannel voiceChannel = (VoiceChannel) chan;

        if(settings.getVCStatus() && guild.getSelfMember().hasPermission(voiceChannel, Permission.VOICE_SET_STATUS)){
            String text = handler.getTopicFormat(bot.getJDA());
            if (!text.equals(voiceChannel.getStatus())) {
                try {
                    voiceChannel.modifyStatus(text).complete(wait);
                } catch (PermissionException | RateLimitedException ignore) {
                }
            }
        }


    }

    // "event"-based methods
    public void onTrackUpdate(long guildId, AudioTrack track, AudioHandler handler) {
        // Update bot status if applicable
        if (bot.getConfig().getSongInStatus()) {
            if (track != null && bot.getJDA().getGuilds().stream().filter(g -> Objects.requireNonNull(g.getSelfMember().getVoiceState()).inAudioChannel()).count() <= 1) {
                // Check if this is a Gensokyo Radio track
                if (handler instanceof AudioHandler && ((AudioHandler) handler).isGensokyoRadioTrack(track)) {
                    try {
                        // Get current track info from GensokyoInfoAgent
                        dev.cosgy.agent.objects.ResultSet info = dev.cosgy.agent.GensokyoInfoAgent.getInfo();
                        
                        if (info != null && info.getSonginfo() != null) {
                            // Format as Artist - Title for Gensokyo Radio
                            String artistTitle = info.getSonginfo().getArtist() + " - " + info.getSonginfo().getTitle();
                            bot.getJDA().getPresence().setActivity(Activity.listening(artistTitle));
                            
                            // Update track title to include current song info
                            // This affects what's displayed in other places
                            try {
                                java.lang.reflect.Field titleField = track.getInfo().getClass().getDeclaredField("title");
                                titleField.setAccessible(true);
                                titleField.set(track.getInfo(), artistTitle);
                                track.setUserData(artistTitle);
                            } catch (Exception e) {
                                // Ignore field access errors
                            }
                            return; // Skip the rest of the method
                        }
                    } catch (Exception e) {
                        // If there was an error, fall back to default behavior
                    }
                }
                
                // Check if title is empty or null, provide a default if needed
                String title = track.getInfo().title;
                if (title == null || title.trim().isEmpty() || title.equals("Unknown title")) {
                    // Try to get a better title from different sources
                    if (handler instanceof AudioHandler) {
                        AudioHandler audioHandler = (AudioHandler) handler;
                        
                        // Check if it's a radio stream with ICY metadata
                        IcyMetadataHandler.StreamMetadata icyMetadata = 
                            bot.getIcyMetadataHandler().getMetadata(String.valueOf(guildId));
                        
                        if (icyMetadata != null) {
                            // First try current track if available
                            if (icyMetadata.getCurrentTrack() != null && !icyMetadata.getCurrentTrack().trim().isEmpty()) {
                                title = icyMetadata.getCurrentTrack();
                            } 
                            // Otherwise use station name
                            else if (icyMetadata.getStationName() != null && !icyMetadata.getStationName().trim().isEmpty()) {
                                title = icyMetadata.getStationName();
                            }
                        }
                    }
                    
                    // If still empty, try to extract filename from URL for local files
                    if (title == null || title.trim().isEmpty() || title.equals("Unknown title")) {
                        String uri = track.getInfo().uri;
                        title = dev.cosgy.jmusicbot.util.LocalAudioMetadata.extractFilenameFromUrl(uri);
                        title = dev.cosgy.jmusicbot.util.LocalAudioMetadata.cleanupFilename(title);
                    }
                    
                    // If still empty after all attempts, use a generic title
                    if (title == null || title.trim().isEmpty()) {
                        title = track.getInfo().isStream ? "Live Stream" : "Music";
                    }
                }
                
                // Now set the activity with our guaranteed non-empty title
                bot.getJDA().getPresence().setActivity(Activity.listening(title));
            } else {
                bot.resetGame();
            }
        }

        // Update channel topic if applicable
        updateTopic(guildId, handler, false);
    }

    public void onMessageDelete(Guild guild, long messageId) {
        Pair<Long, Long> pair = lastNP.get(guild.getIdLong());
        if (pair == null)
            return;
        if (pair.getValue() == messageId)
            lastNP.remove(guild.getIdLong());
    }
}
