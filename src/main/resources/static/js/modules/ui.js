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
        if (!sourceType) return 'fas fa-music';

        const key = sourceType.toLowerCase();
        const icons = {
            'youtube': 'fab fa-youtube',
            'soundcloud': 'fab fa-soundcloud',
            'spotify': 'fab fa-spotify',
            'local': 'fas fa-file-audio',
            'local file': 'fas fa-file-audio',
            'radio': 'fas fa-broadcast-tower',
            'stream': 'fas fa-signal',
            'tiktok': 'fab fa-tiktok',
            'instagram': 'fab fa-instagram',
            'twitter': 'fab fa-twitter',
            'twitch': 'fab fa-twitch',
            'vimeo': 'fab fa-vimeo',
            'bilibili': 'fas fa-tv'
        };

        return icons[key] || 'fas fa-music';
    }

    // Initialize all UI components
    function initializeUI() {
        console.log('UI: Initializing UI components...');

        bindModal('console-btn', 'console-modal', 'console-modal-close', () => {
            if (typeof ConsoleManager !== 'undefined') {
                ConsoleManager.loadConsoleLogs();
            }
        });
        bindModal('commands-btn', 'commands-modal', 'commands-modal-close', () => {
            if (typeof Commands !== 'undefined') {
                Commands.initializeCommandSystem();
            }
        });
        bindModal('config-btn', 'config-modal', 'config-modal-close', () => {
            if (typeof ConfigManager !== 'undefined') {
                ConfigManager.loadConfig();
            }
        });
        bindModal('bot-profile-btn', 'bot-profile-modal', 'bot-profile-modal-close', () => {
            if (typeof BotProfile !== 'undefined' && typeof BotProfile.loadBotProfile === 'function') {
                BotProfile.loadBotProfile();
            }
        });

        bindCommandControls();
        bindConfigControls();
        bindBotProfileControls();
        bindRebootControl();
        bindPlayerControls();
        initializeServerDropdown();
        bindConsoleControls();
        bindModalBackdropClose();

        console.log('UI: Initialization complete');
    }

    function bindModal(buttonId, modalId, closeId, onOpen) {
        const button = document.getElementById(buttonId);
        const modal = document.getElementById(modalId);
        const closeButton = document.getElementById(closeId);
        if (!button || !modal) {
            return;
        }

        button.addEventListener('click', () => {
            modal.style.display = 'flex';
            if (typeof onOpen === 'function') {
                onOpen();
            }
        });

        if (closeButton) {
            closeButton.addEventListener('click', () => {
                modal.style.display = 'none';
            });
        }
    }

    function bindCommandControls() {
        if (typeof Commands === 'undefined') {
            return;
        }

        const executeCommandBtn = document.getElementById('execute-command-btn');
        if (executeCommandBtn) {
            executeCommandBtn.addEventListener('click', Commands.executeDiscordCommand);
        }

        const clearHistoryBtn = document.getElementById('clear-history-btn');
        if (clearHistoryBtn) {
            clearHistoryBtn.addEventListener('click', Commands.clearCommandHistory);
        }

        const commandInput = document.getElementById('command-input');
        if (!commandInput) {
            return;
        }
        commandInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                Commands.executeDiscordCommand();
            }
        });
        commandInput.addEventListener('input', Commands.handleCommandInput);
        commandInput.addEventListener('keydown', Commands.handleCommandKeyDown);
    }

    function bindConfigControls() {
        const saveConfigBtn = document.getElementById('save-config-btn');
        if (saveConfigBtn && typeof ConfigManager !== 'undefined') {
            saveConfigBtn.addEventListener('click', ConfigManager.saveConfig);
        }
    }

    function bindBotProfileControls() {
        if (typeof BotProfile === 'undefined') {
            return;
        }

        const updateNameBtn = document.getElementById('update-name-btn');
        if (updateNameBtn) {
            updateNameBtn.addEventListener('click', BotProfile.updateBotName);
        }

        const updateAvatarBtn = document.getElementById('update-avatar-btn');
        if (updateAvatarBtn) {
            updateAvatarBtn.addEventListener('click', BotProfile.updateBotAvatar);
        }

        const updateBannerBtn = document.getElementById('update-banner-btn');
        if (updateBannerBtn) {
            updateBannerBtn.addEventListener('click', BotProfile.updateBotBanner);
        }
    }

    function bindRebootControl() {
        const rebootBtn = document.getElementById('reboot-btn');
        if (!rebootBtn) {
            return;
        }

        rebootBtn.addEventListener('click', function() {
            if (typeof BotProfile !== 'undefined' && typeof BotProfile.rebootBot === 'function') {
                BotProfile.rebootBot();
                return;
            }
            fallbackReboot();
        });
    }

    function fallbackReboot() {
        if (!confirm('Are you sure you want to reboot the bot?')) {
            return;
        }
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

    function bindPlayerControls() {
        if (typeof Player === 'undefined') {
            return;
        }

        const playButton = document.getElementById('play-button');
        if (playButton) playButton.addEventListener('click', Player.playTrack);

        const pauseButton = document.getElementById('pause-button');
        if (pauseButton) pauseButton.addEventListener('click', Player.pauseTrack);

        const skipButton = document.getElementById('skip-button');
        if (skipButton) skipButton.addEventListener('click', Player.skipTrack);

        const stopButton = document.getElementById('stop-button');
        if (stopButton) stopButton.addEventListener('click', Player.stopTrack);
    }

    function bindConsoleControls() {
        const refreshConsoleBtn = document.getElementById('refresh-console-btn');
        if (refreshConsoleBtn && typeof ConsoleManager !== 'undefined') {
            refreshConsoleBtn.addEventListener('click', ConsoleManager.loadConsoleLogs);
        }

        const clearConsoleBtn = document.getElementById('clear-console-btn');
        if (clearConsoleBtn) {
            clearConsoleBtn.addEventListener('click', () => {
                const consoleLog = document.getElementById('console-log');
                if (consoleLog) {
                    consoleLog.innerHTML = '<div style="color: #B9BBBE;">Console view cleared (logs still exist on server)</div>';
                }
            });
        }

        const deleteLogsBtn = document.getElementById('delete-logs-btn');
        if (deleteLogsBtn) {
            deleteLogsBtn.addEventListener('click', deleteServerLogs);
        }
    }

    async function deleteServerLogs() {
        if (!confirm('Are you sure you want to DELETE ALL LOGS from the server? This action cannot be undone!')) {
            return;
        }
        try {
            const response = await fetch('/api/console/logs', { method: 'DELETE' });
            const result = await response.json();
            if (result.success) {
                alert('Server logs have been successfully deleted!');
                if (typeof ConsoleManager !== 'undefined') {
                    ConsoleManager.loadConsoleLogs();
                }
            } else {
                alert('Failed to delete logs: ' + (result.message || 'Unknown error'));
            }
        } catch (error) {
            console.error('Error deleting logs:', error);
            alert('Error deleting logs: ' + error.message);
        }
    }

    function bindModalBackdropClose() {
        const modals = document.querySelectorAll('.modal');
        window.addEventListener('click', function(event) {
            modals.forEach(modal => {
                if (event.target === modal) {
                    modal.style.display = 'none';
                }
            });
        });
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