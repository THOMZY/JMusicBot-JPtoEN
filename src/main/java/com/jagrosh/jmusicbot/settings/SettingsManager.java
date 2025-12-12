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
package com.jagrosh.jmusicbot.settings;

import com.jagrosh.jdautilities.command.GuildSettingsManager;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import dev.cosgy.jmusicbot.settings.RepeatMode;
import net.dv8tion.jda.api.entities.Guild;
import org.json.JSONException;
import org.json.JSONObject;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.util.HashMap;

/**
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class SettingsManager implements GuildSettingsManager {
    private final static double SKIP_RATIO = .55;
    private final HashMap<Long, Settings> settings;

    public SettingsManager() {
        this.settings = new HashMap<>();
        try {
            JSONObject loadedSettings = new JSONObject(new String(Files.readAllBytes(OtherUtil.getPath("serversettings.json"))));
            loadedSettings.keySet().forEach((id) -> {
                JSONObject o = loadedSettings.getJSONObject(id);

                // to support previous (boolean type) versions.
                try {
                    if (o.getBoolean("repeat")) {
                        o.put("repeat", RepeatMode.ALL);
                    } else {
                        o.put("repeat", RepeatMode.OFF);
                    }
                    // I had entered an incorrect value due to a bug,
                    // so I wanted to change that value to the correct value.
                    if (o.getInt("announce") == 50) {
                        o.put("announce", 0);
                    }
                } catch (JSONException ignored) { /* ignored */ }

                settings.put(Long.parseLong(id), new Settings(this,
                        o.has("text_channel_id") ? o.getString("text_channel_id") : null,
                        o.has("voice_channel_id") ? o.getString("voice_channel_id") : null,
                        o.has("dj_role_id") ? o.getString("dj_role_id") : null,
                        o.has("volume") ? o.getInt("volume") : 10,
                        o.has("default_playlist") ? o.getString("default_playlist") : null,
                        o.has("repeat") ? o.getEnum(RepeatMode.class, "repeat") : RepeatMode.OFF,
                        o.has("prefix") ? o.getString("prefix") : null,
                        o.has("bitrate_warnings_readied") && o.getBoolean("bitrate_warnings_readied"),
                        o.has("announce") ? o.getInt("announce") : 0,
                        o.has("skip_ratio") ? o.getDouble("skip_ratio") : SKIP_RATIO,
                        o.has("vc_status") ? o.getBoolean("vc_status") : false,
                        o.has("topic_status") ? o.getBoolean("topic_status") : false,
                        o.has("force_to_end_que") && o.getBoolean("force_to_end_que"),
                        o.has("songs_played") ? o.getInt("songs_played") : 0,
                        o.has("playtime_millis") ? o.getLong("playtime_millis") : 0));
            });
        } catch (NoSuchFileException e) {
            // ignore, it just means no settings have been saved yet
            // create an empty json file
            try {
                LoggerFactory.getLogger("Settings").info("Created serversettings.json at " + OtherUtil.getPath("serversettings.json").toAbsolutePath() + ".");
                Files.write(OtherUtil.getPath("serversettings.json"), new JSONObject().toString(4).getBytes());
            } catch(IOException ex) {
                LoggerFactory.getLogger("Settings").warn("Failed to create server configuration file: " + ex);
            }
            return;
        } catch(IOException | JSONException e) {
            LoggerFactory.getLogger("Settings").warn("Failed to load server configuration file: " + e);
        }
    }

    /**
     * Get the non-null setting of the guild
     *
     * @param guild the guild to get the settings for
     * @return the existing configuration or new configuration for that guild
     */
    @Override
    public Settings getSettings(Guild guild) {
        return getSettings(guild.getIdLong());
    }

    public Settings getSettings(long guildId) {
        return settings.computeIfAbsent(guildId, id -> createDefaultSettings());
    }

    private Settings createDefaultSettings() {
        return new Settings(this, 0, 0, 0, 10, null, RepeatMode.OFF, null, false, 0, SKIP_RATIO, false, false, false, 0, 0);
    }

    protected void writeSettings() {
        var obj = new JSONObject();
        for (Long key : settings.keySet()) {
            var o = new JSONObject();
            Settings s = settings.get(key);
            if (s.textId != 0)
                o.put("text_channel_id", Long.toString(s.textId));
            if (s.voiceId != 0)
                o.put("voice_channel_id", Long.toString(s.voiceId));
            if (s.roleId != 0)
                o.put("dj_role_id", Long.toString(s.roleId));
            if (s.getVolume() != 50)
                o.put("volume", s.getVolume());
            if (s.getDefaultPlaylist() != null)
                o.put("default_playlist", s.getDefaultPlaylist());
            if (s.getRepeatMode() != RepeatMode.OFF)
                o.put("repeat", s.getRepeatMode());
            if (s.getPrefix() != null)
                o.put("prefix", s.getPrefix());
            if (s.getAnnounce() != 0)
                o.put("announce", s.getAnnounce());
            if (s.getSkipRatio() != SKIP_RATIO)
                o.put("skip_ratio", s.getSkipRatio());
            o.put("vc_status", s.getVCStatus());
            o.put("topic_status", s.getTopicStatus());
            if(s.isForceToEndQue())
                o.put("force_to_end_que", s.isForceToEndQue());
            // Save stats
            if(s.getSongsPlayed() > 0)
                o.put("songs_played", s.getSongsPlayed());
            if(s.getPlayTimeMillis() > 0)
                o.put("playtime_millis", s.getPlayTimeMillis());

            obj.put(Long.toString(key), o);
        }
        try {
            Files.write(OtherUtil.getPath("serversettings.json"), obj.toString(4).getBytes());
        } catch (IOException ex) {
            LoggerFactory.getLogger("Settings").warn("Failed to write to file: " + ex);
        }
    }
}
