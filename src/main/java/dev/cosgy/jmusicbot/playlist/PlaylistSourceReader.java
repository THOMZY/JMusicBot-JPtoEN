package dev.cosgy.jmusicbot.playlist;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

final class PlaylistSourceReader {
    private PlaylistSourceReader() {
    }

    static Result read(Path playlistPath) throws IOException {
        boolean[] shuffle = {false};
        List<String> items = new ArrayList<>();
        for (String line : Files.readAllLines(playlistPath)) {
            MylistLoader.Trim(shuffle, items, line);
        }
        return new Result(items, shuffle[0]);
    }

    static final class Result {
        private final List<String> items;
        private final boolean shuffle;

        private Result(List<String> items, boolean shuffle) {
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
