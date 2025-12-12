/**
 * UI Module - Handles all user interface elements and interactions
 */

const UI = (function() {
    // Format time in MM:SS
    function formatTime(ms) {
        const totalSeconds = Math.floor(ms / 1000);
        const minutes = Math.floor(totalSeconds / 60);
        const seconds = totalSeconds % 60;
        return `${minutes}:${seconds.toString().padStart(2, '0')}`;
    }

    // Toggle modal visibility
    function toggleModal(modalId, show) {
        const modal = document.getElementById(modalId);
        if (show) {
            modal.classList.add('show');
            if (modalId === 'console-modal') {
                ConsoleManager.loadConsoleLogs();
            }
            if (modalId === 'commands-modal') {
                document.getElementById('command-input').focus();
            }
        } else {
            modal.classList.remove('show');
        }
    }

    // Show toast notification
    function showToast(message, success) {
        const toastContainer = document.getElementById('toast-container');
        
        const toast = document.createElement('div');
        toast.className = `toast ${success ? 'success' : 'error'}`;
        toast.textContent = message;
        
        toastContainer.appendChild(toast);
        
        // Remove toast after animation completes
        setTimeout(() => {
            toast.remove();
        }, 5000);
    }

    // Get appropriate icon based on source type
    function getSourceIcon(sourceType) {
        const icons = {
            'YouTube': 'fab fa-youtube',
            'SoundCloud': 'fab fa-soundcloud',
            'Spotify': 'fab fa-spotify',
            'Local': 'fas fa-file-audio',
            'Radio': 'fas fa-broadcast-tower',
            'Stream': 'fas fa-signal'
        };
        
        return icons[sourceType] || 'fas fa-music';
    }

    // Initialize all UI components
    function initializeUI() {
        console.log('UI: Initializing UI components...');
        
        // Navigation buttons are now handled by components.js and router.js
        // We don't attach listeners here to avoid conflicts and double-loading
        
        // Initialize modals
        const consoleBtn = document.getElementById('console-btn');
        const consoleModal = document.getElementById('console-modal');
        const consoleClose = document.getElementById('console-modal-close');
        
        if (consoleBtn && consoleModal) {
            console.log('UI: Set up console button');
            consoleBtn.addEventListener('click', () => {
                consoleModal.style.display = 'flex';
                if (typeof ConsoleManager !== 'undefined') {
                    ConsoleManager.loadConsoleLogs();
                }
            });
            
            if (consoleClose) {
                consoleClose.addEventListener('click', () => {
                    consoleModal.style.display = 'none';
                });
            }
        }
        
        // Commands modal
        const commandsBtn = document.getElementById('commands-btn');
        const commandsModal = document.getElementById('commands-modal');
        const commandsClose = document.getElementById('commands-modal-close');
        
        if (commandsBtn && commandsModal) {
            console.log('UI: Set up commands button');
            commandsBtn.addEventListener('click', () => {
                commandsModal.style.display = 'flex';
                if (typeof Commands !== 'undefined') {
                    Commands.initializeCommandSystem();
                }
            });
            
            if (commandsClose) {
                commandsClose.addEventListener('click', () => {
                    commandsModal.style.display = 'none';
                });
            }
        }
        
        // For command execution
        const executeCommandBtn = document.getElementById('execute-command-btn');
        if (executeCommandBtn && typeof Commands !== 'undefined') {
            executeCommandBtn.addEventListener('click', Commands.executeDiscordCommand);
        }
        
        const clearHistoryBtn = document.getElementById('clear-history-btn');
        if (clearHistoryBtn && typeof Commands !== 'undefined') {
            clearHistoryBtn.addEventListener('click', Commands.clearCommandHistory);
        }
        
        const commandInput = document.getElementById('command-input');
        if (commandInput && typeof Commands !== 'undefined') {
            commandInput.addEventListener('keypress', function(e) {
                if (e.key === 'Enter') {
                    e.preventDefault();
                    Commands.executeDiscordCommand();
                }
            });
            
            commandInput.addEventListener('input', Commands.handleCommandInput);
            commandInput.addEventListener('keydown', Commands.handleCommandKeyDown);
        }
        
        // Config modal
        const configBtn = document.getElementById('config-btn');
        const configModal = document.getElementById('config-modal');
        const configClose = document.getElementById('config-modal-close');
        
        if (configBtn && configModal) {
            console.log('UI: Set up config button');
            configBtn.addEventListener('click', () => {
                configModal.style.display = 'flex';
                if (typeof ConfigManager !== 'undefined') {
                    ConfigManager.loadConfig();
                }
            });
            
            if (configClose) {
                configClose.addEventListener('click', () => {
                    configModal.style.display = 'none';
                });
            }
        }
        
        // Config save button
        const saveConfigBtn = document.getElementById('save-config-btn');
        if (saveConfigBtn && typeof ConfigManager !== 'undefined') {
            saveConfigBtn.addEventListener('click', ConfigManager.saveConfig);
        }
        
        // Initialize bot profile modal
        const botProfileBtn = document.getElementById('bot-profile-btn');
        const botProfileModal = document.getElementById('bot-profile-modal');
        const botProfileClose = document.getElementById('bot-profile-modal-close');
        
        if (botProfileBtn && botProfileModal) {
            console.log('UI: Set up bot profile button');
            botProfileBtn.addEventListener('click', () => {
                botProfileModal.style.display = 'flex';
                if (typeof BotProfile !== 'undefined' && typeof BotProfile.loadBotProfile === 'function') {
                    BotProfile.loadBotProfile();
                }
            });
            
            if (botProfileClose) {
                botProfileClose.addEventListener('click', () => {
                    botProfileModal.style.display = 'none';
                });
            }
        }
        
        // Bot profile form handlers
        const updateNameBtn = document.getElementById('update-name-btn');
        if (updateNameBtn && typeof BotProfile !== 'undefined') {
            updateNameBtn.addEventListener('click', BotProfile.updateBotName);
        }
        
        const updateAvatarBtn = document.getElementById('update-avatar-btn');
        if (updateAvatarBtn && typeof BotProfile !== 'undefined') {
            updateAvatarBtn.addEventListener('click', BotProfile.updateBotAvatar);
        }
        
        const updateBannerBtn = document.getElementById('update-banner-btn');
        if (updateBannerBtn && typeof BotProfile !== 'undefined') {
            updateBannerBtn.addEventListener('click', BotProfile.updateBotBanner);
        }
        
        // Initialize reboot button
        const rebootBtn = document.getElementById('reboot-btn');
        if (rebootBtn) {
            console.log('UI: Set up reboot button');
            rebootBtn.addEventListener('click', function() {
                if (typeof BotProfile !== 'undefined' && typeof BotProfile.rebootBot === 'function') {
                    BotProfile.rebootBot();
                } else {
                    if (confirm('Are you sure you want to reboot the bot?')) {
                        fetch('/api/reboot', { method: 'POST' })
                            .then(response => {
                                if (response.ok) {
                                    showToast('Bot is rebooting...', true);
                                } else {
                                    throw new Error('Failed to reboot the bot');
                                }
                            })
                            .catch(error => {
                                console.error('Reboot error:', error);
                                showToast('Failed to reboot the bot', false);
                            });
                    }
                }
            });
        }
        
        // Initialize player controls if they exist
        if (typeof Player !== 'undefined') {
            const playButton = document.getElementById('play-button');
            if (playButton) playButton.addEventListener('click', Player.playTrack);
            
            const pauseButton = document.getElementById('pause-button');
            if (pauseButton) pauseButton.addEventListener('click', Player.pauseTrack);
            
            const skipButton = document.getElementById('skip-button');
            if (skipButton) skipButton.addEventListener('click', Player.skipTrack);
            
            const stopButton = document.getElementById('stop-button');
            if (stopButton) stopButton.addEventListener('click', Player.stopTrack);
            
            // Initialize progress bar - handled by Router/View init
            // if (typeof Player.setupProgressBarInteraction === 'function') {
            //    Player.setupProgressBarInteraction();
            // }
        }
        
        // Initialize server dropdown
        initializeServerDropdown();
        
        // Console controls
        const refreshConsoleBtn = document.getElementById('refresh-console-btn');
        if (refreshConsoleBtn && typeof ConsoleManager !== 'undefined') {
            refreshConsoleBtn.addEventListener('click', ConsoleManager.loadConsoleLogs);
        }
        
        const clearConsoleBtn = document.getElementById('clear-console-btn');
        if (clearConsoleBtn) {
            clearConsoleBtn.addEventListener('click', () => {
                const consoleLog = document.getElementById('console-log');
                if (consoleLog) {
                    consoleLog.innerHTML = '<div style="color: #B9BBBE;">Console cleared (only in view, logs still exist on server)</div>';
                }
            });
        }
        
        // Set up click outside modals to close them
        const modals = document.querySelectorAll('.modal');
        window.addEventListener('click', function(event) {
            modals.forEach(modal => {
                if (event.target === modal) {
                    modal.style.display = 'none';
                }
            });
        });
        
        console.log('UI: Initialization complete');
    }

    // Initialize server dropdown with proper event handling
    function initializeServerDropdown() {
        console.log('UI: Initializing server dropdown');
        
        // Retry multiple times with increasing delays to ensure DOM is fully loaded
        const maxRetries = 5;
        let retryCount = 0;
        
        function attemptInitialization() {
            const serverDropdownBtn = document.getElementById('server-dropdown-btn');
            const serverDropdownContent = document.getElementById('server-dropdown-content');
            
            if (!serverDropdownBtn || !serverDropdownContent) {
                retryCount++;
                if (retryCount <= maxRetries) {
                    console.warn(`UI: Server dropdown elements not found, retry ${retryCount}/${maxRetries} in ${retryCount * 100}ms`);
                    // Increase delay with each retry
                    setTimeout(attemptInitialization, retryCount * 100);
                } else {
                    console.error('UI: Server dropdown elements not found after maximum retries');
                }
                return;
            }
            
            console.log('UI: Found server dropdown elements, initializing');
            setupServerDropdown(serverDropdownBtn, serverDropdownContent);
        }
        
        // Start the initialization process
        attemptInitialization();
    }
    
    // Helper function to set up server dropdown
    function setupServerDropdown(btn, content) {
        // Remove any existing click listeners to prevent duplicates
        const newBtn = btn.cloneNode(true);
        btn.parentNode.replaceChild(newBtn, btn);
        
        // Add click listener with improved handling
        newBtn.addEventListener('click', (e) => {
            e.stopPropagation(); // Prevent event from bubbling up
            content.classList.toggle('show');
            console.log('UI: Server dropdown toggled', content.classList.contains('show'));
        });
        
        // Close dropdown when clicking outside
        document.addEventListener('click', (e) => {
            if (!e.target.closest('.server-dropdown') && content.classList.contains('show')) {
                content.classList.remove('show');
            }
        });
    }

    // Update bot status indicator based on voice connection status
    function updateBotStatusIndicator(isConnected) {
        const statusIndicator = document.getElementById('bot-status-indicator');
        
        if (isConnected) {
            statusIndicator.classList.add('active');
            statusIndicator.title = 'Connected to voice channel';
        } else {
            statusIndicator.classList.remove('active');
            statusIndicator.title = 'Not connected to voice channel';
        }
    }

    // Public API
    return {
        formatTime,
        toggleModal,
        showToast,
        getSourceIcon,
        initializeUI,
        updateBotStatusIndicator,
        initializeServerDropdown
    };
})(); 