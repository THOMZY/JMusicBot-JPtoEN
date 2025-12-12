/**
 * Bot Profile Module - Handles bot profile (name, avatar, status)
 */

const BotProfile = (function() {
    // Fetch bot information
    async function fetchBotInfo(retryCount = 0) {
        try {
            console.log('BotProfile: Fetching bot information...');
            
            // Check DOM elements first
            const botNameElement = document.getElementById('bot-name');
            const botAvatarElement = document.getElementById('bot-avatar');
            const botProfileBtn = document.getElementById('bot-profile-btn');
            
            if (!botNameElement || !botAvatarElement || !botProfileBtn) {
                if (retryCount < 10) {
                    console.warn(`BotProfile: DOM elements not found, retrying in 200ms (attempt ${retryCount + 1})`);
                    setTimeout(() => fetchBotInfo(retryCount + 1), 200);
                } else {
                    console.error('BotProfile: DOM elements not found after maximum retries');
                }
                return;
            }

            const response = await fetch('/api/bot/info');
            const data = await response.json();
            
            if (data.success) {
                // Update bot name and avatar in the header
                botNameElement.textContent = data.name || 'JMusicBot';
                
                if (data.avatarUrl) {
                    botAvatarElement.src = data.avatarUrl;
                } else {
                    botAvatarElement.src = 'https://cdn.discordapp.com/embed/avatars/0.png';
                }
                
                // Make the bot profile button display a tooltip on hover
                botProfileBtn.title = `${data.name} - Bot ID: ${data.id}`;
                console.log('BotProfile: Information updated successfully');
            } else {
                console.warn('BotProfile: API returned success=false');
            }
        } catch (error) {
            console.error('BotProfile: Error fetching bot info:', error);
            // Set fallback values with safe element checks
            const botNameElement = document.getElementById('bot-name');
            const botAvatarElement = document.getElementById('bot-avatar');
            
            if (botNameElement) botNameElement.textContent = 'JMusicBot';
            if (botAvatarElement) botAvatarElement.src = 'https://cdn.discordapp.com/embed/avatars/0.png';
        }
    }

    // Load bot profile for editing
    async function loadBotProfile() {
        try {
            const response = await fetch('/api/bot/info');
            const data = await response.json();
            
            if (data.success) {
                document.getElementById('profile-avatar').src = data.avatarUrl || 'https://cdn.discordapp.com/embed/avatars/0.png';
                document.getElementById('profile-name').textContent = data.name || 'JMusicBot';
                
                // Pre-fill the input fields with current values for better UX
                document.getElementById('bot-name-input').value = data.name || '';
                document.getElementById('bot-avatar-input').value = data.avatarUrl || '';
                
                // Handle banner elements
                const profileBanner = document.getElementById('profile-banner');
                const profileBannerContainer = document.querySelector('.profile-banner-container');
                
                // Add banner URL if available
                if (document.getElementById('bot-banner-input')) {
                    document.getElementById('bot-banner-input').value = data.bannerUrl || '';
                }
                
                // Display the banner if available
                if (data.bannerUrl) {
                    profileBanner.src = data.bannerUrl;
                    profileBannerContainer.style.display = 'block';
                } else {
                    // If no banner is set, show a placeholder with darkened background
                    profileBannerContainer.style.display = 'block';
                    profileBanner.src = '';
                    profileBanner.alt = 'No banner set';
                    profileBannerContainer.style.backgroundColor = 'var(--darkest-bg)';
                }
            }
            
            // Reset and hide the result message
            const profileResult = document.getElementById('profile-result');
            profileResult.textContent = '';
            profileResult.style.display = 'none';
            
        } catch (error) {
            console.error('Error loading bot profile:', error);
            document.getElementById('profile-avatar').src = 'https://cdn.discordapp.com/embed/avatars/0.png';
            document.getElementById('profile-name').textContent = 'JMusicBot';
        }
    }

    // Update bot name
    async function updateBotName() {
        const newName = document.getElementById('bot-name-input').value.trim();
        const profileResult = document.getElementById('profile-result');
        profileResult.style.display = 'block';
        
        if (!newName) {
            profileResult.textContent = 'Please enter a name';
            profileResult.style.backgroundColor = 'var(--error-color)';
            profileResult.style.color = 'white';
            return;
        }
        
        try {
            const formData = new FormData();
            formData.append('name', newName);
            
            const response = await fetch('/api/bot/setname', {
                method: 'POST',
                body: formData
            });
            
            const data = await response.json();
            
            if (data.success) {
                document.getElementById('profile-name').textContent = newName;
                document.getElementById('bot-name').textContent = newName; // Update in header too
                profileResult.textContent = 'Bot name updated successfully';
                profileResult.style.backgroundColor = 'var(--success-color)';
                profileResult.style.color = 'white';
                
                // Refresh bot info in case any other info changed
                fetchBotInfo();
            } else {
                profileResult.textContent = data.message || 'Failed to update bot name';
                profileResult.style.backgroundColor = 'var(--error-color)';
                profileResult.style.color = 'white';
            }
        } catch (error) {
            console.error('Error updating bot name:', error);
            profileResult.textContent = 'Error updating bot name: ' + error.message;
            profileResult.style.backgroundColor = 'var(--error-color)';
            profileResult.style.color = 'white';
        }
    }

    // Update bot avatar
    async function updateBotAvatar() {
        const newAvatarUrl = document.getElementById('bot-avatar-input').value.trim();
        const profileResult = document.getElementById('profile-result');
        profileResult.style.display = 'block';
        
        if (!newAvatarUrl) {
            profileResult.textContent = 'Please enter an avatar URL';
            profileResult.style.backgroundColor = 'var(--error-color)';
            profileResult.style.color = 'white';
            return;
        }
        
        try {
            const formData = new FormData();
            formData.append('url', newAvatarUrl);
            
            const response = await fetch('/api/bot/setavatar', {
                method: 'POST',
                body: formData
            });
            
            const data = await response.json();
            
            if (data.success) {
                document.getElementById('profile-avatar').src = newAvatarUrl;
                document.getElementById('bot-avatar').src = newAvatarUrl; // Update in header too
                profileResult.textContent = 'Bot avatar updated successfully';
                profileResult.style.backgroundColor = 'var(--success-color)';
                profileResult.style.color = 'white';
                
                // Refresh bot info in case any other info changed
                fetchBotInfo();
            } else {
                profileResult.textContent = data.message || 'Failed to update bot avatar';
                profileResult.style.backgroundColor = 'var(--error-color)';
                profileResult.style.color = 'white';
            }
        } catch (error) {
            console.error('Error updating bot avatar:', error);
            profileResult.textContent = 'Error updating bot avatar: ' + error.message;
            profileResult.style.backgroundColor = 'var(--error-color)';
            profileResult.style.color = 'white';
        }
    }

    // Update bot banner
    async function updateBotBanner() {
        const newBannerUrl = document.getElementById('bot-banner-input').value.trim();
        const profileResult = document.getElementById('profile-result');
        profileResult.style.display = 'block';
        
        if (!newBannerUrl) {
            profileResult.textContent = 'Please enter a banner URL';
            profileResult.style.backgroundColor = 'var(--error-color)';
            profileResult.style.color = 'white';
            return;
        }
        
        try {
            const formData = new FormData();
            formData.append('url', newBannerUrl);
            
            const response = await fetch('/api/bot/setbanner', {
                method: 'POST',
                body: formData
            });
            
            const data = await response.json();
            
            if (data.success) {
                // Update the banner preview
                const profileBanner = document.getElementById('profile-banner');
                if (profileBanner) {
                    profileBanner.src = newBannerUrl;
                }
                
                // Display success message
                profileResult.textContent = 'Bot banner updated successfully';
                profileResult.style.backgroundColor = 'var(--success-color)';
                profileResult.style.color = 'white';
                
                // Refresh bot info in case any other info changed
                fetchBotInfo();
            } else {
                profileResult.textContent = data.message || 'Failed to update bot banner';
                profileResult.style.backgroundColor = 'var(--error-color)';
                profileResult.style.color = 'white';
            }
        } catch (error) {
            console.error('Error updating bot banner:', error);
            profileResult.textContent = 'Error updating bot banner: ' + error.message;
            profileResult.style.backgroundColor = 'var(--error-color)';
            profileResult.style.color = 'white';
        }
    }

    // Reboot the bot
    async function rebootBot() {
        if (!confirm('Are you sure you want to reboot the bot? This will interrupt any current playback.')) {
            return;
        }
        
        try {
            UI.showToast('Attempting to reboot the bot...', true);
            
            const response = await fetch('/api/reboot', {
                method: 'POST'
            });
            
            const data = await response.json();
            
            if (data.success) {
                UI.showToast('Bot is rebooting. It should be back online shortly.', true);
                
                // Disable all controls during reboot
                document.getElementById('play-button').disabled = true;
                document.getElementById('pause-button').disabled = true;
                document.getElementById('skip-button').disabled = true;
                document.getElementById('stop-button').disabled = true;
                document.getElementById('reboot-btn').disabled = true;
                
                // Status update
                document.getElementById('status-message').textContent = 'Rebooting...';
                
                // Check if bot is back online after a delay
                setTimeout(checkBotStatus, 5000);
            } else {
                UI.showToast(data.message || 'Failed to reboot the bot', false);
            }
        } catch (error) {
            console.error('Error rebooting the bot:', error);
            UI.showToast('Error rebooting the bot: ' + error.message, false);
        }
    }

    // Check if bot is back online
    async function checkBotStatus() {
        try {
            const response = await fetch('/api/status');
            
            if (response.ok) {
                UI.showToast('Bot is back online!', true);
                document.getElementById('reboot-btn').disabled = false;
                
                // Refresh everything
                Player.fetchStatus();
                Player.fetchQueue();
                ServerManager.loadServers();
            } else {
                // Try again in a few seconds
                setTimeout(checkBotStatus, 5000);
            }
        } catch (error) {
            // Bot is still restarting, try again
            setTimeout(checkBotStatus, 5000);
        }
    }

    // Public API
    return {
        fetchBotInfo,
        loadBotProfile,
        updateBotName,
        updateBotAvatar,
        updateBotBanner,
        rebootBot,
        checkBotStatus
    };
})(); 