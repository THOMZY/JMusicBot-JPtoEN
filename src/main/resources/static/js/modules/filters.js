/**
 * Audio Filters Module – manages the filters sidebar in the JMusicBot Web Panel.
 * Mirrors the YouTubeChapters pattern (IIFE, public API, position helpers).
 */

const AudioFilters = (function () {
    let initialized = false;
    let positionScheduled = false;
    let positionListenersBound = false;
    let pollIntervalId = null;

    /* ── helpers ── */

    function formatValue(filterName, paramName, value) {
        const v = parseFloat(value);
        const hzParams = ['frequency', 'filterBand', 'filterWidth', 'rotationHz'];
        if (hzParams.includes(paramName)) return v.toFixed(1) + ' Hz';
        return v % 1 === 0 ? v.toFixed(1) : v.toFixed(2);
    }

    /* ── positioning (mirrors YouTubeChapters) ── */

    function positionSidebar() {
        const sidebar = document.getElementById('filters-sidebar');
        const playerCard = document.querySelector('.player-container');
        if (!sidebar || !playerCard) return;

        if (window.innerWidth <= 1200) {
            sidebar.style.removeProperty('--filters-right');
            sidebar.style.removeProperty('--filters-top');
            return;
        }

        const rect = playerCard.getBoundingClientRect();
        const sidebarWidth = sidebar.offsetWidth || 320;

        // Place sidebar to the LEFT of the player
        const desiredRight = window.innerWidth - rect.left + 24;
        const maxRight = window.innerWidth - sidebarWidth - 12;
        const clampedRight = Math.max(12, Math.min(desiredRight, maxRight));

        const headerOffset = document.querySelector('.main-header')?.offsetHeight ?? 70;
        const desiredTop = Math.max(headerOffset + 10, rect.top);

        sidebar.style.setProperty('--filters-right', clampedRight + 'px');
        sidebar.style.setProperty('--filters-top', desiredTop + 'px');
    }

    function schedulePosition() {
        if (positionScheduled) return;
        positionScheduled = true;
        requestAnimationFrame(() => {
            positionScheduled = false;
            positionSidebar();
        });
    }

    function ensurePositionListeners() {
        if (positionListenersBound) return;
        positionListenersBound = true;
        window.addEventListener('resize', schedulePosition);
        window.addEventListener('scroll', schedulePosition);
    }

    /* ── filter state sync ── */

    async function fetchFilters() {
        try {
            const res = await fetch('/api/filters');
            const data = await res.json();
            if (data.success && data.filters) {
                applyStateToUI(data.filters);
            }
        } catch (e) {
            console.error('[AudioFilters] fetch error', e);
        }
    }

    async function sendFilters() {
        const config = buildConfigFromUI();
        try {
            const res = await fetch('/api/filters', {
                method: 'POST',
                headers: { 'Content-Type': 'application/json' },
                body: JSON.stringify(config)
            });
            const data = await res.json();
            if (data.success && data.filters) {
                applyStateToUI(data.filters);
            }
        } catch (e) {
            console.error('[AudioFilters] send error', e);
        }
    }

    /** Read all sliders / toggles and build a config map matching FilterChainConfig.toMap() */
    function buildConfigFromUI() {
        const config = {};
        document.querySelectorAll('.filter-card').forEach(card => {
            const name = card.dataset.filter;
            const enabled = card.querySelector('.filter-enabled')?.checked ?? false;
            const params = { enabled };
            card.querySelectorAll('.filter-slider').forEach(slider => {
                params[slider.dataset.param] = parseFloat(slider.value);
            });
            config[name] = params;
        });
        return config;
    }

    /** Push server state into UI controls */
    function applyStateToUI(filters) {
        let anyEnabled = false;

        Object.keys(filters).forEach(filterName => {
            const f = filters[filterName];
            const card = document.querySelector(`.filter-card[data-filter="${filterName}"]`);
            if (!card) return;

            const toggle = card.querySelector('.filter-enabled');
            if (toggle) toggle.checked = !!f.enabled;
            if (f.enabled) anyEnabled = true;

            Object.keys(f).forEach(param => {
                if (param === 'enabled') return;
                const slider = card.querySelector(`.filter-slider[data-param="${param}"]`);
                if (slider) {
                    slider.value = f[param];
                    const valSpan = document.getElementById(`${filterName}-${param}-val`);
                    if (valSpan) valSpan.textContent = formatValue(filterName, param, f[param]);
                }
            });
        });

        // Body class for the active-dot indicator on the toggle button
        document.body.classList.toggle('has-active-filters', anyEnabled);

        // Always show the sidebar (filters are always available, unlike chapters)
        const sidebar = document.getElementById('filters-sidebar');
        if (sidebar) sidebar.setAttribute('aria-hidden', document.body.classList.contains('filters-open') ? 'false' : 'true');
    }

    /* ── event wiring ── */

    function wireEvents() {
        const container = document.getElementById('filters-container');
        if (!container) return;

        // Toggle switches → send immediately
        container.querySelectorAll('.filter-enabled').forEach(toggle => {
            toggle.addEventListener('change', () => {
                // Also expand/collapse the card body on toggle
                const card = toggle.closest('.filter-card');
                if (card) card.classList.toggle('open', toggle.checked);
                sendFilters();
            });
        });

        // Collapsible headers
        container.querySelectorAll('.filter-card-header').forEach(header => {
            header.addEventListener('click', (e) => {
                // Don't toggle collapse when clicking the switch itself
                if (e.target.closest('.filter-toggle-switch')) return;
                const card = header.closest('.filter-card');
                if (card) card.classList.toggle('open');
            });
        });

        // Sliders → debounced send
        let sliderTimeout;
        container.querySelectorAll('.filter-slider').forEach(slider => {
            slider.addEventListener('input', () => {
                // Update displayed value instantly
                const filterName = slider.dataset.filter;
                const paramName = slider.dataset.param;
                const valSpan = document.getElementById(`${filterName}-${paramName}-val`);
                if (valSpan) valSpan.textContent = formatValue(filterName, paramName, slider.value);

                clearTimeout(sliderTimeout);
                sliderTimeout = setTimeout(sendFilters, 200);
            });
        });

        // Reset All
        const resetBtn = document.getElementById('filters-reset-all');
        if (resetBtn) {
            resetBtn.addEventListener('click', async () => {
                // Reset all sliders to default then send
                container.querySelectorAll('.filter-slider').forEach(s => {
                    s.value = s.defaultValue;
                    const fn = s.dataset.filter;
                    const pn = s.dataset.param;
                    const vs = document.getElementById(`${fn}-${pn}-val`);
                    if (vs) vs.textContent = formatValue(fn, pn, s.defaultValue);
                });
                container.querySelectorAll('.filter-enabled').forEach(t => { t.checked = false; });
                container.querySelectorAll('.filter-card').forEach(c => c.classList.remove('open'));
                await sendFilters();
            });
        }

        // Close button
        const closeBtn = document.getElementById('filters-close');
        if (closeBtn) {
            closeBtn.addEventListener('click', () => {
                document.body.classList.remove('filters-open');
                const sidebar = document.getElementById('filters-sidebar');
                if (sidebar) sidebar.setAttribute('aria-hidden', 'true');
                const toggleBtn = document.getElementById('filters-toggle');
                if (toggleBtn) toggleBtn.setAttribute('aria-expanded', 'false');
            });
        }
    }

    /* ── public API ── */

    async function initialize() {
        if (initialized) return;
        initialized = true;

        // Load the HTML component
        const host = document.getElementById('filters-component');
        if (host) {
            try {
                const resp = await fetch('components/filters.html');
                host.innerHTML = await resp.text();
            } catch (e) {
                console.error('[AudioFilters] failed to load component', e);
                return;
            }
        }

        wireEvents();
        // Filters are always available — mark body so the sidebar can be shown
        document.body.classList.add('has-filters');
        await fetchFilters();
        ensurePositionListeners();
        schedulePosition();

        // Poll filter state periodically as a fallback. Real-time updates
        // arrive through /ws/events (PanelEvents); this slow timer only
        // matters if the WebSocket connection is down.
        pollIntervalId = setInterval(() => {
            fetchFilters().catch(e => console.error('[AudioFilters] poll error', e));
        }, 30000);
    }

    function refresh() {
        fetchFilters();
    }

    function setPlayerViewActive(active) {
        const sidebar = document.getElementById('filters-sidebar');
        const toggleBtn = document.getElementById('filters-toggle');

        if (active) {
            document.body.classList.add('has-filters');
            return;
        }

        // Outside player view, force-close and hide the filters sidebar.
        document.body.classList.remove('filters-open');
        document.body.classList.remove('has-filters');
        if (sidebar) sidebar.setAttribute('aria-hidden', 'true');
        if (toggleBtn) toggleBtn.setAttribute('aria-expanded', 'false');
    }

    return {
        initialize,
        refresh,
        positionSidebar: schedulePosition,
        setPlayerViewActive
    };
})();
