package dev.cosgy.jmusicbot.playlist;

import com.jagrosh.jmusicbot.BotConfig;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

/**
 * @author kosugi_kun
 */
public class MylistLoader {
    private final BotConfig config;

    public MylistLoader(BotConfig config) {
        this.config = config;
    }

    public static void Trim(boolean[] shuffle, List<String> list, String str) {
        String s = str.trim();
        if (s.isEmpty())
            return;
        if (s.startsWith("#") || s.startsWith("//")) {
            s = s.replaceAll("\\s+", "");
            if (s.equalsIgnoreCase("#shuffle") || s.equalsIgnoreCase("//shuffle"))
                shuffle[0] = true;
        } else
            list.add(s);
    }

    public List<String> getPlaylistNames(String userId) {
        if (folderExists()) {
            if (folderUserExists(userId)) {
                File folder = new File(OtherUtil.getPath(config.getMylistfolder() + File.separator + userId).toString());
                File[] files = folder.listFiles((pathname) -> pathname.getName().endsWith(".txt"));
                if (files == null) {
                    return Collections.emptyList();
                }
                return Arrays.stream(files)
                        .map(f -> f.getName().substring(0, f.getName().length() - 4))
                        .collect(Collectors.toList());
            } else {
                createUserFolder(userId);
                return getPlaylistNames(userId);
            }
        } else {
            createFolder();
            createUserFolder(userId);
            return getPlaylistNames(userId);
        }
    }

    public void createUserFolder(String userId) {
        try {
            Files.createDirectory(Paths.get(config.getMylistfolder() + File.separator + userId));
        } catch (IOException ignored) {
        }
    }

    public void createFolder() {
        try {
            Files.createDirectory(Paths.get(config.getMylistfolder()));
        } catch (IOException ignore) {
        }
    }

    public boolean folderUserExists(String userId) {
        return Files.exists(Paths.get(config.getMylistfolder() + File.separator + userId));
    }

    public boolean folderExists() {
        return Files.exists(Paths.get(config.getMylistfolder()));
    }

    public void createPlaylist(String userId, String name) throws IOException {
        Files.createFile(Paths.get(config.getMylistfolder() + File.separator + userId + File.separator + name + ".txt"));
    }

    public void deletePlaylist(String userId, String name) throws IOException {
        Files.delete(Paths.get(config.getMylistfolder() + File.separator + userId + File.separator + name + ".txt"));
    }

    public void writePlaylist(String userId, String name, String text) throws IOException {
        Files.write(Paths.get(config.getMylistfolder() + File.separator + userId + File.separator + name + ".txt"), text.trim().getBytes(StandardCharsets.UTF_8));
    }

    public Playlist getPlaylist(String userId, String name) {
        if (!getPlaylistNames(userId).contains(name))
            return null;
        if (!folderExists()) {
            createFolder();
            createUserFolder(userId);
            return null;
        }
        if (!folderUserExists(userId)) {
            createUserFolder(userId);
            return null;
        }

        return loadPlaylistFromPath(name, Paths.get(config.getMylistfolder() + File.separator + userId + File.separator + name + ".txt"));
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
                    return new PlaylistLoadError(index, item, "This track exceeds the allowed maximum length.");
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
