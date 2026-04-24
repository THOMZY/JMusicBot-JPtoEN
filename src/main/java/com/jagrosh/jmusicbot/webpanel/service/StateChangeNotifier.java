/*
 * Copyright 2026 THOMZY
 */
package com.jagrosh.jmusicbot.webpanel.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jagrosh.jmusicbot.webpanel.model.MusicStatus;
import com.jagrosh.jmusicbot.webpanel.model.QueueTrack;
import com.jagrosh.jmusicbot.webpanel.websocket.EventsHandler;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Periodically samples the relevant pieces of music state and pushes a
 * tiny notification through {@link EventsHandler} whenever something
 * changed. This avoids invasive hooks at every mutation site (Discord
 * commands, queue mutations, filter changes, etc.) while still giving
 * the web panel near-instant feedback compared to client polling.
 */
@Component
public class StateChangeNotifier {

    /**
     * Sampling cadence. Small enough to feel real-time, large enough to
     * keep CPU usage negligible. The whole tick is skipped when no
     * clients are connected.
     */
    private static final long TICK_MS = 750L;

    private final MusicService musicService;
    private final EventsHandler events;
    private final ObjectMapper mapper = new ObjectMapper();

    private int lastQueueHash = 0;
    private int lastStatusHash = 0;
    private int lastFiltersHash = 0;
    private boolean primed = false;

    public StateChangeNotifier(MusicService musicService, EventsHandler events) {
        this.musicService = musicService;
        this.events = events;
    }

    @Scheduled(fixedDelay = TICK_MS)
    public void tick() {
        if (!events.hasClients()) {
            // Reset baseline so the first reconnecting client doesn't
            // miss a transition that happened while nobody was watching.
            primed = false;
            return;
        }

        int qHash = computeQueueHash();
        int sHash = computeStatusHash();
        int fHash = computeFiltersHash();

        if (!primed) {
            lastQueueHash = qHash;
            lastStatusHash = sHash;
            lastFiltersHash = fHash;
            primed = true;
            return;
        }

        if (qHash != lastQueueHash) {
            lastQueueHash = qHash;
            events.broadcast("queue");
        }
        if (sHash != lastStatusHash) {
            lastStatusHash = sHash;
            events.broadcast("status");
        }
        if (fHash != lastFiltersHash) {
            lastFiltersHash = fHash;
            events.broadcast("filters");
        }
    }

    private int computeQueueHash() {
        try {
            List<QueueTrack> queue = musicService.getQueue();
            return mapper.writeValueAsString(queue).hashCode();
        } catch (Exception ex) {
            return 0;
        }
    }

    private int computeStatusHash() {
        try {
            MusicStatus s = musicService.getCurrentStatus();
            // Intentionally exclude currentTrackPosition (changes every tick).
            return Objects.hash(
                    s.isPlaying(),
                    s.isPaused(),
                    s.isHasNext(),
                    s.isInVoiceChannel(),
                    s.getQueueSize(),
                    s.getVolume(),
                    s.getCurrentTrackUri(),
                    s.getCurrentTrackTitle(),
                    s.getCurrentTrackAuthor(),
                    s.getCurrentTrackThumbnail(),
                    s.getCurrentTrackDuration(),
                    s.getSourceType(),
                    s.getSource(),
                    s.getRequester());
        } catch (Exception ex) {
            return 0;
        }
    }

    private int computeFiltersHash() {
        try {
            Map<String, Object> filters = musicService.getFilters();
            return mapper.writeValueAsString(filters).hashCode();
        } catch (Exception ex) {
            return 0;
        }
    }
}
