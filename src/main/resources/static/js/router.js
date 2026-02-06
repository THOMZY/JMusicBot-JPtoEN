/**
 * Router for JMusicBot Web Panel SPA
 */

const Router = (() => {
    // Current view
    let currentView = null;

    // View configurations
    const views = {
        'player': {
            path: '/index.html',
            alias: ['/'],
            template: 'components/views/player.html',
            title: 'Player',
            navId: 'player-btn',
            init: async () => {
                console.log('Initializing Player View...');
                const componentLoads = [
                    loadComponent('player', '#player-component'),
                    loadComponent('queue', '#queue-component'),
                    loadComponent('add-track', '#add-track-component')
                ];

                const chaptersHost = document.querySelector('#chapters-component');
                if (chaptersHost) {
                    componentLoads.push(loadComponent('youtube-chapters', chaptersHost));
                } else {
                    console.warn('Chapters sidebar host #chapters-component is missing; skipping YouTube chapters UI for this layout.');
                }

                    // If the SPA was entered from a lightweight entry page (e.g. history.html),
                    // Player/UI/YouTubeChapters modules might not be loaded yet.
                    // Load them on-demand so the Player view works without needing a manual refresh.
                    if (typeof loadScript === 'function') {
                        const moduleLoads = [];
                        if (typeof UI === 'undefined') {
                            moduleLoads.push(loadScript('js/modules/ui.js'));
                        }
                        if (typeof Player === 'undefined') {
                            moduleLoads.push(loadScript('js/modules/player.js'));
                        }
                        if (typeof YouTubeChapters === 'undefined') {
                            moduleLoads.push(loadScript('js/modules/youtube-chapters.js'));
                        }

                        if (moduleLoads.length > 0) {
                            await Promise.all(moduleLoads);
                        }
                    }

                await Promise.all(componentLoads);
                
                // Re-attach event listeners for the player view
                if (typeof setupTrackInputForm === 'function') {
                    setupTrackInputForm();
                }

                // Refresh player UI
                if (typeof Player !== 'undefined') {
                    // If we didn't boot from index.html, initializeApp() may not have called Player.initialize().
                    // Start it once so periodic refresh works without requiring a full reload.
                    if (typeof Player.initialize === 'function' && !window.__playerInitialized) {
                        window.__playerInitialized = true;
                        Player.initialize();
                    }

                    Player.fetchStatus();
                    Player.fetchQueue();
                    Player.setupProgressBarInteraction();
                }
                
                // Check for chapters
                if (typeof YouTubeChapters !== 'undefined' && window.currentStatus && 
                    window.currentStatus.playing && window.currentStatus.sourceType === 'YouTube') {
                    YouTubeChapters.fetchYouTubeChapters();
                }
            }
        },
        'history': {
            path: '/history.html',
            template: 'components/views/history.html',
            title: 'History',
            navId: 'history-btn',
            init: async () => {
                console.log('Initializing History View...');
                await loadComponent('history-content', '#history-content-component');
                
                // Initialize history module
                if (typeof HistoryModule !== 'undefined') {
                    HistoryModule.init();
                } else if (typeof window.initializeHistoryPage === 'function') {
                    window.initializeHistoryPage();
                }
            }
        },
        'channels': {
            path: '/channels.html',
            template: 'components/views/channels.html',
            title: 'Channels',
            navId: 'channels-btn',
            init: async () => {
                console.log('Initializing Channels View...');
                await loadComponent('channels', '#channels-component');
                
                // Initialize channels manager
                if (typeof ChannelsManager !== 'undefined') {
                    ChannelsManager.initialize();
                }
            }
        }
    };

    /**
     * Initialize the router
     */
    const init = () => {
        // Handle browser back/forward
        window.addEventListener('popstate', (e) => {
            handleLocation();
        });

        // Intercept clicks on links
        document.addEventListener('click', (e) => {
            // Find closest anchor tag
            const link = e.target.closest('a');
            if (link && link.href && link.href.startsWith(window.location.origin)) {
                // Check if it's a navigation link we should handle
                const path = new URL(link.href).pathname;
                // Simple check if it matches one of our views
                const viewName = getViewNameFromPath(path);
                
                if (viewName) {
                    e.preventDefault();
                    navigateTo(viewName);
                }
            }
        });

        // Initial load
        handleLocation();
    };

    /**
     * Get view name from path
     */
    const getViewNameFromPath = (path) => {
        // Normalize path
        if (!path.startsWith('/')) path = '/' + path;
        
        for (const [name, config] of Object.entries(views)) {
            if (config.path === path || (config.alias && config.alias.includes(path))) {
                return name;
            }
        }
        return null;
    };

    /**
     * Handle current location
     */
    const handleLocation = () => {
        const path = window.location.pathname;
        let viewName = getViewNameFromPath(path);
        
        // Default to player if not found
        if (!viewName) {
            viewName = 'player';
            // Optionally replace state to /index.html
            history.replaceState(null, '', '/index.html');
        }
        
        loadView(viewName);
    };

    /**
     * Navigate to a specific view
     */
    const navigateTo = (viewName) => {
        const config = views[viewName];
        if (!config) return;

        // Update URL
        history.pushState(null, '', config.path);
        
        // Load view
        loadView(viewName);
    };

    /**
     * Load a view
     */
    const loadView = async (viewName) => {
        if (currentView === viewName) return;
        
        const config = views[viewName];
        if (!config) return;

        // Reset server dropdown to normal (remove "All Servers") if leaving history
        if (viewName !== 'history' && typeof ServerManager !== 'undefined' && typeof ServerManager.refreshDropdown === 'function') {
            ServerManager.refreshDropdown();
        }

        // Hide the floating chapters sidebar whenever we leave the player view
        if (viewName !== 'player' && typeof YouTubeChapters !== 'undefined') {
            YouTubeChapters.hideChaptersContainer();
        }

        console.log(`Loading view: ${viewName}`);
        
        // Update title
        document.title = `${config.title} - JMusicBot Web Panel`;
        
        // Update navigation state
        updateNavigation(config.navId);
        
        // Load template
        const mainView = document.getElementById('main-view');
        if (!mainView) {
            console.error('Main view container not found!');
            return;
        }

        try {
            // Show loading or transition?
            // mainView.innerHTML = '<div class="loading">Loading...</div>';
            
            const response = await fetch(config.template);
            const html = await response.text();
            mainView.innerHTML = html;
            
            // Initialize view
            if (config.init) {
                await config.init();
            }
            
            currentView = viewName;
            
        } catch (error) {
            console.error(`Error loading view ${viewName}:`, error);
            mainView.innerHTML = `<div class="error">Error loading view: ${error.message}</div>`;
        }
    };

    /**
     * Update navigation buttons state
     */
    const updateNavigation = (activeId) => {
        // Enable all nav buttons
        const navButtons = document.querySelectorAll('.nav-btn');
        navButtons.forEach(btn => {
            btn.removeAttribute('disabled');
            btn.classList.remove('active');
        });
        
        // Disable active button
        if (activeId) {
            const activeBtn = document.getElementById(activeId);
            if (activeBtn) {
                activeBtn.setAttribute('disabled', 'true');
                activeBtn.classList.add('active');
            }
        }
    };

    // Public API
    return {
        init,
        navigateTo
    };
})();

// Expose Router globally
window.Router = Router;

// Initialize router when DOM is loaded
document.addEventListener('DOMContentLoaded', () => {
    // Wait for other scripts to load?
    // We'll initialize it explicitly from the main script
});
