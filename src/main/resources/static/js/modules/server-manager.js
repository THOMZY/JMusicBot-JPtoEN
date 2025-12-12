/**
 * Server Manager Module - Handles Discord server selection and management
 */

const ServerManager = (function() {
    let servers = [];
    let currentGuildId = null;
    
    // Initialize - this function will be called from the main script
    function initialize() {
        // This function will be used as an entry point if needed
        console.log('Server Manager initialized');
    }
    
    // Load available servers
    async function loadServers() {
        try {
            console.log('Loading servers...');
            
            // Check if DOM elements exist
            const serverDropdownContent = document.getElementById('server-dropdown-content');
            const selectedServerName = document.getElementById('selected-server-name');
            
            if (!serverDropdownContent || !selectedServerName) {
                console.warn('ServerManager: DOM elements not found, retrying in 200ms');
                setTimeout(loadServers, 200);
                return;
            }
            
            const response = await fetch('/api/guilds');
            servers = await response.json();
            console.log('Servers loaded:', servers.length);
            
            // Make servers available globally
            window.servers = servers;
            
            serverDropdownContent.innerHTML = '';
            
            if (servers.length === 0) {
                serverDropdownContent.innerHTML = '<div class="server-item">No servers available</div>';
                selectedServerName.textContent = 'No servers available';
                return;
            }
            
            servers.forEach(guild => {
                const item = document.createElement('div');
                item.className = 'server-item';
                item.setAttribute('data-id', guild.id);
                
                // Create icon element (using default if none provided)
                const iconUrl = guild.iconUrl || 'https://cdn.discordapp.com/embed/avatars/0.png';
                
                item.innerHTML = `
                    <img class="server-icon" src="${iconUrl}" alt="${guild.name}">
                    <span class="server-name">${guild.name}</span>
                `;
                
                // Add debug log to verify the click event
                item.addEventListener('click', (event) => {
                    console.log('Server clicked:', guild.name, guild.id);
                    event.stopPropagation(); // Prevent event bubbling
                    changeServer(guild.id);
                    document.getElementById('server-dropdown-content').classList.remove('show');
                });
                
                serverDropdownContent.appendChild(item);
            });
        } catch (error) {
            console.error('Error loading servers:', error);
            UI.showToast('Error loading servers', false);
            
            const serverDropdownContent = document.getElementById('server-dropdown-content');
            const selectedServerName = document.getElementById('selected-server-name');
            
            if (serverDropdownContent) serverDropdownContent.innerHTML = '<div class="server-item">Error loading servers</div>';
            if (selectedServerName) selectedServerName.textContent = 'Error loading servers';
        }
    }

    // Get currently selected guild
    async function getSelectedGuild() {
        try {
            const response = await fetch('/api/guild/selected');
            const data = await response.json();
            // Update global currentGuildId
            if (data && data.guildId) {
                currentGuildId = data.guildId;
                window.currentGuildId = currentGuildId;
                return data.guildId;
            }
            return null;
        } catch (error) {
            console.error('Error getting selected guild:', error);
            return null;
        }
    }

    // Update server display
    function updateServerDisplay() {
        if (!currentGuildId || !servers || servers.length === 0) {
            document.getElementById('selected-server-name').textContent = 'No server selected';
            return;
        }
        
        const selectedServer = servers.find(server => server.id === currentGuildId);
        if (!selectedServer) {
            document.getElementById('selected-server-name').textContent = 'Server not found';
            return;
        }
        
        document.getElementById('selected-server-name').textContent = selectedServer.name;
        
        // Update icon if available
        const iconElement = document.getElementById('selected-server-icon');
        if (selectedServer.iconUrl) {
            iconElement.src = selectedServer.iconUrl;
        } else {
            iconElement.src = 'https://cdn.discordapp.com/embed/avatars/0.png';
        }
    }

    // Change server
    async function changeServer(guildId) {
        if (!guildId) return;
        
        try {
            console.log('Changing server to:', guildId);
            const response = await fetch(`/api/guild/select/${guildId}`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                }
            });
            
            const data = await response.json();
            
            if (data.success) {
                currentGuildId = guildId;
                window.currentGuildId = currentGuildId;
                updateServerDisplay();
                // Refresh data for the newly selected server
                if (typeof Player !== 'undefined') {
                    Player.fetchStatus();
                    Player.fetchQueue();
                }
                
                // Dispatch server changed event
                const event = new CustomEvent('server:changed', {
                    detail: {
                        guildId: currentGuildId
                    }
                });
                document.dispatchEvent(event);
                
                UI.showToast('Server changed successfully', true);
            } else {
                UI.showToast(data.message || 'Failed to change server', false);
            }
        } catch (error) {
            console.error('Error changing server:', error);
            UI.showToast('Error changing server', false);
        }
    }

    // Get current guild ID
    function getSelectedGuildId() {
        return currentGuildId;
    }

    // Public API
    return {
        initialize,
        loadServers,
        getSelectedGuild,
        getSelectedGuildId,
        updateServerDisplay,
        changeServer
    };
})(); 