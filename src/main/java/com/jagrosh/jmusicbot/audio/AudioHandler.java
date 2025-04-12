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
import com.jagrosh.jmusicbot.JMusicBot;
import com.jagrosh.jmusicbot.PlayStatus;
import com.jagrosh.jmusicbot.playlist.PlaylistLoader.Playlist;
import com.jagrosh.jmusicbot.queue.FairQueue;
import com.jagrosh.jmusicbot.settings.Settings;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.source.youtube.YoutubeAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import dev.cosgy.jmusicbot.settings.RepeatMode;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.audio.AudioSendHandler;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.utils.messages.MessageCreateBuilder;
import net.dv8tion.jda.api.utils.messages.MessageCreateData;

import java.nio.ByteBuffer;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;

/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class AudioHandler extends AudioEventAdapter implements AudioSendHandler {
    private final FairQueue<QueuedTrack> queue = new FairQueue<>();
    private final List<AudioTrack> defaultQueue = new LinkedList<>();
    private final Set<String> votes = new HashSet<>();
    private final PlayerManager manager;
    private final AudioPlayer audioPlayer;
    private final long guildId;
    private final String stringGuildId;
    private AudioFrame lastFrame;
    private long streamStartTime;

    protected AudioHandler(PlayerManager manager, Guild guild, AudioPlayer player) {
        this.manager = manager;
        this.audioPlayer = player;
        this.guildId = guild.getIdLong();
        this.stringGuildId = guild.getId();
    }

    public int addTrackToFront(QueuedTrack qtrack) {
        if (audioPlayer.getPlayingTrack() == null) {
            audioPlayer.playTrack(qtrack.getTrack());
            return -1;
        } else {
            queue.addAt(0, qtrack);
            return 0;
        }
    }

    public int addTrack(QueuedTrack qtrack) {
        if (audioPlayer.getPlayingTrack() == null) {
            audioPlayer.playTrack(qtrack.getTrack());
            return -1;
        } else {
            boolean toEnt = manager.getBot().getSettingsManager().getSettings(guildId).isForceToEndQue();
            return queue.add(qtrack, toEnt);
        }
    }

    public void addTrackIfRepeat(AudioTrack track) {
        // If in repeat mode, add the track to the end of the queue
        RepeatMode mode = manager.getBot().getSettingsManager().getSettings(guildId).getRepeatMode();
        boolean toEnt = manager.getBot().getSettingsManager().getSettings(guildId).isForceToEndQue();
        if (mode != RepeatMode.OFF) {
            queue.add(new QueuedTrack(track.makeClone(), track.getUserData(RequestMetadata.class)), toEnt);
        }
    }

    public FairQueue<QueuedTrack> getQueue() {
        return queue;
    }

    public void stopAndClear() {
        queue.clear();
        defaultQueue.clear();
        audioPlayer.stopTrack();
        //current = null;

        Guild guild = guild(manager.getBot().getJDA());
        Bot.updatePlayStatus(guild, guild.getSelfMember(), PlayStatus.STOPPED);
    }

    public boolean isMusicPlaying(JDA jda) {
        return guild(jda).getSelfMember().getVoiceState().inAudioChannel() && audioPlayer.getPlayingTrack() != null;
    }

    public Set<String> getVotes() {
        return votes;
    }

    public AudioPlayer getPlayer() {
        return audioPlayer;
    }

    public RequestMetadata getRequestMetadata() {
        if (audioPlayer.getPlayingTrack() == null)
            return RequestMetadata.EMPTY;
        RequestMetadata rm = audioPlayer.getPlayingTrack().getUserData(RequestMetadata.class);
        return rm == null ? RequestMetadata.EMPTY : rm;
    }

    /**
     * Update stats when a track is skipped
     */
    public void updateStatsOnSkip() {
        AudioTrack track = audioPlayer.getPlayingTrack();
        if (track != null) {
            Settings settings = manager.getBot().getSettingsManager().getSettings(guildId);
            settings.incrementSongsPlayed();
            // If it's a stream, calculate the actual listening time until skip
            if (track.getInfo().isStream) {
                long streamDuration = System.currentTimeMillis() - streamStartTime;
                settings.addPlayTime(streamDuration);
            } else {
                // For a normal track, count the time played
                settings.addPlayTime(track.getPosition());
            }
        }
    }

    public boolean playFromDefault() {
        if (!defaultQueue.isEmpty()) {
            audioPlayer.playTrack(defaultQueue.remove(0));
            return true;
        }
        Settings settings = manager.getBot().getSettingsManager().getSettings(guildId);
        if (settings == null || settings.getDefaultPlaylist() == null)
            return false;

        Playlist pl = manager.getBot().getPlaylistLoader().getPlaylist(stringGuildId, settings.getDefaultPlaylist());
        if (pl == null || pl.getItems().isEmpty())
            return false;
        pl.loadTracks(manager, (at) -> {
            if (audioPlayer.getPlayingTrack() == null)
                audioPlayer.playTrack(at);
            else
                defaultQueue.add(at);
        }, () -> {
            if (pl.getTracks().isEmpty() && !manager.getBot().getConfig().getStay())
                manager.getBot().closeAudioConnection(guildId);
        });
        return true;
    }

    // Audio Events
    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
        RepeatMode repeatMode = manager.getBot().getSettingsManager().getSettings(guildId).getRepeatMode();

        // If it's a YouTube livestream that ended prematurely, replay it
        if (track != null && track.getInfo().isStream && track.getInfo().uri.contains("youtube.com") 
            && (endReason == AudioTrackEndReason.FINISHED || endReason == AudioTrackEndReason.LOAD_FAILED)) {
            // For YouTube livestreams, automatically replay the same stream
            
            // Update stats before replaying
            if (track.getInfo().isStream) {
                Settings settings = manager.getBot().getSettingsManager().getSettings(guildId);
                long streamDuration = System.currentTimeMillis() - streamStartTime;
                settings.addPlayTime(streamDuration);
            }
            
            player.playTrack(track.makeClone());
            return;
        }

        // If the song finishes playing normally and repeat mode is enabled (!OFF), it will be re-added to the queue.
        if (endReason == AudioTrackEndReason.FINISHED) {
            // Update stats when a track finishes playing
            if (track != null) {
                Settings settings = manager.getBot().getSettingsManager().getSettings(guildId);
                settings.incrementSongsPlayed();
                // If it's a stream, calculate the actual listening time
                if (track.getInfo().isStream) {
                    long streamDuration = System.currentTimeMillis() - streamStartTime;
                    settings.addPlayTime(streamDuration);
                } else {
                    settings.addPlayTime(track.getDuration());
                }
            }
            
            if (repeatMode != RepeatMode.OFF) {
                // in RepeatMode.ALL
                if (repeatMode == RepeatMode.ALL) {
                    queue.add(new QueuedTrack(track.makeClone(), track.getUserData(RequestMetadata.class)));

                    // in RepeatMode.SINGLE
                } else if (repeatMode == RepeatMode.SINGLE) {
                    queue.addAt(0, new QueuedTrack(track.makeClone(), track.getUserData(RequestMetadata.class)));
                }
            }
        }

        if (queue.isEmpty()) {
            if (!playFromDefault()) {
                manager.getBot().getNowplayingHandler().onTrackUpdate(guildId, null, this);
                if (!manager.getBot().getConfig().getStay()) manager.getBot().closeAudioConnection(guildId);

                player.setPaused(false);

                Guild guild = guild(manager.getBot().getJDA());
                Bot.updatePlayStatus(guild, guild.getSelfMember(), PlayStatus.STOPPED);
            }
        } else {
            QueuedTrack qt = queue.pull();
            player.playTrack(qt.getTrack());
        }
    }

    @Override
    public void onTrackStart(AudioPlayer player, AudioTrack track) {
        votes.clear();
        manager.getBot().getNowplayingHandler().onTrackUpdate(guildId, track, this);

        // If it's a stream, record when it starts
        if (track.getInfo().isStream) {
            streamStartTime = System.currentTimeMillis();
        }

        // Check if this is not a radio track, clear radio data if needed
        if (!isRadioTrack(track)) {
            // Clear radio data from RadioCmd maps when a non-radio track starts playing
            if (dev.cosgy.jmusicbot.slashcommands.music.RadioCmd.lastStationPaths.containsKey(stringGuildId)) {
                dev.cosgy.jmusicbot.slashcommands.music.RadioCmd.lastStationPaths.remove(stringGuildId);
                dev.cosgy.jmusicbot.slashcommands.music.RadioCmd.lastStationLogos.remove(stringGuildId);
            }
        }
        
        // Always clear Spotify data when a new track starts, unless it's explicitly a Spotify track
        if (!track.getSourceManager().getSourceName().equalsIgnoreCase("spotify")) {
            dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.lastTrackIds.remove(stringGuildId);
        }

        Guild guild = guild(manager.getBot().getJDA());
        Bot.updatePlayStatus(guild, guild.getSelfMember(), PlayStatus.PLAYING);
    }

    /**
     * Determines if a track is a radio station track
     */
    private boolean isRadioTrack(AudioTrack track) {
        if (track == null) return false;
        
        // For tracks coming from a stream source that could be a radio station
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
        
        // If we're not sure, assume it's not a radio track
        return false;
    }

    /**
     * Determines if a track was loaded through the Spotify command
     */
    private boolean isSpotifyTrack(AudioTrack track) {
        if (track == null) return false;
        
        // Check if this track is from Spotify based on stored data
        return dev.cosgy.jmusicbot.slashcommands.music.SpotifyCmd.lastTrackIds.containsKey(stringGuildId);
    }

    // Formatting
    public MessageCreateData getNowPlaying(JDA jda) throws Exception {
        if (isMusicPlaying(jda)) {
            Guild guild = guild(jda);
            AudioTrack track = audioPlayer.getPlayingTrack();
            MessageCreateBuilder mb = new MessageCreateBuilder();
            mb.addContent(FormatUtil.filter(manager.getBot().getConfig().getSuccess() + " **" + guild.getSelfMember().getVoiceState().getChannel().getAsMention() + "** is playing now..."));
            EmbedBuilder eb = new EmbedBuilder();
            eb.setColor(guild.getSelfMember().getColor());
            RequestMetadata rm = getRequestMetadata();
            if (!track.getInfo().uri.matches(".*stream.gensokyoradio.net/.*")) {
                if (rm.getOwner() != 0L) {
                    User u = guild.getJDA().getUserById(rm.user.id);
                    if (u == null)
                        eb.setAuthor(rm.user.username, null, rm.user.avatar);
                    else
                        eb.setAuthor(u.getName(), null, u.getEffectiveAvatarUrl());
                }
                try {
                    eb.setTitle(track.getInfo().title, track.getInfo().uri);
                } catch (Exception e) {
                    eb.setTitle(track.getInfo().title);
                }

                // Improved thumbnail handling for all track types
                if (manager.getBot().getConfig().useNPImages()) {
                    // First try to use artworkUrl if it exists (works for most platforms including SoundCloud)
                    if (track.getInfo().artworkUrl != null && !track.getInfo().artworkUrl.isEmpty()) {
                        eb.setThumbnail(track.getInfo().artworkUrl);
                    }
                    // Special handling for YouTube tracks
                    else if (track instanceof YoutubeAudioTrack || 
                            (track.getSourceManager() != null && track.getSourceManager().getSourceName().equalsIgnoreCase("youtube"))) {
                        // Extract the video ID
                        String videoId = track.getIdentifier();
                        
                        // If the identifier is a full URL, extract the video ID from it
                        if (videoId.contains("?v=")) {
                            videoId = videoId.substring(videoId.indexOf("?v=") + 3);
                            if (videoId.contains("&")) {
                                videoId = videoId.substring(0, videoId.indexOf("&"));
                            }
                        }
                        
                        // Use the highest quality thumbnail available
                        eb.setThumbnail("https://img.youtube.com/vi/" + videoId + "/maxresdefault.jpg");
                    }
                    // Special handling for Spotify tracks - use track URI as identifier
                    else if (track.getSourceManager() != null && track.getSourceManager().getSourceName().equalsIgnoreCase("spotify")) {
                        // For Spotify, we rely on Lavaplayer to provide artworkUrl, but log debugging info
                        System.out.println("Spotify track detected but no artwork URL found: " + track.getInfo().title);
                    }
                }

                // Determine source platform from track information
                String sourcePlatform = "Unknown Source";
                if (track instanceof YoutubeAudioTrack) {
                    sourcePlatform = "YouTube";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("soundcloud")) {
                    sourcePlatform = "SoundCloud";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("bandcamp")) {
                    sourcePlatform = "Bandcamp";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("twitch")) {
                    sourcePlatform = "Twitch";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("vimeo")) {
                    sourcePlatform = "Vimeo";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("http")) {
                    sourcePlatform = "HTTP Stream";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("local")) {
                    sourcePlatform = "Local File";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("beam")) {
                    sourcePlatform = "Beam";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("niconico")) {
                    sourcePlatform = "Niconico";
                } else {
                    sourcePlatform = track.getSourceManager().getSourceName();
                }

                // Set footer to show source platform
                eb.setFooter("Source: " + sourcePlatform, null);

                double progress = (double) audioPlayer.getPlayingTrack().getPosition() / track.getDuration();
                StringBuilder descBuilder = new StringBuilder();
                
                // Add artist information if available
                if (track.getInfo().author != null && !track.getInfo().author.isEmpty()) {
                    descBuilder.append("**Artist:** ").append(track.getInfo().author).append("\n\n");
                }
                
                // Add progress bar and other information
                descBuilder.append((audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI))
                        .append(" ")
                        .append(FormatUtil.progressBar(progress))
                        .append(" `[")
                        .append(FormatUtil.formatTime(track.getPosition()))
                        .append("/")
                        .append(FormatUtil.formatTime(track.getDuration()))
                        .append("]` ")
                        .append(FormatUtil.volumeIcon(audioPlayer.getVolume()));
                
                eb.setDescription(descBuilder.toString());

            } else {
                if (rm.getOwner() != 0L) {
                    User u = guild.getJDA().getUserById(rm.user.id);
                    if (u == null)
                        eb.setAuthor(rm.user.username, null, rm.user.avatar);
                    else
                        eb.setAuthor(u.getName(), null, u.getEffectiveAvatarUrl());
                }
                try {
                    eb.setTitle(track.getInfo().title, track.getInfo().uri);
                } catch (Exception e) {
                    eb.setTitle(track.getInfo().title);
                }

                // Improved thumbnail handling for all track types
                if (manager.getBot().getConfig().useNPImages()) {
                    // First try to use artworkUrl if it exists (works for most platforms including SoundCloud)
                    if (track.getInfo().artworkUrl != null && !track.getInfo().artworkUrl.isEmpty()) {
                        eb.setThumbnail(track.getInfo().artworkUrl);
                    }
                    // Special handling for YouTube tracks
                    else if (track instanceof YoutubeAudioTrack || 
                            (track.getSourceManager() != null && track.getSourceManager().getSourceName().equalsIgnoreCase("youtube"))) {
                        // Extract the video ID
                        String videoId = track.getIdentifier();
                        
                        // If the identifier is a full URL, extract the video ID from it
                        if (videoId.contains("?v=")) {
                            videoId = videoId.substring(videoId.indexOf("?v=") + 3);
                            if (videoId.contains("&")) {
                                videoId = videoId.substring(0, videoId.indexOf("&"));
                            }
                        }
                        
                        // Use the highest quality thumbnail available
                        eb.setThumbnail("https://img.youtube.com/vi/" + videoId + "/maxresdefault.jpg");
                    }
                    // Special handling for Spotify tracks - use track URI as identifier
                    else if (track.getSourceManager() != null && track.getSourceManager().getSourceName().equalsIgnoreCase("spotify")) {
                        // For Spotify, we rely on Lavaplayer to provide artworkUrl, but log debugging info
                        System.out.println("Spotify track detected but no artwork URL found: " + track.getInfo().title);
                    }
                }

                // Determine source platform from track information
                String sourcePlatform = "Unknown Source";
                if (track instanceof YoutubeAudioTrack) {
                    sourcePlatform = "YouTube";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("soundcloud")) {
                    sourcePlatform = "SoundCloud";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("bandcamp")) {
                    sourcePlatform = "Bandcamp";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("twitch")) {
                    sourcePlatform = "Twitch";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("vimeo")) {
                    sourcePlatform = "Vimeo";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("http")) {
                    sourcePlatform = "HTTP Stream";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("local")) {
                    sourcePlatform = "Local File";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("beam")) {
                    sourcePlatform = "Beam";
                } else if (track.getSourceManager().getSourceName().equalsIgnoreCase("niconico")) {
                    sourcePlatform = "Niconico";
                } else {
                    sourcePlatform = track.getSourceManager().getSourceName();
                }

                // Set footer to show source platform
                eb.setFooter("Source: " + sourcePlatform, null);

                double progress = (double) audioPlayer.getPlayingTrack().getPosition() / track.getDuration();
                StringBuilder descBuilder = new StringBuilder();
                
                // Add progress bar and other information
                descBuilder.append((audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI))
                        .append(" ")
                        .append(FormatUtil.progressBar(progress))
                        .append(" `[")
                        .append(FormatUtil.formatTime(track.getPosition()))
                        .append("/")
                        .append(FormatUtil.formatTime(track.getDuration()))
                        .append("]` ")
                        .append(FormatUtil.volumeIcon(audioPlayer.getVolume()));
                
                eb.setDescription(descBuilder.toString());
            }

            return mb.addEmbeds(eb.build()).build();
        } else return null;
    }

    public MessageCreateData getNoMusicPlaying(JDA jda) {
        Guild guild = guild(jda);
        return new MessageCreateBuilder()
                .setContent(FormatUtil.filter(manager.getBot().getConfig().getSuccess() + " **No music is playing.**"))
                .setEmbeds(new EmbedBuilder()
                        .setTitle("No music is playing.")
                        .setDescription(JMusicBot.STOP_EMOJI + " " + FormatUtil.progressBar(-1) + " " + FormatUtil.volumeIcon(audioPlayer.getVolume()))
                        .setColor(guild.getSelfMember().getColor())
                        .build())
                .build();
    }

    public String getTopicFormat(JDA jda) {
        if (isMusicPlaying(jda)) {
            long userid = getRequestMetadata().getOwner();
            AudioTrack track = audioPlayer.getPlayingTrack();

            // Check if Gensokyo Radio is playing.
            if (track.getInfo().uri.matches(".*stream.gensokyoradio.net/.*")) {
                return "**Gensokyo Radio** [" + (userid == 0 ? "📻" : "<@" + userid + ">") + "]"
                        + "\n" + (audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI) + " "
                        + "[LIVE] "
                        + FormatUtil.volumeIcon(audioPlayer.getVolume());
            }

            String title = track.getInfo().title;
            if (title == null || title.equals("Unknown title"))
                title = track.getInfo().uri;
            return "**" + title + "** [" + (userid == 0 ? "📻" : "<@" + userid + ">") + "]"
                    + "\n" + (audioPlayer.isPaused() ? JMusicBot.PAUSE_EMOJI : JMusicBot.PLAY_EMOJI) + " "
                    + "[" + FormatUtil.formatTime(track.getDuration()) + "] "
                    + FormatUtil.volumeIcon(audioPlayer.getVolume());
        } else return "No music is playing" + JMusicBot.STOP_EMOJI + " " + FormatUtil.volumeIcon(audioPlayer.getVolume());
    }

    // Audio Send Handler methods
    @Override
    public boolean canProvide() {
        lastFrame = audioPlayer.provide();
        return lastFrame != null;
    }

    @Override
    public ByteBuffer provide20MsAudio() {
        return ByteBuffer.wrap(lastFrame.getData());
    }

    @Override
    public boolean isOpus() {
        return true;
    }


    // Private methods
    private Guild guild(JDA jda) {
        return jda.getGuildById(guildId);
    }
}
