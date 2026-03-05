package dev.cosgy.jmusicbot.playlist;

import com.jagrosh.jmusicbot.BotConfig;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.LinkedList;
import java.util.List;
import java.util.function.Consumer;

abstract class BasePlaylist<E> {
    private final String name;
    private final List<String> items;
    private final boolean shuffle;
    private final List<AudioTrack> tracks = new LinkedList<>();
    private final List<E> errors = new LinkedList<>();
    private boolean loaded = false;

    protected BasePlaylist(String name, List<String> items, boolean shuffle) {
        this.name = name;
        this.items = items;
        this.shuffle = shuffle;
    }

    protected abstract PlaylistAsyncLoader.ErrorFactory<E> createErrorFactory();

    public void loadTracks(AudioPlayerManager manager, BotConfig config, Consumer<AudioTrack> consumer, Runnable callback) {
        if (loaded)
            return;
        loaded = true;
        PlaylistAsyncLoader.loadTracks(manager, name, items, shuffle, config, tracks, errors, consumer, callback,
                this::shuffleTracks, createErrorFactory());
    }

    protected void shuffleTracks() {
        PlaylistLoaderSupport.shuffle(tracks);
    }

    public String getName() {
        return name;
    }

    public List<String> getItems() {
        return items;
    }

    public List<AudioTrack> getTracks() {
        return tracks;
    }

    public List<E> getErrors() {
        return errors;
    }
}
