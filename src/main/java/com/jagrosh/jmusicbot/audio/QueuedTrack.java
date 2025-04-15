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

import com.jagrosh.jmusicbot.queue.Queueable;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import dev.cosgy.agent.GensokyoInfoAgent;
import dev.cosgy.agent.objects.ResultSet;
import net.dv8tion.jda.api.entities.User;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class QueuedTrack implements Queueable {
    private final AudioTrack track;

    public QueuedTrack(AudioTrack track, User owner) {
        this(track, new RequestMetadata(owner));
    }

    public QueuedTrack(AudioTrack track, RequestMetadata rm) {
        this.track = track;
        this.track.setUserData(rm);
    }

    @Override
    public long getIdentifier() {
        return track.getUserData(RequestMetadata.class).getOwner();
    }

    public AudioTrack getTrack() {
        return track;
    }

    @Override
    public String toString() {

        if (track.getInfo().uri.contains("https://stream.gensokyoradio.net/")) {

            ResultSet data = null;
            try {
                data = GensokyoInfoAgent.getInfo();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

            String title = data.getSonginfo().getTitle();
            String titleUrl = data.getMisc().getCirclelink().equals("") ?
                    "https://gensokyoradio.net/" :
                    data.getMisc().getCirclelink();
            return "`[" + FormatUtil.formatTime(data.getSongtimes().getDuration()) + "]` [**" + title + "**](" + titleUrl + ") - <@" + track.getUserData(RequestMetadata.class).getOwner() + ">";
        }

        String entry = "`[" + FormatUtil.formatTime(track.getDuration()) + "]` ";
        AudioTrackInfo trackInfo = track.getInfo();
        
        // Get track title or filename if title is missing or "Unknown title"
        String title = trackInfo.title;
        if (title == null || title.isEmpty() || title.equals("Unknown title")) {
            // Extract filename from URL for local files
            title = dev.cosgy.jmusicbot.util.LocalAudioMetadata.extractFilenameFromUrl(trackInfo.uri);
            title = dev.cosgy.jmusicbot.util.LocalAudioMetadata.cleanupFilename(title);
        }
        
        entry = entry + (trackInfo.uri.startsWith("http") ? "[**" + title + "**](" + trackInfo.uri + ")" : "**" + title + "**");
        return entry + " - <@" + track.getUserData(RequestMetadata.class).getOwner() + ">";
    }
}
