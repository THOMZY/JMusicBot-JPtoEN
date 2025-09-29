/**
 * History Module for JMusicBot Web Panel
 * Handles retrieval and display of music playback history
 */

const HistoryModule = (() => {
    // Private variables
    let historyData = [];
    let totalRecords = 0;
    let currentPage = 1;
    let recordsPerPage = 20;
    let searchQuery = '';
    let activeFilters = {
        type: 'all', // all, spotify, youtube, radio, local, stream
        timeRange: 'all', // all, today, week, month
        requester: 'all', // all, or specific requester name
        guildId: 'all' // all, or specific guild ID
    };
    let currentGuildId = null;
    let servers = [];
    let uniqueRequesters = []; // Store unique requester names

    // DOM Elements
    const historyElements = {
        historyContainer: null,
        historyList: null,
        pageInfo: null,
        pagination: null,
        searchInput: null,
        filterButtons: null,
        serverDropdownBtn: null,
        serverDropdownContent: null,
        selectedServerIcon: null,
        selectedServerName: null,
        requesterSelect: null // Add requester select element
    };

    /**
     * Initialize the history module
     */
    const init = () => {
        console.log('Initializing history module...');
        
        // Initialize Bot Profile to update the header
        if (typeof BotProfile !== 'undefined') {
            console.log('Loading bot profile information...');
            BotProfile.fetchBotInfo();
        } else {
            console.error('BotProfile module not found');
        }
        
        // Cache DOM elements - Use a short delay to ensure the component is fully loaded
        setTimeout(() => {
            cacheElements();
            
            // Set up event listeners
            setupEventListeners();
            
            // Initialize Server Manager
            if (typeof ServerManager !== 'undefined') {
                console.log('Initializing Server Manager...');
                if (typeof ServerManager.initialize === 'function') {
                    ServerManager.initialize();
                }
                
                // Make sure server dropdown is properly initialized
                if (typeof UI !== 'undefined' && typeof UI.initializeServerDropdown === 'function') {
                    console.log('Initializing server dropdown...');
                    UI.initializeServerDropdown();
                }
                
                // Load servers first, then load history
                loadServerList().then(() => {
                    // Try to get the selected guild from the server
                    getSelectedGuild().then(guildId => {
                        if (guildId) {
                            currentGuildId = guildId;
                            activeFilters.guildId = guildId;
                            
                            // Use ServerManager for display if available
                            if (typeof ServerManager !== 'undefined' && typeof ServerManager.updateServerDisplay === 'function') {
                                ServerManager.updateServerDisplay();
                            } else {
                                updateServerDisplay();
                            }
                        }
                        
                        // Load requester list for the filter first
                        loadRequesterList().then(() => {
                            // Load history with the selected guild
                            loadHistory();
                        });
                    });
                });
            } else {
                console.error('ServerManager module not loaded');
                // Load requester list for the filter first
                loadRequesterList().then(() => {
                    // Then load history
                    loadHistory();
                });
            }
        }, 200); // 200ms delay to ensure DOM elements are ready
    };

    /**
     * Cache DOM elements for later use
     */
    const cacheElements = () => {
        historyElements.historyContainer = document.getElementById('history-container');
        if (!historyElements.historyContainer) {
            console.error('History container not found! Make sure the history-content component is loaded correctly.');
            return false;
        }
        
        historyElements.historyList = document.getElementById('history-list');
        historyElements.pageInfo = document.getElementById('history-page-info');
        historyElements.pagination = document.getElementById('history-pagination');
        historyElements.searchInput = document.getElementById('history-search-input');
        historyElements.filterButtons = document.querySelectorAll('.history-filter-btn');
        historyElements.requesterSelect = document.getElementById('requester-filter-select');
        
        // Server selection elements
        historyElements.serverDropdownBtn = document.getElementById('server-dropdown-btn');
        historyElements.serverDropdownContent = document.getElementById('server-dropdown-content');
        historyElements.selectedServerIcon = document.getElementById('selected-server-icon');
        historyElements.selectedServerName = document.getElementById('selected-server-name');
        
        // Log if any important elements are missing
        if (!historyElements.historyList) console.error('History list element not found!');
        if (!historyElements.pageInfo) console.error('Page info element not found!');
        if (!historyElements.pagination) console.error('Pagination element not found!');
        if (!historyElements.searchInput) console.error('Search input element not found!');
        if (!historyElements.requesterSelect) console.error('Requester select element not found!');
        
        return true;
    };

    /**
     * Set up event listeners
     */
    const setupEventListeners = () => {
        // Search input
        historyElements.searchInput.addEventListener('keyup', (e) => {
            if (e.key === 'Enter') {
                searchHistory();
            }
        });
        
        document.getElementById('history-search-btn').addEventListener('click', searchHistory);
        
        // Filter buttons
        historyElements.filterButtons.forEach(btn => {
            btn.addEventListener('click', function() {
                const filterType = this.getAttribute('data-filter');
                const filterValue = this.getAttribute('data-value');
                
                // If this is for requester filter and it's the "Anyone" button
                if (filterType === 'requester' && filterValue === 'all') {
                    // Reset the requester filter
                    activeFilters.requester = 'all';
                    historyElements.requesterSelect.value = '';
                }
                
                // Update active filters
                activeFilters[filterType] = filterValue;
                
                // Update UI
                updateFilterUI();
                
                // Reload history with new filters
                currentPage = 1;
                loadHistory();
            });
        });
        
        // Requester select change event
        historyElements.requesterSelect.addEventListener('change', function() {
            if (this.value) {
                activeFilters.requester = this.value;
                
                // Update filter buttons (deactivate "Anyone" button)
                document.querySelectorAll('.history-filter-btn[data-filter="requester"]').forEach(btn => {
                    btn.classList.remove('active');
                });
                
                // Reload history with new requester filter
                currentPage = 1;
                loadHistory();
            }
        });
        
        // Server dropdown toggle
        historyElements.serverDropdownBtn.addEventListener('click', function() {
            historyElements.serverDropdownContent.classList.toggle('show');
        });
        
        // Close dropdown when clicking outside
        document.addEventListener('click', function(event) {
            if (!event.target.matches('.server-select-btn') && 
                !event.target.closest('.server-select-btn') && 
                historyElements.serverDropdownContent.classList.contains('show')) {
                historyElements.serverDropdownContent.classList.remove('show');
            }
        });
        
        // Pagination events will be added dynamically
    };

    /**
     * Load the server list from the API
     */
    const loadServerList = async () => {
        try {
            const response = await fetch('/api/guilds');
            const data = await response.json();
            
            if (data && Array.isArray(data)) {
                servers = data;
                
                // Clear the dropdown
                historyElements.serverDropdownContent.innerHTML = '';
                
                // Add an "All Servers" option
                const allServersItem = document.createElement('div');
                allServersItem.className = 'server-item';
                allServersItem.innerHTML = `
                    <img class="server-icon" src="https://cdn.discordapp.com/embed/avatars/0.png" alt="All Servers">
                    <span>All Servers</span>
                `;
                allServersItem.addEventListener('click', function() {
                    selectGuild('all');
                });
                historyElements.serverDropdownContent.appendChild(allServersItem);
                
                // Add server items
                servers.forEach(server => {
                    const serverItem = document.createElement('div');
                    serverItem.className = 'server-item';
                    serverItem.innerHTML = `
                        <img class="server-icon" src="${server.iconUrl || 'https://cdn.discordapp.com/embed/avatars/0.png'}" alt="${server.name}">
                        <span>${server.name}</span>
                    `;
                    serverItem.addEventListener('click', function() {
                        selectGuild(server.id);
                    });
                    historyElements.serverDropdownContent.appendChild(serverItem);
                });
            }
        } catch (error) {
            console.error('Error loading servers:', error);
        }
    };

    /**
     * Get the currently selected guild from the server
     */
    const getSelectedGuild = async () => {
        try {
            const response = await fetch('/api/guild/selected');
            const data = await response.json();
            
            if (data && data.guildId) {
                return data.guildId;
            }
        } catch (error) {
            console.error('Error getting selected guild:', error);
        }
        
        return null;
    };

    /**
     * Load the list of all unique requesters from the API
     */
    const loadRequesterList = async () => {
        try {
            const response = await fetch('/api/history/requesters');
            const data = await response.json();
            
            if (data.success && Array.isArray(data.requesters)) {
                // Update the dropdown with all available requesters
                updateRequesterDropdown(data.requesters);
            } else {
                console.error('Failed to load requesters:', data.message || 'Unknown error');
            }
        } catch (error) {
            console.error('Error loading requester list:', error);
        }
        
        return true; // Always return true to continue initialization
    };

    /**
     * Update the requester dropdown with the provided list of requesters
     */
    const updateRequesterDropdown = (requesters) => {
        // Sort alphabetically
        const sortedRequesters = [...requesters].sort();
        uniqueRequesters = sortedRequesters;
        
        // Update the dropdown
        const select = historyElements.requesterSelect;
        
        // Save current selection
        const currentSelection = select.value;
        
        // Clear existing options except the first one
        select.innerHTML = '<option value="">Select Requester</option>';
        
        // Add requester options
        sortedRequesters.forEach(requester => {
            const option = document.createElement('option');
            option.value = requester;
            option.textContent = requester;
            select.appendChild(option);
        });
        
        // Restore selection if it still exists
        if (currentSelection && sortedRequesters.includes(currentSelection)) {
            select.value = currentSelection;
        }
    };

    /**
     * Select a guild and update the history
     * @param {string} guildId - The guild ID to select
     */
    const selectGuild = async (guildId) => {
        // Close the dropdown
        historyElements.serverDropdownContent.classList.remove('show');
        
        // If selecting 'all', we don't need to call the API
        if (guildId === 'all') {
            currentGuildId = null;
            activeFilters.guildId = 'all';
            
            // Use ServerManager for display if available
            if (typeof ServerManager !== 'undefined' && typeof ServerManager.changeServer === 'function') {
                window.currentGuildId = null; // Update global variable
                ServerManager.updateServerDisplay();
            } else {
                updateServerDisplay();
            }
            
            // Reset all filters
            activeFilters.type = 'all';
            activeFilters.requester = 'all';
            activeFilters.timeRange = 'all';
            historyElements.requesterSelect.value = '';
            updateFilterUI();
            
            // Reset to first page
            currentPage = 1;
            
            // Reload requester list for the new server selection
            await loadRequesterList();
            
            // Then load history
            loadHistory();
            return;
        }
        
        try {
            // Use ServerManager if available for consistent server selection across pages
            if (typeof ServerManager !== 'undefined' && typeof ServerManager.changeServer === 'function') {
                console.log('Using ServerManager to change server to:', guildId);
                
                // Call ServerManager to change the server - this will also update the display
                await ServerManager.changeServer(guildId);
                
                // Update our local variables
                currentGuildId = guildId;
                activeFilters.guildId = guildId;
                
                // Reset all filters
                activeFilters.type = 'all';
                activeFilters.requester = 'all';
                activeFilters.timeRange = 'all';
                historyElements.requesterSelect.value = '';
                updateFilterUI();
                
                // Reset to first page
                currentPage = 1;
                
                // Reload requester list for the new server selection
                await loadRequesterList();
                
                // Then load history
                loadHistory();
            } else {
                // Fallback to direct API call
                console.log('Using direct API call to change server to:', guildId);
                const response = await fetch(`/api/guild/select/${guildId}`, {
                    method: 'POST'
                });
                
                const data = await response.json();
                
                if (data.success) {
                    currentGuildId = guildId;
                    activeFilters.guildId = guildId;
                    updateServerDisplay();
                    
                    // Reset all filters
                    activeFilters.type = 'all';
                    activeFilters.requester = 'all';
                    activeFilters.timeRange = 'all';
                    historyElements.requesterSelect.value = '';
                    updateFilterUI();
                    
                    // Reset to first page
                    currentPage = 1;
                    
                    // Reload requester list for the new server selection
                    await loadRequesterList();
                    
                    // Then load history
                    loadHistory();
                } else {
                    console.error('Failed to select guild:', data.message);
                }
            }
        } catch (error) {
            console.error('Error selecting guild:', error);
        }
    };

    /**
     * Update the server display with the currently selected server
     */
    const updateServerDisplay = () => {
        if (currentGuildId && currentGuildId !== 'all') {
            const selectedServer = servers.find(server => server.id === currentGuildId);
            
            if (selectedServer) {
                historyElements.selectedServerName.textContent = selectedServer.name;
                historyElements.selectedServerIcon.src = selectedServer.iconUrl || 'https://cdn.discordapp.com/embed/avatars/0.png';
                return;
            }
        }
        
        // If no server is selected or 'all' is selected
        historyElements.selectedServerName.textContent = 'All Servers';
        historyElements.selectedServerIcon.src = 'https://cdn.discordapp.com/embed/avatars/0.png';
    };

    /**
     * Update the filter buttons UI based on active filters
     */
    const updateFilterUI = () => {
        // Remove active class from all filter buttons
        historyElements.filterButtons.forEach(btn => {
            btn.classList.remove('active');
        });
        
        // Add active class to active filter buttons
        historyElements.filterButtons.forEach(btn => {
            const filterType = btn.getAttribute('data-filter');
            const filterValue = btn.getAttribute('data-value');
            
            if (activeFilters[filterType] === filterValue) {
                btn.classList.add('active');
            }
        });
    };

    /**
     * Load history data from the API
     */
    const loadHistory = () => {
        // Show loading state
        historyElements.historyList.innerHTML = '<div class="history-loading">Loading history...</div>';
        
        // Calculate offset
        const offset = (currentPage - 1) * recordsPerPage;
        
        // Determine API endpoint based on whether we're searching or not
        let endpoint = '/api/history';
        let params = `?limit=${recordsPerPage}&offset=${offset}`;
        
        // Add guild filter if specified
        if (activeFilters.guildId !== 'all' && currentGuildId) {
            params += `&guildId=${currentGuildId}`;
        }
        
        // Add type filter if specified
        if (activeFilters.type !== 'all') {
            params += `&type=${activeFilters.type}`;
        }
        
        // Add requester filter if specified
        if (activeFilters.requester !== 'all') {
            params += `&requester=${encodeURIComponent(activeFilters.requester)}`;
        }
        
        // Add time range filter if specified
        if (activeFilters.timeRange !== 'all') {
            params += `&timeRange=${encodeURIComponent(activeFilters.timeRange)}`;
        }
        
        if (searchQuery) {
            endpoint = '/api/history/search';
            params = `?query=${encodeURIComponent(searchQuery)}&limit=${recordsPerPage}&offset=${offset}`;
            
            // Add guild filter to search query if specified
            if (activeFilters.guildId !== 'all' && currentGuildId) {
                params += `&guildId=${currentGuildId}`;
            }
            
            // Also add type filter to search if specified
            if (activeFilters.type !== 'all') {
                params += `&type=${activeFilters.type}`;
            }
            
            // Add requester filter if specified
            if (activeFilters.requester !== 'all') {
                params += `&requester=${encodeURIComponent(activeFilters.requester)}`;
            }
            
            // Add time range filter if specified
            if (activeFilters.timeRange !== 'all') {
                params += `&timeRange=${encodeURIComponent(activeFilters.timeRange)}`;
            }
        }
        
        // Fetch history data
        fetch(endpoint + params)
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    historyData = data.history;
                    totalRecords = data.total;
                    
                    renderHistory();
                    renderPagination();
                } else {
                    historyElements.historyList.innerHTML = `<div class="history-error">${data.message || 'Error loading history'}</div>`;
                }
            })
            .catch(error => {
                console.error('Error fetching history:', error);
                historyElements.historyList.innerHTML = '<div class="history-error">Failed to load history. Please try again later.</div>';
            });
    };

    /**
     * Search through the history
     */
    const searchHistory = () => {
        searchQuery = historyElements.searchInput.value.trim();
        currentPage = 1;
        loadHistory();
    };

    /**
     * Render history items in the list
     */
    const renderHistory = () => {
        if (!historyData || historyData.length === 0) {
            historyElements.historyList.innerHTML = '<div class="history-empty">No history records found</div>';
            return;
        }
        
        // No need to filter data client-side anymore as filters are applied server-side
        let filteredData = historyData;
        
        // Clear the list
        historyElements.historyList.innerHTML = '';
        
        // Update page info
        const start = (currentPage - 1) * recordsPerPage + 1;
        const end = Math.min(start + filteredData.length - 1, totalRecords);
        historyElements.pageInfo.textContent = `Showing ${start}-${end} of ${totalRecords} records`;
        
        // Create a document fragment for better performance
        const fragment = document.createDocumentFragment();
        
        // Add history items
        filteredData.forEach(record => {
            const historyItem = document.createElement('div');
            historyItem.className = 'history-item';
            
            // Determine thumbnail image source
            let thumbnailSrc = 'https://cdn.discordapp.com/embed/avatars/0.png'; // Default
            let sourceType = 'Unknown';
            let sourceIcon = '';
            let hasStationLogo = false;
            let stationLogoUrl = '';
            let additionalMetadata = [];

            // Check for Gensokyo Radio first (higher priority than other types)
            if (record.gensokyoTitle) {
                // Use album art as thumbnail
                if (record.gensokyoAlbumArtUrl) {
                    thumbnailSrc = record.gensokyoAlbumArtUrl;
                } else {
                    thumbnailSrc = 'https://stream.gensokyoradio.net/images/logo.png'; // Default Gensokyo Radio logo
                }
                
                sourceType = 'Gensokyo Radio';
                sourceIcon = '<i class="fas fa-music source-icon-gensokyoradio"></i>';
                hasStationLogo = true;
                stationLogoUrl = 'https://stream.gensokyoradio.net/images/logo.png';
                
                // Add additional metadata for Gensokyo Radio
                if (record.gensokyoAlbum) {
                    additionalMetadata.push({
                        icon: 'fas fa-compact-disc',
                        text: record.gensokyoAlbum
                    });
                }
                if (record.gensokyoCircle) {
                    additionalMetadata.push({
                        icon: 'fas fa-users',
                        text: record.gensokyoCircle
                    });
                }
                if (record.gensokyoYear) {
                    additionalMetadata.push({
                        icon: 'fas fa-calendar-alt',
                        text: record.gensokyoYear
                    });
                }
            }
            // Check for Spotify
            else if (record.spotifyAlbumImageUrl) {
                thumbnailSrc = record.spotifyAlbumImageUrl;
                sourceType = 'Spotify';
                sourceIcon = '<i class="fab fa-spotify source-icon-spotify"></i>';
                
                // Add additional metadata for Spotify
                if (record.spotifyAlbumName) {
                    additionalMetadata.push({
                        icon: 'fas fa-compact-disc',
                        text: record.spotifyAlbumName
                    });
                }
                if (record.spotifyReleaseYear) {
                    additionalMetadata.push({
                        icon: 'fas fa-calendar-alt',
                        text: record.spotifyReleaseYear
                    });
                }
            } 
            // Check for YouTube
            else if (record.youtubeVideoId) {
                thumbnailSrc = `https://img.youtube.com/vi/${record.youtubeVideoId}/mqdefault.jpg`;
                sourceType = 'YouTube';
                sourceIcon = '<i class="fab fa-youtube source-icon-youtube"></i>';
            } 
            // Check for Radio
            else if (record.radioLogoUrl) {
                // For radio tracks, use the song image as the main thumbnail if available
                if (record.radioSongImageUrl) {
                    thumbnailSrc = record.radioSongImageUrl;
                } else {
                    thumbnailSrc = record.radioLogoUrl;
                }
                
                // Clean up title by removing "| RADIO NAME" if present
                if (record.title) {
                    const pipeIndex = record.title.lastIndexOf(" | ");
                    if (pipeIndex > 0) {
                        record.title = record.title.substring(0, pipeIndex).trim();
                    }
                }
                
                sourceType = 'Radio';
                sourceIcon = '<i class="fas fa-broadcast-tower source-icon-radio"></i>';
                hasStationLogo = true;
                stationLogoUrl = record.radioLogoUrl;
            } 
            // Check for SoundCloud
            else if (record.url && record.url.includes('soundcloud.com') || record.soundCloudArtworkUrl) {
                if (record.soundCloudArtworkUrl) {
                    thumbnailSrc = record.soundCloudArtworkUrl;
                } else {
                    thumbnailSrc = 'https://developers.soundcloud.com/assets/logo_big_white-65c2b096da68dd533db18b5b56e1e432.png';
                }
                sourceType = 'SoundCloud';
                sourceIcon = '<i class="fab fa-soundcloud source-icon-soundcloud"></i>';
            }
            // Check for Local files
            else if (record.localAlbum || record.localGenre || record.localYear || record.localArtworkHash) {
                sourceType = 'Local File';
                sourceIcon = '<i class="fas fa-file-audio source-icon-local"></i>';
                if (record.localArtworkHash) {
                    thumbnailSrc = `/${record.localArtworkHash}`; // Adjusted path to match how it's saved
                } else {
                    thumbnailSrc = 'https://cdn-icons-png.flaticon.com/512/4725/4725478.png'; // Default local file icon
                }

                // Add additional metadata for Local Files
                if (record.localAlbum && record.localAlbum !== "Unknown Album") {
                    additionalMetadata.push({
                        icon: 'fas fa-compact-disc',
                        text: record.localAlbum
                    });
                }
                if (record.localGenre && record.localGenre !== "Unknown Genre") {
                    additionalMetadata.push({
                        icon: 'fas fa-tag',
                        text: record.localGenre
                    });
                }
                if (record.localYear && record.localYear !== "") {
                    additionalMetadata.push({
                        icon: 'fas fa-calendar-alt',
                        text: record.localYear
                    });
                }
            }
            
            // Build additional metadata HTML if any
            let additionalMetadataHtml = '';
            if (additionalMetadata.length > 0) {
                additionalMetadataHtml = '<div class="additional-metadata">';
                additionalMetadata.forEach(meta => {
                    additionalMetadataHtml += `
                        <div class="metadata-item">
                            <i class="${meta.icon}"></i> ${meta.text}
                        </div>
                    `;
                });
                additionalMetadataHtml += '</div><div class="metadata-separator"></div>';
            }
            
            // Requester info with avatar if available
            const requesterHtml = record.requesterName ? `
                <div class="metadata-item requester-item" data-requester="${record.requesterName}">
                    <i class="fas fa-user-music"></i> 
                    ${record.requesterAvatar ? 
                        `<img src="${record.requesterAvatar}" alt="${record.requesterName}" class="requester-avatar">` : 
                        '<i class="fas fa-user"></i>'} 
                    ${record.requesterName}
                </div>
            ` : '';
            
            // Build HTML
            historyItem.innerHTML = `
                <div class="history-item-thumbnail">
                    <img src="${thumbnailSrc}" alt="${record.title}" onerror="this.src='https://cdn.discordapp.com/embed/avatars/0.png'">
                </div>
                <div class="history-item-info">
                    <div class="history-item-title">${record.title || 'Unknown Title'}</div>
                    <div class="history-item-artist">${record.artist || 'Unknown Artist'}</div>
                    ${additionalMetadataHtml}
                    <div class="history-item-metadata">
                        ${(sourceType !== 'Radio' && sourceType !== 'Gensokyo Radio') ? `
                        <div class="metadata-item">
                            <i class="fas fa-clock"></i> ${record.formattedDuration}
                        </div>` : ''}
                        <div class="metadata-item">
                            ${sourceIcon} ${sourceType}
                        </div>
                        ${requesterHtml}
                        <div class="metadata-item">
                            <i class="fas fa-server"></i> ${record.guildName}
                        </div>
                        <div class="metadata-item">
                            <i class="fas fa-calendar"></i> ${record.formattedPlayedAt}
                        </div>
                    </div>
                </div>
                ${hasStationLogo ? `
                <div class="station-logo-container">
                    <img src="${stationLogoUrl}" alt="Station Logo" class="station-logo">
                </div>` : ''}
                <div class="history-item-actions">
                    <button class="history-action-btn play-again-btn" data-url="${record.spotifyTrackId ? `https://open.spotify.com/track/${record.spotifyTrackId}` : record.url}" data-title="${record.title || ''}" data-artist="${record.artist || ''}" data-source-type="${sourceType}" title="Add to queue">
                        <i class="fas fa-plus"></i>
                    </button>
                    <button class="history-action-btn open-url-btn" data-url="${record.url}" title="Open URL">
                        <i class="fas fa-external-link-alt"></i>
                    </button>
                </div>
            `;
            
            // Add filter by requester event
            const requesterItem = historyItem.querySelector('.requester-item');
            if (requesterItem) {
                requesterItem.addEventListener('click', function() {
                    const requesterName = this.getAttribute('data-requester');
                    if (requesterName) {
                        // Update filter
                        activeFilters.requester = requesterName;
                        
                        // Update UI
                        document.querySelectorAll('.history-filter-btn[data-filter="requester"]').forEach(btn => {
                            btn.classList.remove('active');
                        });
                        
                        // Update select dropdown
                        historyElements.requesterSelect.value = requesterName;
                        
                        // Reload history
                        currentPage = 1;
                        loadHistory();
                    }
                });
            }
            
            // Add play again event listener
            historyItem.querySelector('.play-again-btn').addEventListener('click', function() {
                const url = this.getAttribute('data-url');
                const title = this.getAttribute('data-title');
                const artist = this.getAttribute('data-artist');
                const sourceType = this.getAttribute('data-source-type');
                
                // Handle Radio and Gensokyo Radio tracks differently
                if (sourceType === 'Radio' || sourceType === 'Gensokyo Radio') {
                    addToQueueViaSearch(title, artist, sourceType);
                } else {
                    addToQueue(url);
                }
            });
            
            // Add open URL event listener
            historyItem.querySelector('.open-url-btn').addEventListener('click', function() {
                const url = this.getAttribute('data-url');
                window.open(url, '_blank');
            });
            
            fragment.appendChild(historyItem);
        });
        
        // Append all at once
        historyElements.historyList.appendChild(fragment);
        
        // Scroll to top of the content wrapper after rendering
        scrollToTop();
    };

    /**
     * Scroll to the top of the history content
     */
    const scrollToTop = () => {
        const contentWrapper = document.querySelector('.history-content-wrapper');
        if (contentWrapper) {
            contentWrapper.scrollTop = 0;
        }
    };

    /**
     * Add a track to the queue
     * @param {string} url - The track URL
     */
    const addToQueue = (url) => {
        if (!url) return;
        
        // Check if this is a Spotify URL - the API will handle it specially
        const isSpotifyUrl = url.match(/https:\/\/open\.spotify\.com\/(intl-[a-z]+\/)?track\/[a-zA-Z0-9]+.*/);
        
        fetch('/api/queue/add', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/x-www-form-urlencoded'
            },
            body: `url=${encodeURIComponent(url)}`
        })
        .then(response => response.json())
        .then(data => {
            // Show notification
            const notification = document.createElement('div');
            notification.className = data.success ? 'notification success' : 'notification error';
            notification.textContent = data.message;
            document.body.appendChild(notification);
            
            // Remove after timeout
            setTimeout(() => {
                notification.classList.add('fade-out');
                setTimeout(() => notification.remove(), 500);
            }, 3000);
        })
        .catch(error => {
            console.error('Error adding to queue:', error);
        });
    };

    /**
     * Add a track to the queue using title and artist search
     * @param {string} title - The track title
     * @param {string} artist - The track artist
     * @param {string} sourceType - The source type (Radio or Gensokyo Radio)
     */
    const addToQueueViaSearch = (title, artist, sourceType) => {
        if (!title) return;
        
        // Construct a search query based on source type
        let searchQuery;
        
        if (sourceType === 'Radio') {
            // For radio stations, use only the title which already contains artist - title info
            searchQuery = title;
        } else if (sourceType === 'Gensokyo Radio') {
            // For Gensokyo Radio, use title and artist separately
            searchQuery = title;
            if (artist && artist !== 'Unknown Artist') {
                searchQuery = artist + ' - ' + title;
            }
        }
        
        // Build a /play command to search on YouTube
        const command = `/play ${searchQuery}`;
        
        // Execute the command
        fetch('/api/command/execute', {
            method: 'POST',
            headers: {
                'Content-Type': 'application/json'
            },
            body: JSON.stringify({ command: command.startsWith('/') ? command.substring(1) : command })
        })
        .then(response => response.json())
        .then(data => {
            // Show notification
            const notification = document.createElement('div');
            notification.className = data.success ? 'notification success' : 'notification error';
            notification.textContent = data.success 
                ? `Searching for "${searchQuery}" on YouTube` 
                : `Error: ${data.message}`;
            document.body.appendChild(notification);
            
            // Remove after timeout
            setTimeout(() => {
                notification.classList.add('fade-out');
                setTimeout(() => notification.remove(), 500);
            }, 3000);
        })
        .catch(error => {
            console.error('Error executing search command:', error);
            
            // Show error notification
            const notification = document.createElement('div');
            notification.className = 'notification error';
            notification.textContent = `Error searching for "${searchQuery}"`;
            document.body.appendChild(notification);
            
            // Remove after timeout
            setTimeout(() => {
                notification.classList.add('fade-out');
                setTimeout(() => notification.remove(), 500);
            }, 500);
        });
    };

    /**
     * Add event listeners for pagination buttons
     */
    const setupPaginationEvents = () => {
        // Calculate total pages for the event handlers
        const totalPages = Math.ceil(totalRecords / recordsPerPage);
        
        document.querySelectorAll('.page-btn').forEach(btn => {
            btn.addEventListener('click', function() {
                const page = parseInt(this.getAttribute('data-page'));
                currentPage = page;
                loadHistory();
            });
        });
        
        const prevBtn = document.querySelector('.prev-btn');
        if (prevBtn) {
            prevBtn.addEventListener('click', function() {
                if (currentPage > 1) {
                    currentPage--;
                    loadHistory();
                }
            });
        }
        
        const nextBtn = document.querySelector('.next-btn');
        if (nextBtn) {
            nextBtn.addEventListener('click', function() {
                if (currentPage < totalPages) {
                    currentPage++;
                    loadHistory();
                }
            });
        }
    };

    /**
     * Render pagination controls
     */
    const renderPagination = () => {
        // Calculate total pages
        const totalPages = Math.ceil(totalRecords / recordsPerPage);
        
        if (totalPages <= 1) {
            historyElements.pagination.innerHTML = '';
            return;
        }
        
        // Create pagination HTML
        let paginationHTML = '';
        
        // Previous button
        paginationHTML += `
            <button class="pagination-btn prev-btn" ${currentPage === 1 ? 'disabled' : ''}>
                <i class="fas fa-chevron-left"></i> Previous
            </button>
        `;
        
        // Page numbers
        paginationHTML += '<div class="pagination-pages">';
        
        // Calculate which page numbers to show
        let startPage = Math.max(1, currentPage - 2);
        let endPage = Math.min(totalPages, startPage + 4);
        
        // Adjust startPage if we're at the end
        if (endPage - startPage < 4) {
            startPage = Math.max(1, endPage - 4);
        }
        
        // First page button
        if (startPage > 1) {
            paginationHTML += `<button class="pagination-btn page-btn" data-page="1">1</button>`;
            if (startPage > 2) {
                paginationHTML += `<span class="pagination-ellipsis">...</span>`;
            }
        }
        
        // Page number buttons
        for (let i = startPage; i <= endPage; i++) {
            paginationHTML += `
                <button class="pagination-btn page-btn ${i === currentPage ? 'active' : ''}" data-page="${i}">
                    ${i}
                </button>
            `;
        }
        
        // Last page button
        if (endPage < totalPages) {
            if (endPage < totalPages - 1) {
                paginationHTML += `<span class="pagination-ellipsis">...</span>`;
            }
            paginationHTML += `<button class="pagination-btn page-btn" data-page="${totalPages}">${totalPages}</button>`;
        }
        
        paginationHTML += '</div>';
        
        // Next button
        paginationHTML += `
            <button class="pagination-btn next-btn" ${currentPage === totalPages ? 'disabled' : ''}>
                Next <i class="fas fa-chevron-right"></i>
            </button>
        `;
        
        // Set HTML
        historyElements.pagination.innerHTML = paginationHTML;
        
        // Add event listeners
        setupPaginationEvents();
    };

    // Public API
    return {
        init
    };
})();

// Expose the initialization function for use with the modular system
window.initializeHistoryPage = function() {
    console.log('Initializing history page...');
    
    // Make sure UI is initialized first
    if (typeof UI !== 'undefined') {
        console.log('Making sure UI is properly initialized...');
        UI.initializeUI(); // Ensure UI components are initialized
    }
    
    // When using the modular system, we need to check if the component has been loaded
    const historyContentComponent = document.getElementById('history-content-component');
    
    if (historyContentComponent) {
        // Check if the history container is inside the component
        if (historyContentComponent.querySelector('#history-container')) {
            console.log('History container found, initializing History Module');
            HistoryModule.init();
        } else {
            console.error('History container not found inside history content component.');
            setTimeout(() => {
                // Try again after a short delay
                if (document.getElementById('history-container')) {
                    console.log('History container found on second attempt.');
                    HistoryModule.init();
                } else {
                    console.error('History container still not found after delay. Check your component loading.');
                }
            }, 500);
        }
    } else {
        // Fallback to old method for backwards compatibility
        const historyContainer = document.getElementById('history-container');
        if (historyContainer) {
            HistoryModule.init();
        } else {
            console.error('History container not found. Check that the history-content component is loaded correctly.');
        }
    }
};

// Keep the original DOMContentLoaded event for backwards compatibility
document.addEventListener('DOMContentLoaded', function() {
    // Only initialize if we're not using the modular system
    // and if we haven't already initialized through initializeHistoryPage
    if (!window.usingModularSystem && typeof window.initializeHistoryPage === 'function') {
        setTimeout(() => {
            if (document.getElementById('history-container')) {
                HistoryModule.init();
            }
        }, 100);
    }
}); 