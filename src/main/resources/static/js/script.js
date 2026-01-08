/**
 * JMusicBot Web Panel - Main Script
 * This file loads all required modules and initializes the web panel functionality.
 */

// Make functions accessible globally
window.initializeApp = initializeApp;
window.setupTrackInputForm = setupTrackInputForm;
window.setupModalButtons = setupModalButtons;

// Initialize the application after all modules are loaded
function initializeApp() {
    try {
        console.log('Initializing application...');
        
        // Initialize UI components first
        if (typeof UI !== 'undefined') {
            console.log('Initializing UI module...');
            UI.initializeUI();
        }
        
        // Initialize server manager and load servers
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
                    }
                })
                .catch(console.error);
        }
        
        // Load bot profile information and start status updates
        if (typeof BotProfile !== 'undefined') {
            BotProfile.fetchBotInfo();
            console.log('Starting bot status updates...');
            BotProfile.startStatusUpdates();
        }
        
        // Set up socket connections
        if (typeof Player !== 'undefined') {
            console.log('Initializing Player...');
            Player.initialize();
        }
        
        // Setup global event handlers (modals, etc)
        setupModalButtons();
        
        console.log('Application initialized successfully!');
    } catch (error) {
        console.error('Error initializing the app:', error);
    }
}

// ... existing code ...

// Setup all event handlers for buttons and forms
function setupEventHandlers() {
    // This is now handled by Router and View initialization
    // setupTrackInputForm();
    // setupNavigation();
    setupModalButtons();
}

// Handle form submission for adding tracks
function setupTrackInputForm() {
    const addUrlForm = document.getElementById('add-url-form');
    
    if (addUrlForm) {
        // Remove old listeners to avoid duplicates if called multiple times
        // Cloning the form also clones its children (buttons, inputs)
        const newForm = addUrlForm.cloneNode(true);
        addUrlForm.parentNode.replaceChild(newForm, addUrlForm);
        
        // Setup Submit (Add to Queue)
        newForm.addEventListener('submit', function(e) {
            e.preventDefault();
            // Re-get input from the live DOM
            const input = document.getElementById('url-input');
            if (input && input.value.trim() && typeof Player !== 'undefined') {
                Player.addToQueue(input.value.trim());
                input.value = '';
            }
        });

        // Setup Add Next Button
        // We must find the button inside the NEW form because the old one is detached
        const newAddNextBtn = newForm.querySelector('#add-next-button');
        if (newAddNextBtn) {
            newAddNextBtn.addEventListener('click', function(e) {
                e.preventDefault();
                const input = document.getElementById('url-input');
                
                if (input && input.value.trim()) {
                    if (typeof Player !== 'undefined') {
                        Player.addToQueue(input.value.trim(), true); // true = add next
                        input.value = '';
                    } else {
                        console.error('Player module is not defined');
                    }
                } else {
                    // Provide feedback if input is empty
                    if (typeof UI !== 'undefined') {
                        UI.showToast('Please enter a URL first', false);
                    }
                }
            });
        }
    }
}

// Set up navigation buttons
function setupNavigation() {
    // Handled by Router and components.js
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

// Re-attach the chapters toggle button whenever the player component loads
document.addEventListener('component:loaded', (event) => {
    if (event.detail?.name === 'player') {
        setupChaptersToggleButton();
    }
});

function setupChaptersToggleButton() {
    const toggleBtn = document.getElementById('chapters-toggle');
    if (!toggleBtn) {
        return;
    }

    const newBtn = toggleBtn.cloneNode(true);
    toggleBtn.parentNode.replaceChild(newBtn, toggleBtn);

    newBtn.addEventListener('click', () => {
        const isOpen = document.body.classList.toggle('chapters-open');
        newBtn.setAttribute('aria-expanded', isOpen ? 'true' : 'false');
        document.body.classList.toggle('chapters-user-closed', !isOpen);
        if (typeof YouTubeChapters !== 'undefined' && typeof YouTubeChapters.repositionDrawer === 'function') {
            YouTubeChapters.repositionDrawer();
        }
    });
}
