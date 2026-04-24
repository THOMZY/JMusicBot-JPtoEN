/**
 * Live cursors - shows the mouse pointer of every other user currently
 * looking at the web panel.
 *
 * Position protocol: each cursor is anchored to the DOM element it is
 * hovering, plus a relative (rx, ry) offset inside that element's
 * bounding rect (0..1). The receiver looks up the same element by a
 * stable selector and places the cursor at element.rect + (rx, ry) * size.
 *
 * This makes positions correct across any layout differences:
 *  - different screen sizes / resolutions
 *  - different browser zoom levels
 *  - responsive breakpoints rearranging the UI
 *  - scrolling
 *  - sidebars / panels open or closed independently
 *
 * As a fallback (e.g. cursor over empty body), a viewport-relative
 * normalized position is sent and used.
 */
(function () {
    const ADJECTIVES = [
        'Funky', 'Sleepy', 'Groovy', 'Cosmic', 'Spicy', 'Mellow', 'Jazzy',
        'Glitchy', 'Velvet', 'Neon', 'Lofi', 'Disco', 'Retro', 'Snazzy',
        'Wobbly', 'Bouncy', 'Cheesy', 'Sparkly', 'Smooth', 'Loud'
    ];
    const ANIMALS = [
        'Otter', 'Panda', 'Falcon', 'Llama', 'Hedgehog', 'Axolotl', 'Octopus',
        'Walrus', 'Penguin', 'Capybara', 'Yak', 'Narwhal', 'Sloth', 'Frog',
        'Raccoon', 'Goose', 'Tapir', 'Quokka', 'Lemur', 'Badger'
    ];

    function pick(arr) { return arr[Math.floor(Math.random() * arr.length)]; }

    function loadIdentity() {
        try {
            const cached = JSON.parse(localStorage.getItem('liveCursorIdentity') || 'null');
            if (cached && cached.name && cached.color) return cached;
        } catch (e) { /* ignore */ }
        const hue = Math.floor(Math.random() * 360);
        const identity = {
            name: pick(ADJECTIVES) + ' ' + pick(ANIMALS),
            color: `hsl(${hue}, 85%, 60%)`
        };
        try { localStorage.setItem('liveCursorIdentity', JSON.stringify(identity)); } catch (e) { /* ignore */ }
        return identity;
    }

    const identity = loadIdentity();
    const peers = new Map(); // id -> { el, lastMsg }
    let myId = null;
    let socket = null;
    let layer = null;
    let pendingPayload = null;
    let sendTimer = null;
    let lastSnapshot = null; // last computed position payload
    let reconnectDelay = 800;
    const SEND_INTERVAL_MS = 40;
    // Canonical identifier for the current page so peers on /, /index.html
    // (the spring root view forwards / -> /index.html) and other variants
    // are bucketed together correctly. The web panel is a SPA that uses
    // history.pushState, so we recompute it on every navigation.
    let currentPage = canonicalPage(location.pathname);

    function canonicalPage(path) {
        let p = (path || '/').toLowerCase();
        // strip trailing slashes
        p = p.replace(/\/+$/, '');
        // strip .html extension
        p = p.replace(/\.html?$/i, '');
        // collapse "" or "/index" -> "index"
        if (p === '' || p === '/' || p === '/index') return 'index';
        // strip leading slash
        return p.replace(/^\/+/, '');
    }

    /** Recompute currentPage; if it changed, drop every visible peer (they
     *  were on the old page) and announce our new location. */
    function refreshCurrentPage() {
        const next = canonicalPage(location.pathname);
        if (next === currentPage) return;
        currentPage = next;
        for (const id of Array.from(peers.keys())) removePeer(id);
        announcePresence();
    }

    function announcePresence() {
        if (!socket || socket.readyState !== WebSocket.OPEN) return;
        const payload = Object.assign({
            name: identity.name,
            color: identity.color,
            page: currentPage
        }, lastSnapshot || {});
        try { socket.send(JSON.stringify(payload)); } catch (e) { /* ignore */ }
    }

    function ensureLayer() {
        if (layer) return layer;
        layer = document.createElement('div');
        layer.id = 'live-cursors-layer';
        Object.assign(layer.style, {
            position: 'fixed',
            inset: '0',
            pointerEvents: 'none',
            zIndex: '2147483600',
            overflow: 'hidden'
        });
        document.body.appendChild(layer);
        return layer;
    }

    function buildCursorElement(name, color) {
        const wrap = document.createElement('div');
        Object.assign(wrap.style, {
            position: 'absolute',
            left: '0',
            top: '0',
            transform: 'translate(-9999px,-9999px)',
            transition: 'transform 90ms linear',
            willChange: 'transform',
            pointerEvents: 'none'
        });
        wrap.innerHTML = `
            <svg width="22" height="22" viewBox="0 0 22 22" style="display:block;filter:drop-shadow(0 1px 2px rgba(0,0,0,.35));">
                <path d="M2 2 L2 18 L7 14 L10 20 L13 19 L10 13 L17 13 Z"
                      fill="${color}" stroke="white" stroke-width="1.2" stroke-linejoin="round"/>
            </svg>
            <div class="live-cursor-label" style="
                margin: 2px 0 0 14px;
                padding: 2px 6px;
                font: 600 11px/1.2 system-ui, sans-serif;
                color: #fff;
                background: ${color};
                border-radius: 6px;
                white-space: nowrap;
                box-shadow: 0 1px 2px rgba(0,0,0,.35);
            ">${escapeHtml(name)}</div>
        `;
        return wrap;
    }

    function escapeHtml(s) {
        return String(s).replace(/[&<>"']/g, c => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        }[c]));
    }

    function upsertPeer(id, name, color) {
        let peer = peers.get(id);
        if (!peer) {
            const el = buildCursorElement(name || 'Anonymous', color || '#888');
            ensureLayer().appendChild(el);
            peer = { el, lastMsg: null };
            peers.set(id, peer);
        }
        return peer;
    }

    function removePeer(id) {
        const peer = peers.get(id);
        if (!peer) return;
        peer.el.remove();
        peers.delete(id);
    }

    /**
     * Build a stable selector for a given DOM element. Prefers the nearest
     * ancestor with an id, then chains :nth-of-type segments down to the
     * target. Result is something like
     *   "#queue-component > div:nth-of-type(2) > button:nth-of-type(3)".
     * Empty string means "no usable selector" (fall back to body).
     */
    function selectorFor(el) {
        if (!el || el === document.body || el === document.documentElement) return '';
        const parts = [];
        let cur = el;
        while (cur && cur.nodeType === 1 && cur !== document.body) {
            if (cur.id) {
                // CSS.escape gracefully handles ids with weird characters.
                parts.unshift('#' + (window.CSS && CSS.escape ? CSS.escape(cur.id) : cur.id));
                return parts.join(' > ');
            }
            const parent = cur.parentElement;
            if (!parent) break;
            // index among same-tag siblings (1-based for :nth-of-type)
            let idx = 1;
            let sib = cur.previousElementSibling;
            while (sib) {
                if (sib.tagName === cur.tagName) idx++;
                sib = sib.previousElementSibling;
            }
            parts.unshift(cur.tagName.toLowerCase() + ':nth-of-type(' + idx + ')');
            cur = parent;
        }
        return parts.length ? 'body > ' + parts.join(' > ') : '';
    }

    /** Resolve a selector previously produced by selectorFor() back to an
     *  element on this page. Returns null if not found. */
    function resolveSelector(sel) {
        if (!sel) return null;
        try { return document.querySelector(sel); } catch (e) { return null; }
    }

    /**
     * Compute a position payload for a given mouse event.
     *  - sel : stable selector for the element under the cursor
     *  - rx, ry : 0..1 offset inside that element's bounding rect
     *  - vx, vy : 0..1 viewport-relative fallback (if selector misses)
     */
    function snapshotFromEvent(e) {
        const x = e.clientX, y = e.clientY;
        // Pierce shadow boundaries by walking elementsFromPoint and skipping
        // our own cursor layer.
        let target = null;
        if (typeof document.elementsFromPoint === 'function') {
            const stack = document.elementsFromPoint(x, y);
            for (const node of stack) {
                if (!node) continue;
                if (node.id === 'live-cursors-layer') continue;
                if (layer && layer.contains(node)) continue;
                target = node;
                break;
            }
        }
        if (!target) target = document.elementFromPoint(x, y);

        const payload = {
            vx: x / Math.max(1, window.innerWidth),
            vy: y / Math.max(1, window.innerHeight)
        };
        if (target && target !== document.documentElement && target !== document.body) {
            const sel = selectorFor(target);
            if (sel) {
                const r = target.getBoundingClientRect();
                if (r.width > 0 && r.height > 0) {
                    payload.sel = sel;
                    payload.rx = (x - r.left) / r.width;
                    payload.ry = (y - r.top) / r.height;
                }
            }
        }
        return payload;
    }

    /** Place a peer using whatever positioning data we received. */
    function placePeer(peer, msg) {
        peer.lastMsg = msg;
        let x, y;
        const target = resolveSelector(msg.sel);
        if (target && typeof msg.rx === 'number' && typeof msg.ry === 'number') {
            const r = target.getBoundingClientRect();
            // If the resolved element is currently zero-sized (hidden), fall
            // back to viewport coords.
            if (r.width > 0 && r.height > 0) {
                x = r.left + msg.rx * r.width;
                y = r.top + msg.ry * r.height;
            }
        }
        if (x === undefined && typeof msg.vx === 'number' && typeof msg.vy === 'number') {
            x = msg.vx * window.innerWidth;
            y = msg.vy * window.innerHeight;
        }
        if (x === undefined) return;
        peer.el.style.transform = `translate(${Math.round(x)}px, ${Math.round(y)}px)`;
        // Hide cursors that fall outside the viewport (peer is on a
        // section we can't see right now, or scrolled away).
        const inside = x >= -20 && x <= window.innerWidth + 20
                    && y >= -20 && y <= window.innerHeight + 20;
        peer.el.style.opacity = inside ? '1' : '0';
    }

    /** Re-place every known peer; called on scroll/resize/DOM changes so
     *  cursors stay pinned to their underlying element. */
    function reflowPeers() {
        for (const peer of peers.values()) {
            if (peer.lastMsg) placePeer(peer, peer.lastMsg);
        }
    }

    function handleMessage(raw) {
        let msg;
        try { msg = JSON.parse(raw); } catch (e) { return; }
        if (!msg || !msg.type) return;
        if (msg.type === 'hello') {
            myId = msg.id;
            return;
        }
        if (msg.type === 'leave') {
            removePeer(msg.id);
            return;
        }
        if (msg.type === 'move') {
            if (msg.page !== currentPage) {
                removePeer(msg.id);
                return;
            }
            const hasPos = msg.sel || (typeof msg.vx === 'number' && typeof msg.vy === 'number');
            if (!hasPos && !peers.has(msg.id)) return;
            const peer = upsertPeer(msg.id, msg.name, msg.color);
            if (hasPos) placePeer(peer, msg);
            if (msg.click) flashClick(peer, msg.color);
            return;
        }
    }

    function flashClick(peer, color) {
        const ring = document.createElement('div');
        Object.assign(ring.style, {
            position: 'absolute',
            left: '0', top: '0',
            width: '6px', height: '6px',
            borderRadius: '50%',
            border: `2px solid ${color || '#fff'}`,
            transform: peer.el.style.transform,
            opacity: '0.9',
            pointerEvents: 'none',
            transition: 'transform 400ms ease-out, opacity 400ms ease-out'
        });
        ensureLayer().appendChild(ring);
        requestAnimationFrame(() => {
            const m = /translate\(([-\d.]+)px,\s*([-\d.]+)px\)/.exec(peer.el.style.transform);
            const x = m ? parseFloat(m[1]) : 0;
            const y = m ? parseFloat(m[2]) : 0;
            ring.style.transform = `translate(${x - 14}px, ${y - 14}px) scale(6)`;
            ring.style.opacity = '0';
        });
        setTimeout(() => ring.remove(), 450);
    }

    function scheduleSend() {
        if (sendTimer) return;
        sendTimer = setTimeout(() => {
            sendTimer = null;
            if (pendingPayload && socket && socket.readyState === WebSocket.OPEN) {
                try { socket.send(JSON.stringify(pendingPayload)); } catch (e) { /* ignore */ }
                pendingPayload = null;
            }
        }, SEND_INTERVAL_MS);
    }

    function attachInputListeners() {
        // Use capture phase so handlers that call stopPropagation() can't
        // hide mouse activity from us.
        const onMove = e => {
            const snap = snapshotFromEvent(e);
            lastSnapshot = snap;
            pendingPayload = Object.assign({
                name: identity.name,
                color: identity.color,
                page: currentPage
            }, snap);
            scheduleSend();
        };
        window.addEventListener('mousemove', onMove, { passive: true, capture: true });
        // pointermove for mice/pens (Firefox throttles mousemove differently
        // than Chrome).
        window.addEventListener('pointermove', e => {
            if (e.pointerType === 'mouse' || e.pointerType === 'pen') onMove(e);
        }, { passive: true, capture: true });

        const onDown = e => {
            // Refresh snapshot from this event so the click coords are exact.
            if (typeof e.clientX === 'number') {
                lastSnapshot = snapshotFromEvent(e);
            }
            const payload = Object.assign({
                name: identity.name,
                color: identity.color,
                page: currentPage,
                click: true
            }, lastSnapshot || {});
            if (socket && socket.readyState === WebSocket.OPEN) {
                try { socket.send(JSON.stringify(payload)); } catch (err) { /* ignore */ }
            }
            pendingPayload = null;
            if (sendTimer) { clearTimeout(sendTimer); sendTimer = null; }
        };
        window.addEventListener('pointerdown', onDown, { passive: true, capture: true });
        window.addEventListener('mousedown', onDown, { passive: true, capture: true });

        // Hide our pointer to other users when leaving the page tab
        window.addEventListener('blur', () => {
            if (socket && socket.readyState === WebSocket.OPEN) {
                try { socket.send(JSON.stringify({ name: identity.name, color: identity.color, page: '__away__' })); } catch (e) { /* ignore */ }
            }
        });

        // Keep peers pinned to their underlying element on any layout change.
        window.addEventListener('resize', reflowPeers);
        window.addEventListener('scroll', reflowPeers, { passive: true, capture: true });
        // DOM mutations (queue refresh, modal open, tab switch, etc.) can
        // move every peer's anchor element. Throttle reflow with rAF.
        let reflowQueued = false;
        const queueReflow = () => {
            if (reflowQueued) return;
            reflowQueued = true;
            requestAnimationFrame(() => { reflowQueued = false; reflowPeers(); });
        };
        try {
            const mo = new MutationObserver(queueReflow);
            mo.observe(document.body, {
                childList: true, subtree: true, attributes: true,
                attributeFilter: ['class', 'style', 'hidden']
            });
        } catch (e) { /* ignore */ }
    }

    function connect() {
        const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
        const url = `${proto}//${location.host}/ws/cursors`;
        try {
            socket = new WebSocket(url);
        } catch (e) {
            // Server not ready yet (e.g. during the bot's initial boot); retry quickly.
            setTimeout(connect, 800);
            return;
        }
        socket.addEventListener('open', () => {
            reconnectDelay = 800;
            // Make our presence known on this page right away so peers don't
            // have to wait for the first mousemove to see us.
            announcePresence();
        });
        socket.addEventListener('message', e => handleMessage(e.data));
        socket.addEventListener('close', () => {
            for (const id of Array.from(peers.keys())) removePeer(id);
            setTimeout(connect, reconnectDelay);
            // Cap at 5s but stay aggressive at first so the cursors appear
            // quickly during the bot's initial startup.
            reconnectDelay = Math.min(reconnectDelay * 2, 5000);
        });
        socket.addEventListener('error', () => { try { socket.close(); } catch (e) { /* ignore */ } });
    }

    function start() {
        ensureLayer();
        attachInputListeners();
        // Listen for SPA navigations (the panel uses history.pushState).
        window.addEventListener('popstate', refreshCurrentPage);
        const wrap = (name) => {
            const orig = history[name];
            if (typeof orig !== 'function') return;
            history[name] = function () {
                const ret = orig.apply(this, arguments);
                refreshCurrentPage();
                return ret;
            };
        };
        wrap('pushState');
        wrap('replaceState');
        connect();
    }

    // Connect as soon as the script is parsed so the initial cursor exchange
    // happens in parallel with the rest of the page bootstrap. Falls back to
    // DOMContentLoaded if document.body isn't available yet.
    if (!document.body) {
        document.addEventListener('DOMContentLoaded', start);
    } else {
        start();
    }
})();
