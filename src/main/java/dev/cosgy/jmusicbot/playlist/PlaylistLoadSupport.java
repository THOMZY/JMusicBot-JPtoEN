package dev.cosgy.jmusicbot.playlist;

import com.jagrosh.jmusicbot.BotConfig;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class PlaylistLoadSupport {
    private PlaylistLoadSupport() {
    }

    public static boolean addTrackIfAllowed(AudioTrack track, BotConfig config, List<AudioTrack> tracks, Consumer<AudioTrack> consumer) {
        if (config.isTooLong(track)) {
            return false;
        }
        track.setUserData(0L);
        tracks.add(track);
        consumer.accept(track);
        return true;
    }

    public static void appendPlaylistTracks(AudioPlaylist playlist, boolean shuffle, BotConfig config,
                                     List<AudioTrack> tracks, Consumer<AudioTrack> consumer) {
        List<AudioTrack> loaded = new ArrayList<>(playlist.getTracks());
        if (shuffle) {
            shuffle(loaded);
        }
        loaded.removeIf(config::isTooLong);
        loaded.forEach(track -> track.setUserData(0L));
        tracks.addAll(loaded);
        loaded.forEach(consumer);
    }

    public static <T> void shuffle(List<T> list) {
        for (int first = 0; first < list.size(); first++) {
            int second = (int) (Math.random() * list.size());
            T tmp = list.get(first);
            list.set(first, list.get(second));
            list.set(second, tmp);
        }
    }
}
