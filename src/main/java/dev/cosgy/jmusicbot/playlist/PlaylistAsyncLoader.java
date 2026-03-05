package dev.cosgy.jmusicbot.playlist;

import com.jagrosh.jmusicbot.BotConfig;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.List;
import java.util.function.Consumer;

final class PlaylistAsyncLoader {
    private PlaylistAsyncLoader() {
    }

    interface ErrorFactory<E> {
        E tooLong(int index, String item);

        E noMatches(int index, String item);

        E loadFailed(int index, String item, FriendlyException exception);
    }

    static <E> void loadTracks(AudioPlayerManager manager,
                               String orderKey,
                               List<String> items,
                               boolean shuffle,
                               BotConfig config,
                               List<AudioTrack> tracks,
                               List<E> errors,
                               Consumer<AudioTrack> consumer,
                               Runnable callback,
                               Runnable shuffleTracks,
                               ErrorFactory<E> errorFactory) {
        for (int i = 0; i < items.size(); i++) {
            boolean last = i + 1 == items.size();
            int index = i;
            manager.loadItemOrdered(orderKey, items.get(i), new AudioLoadResultHandler() {
                private void done() {
                    if (!last) {
                        return;
                    }
                    if (shuffle) {
                        shuffleTracks.run();
                    }
                    if (callback != null) {
                        callback.run();
                    }
                }

                @Override
                public void trackLoaded(AudioTrack at) {
                    if (!PlaylistLoadSupport.addTrackIfAllowed(at, config, tracks, consumer)) {
                        errors.add(errorFactory.tooLong(index, items.get(index)));
                    }
                    done();
                }

                @Override
                public void playlistLoaded(AudioPlaylist ap) {
                    if (ap.isSearchResult()) {
                        trackLoaded(ap.getTracks().get(0));
                    } else if (ap.getSelectedTrack() != null) {
                        trackLoaded(ap.getSelectedTrack());
                    } else {
                        PlaylistLoadSupport.appendPlaylistTracks(ap, shuffle, config, tracks, consumer);
                    }
                    done();
                }

                @Override
                public void noMatches() {
                    errors.add(errorFactory.noMatches(index, items.get(index)));
                    done();
                }

                @Override
                public void loadFailed(FriendlyException fe) {
                    errors.add(errorFactory.loadFailed(index, items.get(index), fe));
                    done();
                }
            });
        }
    }
}
