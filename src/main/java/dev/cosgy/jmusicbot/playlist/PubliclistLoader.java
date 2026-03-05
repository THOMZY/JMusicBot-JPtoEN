package dev.cosgy.jmusicbot.playlist;

import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author kosugi_kun
 */
public class PubliclistLoader {
    private final BotConfig config;

    public PubliclistLoader(BotConfig config) {
        this.config = config;
    }

    public List<String> getPlaylistNames() {
        if (folderExists()) {
            File folder = new File(config.getPublistFolder());
            return Arrays.stream(Objects.requireNonNull(folder.listFiles((pathname) -> pathname.getName().endsWith(".txt"))))
                    .map(f -> f.getName().substring(0, f.getName().length() - 4))
                    .collect(Collectors.toList());
        } else {
            createFolder();
            return Collections.emptyList();
        }
    }

    public void createFolder() {
        try {
            Files.createDirectory(OtherUtil.getPath(config.getPublistFolder()));
        } catch (IOException ignore) {
        }
    }

    public boolean folderExists() {
        return Files.exists(OtherUtil.getPath(config.getPublistFolder()));
    }

    public void createPlaylist(String name) throws IOException {
        Files.createFile(OtherUtil.getPath(config.getPublistFolder() + File.separator + name + ".txt"));
    }

    public void deletePlaylist(String name) throws IOException {
        Files.delete(OtherUtil.getPath(config.getPublistFolder() + File.separator + name + ".txt"));
    }

    public void writePlaylist(String name, String text) throws IOException {
        Files.write(OtherUtil.getPath(config.getPublistFolder() + File.separator + name + ".txt"), text.trim().getBytes(StandardCharsets.UTF_8));
    }

    public Playlist getPlaylist(String name) {
        if (!getPlaylistNames().contains(name))
            return null;
        if (!folderExists()) {
            createFolder();
            return null;
        }

        return loadPlaylistFromPath(name, OtherUtil.getPath(config.getPublistFolder() + File.separator + name + ".txt"));
    }

    private Playlist loadPlaylistFromPath(String name, java.nio.file.Path playlistPath) {
        return PlaylistLoaderSupport.loadPlaylist(name, playlistPath, Playlist::new);
    }

    public static class PlaylistLoadError extends BasePlaylistLoadError {
        private PlaylistLoadError(int number, String item, String reason) {
            super(number, item, reason);
        }
    }

    public class Playlist extends BasePlaylist<PlaylistLoadError> {

        private Playlist(String name, List<String> items, boolean shuffle) {
            super(name, items, shuffle);
        }

        public void loadTracks(com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager manager, java.util.function.Consumer<AudioTrack> consumer, Runnable callback) {
            super.loadTracks(manager, config, consumer, callback);
        }

        protected PlaylistAsyncLoader.ErrorFactory<PlaylistLoadError> createErrorFactory() {
            return new PlaylistAsyncLoader.ErrorFactory<>() {
                @Override
                public PlaylistLoadError tooLong(int index, String item) {
                    return new PlaylistLoadError(index, item, "This track exceeds the allowed maximum length");
                }

                @Override
                public PlaylistLoadError noMatches(int index, String item) {
                    return new PlaylistLoadError(index, item, "No matching item was found.");
                }

                @Override
                public PlaylistLoadError loadFailed(int index, String item, com.sedmelluq.discord.lavaplayer.tools.FriendlyException exception) {
                    return new PlaylistLoadError(index, item, "Failed to load the track: " + exception.getLocalizedMessage());
                }
            };
        }
    }
}