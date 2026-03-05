package dev.cosgy.jmusicbot.playlist;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.IntStream;

final class PlaylistLoaderSupport {
    private PlaylistLoaderSupport() {
    }

    interface PlaylistFactory<P> {
        P create(String name, List<String> items, boolean shuffle);
    }

    static <T> void shuffle(List<T> list) {
        IntStream.range(0, list.size()).forEach(first -> {
            int second = (int) (Math.random() * list.size());
            T tmp = list.get(first);
            list.set(first, list.get(second));
            list.set(second, tmp);
        });
    }

    static LoadedPlaylist readPlaylist(Path playlistPath) throws IOException {
        PlaylistSourceReader.Result source = PlaylistSourceReader.read(playlistPath);
        List<String> items = source.getItems();
        boolean shuffle = source.isShuffle();
        if (shuffle)
            shuffle(items);
        return new LoadedPlaylist(items, shuffle);
    }

    static <P> P loadPlaylist(String name, Path playlistPath, PlaylistFactory<P> factory) {
        try {
            LoadedPlaylist source = readPlaylist(playlistPath);
            return factory.create(name, source.getItems(), source.isShuffle());
        } catch (IOException e) {
            return null;
        }
    }

    static final class LoadedPlaylist {
        private final List<String> items;
        private final boolean shuffle;

        LoadedPlaylist(List<String> items, boolean shuffle) {
            this.items = items;
            this.shuffle = shuffle;
        }

        List<String> getItems() {
            return items;
        }

        boolean isShuffle() {
            return shuffle;
        }
    }
}
