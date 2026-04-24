/**
 * Events Module — listens to /ws/events and triggers immediate refreshes
 * of player state, queue and audio filters whenever the server signals
 * that something changed (Discord-side commands, other panels, etc.).
 *
 * Falls back gracefully on slower polling timers if the socket is
 * unavailable.
 */
const PanelEvents = (function () {
    let socket = null;
    let reconnectTimer = null;
    let reconnectDelay = 1000;
    const RECONNECT_MAX = 15000;

    // Coalesce bursts so two consecutive "queue" events only trigger one fetch.
    const pendingTypes = new Set();
    let flushScheduled = false;

    function scheduleFlush() {
        if (flushScheduled) return;
        flushScheduled = true;
        // Tiny delay to coalesce simultaneous events
        setTimeout(() => {
            flushScheduled = false;
            const types = Array.from(pendingTypes);
            pendingTypes.clear();
            for (const t of types) {
                try { dispatch(t); } catch (e) { console.error('[PanelEvents] dispatch error', e); }
            }
        }, 30);
    }

    function dispatch(type) {
        if (type === 'queue') {
            if (typeof Player !== 'undefined' && Player.fetchQueue) Player.fetchQueue();
            return;
        }
        if (type === 'status') {
            if (typeof Player !== 'undefined' && Player.fetchStatus) Player.fetchStatus();
            return;
        }
        if (type === 'filters') {
            if (typeof AudioFilters !== 'undefined' && AudioFilters.refresh) AudioFilters.refresh();
            return;
        }
    }

    function connect() {
        if (socket && (socket.readyState === WebSocket.OPEN || socket.readyState === WebSocket.CONNECTING)) {
            return;
        }
        const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
        const url = `${proto}//${location.host}/ws/events`;
        try {
            socket = new WebSocket(url);
        } catch (e) {
            scheduleReconnect();
            return;
        }

        socket.addEventListener('open', () => {
            reconnectDelay = 1000;
            // On (re)connect, force a one-shot refresh of everything so
            // we recover from anything that changed while disconnected.
            pendingTypes.add('status');
            pendingTypes.add('queue');
            pendingTypes.add('filters');
            scheduleFlush();
        });

        socket.addEventListener('message', (ev) => {
            let msg;
            try { msg = JSON.parse(ev.data); } catch (e) { return; }
            if (!msg || !msg.type) return;
            pendingTypes.add(msg.type);
            scheduleFlush();
        });

        socket.addEventListener('close', () => {
            socket = null;
            scheduleReconnect();
        });

        socket.addEventListener('error', () => {
            try { socket && socket.close(); } catch (_) { /* ignore */ }
        });
    }

    function scheduleReconnect() {
        if (reconnectTimer) return;
        reconnectTimer = setTimeout(() => {
            reconnectTimer = null;
            reconnectDelay = Math.min(RECONNECT_MAX, reconnectDelay * 2);
            connect();
        }, reconnectDelay);
    }

    function initialize() {
        connect();
    }

    return { initialize };
})();

// Auto-init as soon as the script is parsed; the dispatch handlers will
// safely no-op until the corresponding modules are also loaded.
PanelEvents.initialize();
