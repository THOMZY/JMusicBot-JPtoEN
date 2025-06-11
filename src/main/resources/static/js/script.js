/**
 * JMusicBot Web Panel - Main Script
 * This file loads all required modules and initializes the web panel functionality.
 */

// Make initializeApp accessible globally 
window.initializeApp = initializeApp;

// Initialize the application after all modules are loaded
function initializeApp() {
    try {
        console.log('Initializing application...');
        
        // Initialize UI components first
        if (typeof UI !== 'undefined') {
            console.log('Initializing UI module...');
            UI.initializeUI();
        } else {
            console.error('UI module not found');
        }
        
        // Initialize server manager and load servers - with error handling
        if (typeof ServerManager !== 'undefined') {
            console.log('Initializing Server Manager...');
            ServerManager.initialize();
            
            // Load servers and update display
            ServerManager.loadServers()
                .then(() => {
                    return ServerManager.getSelectedGuild();
                })
                .then(guildId => {
                    if (guildId) {
                        window.currentGuildId = guildId;
                        ServerManager.updateServerDisplay();
                        console.log('Selected guild updated:', guildId);
                    } else {
                        console.warn('No guild selected');
                    }
                })
                .catch(error => {
                    console.error('Error in server initialization:', error);
                });
        } else {
            console.error('ServerManager module not found');
        }
        
        // Load bot profile information
        if (typeof BotProfile !== 'undefined') {
            console.log('Initializing Bot Profile...');
            BotProfile.fetchBotInfo();
        } else {
            console.error('BotProfile module not found');
        }
        
        // Set up socket connections and event handlers
        if (typeof Player !== 'undefined') {
            console.log('Initializing Player...');
            Player.initialize();
            
            // Make sure we check for YouTube chapters after player is initialized
            setTimeout(() => {
                if (typeof YouTubeChapters !== 'undefined' && window.currentStatus && 
                    window.currentStatus.playing && window.currentStatus.sourceType === 'YouTube') {
                    console.log('Checking for YouTube chapters...');
                    YouTubeChapters.fetchYouTubeChapters();
                }
            }, 1000); // Small delay to ensure player status is fetched
        } else {
            console.error('Player module not found');
        }
        
        // Update status message
        const statusElement = document.getElementById('status-message');
        if (statusElement) {
            statusElement.textContent = 'Connected to server';
        }
        
        // Setup event handlers for buttons and forms
        setupEventHandlers();
        
        console.log('Application initialized successfully!');
    } catch (error) {
        console.error('Error initializing the app:', error);
        const statusElement = document.getElementById('status-message');
        if (statusElement) {
            statusElement.textContent = 'Error initializing the application. Check console for details.';
        }
    }
}

// Set up error handling for the entire application
window.onerror = function(message, source, lineno, colno, error) {
    console.error('Global error:', message, 'at', source, ':', lineno, ':', colno);
    const statusElement = document.getElementById('status-message');
    if (statusElement) {
        statusElement.textContent = 'Application error. Check console for details.';
    }
    return false;
};

// Setup all event handlers for buttons and forms
function setupEventHandlers() {
    setupTrackInputForm();
    setupNavigation();
    setupModalButtons();
}

// Handle form submission for adding tracks
function setupTrackInputForm() {
    const addUrlForm = document.getElementById('add-url-form');
    const urlInput = document.getElementById('url-input');
    const addNextButton = document.getElementById('add-next-button');
    
    if (addUrlForm) {
        addUrlForm.addEventListener('submit', function(e) {
            e.preventDefault();
            if (urlInput && urlInput.value.trim() && typeof Player !== 'undefined') {
                Player.addToQueue(urlInput.value.trim());
                urlInput.value = '';
            }
        });
    }
    
    if (addNextButton && urlInput) {
        addNextButton.addEventListener('click', function() {
            if (urlInput.value.trim() && typeof Player !== 'undefined') {
                Player.addToQueue(urlInput.value.trim(), true); // true = add next
                urlInput.value = '';
            }
        });
    }
}

// Set up navigation buttons
function setupNavigation() {
    // player button is handled by header link
    
    // History button
    const historyBtn = document.getElementById('history-btn');
    if (historyBtn && !historyBtn.hasAttribute('disabled')) {
        historyBtn.addEventListener('click', function() {
            window.location.href = 'history.html';
        });
    }
    
    // Setup player button if on a different page
    const playerBtn = document.getElementById('player-btn');
    if (playerBtn && !playerBtn.hasAttribute('disabled')) {
        playerBtn.addEventListener('click', function() {
            window.location.href = 'index.html';
        });
    }
}

// Set up buttons to open modals
function setupModalButtons() {
    // Commands modal
    const commandsBtn = document.getElementById('commands-btn');
    const commandsModal = document.getElementById('commands-modal');
    const commandsClose = document.getElementById('commands-modal-close');
    
    if (commandsBtn && commandsModal) {
        commandsBtn.addEventListener('click', function() {
            commandsModal.style.display = 'flex';
        });
        
        if (commandsClose) {
            commandsClose.addEventListener('click', function() {
                commandsModal.style.display = 'none';
            });
        }
    }
    
    // Console modal
    const consoleBtn = document.getElementById('console-btn');
    const consoleModal = document.getElementById('console-modal');
    const consoleClose = document.getElementById('console-modal-close');
    
    if (consoleBtn && consoleModal) {
        consoleBtn.addEventListener('click', function() {
            consoleModal.style.display = 'flex';
            if (typeof ConsoleManager !== 'undefined') {
                ConsoleManager.refreshLogs();
            }
        });
        
        if (consoleClose) {
            consoleClose.addEventListener('click', function() {
                consoleModal.style.display = 'none';
            });
        }
    }
    
    // Config modal
    const configBtn = document.getElementById('config-btn');
    const configModal = document.getElementById('config-modal');
    const configClose = document.getElementById('config-modal-close');
    
    if (configBtn && configModal) {
        configBtn.addEventListener('click', function() {
            configModal.style.display = 'flex';
            if (typeof ConfigManager !== 'undefined') {
                ConfigManager.loadConfig();
            }
        });
        
        if (configClose) {
            configClose.addEventListener('click', function() {
                configModal.style.display = 'none';
            });
        }
    }
    
    // Bot profile modal
    const botProfileBtn = document.getElementById('bot-profile-btn');
    const botProfileModal = document.getElementById('bot-profile-modal');
    const botProfileClose = document.getElementById('bot-profile-modal-close');
    
    if (botProfileBtn && botProfileModal) {
        botProfileBtn.addEventListener('click', function() {
            botProfileModal.style.display = 'flex';
        });
        
        if (botProfileClose) {
            botProfileClose.addEventListener('click', function() {
                botProfileModal.style.display = 'none';
            });
        }
    }
    
    // Reboot button
    const rebootBtn = document.getElementById('reboot-btn');
    if (rebootBtn) {
        rebootBtn.addEventListener('click', function() {
            if (confirm('Are you sure you want to reboot the bot?')) {
                fetch('/api/reboot', { method: 'POST' })
                    .then(response => {
                        if (response.ok) {
                            if (typeof UI !== 'undefined') {
                                UI.showToast('Bot is rebooting...', 'info');
                            } else {
                                alert('Bot is rebooting...');
                            }
                        } else {
                            throw new Error('Failed to reboot the bot');
                        }
                    })
                    .catch(error => {
                        console.error('Reboot error:', error);
                        if (typeof UI !== 'undefined') {
                            UI.showToast('Failed to reboot the bot', 'error');
                        } else {
                            alert('Failed to reboot the bot');
                        }
                    });
            }
        });
    }
}

// Initialize global variables
window.currentGuildId = null;
window.servers = [];
