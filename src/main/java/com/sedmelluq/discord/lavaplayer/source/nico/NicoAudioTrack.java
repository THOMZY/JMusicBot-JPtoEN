package com.sedmelluq.discord.lavaplayer.source.nico;

import com.sedmelluq.discord.lavaplayer.container.MediaContainerDescriptor;
import com.sedmelluq.discord.lavaplayer.source.AudioSourceManager;
import com.sedmelluq.discord.lavaplayer.source.local.LocalSeekableInputStream;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackInfo;
import com.sedmelluq.discord.lavaplayer.track.DelegatedAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.InternalAudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.LocalAudioTrackExecutor;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Audio track that handles processing NicoNico tracks.
 */
public class NicoAudioTrack extends DelegatedAudioTrack {
    private static final Logger log = LoggerFactory.getLogger(NicoAudioTrack.class);
    private final MediaContainerDescriptor containerTrackFactory;
    private final NicoAudioSourceManager sourceManager;

    /**
     * @param trackInfo     Track info
     * @param sourceManager Source manager which was used to find this track
     */
    public NicoAudioTrack(AudioTrackInfo trackInfo, NicoAudioSourceManager sourceManager, MediaContainerDescriptor containerTrackFactory) {
        super(trackInfo);

        this.containerTrackFactory = containerTrackFactory;
        this.sourceManager = sourceManager;
    }

    @Override
    public void process(LocalAudioTrackExecutor localExecutor) throws Exception {
        File playbackUrl = downloadAudio();

        log.debug("Starting NicoNico track from URL: {}", playbackUrl);
        try (LocalSeekableInputStream inputStream = new LocalSeekableInputStream(playbackUrl)) {
            processDelegate((InternalAudioTrack) containerTrackFactory.createTrack(trackInfo, inputStream), localExecutor);
        }
    }

    //
    private @NotNull File downloadAudio() {
        String path = new File(".").getAbsoluteFile().getParent();
        Path file = Path.of(path, "cache" + File.separator + getIdentifier() + ".wav");

        if (Files.notExists(file)) {
            try {
                log.info("Downloading NicoNico track from: {}", getIdentifier());
                Runtime runtime = Runtime.getRuntime();

                String login = "";
                if (NicoAudioSourceManager.userName != null && NicoAudioSourceManager.password != null) {
                    login += " --username " + NicoAudioSourceManager.userName + " --password " + NicoAudioSourceManager.password;
                    log.info("Niconico login information was used.");
                    if(NicoAudioSourceManager.twofactor != null) {
                        String code = TOTPGenerator.getCode(NicoAudioSourceManager.twofactor);

                        login += " --twofactor " + code;
                        log.info("Two-factor authentication was performed:{}", code);
                    }
                }

                String command = "yt-dlp" + login + " --extract-audio --audio-format wav https://www.nicovideo.jp/watch/" + getIdentifier() + " --output cache/" + getIdentifier() + ".wav";

                Process process = runtime.exec(command.split(" "));

                // Create a thread to read the error stream
                new Thread(() -> {
                    try (BufferedReader errorReader = new BufferedReader(new InputStreamReader(process.getErrorStream()))) {
                        String line;
                        while ((line = errorReader.readLine()) != null) {
                            log.error(line);
                        }
                    } catch (IOException e) {
                        log.error("Error reading process error stream", e);
                    }
                }).start();

                // Create a thread to read the standard output stream
                new Thread(() -> {
                    try (BufferedReader inputReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                        String line;
                        while ((line = inputReader.readLine()) != null) {
                            log.debug(line);
                        }
                    } catch (IOException e) {
                        log.error("Error reading process input stream", e);
                    }
                }).start();

                int exitCode = process.waitFor();
                if (exitCode != 0) {
                    throw new RuntimeException("yt-dlp command failed with exit code " + exitCode);
                }
                process.destroy();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
        return file.toFile();
    }

    @Override
    protected AudioTrack makeShallowClone() {
        return new NicoAudioTrack(trackInfo, sourceManager, containerTrackFactory);
    }

    @Override
    public AudioSourceManager getSourceManager() {
        return sourceManager;
    }
}
