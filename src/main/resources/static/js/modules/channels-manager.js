/**
 * JMusicBot Web Panel - Channels Manager Module
 * This module handles the Channels page and Discord channel listings
 */

const ChannelsManager = (function() {
    // Private variables
    let currentServerId = null;
    let serverChannels = {};
    let selectedChannelId = null;
    let serverRolesCache = {}; // Cache for server roles to avoid multiple fetches
    
    /**
     * Escape HTML characters
     * @param {string} html - The HTML to escape
     * @returns {string} Escaped HTML
     */
    function escapeHtml(html) {
        if (typeof html !== 'string') {
            console.warn('escapeHtml called with non-string value:', html);
            return ''; // Return empty string or handle as appropriate
        }
        const escapeMap = {
            '&': '&amp;',
            '<': '&lt;',
            '>': '&gt;',
            '"': '&quot;',
            "'": '&#039;'
        };
        return html.replace(/[&<>"']/g, m => escapeMap[m]);
    }
    
    /**
     * Initialize the Channels Manager
     */
    async function initialize() {
        console.log('Initializing Channels Manager');
        
        // Get the currently selected server from the server manager
        if (typeof ServerManager !== 'undefined' && ServerManager.getSelectedGuildId) {
            currentServerId = ServerManager.getSelectedGuildId();
            
            if (currentServerId) {
                loadChannelsForServer(currentServerId);
            } else {
                console.log('No server currently selected, waiting for selection');
                showMessage('Select a server from the dropdown in the header to view channels.');
            }
            
            // Listen for server change events from the header
            document.addEventListener('server:changed', function(e) {
                if (e.detail && e.detail.guildId) {
                    loadChannelsForServer(e.detail.guildId);
                }
            });
            
            // Listen for component loaded events to refresh channels when component is ready
            document.addEventListener('component:loaded', function(e) {
                if (e.detail && e.detail.name === 'channels') {
                    // Reload channels for the current server
                    if (currentServerId) {
                        setTimeout(() => loadChannelsForServer(currentServerId), 100);
                    }
                }
            });
        } else {
            console.error('ServerManager not available - channels will not load');
            showError('Server manager not available. Please refresh the page.');
        }
        
        // Add event listeners for sidebar toggle (Mobile)
        document.addEventListener('click', function(e) {
            // Toggle Button
            if (e.target.closest('#channels-sidebar-toggle')) {
                const categories = document.getElementById('channel-categories');
                const container = document.querySelector('.channels-container');
                if (categories && container) {
                    categories.classList.toggle('active');
                    container.classList.toggle('sidebar-open');
                }
            }
            
            // Close sidebar when clicking outside (on the overlay)
            const container = document.querySelector('.channels-container');
            if (container && container.classList.contains('sidebar-open')) {
                // If click is not on the sidebar itself and not on the toggle button
                if (!e.target.closest('#channel-categories') && !e.target.closest('#channels-sidebar-toggle')) {
                    const categories = document.getElementById('channel-categories');
                    if (categories) categories.classList.remove('active');
                    container.classList.remove('sidebar-open');
                }
            }
        });

        // Add event listeners for category toggles
        document.addEventListener('click', function(e) {
            if (e.target.closest('.category-header')) {
                const categoryHeader = e.target.closest('.category-header');
                const categoryId = categoryHeader.getAttribute('data-category-id');
                toggleCategory(categoryId);
            }
        });
        
        // Add event listeners for channel selection
        document.addEventListener('click', function(e) {
            if (e.target.closest('.channel-item')) {
                const channelItem = e.target.closest('.channel-item');
                
                // Close sidebar on mobile
                const categories = document.getElementById('channel-categories');
                const container = document.querySelector('.channels-container');
                if (categories && categories.classList.contains('active')) {
                    categories.classList.remove('active');
                    if (container) container.classList.remove('sidebar-open');
                }

                const channelId = channelItem.getAttribute('data-channel-id');
                const channelType = channelItem.getAttribute('data-channel-type');
                
                // Only show details for text channels
                if (channelType === 'text') {
                    selectChannel(channelId);
                }
            }
        });
        
        // Add event listener for permission button
        document.addEventListener('click', function(e) {
            if (e.target.closest('#view-permissions-btn')) {
                togglePermissionsView();
            }
        });
        
        // Add event listener for loading more messages
        document.addEventListener('click', function(e) {
            const loadMoreBtn = e.target.closest('#load-more-messages');
            if (loadMoreBtn) {
                console.log('Load more button clicked');
                if (selectedChannelId) {
                    loadMoreMessages(selectedChannelId);
                } else {
                    console.error('No channel selected when trying to load more messages');
                }
            }
        });

        // Add event listener for clicking on an author's name to show roles
        document.addEventListener('click', async function(e) {
            const authorSpan = e.target.closest('.message-author');
            if (authorSpan && authorSpan.dataset.authorId) {
                const authorId = authorSpan.dataset.authorId;
                const authorName = authorSpan.dataset.authorName;
                // Assuming currentServerId is available and correct
                if (currentServerId && authorId) {
                    showUserProfilePopup(currentServerId, authorId, authorName, authorSpan);
                }
            }
        });
    }
    
    /**
     * Load channels for a specific server
     * @param {string} serverId - Discord server ID
     */
    async function loadChannelsForServer(serverId) {
        if (!serverId) {
            showError('No server selected. Please select a server from the dropdown in the header.');
            return;
        }
        
        if (currentServerId === serverId && serverChannels[serverId]) {
            // Already loaded, just show them
            renderChannels(serverChannels[serverId], serverId);
            return;
        }
        
        currentServerId = serverId;
        const channelCategoriesEl = document.getElementById('channel-categories');
        const channelDetailsEl = document.getElementById('channel-details');
        
        // Clear existing content and show loading
        channelCategoriesEl.innerHTML = '<div class="loading-indicator"><i class="fas fa-spinner fa-spin fa-2x"></i><p>Loading channels...</p></div>';
        
        // Reset channel details view
        if (channelDetailsEl) {
            channelDetailsEl.innerHTML = '<div class="no-channel-selected"><i class="fas fa-hashtag fa-3x"></i><p>Select a text channel to view details</p></div>';
        }
        
        try {
            const response = await fetch(`/api/servers/${serverId}/channels`);
            
            if (!response.ok) {
                throw new Error(`HTTP error ${response.status}`);
            }
            
            const channels = await response.json();
            
            // Store channels for this server
            serverChannels[serverId] = channels;
            
            // Render channels
            renderChannels(channels, serverId);
        } catch (error) {
            console.error(`Error loading channels for server ${serverId}:`, error);
            channelCategoriesEl.innerHTML = '<div class="error-message">Failed to load channels. Please try again later.</div>';
        }
    }
    
    /**
     * Render channels grouped by category
     * @param {Array} channels - List of Discord channels
     * @param {string} serverId - Discord server ID
     */
    function renderChannels(channels, serverId) {
        const channelCategoriesEl = document.getElementById('channel-categories');
        channelCategoriesEl.innerHTML = '';
        
        // Group channels by category
        const categories = {};
        const uncategorizedChannels = [];
        
        channels.forEach(channel => {
            if (channel.type === 'category') {
                categories[channel.id] = {
                    ...channel,
                    channels: []
                };
            } else if (channel.parentId && categories[channel.parentId]) {
                categories[channel.parentId].channels.push(channel);
            } else {
                uncategorizedChannels.push(channel);
            }
        });
        
        // Render categories and their channels
        Object.values(categories).forEach(category => {
            const categoryEl = document.createElement('div');
            categoryEl.className = 'category';
            categoryEl.innerHTML = `
                <div class="category-header" data-category-id="${category.id}">
                    <i class="fas fa-chevron-right category-toggle expanded"></i>
                    <span class="category-name">${category.name}</span>
                </div>
                <div class="channel-list" data-category-id="${category.id}">
                    ${renderChannelItems(category.channels)}
                </div>
            `;
            
            channelCategoriesEl.appendChild(categoryEl);
        });
        
        // Render uncategorized channels if any
        if (uncategorizedChannels.length > 0) {
            const uncategorizedEl = document.createElement('div');
            uncategorizedEl.className = 'category';
            uncategorizedEl.innerHTML = `
                <div class="category-header" data-category-id="uncategorized">
                    <i class="fas fa-chevron-right category-toggle expanded"></i>
                    <span class="category-name">Uncategorized</span>
                </div>
                <div class="channel-list" data-category-id="uncategorized">
                    ${renderChannelItems(uncategorizedChannels)}
                </div>
            `;
            
            channelCategoriesEl.appendChild(uncategorizedEl);
        }
        
        // If no channels, show message
        if (Object.keys(categories).length === 0 && uncategorizedChannels.length === 0) {
            channelCategoriesEl.innerHTML = '<div class="no-channels">No channels available in this server</div>';
        }
    }
    
    /**
     * Render list of channel items
     * @param {Array} channels - List of channels to render
     * @returns {string} HTML string of channel items
     */
    function renderChannelItems(channels) {
        if (!channels || channels.length === 0) {
            return '<div class="no-channels">No channels in this category</div>';
        }
        
        return channels.map(channel => {
            let iconClass = 'fas fa-hashtag';
            let channelClass = 'text-channel';
            
            if (channel.type === 'voice') {
                iconClass = 'fas fa-volume-up';
                channelClass = 'voice-channel';
            }
            
            if (!channel.accessible) {
                channelClass += ' locked-channel';
                iconClass = 'fas fa-lock';
            }
            
            // Basic channel structure with flex-direction: column for voice channels
            let channelHtml = `
                <div class="channel-item ${channelClass}" data-channel-id="${channel.id}" data-channel-type="${channel.type}">
                    <div class="channel-item-header">
                        <i class="channel-icon ${iconClass}"></i>
                        <span class="channel-name">${channel.name}</span>
                    </div>
            `;
            
            // Add connected users for voice channels below the channel name
            if (channel.type === 'voice' && channel.connectedUsers && channel.connectedUsers.length > 0) {
                channelHtml += `<div class="channel-connected-users">`;
                
                channel.connectedUsers.forEach(user => {
                    let userStatusIcons = '';
                    
                    if (user.muted) {
                        userStatusIcons += '<i class="user-status-icon fas fa-microphone-slash" title="Muted"></i>';
                    }
                    
                    if (user.deafened) {
                        userStatusIcons += '<i class="user-status-icon fas fa-volume-mute" title="Deafened"></i>';
                    }
                    
                    if (user.streaming) {
                        userStatusIcons += '<i class="user-status-icon fas fa-video" title="Streaming"></i>';
                    }
                    
                    if (user.videoEnabled) {
                        userStatusIcons += '<i class="user-status-icon fas fa-camera" title="Camera On"></i>';
                    }
                    
                    channelHtml += `
                        <div class="connected-user">
                            <div class="user-avatar">
                                <img src="${user.avatarUrl || 'img/default-avatar.png'}" alt="Avatar">
                            </div>
                            <div class="user-info">
                                <span class="user-name">${user.nickname || user.name}${user.isBot ? ' <span class="user-bot-tag">BOT</span>' : ''}</span>
                                <div class="user-status">${userStatusIcons}</div>
                            </div>
                        </div>
                    `;
                });
                
                channelHtml += `</div>`;
            }
            
            // Close the channel item div
            channelHtml += `</div>`;
            
            return channelHtml;
        }).join('');
    }
    
    /**
     * Toggle expanding/collapsing a category
     * @param {string} categoryId - ID of the category to toggle
     */
    function toggleCategory(categoryId) {
        const categoryHeader = document.querySelector(`.category-header[data-category-id="${categoryId}"]`);
        const categoryChannels = document.querySelector(`.channel-list[data-category-id="${categoryId}"]`);
        const toggleIcon = categoryHeader.querySelector('.category-toggle');
        
        if (categoryChannels.style.display === 'none') {
            categoryChannels.style.display = 'block';
            toggleIcon.classList.add('expanded');
        } else {
            categoryChannels.style.display = 'none';
            toggleIcon.classList.remove('expanded');
        }
    }
    
    /**
     * Toggle permissions view visibility
     */
    function togglePermissionsView() {
        const permissionsView = document.getElementById('permissions-panel');
        const messagesView = document.getElementById('channel-messages');
        const permissionsBtn = document.getElementById('view-permissions-btn');
        
        if (permissionsView.style.display === 'none') {
            permissionsView.style.display = 'block';
            messagesView.style.display = 'none';
            permissionsBtn.querySelector('span').textContent = 'View Messages';
            permissionsBtn.querySelector('i').className = 'fas fa-comments';
        } else {
            permissionsView.style.display = 'none';
            messagesView.style.display = 'block';
            permissionsBtn.querySelector('span').textContent = 'View Permissions';
            permissionsBtn.querySelector('i').className = 'fas fa-shield-alt';
        }
    }
    
    /**
     * Select a channel and show its details
     * @param {string} channelId - ID of the channel to select
     */
    async function selectChannel(channelId) {
        if (selectedChannelId === channelId) {
            return; // Already selected
        }
        
        const channelItems = document.querySelectorAll('.channel-item');
        channelItems.forEach(item => item.classList.remove('active'));
        
        const selectedItem = document.querySelector(`.channel-item[data-channel-id="${channelId}"]`);
        if (selectedItem) {
            selectedItem.classList.add('active');
        }
        
        selectedChannelId = channelId;
        
        // Update details panel
        const channelDetailsEl = document.getElementById('channel-details');
        if (channelDetailsEl) {
            channelDetailsEl.innerHTML = '<div class="loading-indicator"><i class="fas fa-spinner fa-spin fa-2x"></i><p>Loading channel details...</p></div>';
        }
        
        try {
            const [channelResponse, messagesResponse] = await Promise.all([
                fetch(`/api/channels/${channelId}`),
                fetch(`/api/channels/${channelId}/messages?limit=25`)
            ]);
            
            if (!channelResponse.ok || !messagesResponse.ok) {
                throw new Error(`Failed to fetch channel data`);
            }
            
            const channel = await channelResponse.json();
            const messages = await messagesResponse.json();
            
            if (channelDetailsEl) {
                renderChannelDetails(channel, messages);
                
                // Initialize interactive Markdown elements
                initializeMarkdownInteractivity();
            }
        } catch (error) {
            console.error(`Error loading channel ${channelId}:`, error);
            if (channelDetailsEl) {
                channelDetailsEl.innerHTML = '<div class="error-message">Failed to load channel details. Please try again later.</div>';
            }
        }
    }
    
    /**
     * Initialize interactive elements in Markdown content
     */
    function initializeMarkdownInteractivity() {
        // Initialize spoiler tags
        const spoilers = document.querySelectorAll('.spoiler');
        spoilers.forEach(spoiler => {
            spoiler.addEventListener('click', function() {
                this.classList.toggle('revealed');
            });
        });
        
        // Initialize highlight.js for code blocks
        const codeBlocks = document.querySelectorAll('pre code');
        if (window.hljs) {
            codeBlocks.forEach(block => {
                // Apply syntax highlighting
                window.hljs.highlightElement(block);
            });
        }
        
        // Add copy button to code blocks
        document.querySelectorAll('pre').forEach(block => {
            // Only add copy button if it doesn't already exist
            if (block.querySelector('.copy-code-btn')) return;
            
            const copyButton = document.createElement('button');
            copyButton.className = 'copy-code-btn';
            copyButton.innerHTML = '<i class="fas fa-copy"></i>';
            copyButton.title = 'Copy code';
            
            copyButton.addEventListener('click', function() {
                const code = block.querySelector('code').innerText;
                navigator.clipboard.writeText(code).then(() => {
                    // Show success indicator
                    copyButton.innerHTML = '<i class="fas fa-check"></i>';
                    copyButton.classList.add('copied');
                    
                    // Reset after 2 seconds
                    setTimeout(() => {
                        copyButton.innerHTML = '<i class="fas fa-copy"></i>';
                        copyButton.classList.remove('copied');
                    }, 2000);
                });
            });
            
            block.style.position = 'relative';
            block.appendChild(copyButton);
        });
        
        // Initialize markdown links to open in new tab
        const links = document.querySelectorAll('.message-text a, .embed-description a');
        links.forEach(link => {
            link.setAttribute('target', '_blank');
            link.setAttribute('rel', 'noopener noreferrer');
        });
    }
    
    /**
     * Initialize command suggestions for the message input
     * @param {HTMLElement} messageInput - The message input element
     */
    function initializeCommandSuggestions(messageInput) {
        if (!messageInput) return;
        
        // Create suggestion container if it doesn't exist
        let suggestionContainer = document.getElementById('message-autocomplete');
        if (!suggestionContainer) {
            suggestionContainer = document.createElement('div');
            suggestionContainer.id = 'message-autocomplete';
            suggestionContainer.className = 'message-autocomplete';
            messageInput.parentNode.appendChild(suggestionContainer);
        }
        
        // Create command preview container if it doesn't exist
        let previewContainer = document.getElementById('message-command-preview');
        if (!previewContainer) {
            previewContainer = document.createElement('div');
            previewContainer.id = 'message-command-preview';
            previewContainer.className = 'message-command-preview';
            messageInput.parentNode.appendChild(previewContainer);
        }
        
        // Track selection index
        let selectedIndex = -1;
        
        // Listen for input to show suggestions
        messageInput.addEventListener('input', function() {
            const text = messageInput.value.trim();
            if (!text) {
                hideSuggestions();
                hideCommandPreview();
                return;
            }
            
            // Only show suggestions for commands
            const isSlashCommand = text.startsWith('/');
            const hasPrefix = isCommand(text);
            
            if (isSlashCommand || hasPrefix) {
                // Get command part (without prefix)
                const commandText = isSlashCommand ? text.substring(1) : text.substring(text.indexOf(getPrefix()) + getPrefix().length);
                const parts = commandText.split(' ');
                const commandPart = parts[0].toLowerCase();
                
                // Find matching commands if Commands module is available
                if (typeof Commands !== 'undefined' && Commands.getAvailableCommands) {
                    const availableCommands = Commands.getAvailableCommands();
                    const matches = availableCommands.filter(cmd => 
                        cmd.name.toLowerCase().startsWith(commandPart) ||
                        (cmd.aliases && cmd.aliases.some(alias => alias.toLowerCase().startsWith(commandPart)))
                    );
                    
                    if (matches.length > 0) {
                        // If we have exact command match and more than one part, show command preview
                        const exactMatch = matches.find(cmd => cmd.name.toLowerCase() === commandPart || 
                            (cmd.aliases && cmd.aliases.some(alias => alias.toLowerCase() === commandPart)));
                        
                        if (exactMatch && parts.length > 1) {
                            showCommandPreview(previewContainer, exactMatch, parts.slice(1).join(' '));
                            hideSuggestions();
                        } else {
                            showSuggestions(suggestionContainer, matches, messageInput, isSlashCommand);
                        }
                        return;
                    }
                }
            }
            
            // Hide if not a command or no matches
            hideSuggestions();
            hideCommandPreview();
        });
        
        // Handle keyboard navigation
        messageInput.addEventListener('keydown', function(e) {
            // Only handle if suggestions are visible
            if (suggestionContainer.style.display !== 'block') return;
            
            const suggestions = suggestionContainer.querySelectorAll('.command-suggestion');
            if (suggestions.length === 0) return;
            
            switch(e.key) {
                case 'ArrowDown':
                    e.preventDefault();
                    navigateSuggestions(1, suggestions);
                    break;
                case 'ArrowUp':
                    e.preventDefault();
                    navigateSuggestions(-1, suggestions);
                    break;
                case 'Tab':
                    e.preventDefault();
                    if (selectedIndex >= 0 && selectedIndex < suggestions.length) {
                        applySuggestion(suggestions[selectedIndex], messageInput);
                    }
                    break;
                case 'Escape':
                    e.preventDefault();
                    hideSuggestions();
                    break;
            }
        });
        
        // Navigate through suggestions
        function navigateSuggestions(direction, suggestions) {
            // Clear current selection
            suggestions.forEach(s => s.classList.remove('selected'));
            
            // Update selected index
            selectedIndex += direction;
            
            // Handle bounds
            if (selectedIndex < 0) {
                selectedIndex = suggestions.length - 1;
            } else if (selectedIndex >= suggestions.length) {
                selectedIndex = 0;
            }
            
            // Apply new selection
            if (selectedIndex >= 0) {
                const selected = suggestions[selectedIndex];
                selected.classList.add('selected');
                selected.scrollIntoView({ block: 'nearest' });
            }
        }
        
        // Show command suggestions
        function showSuggestions(container, commands, inputElement, isSlashCommand) {
            container.innerHTML = '';
            selectedIndex = -1;
            
            commands.forEach((cmd, index) => {
                const suggestion = document.createElement('div');
                suggestion.className = 'command-suggestion';
                suggestion.setAttribute('data-command', cmd.name);
                
                // Create suggestion content
                const nameContent = document.createElement('div');
                
                const nameSpan = document.createElement('span');
                nameSpan.className = 'command-suggestion-name';
                nameSpan.textContent = (isSlashCommand ? '/' : getPrefix()) + cmd.name;
                
                const argsSpan = document.createElement('span');
                argsSpan.className = 'command-suggestion-args';
                argsSpan.textContent = cmd.arguments || '';
                
                nameContent.appendChild(nameSpan);
                if (cmd.arguments) {
                    nameContent.appendChild(argsSpan);
                }
                
                const helpDiv = document.createElement('div');
                helpDiv.className = 'command-suggestion-help';
                helpDiv.textContent = cmd.help;
                
                suggestion.appendChild(nameContent);
                suggestion.appendChild(helpDiv);
                
                // Add click handler
                suggestion.addEventListener('click', () => {
                    applySuggestion(suggestion, inputElement);
                });
                
                container.appendChild(suggestion);
            });
            
            // Show the autocomplete
            container.style.display = 'block';
        }
        
        // Hide suggestions
        function hideSuggestions() {
            suggestionContainer.style.display = 'none';
            selectedIndex = -1;
        }
        
        // Apply suggestion to the input
        function applySuggestion(suggestionElement, inputElement) {
            const commandName = suggestionElement.getAttribute('data-command');
            
            // Get current input
            const currentValue = inputElement.value;
            const isSlashCommand = currentValue.startsWith('/');
            
            // Build new value with the selected command
            let newValue;
            if (isSlashCommand) {
                newValue = '/' + commandName + ' ';
            } else {
                // Use configured prefix
                newValue = getPrefix() + commandName + ' ';
            }
            
            // Update input value
            inputElement.value = newValue;
            
            // Set cursor position after command name
            inputElement.setSelectionRange(newValue.length, newValue.length);
            
            // Hide suggestions
            hideSuggestions();
            
            // Show preview if available
            if (typeof Commands !== 'undefined' && Commands.getAvailableCommands) {
                const availableCommands = Commands.getAvailableCommands();
                const command = availableCommands.find(cmd => cmd.name === commandName);
                if (command) {
                    showCommandPreview(previewContainer, command, '');
                }
            }
            
            // Focus input
            inputElement.focus();
        }
        
        // Show command preview
        function showCommandPreview(container, command, args) {
            // Create preview content
            let html = `
                <div class="preview-title">
                    <i class="fas fa-terminal"></i> ${command.name}
                </div>
                <div class="preview-description">${command.description || command.help}</div>
            `;
            
            // Add arguments section if needed
            if (command.args && command.args.length > 0) {
                html += '<div class="preview-arguments">';
                
                command.args.forEach(arg => {
                    const requiredClass = arg.required ? 'argument-required' : 'argument-optional';
                    const requiredText = arg.required ? 'Required' : 'Optional';
                    
                    html += `
                        <div class="preview-argument">
                            <span class="argument-name">${arg.name}</span>
                            <span class="argument-description">${arg.description}</span>
                            <span class="${requiredClass}">${requiredText}</span>
                        </div>
                    `;
                });
                
                html += '</div>';
            }
            
            // Update and show the preview
            container.innerHTML = html;
            container.style.display = 'block';
        }
        
        // Hide command preview
        function hideCommandPreview() {
            previewContainer.style.display = 'none';
        }
    }
    
    /**
     * Get the configured command prefix
     * @returns {string} The command prefix
     */
    function getPrefix() {
        // Get the bot prefix from configuration if available
        let prefix = '';
        if (typeof ConfigManager !== 'undefined' && ConfigManager.getConfig) {
            const config = ConfigManager.getConfig();
            if (config && config.prefix) {
                prefix = config.prefix;
            }
        }
        
        // Default prefix is "!" if not configured
        return prefix || '!';
    }
    
    /**
     * Load more messages for a channel
     * @param {string} channelId - ID of the channel
     */
    async function loadMoreMessages(channelId) {
        if (!channelId) return;
        
        const messagesContainer = document.querySelector('.messages-container');
        const loadMoreBtn = document.getElementById('load-more-messages');
        const loadingIndicator = document.querySelector('.load-more-container .loading-indicator');
        
        if (!messagesContainer || !loadMoreBtn) return;
        
        // Get the oldest message ID currently displayed
        const messageItems = document.querySelectorAll('.message-item');
        if (!messageItems.length) return;
        
        const oldestMessageId = messageItems[0].getAttribute('data-message-id');
        if (!oldestMessageId) return;
        
        // Save current scroll position and height before loading
        const scrollTop = messagesContainer.scrollTop;
        const prevHeight = messagesContainer.scrollHeight;
        
        // Show loading indicator, disable button
        if (loadingIndicator) {
            loadingIndicator.style.display = 'flex';
        }
        loadMoreBtn.disabled = true;
        
        try {
            const response = await fetch(`/api/channels/${channelId}/messages?before=${oldestMessageId}&limit=25`);
            
            if (!response.ok) {
                throw new Error(`HTTP error ${response.status}`);
            }
            
            const messages = await response.json();
            
            if (messages && messages.length > 0) {
                // Get the load more container so we can remove it before inserting new messages
                const loadMoreContainer = document.querySelector('.load-more-container');
                if (loadMoreContainer) {
                    loadMoreContainer.remove();
                }
                
                // Insert new messages at the top of the container
                const tempDiv = document.createElement('div');
                tempDiv.innerHTML = renderMessageItems(messages);
                
                // We want to prepend the new messages
                const newMessages = Array.from(tempDiv.children);
                newMessages.reverse().forEach(node => {
                    messagesContainer.insertBefore(node, messagesContainer.firstChild);
                });
                
                // Initialize Markdown for new messages
                initializeMarkdownInteractivity();
                
                // Create and reinsert the load more button at the top
                const newLoadMoreContainer = document.createElement('div');
                newLoadMoreContainer.className = 'load-more-container';
                newLoadMoreContainer.id = 'load-more-container';
                newLoadMoreContainer.innerHTML = `
                    <button id="load-more-messages" class="load-more-btn">
                        <i class="fas fa-arrow-up" style="margin-right: 5px;"></i>
                        Load more messages
                    </button>
                    <div class="loading-indicator" style="display: none; position: absolute; width: 100%; justify-content: center; background-color: rgba(47, 49, 54, 0.9);">
                        <i class="fas fa-spinner fa-spin" style="margin-right: 5px;"></i>
                        Loading...
                    </div>
                `;
                
                // Insert the new load more container at the top
                messagesContainer.insertBefore(newLoadMoreContainer, messagesContainer.firstChild);
                
                // Add the event listener to the new button
                const newLoadMoreButton = document.getElementById('load-more-messages');
                if (newLoadMoreButton) {
                    newLoadMoreButton.addEventListener('click', () => {
                        console.log('Load more button direct click');
                        if (selectedChannelId) {
                            loadMoreMessages(selectedChannelId);
                        }
                    });
                }
                
                // Calculate height difference and adjust scroll position to maintain the same view
                const newHeight = messagesContainer.scrollHeight;
                const heightDifference = newHeight - prevHeight;
                
                // Set the scroll position to maintain the user's current view
                messagesContainer.scrollTop = scrollTop + heightDifference;
                
            } else {
                // No more messages
                loadMoreBtn.disabled = true;
                loadMoreBtn.innerHTML = 'No more messages';
                
                // Hide the button after a delay
                setTimeout(() => {
                    const loadMoreContainer = document.querySelector('.load-more-container');
                    if (loadMoreContainer) {
                        loadMoreContainer.style.display = 'none';
                    }
                }, 3000);
            }
        } catch (error) {
            console.error('Error loading more messages:', error);
            loadMoreBtn.disabled = false;
            loadMoreBtn.innerHTML = '<i class="fas fa-exclamation-circle"></i> Error - Try Again';
        } finally {
            // Hide loading indicator
            if (loadingIndicator) {
                loadingIndicator.style.display = 'none';
            }
        }
    }
    
    /**
     * Render channel details in the right panel
     * @param {Object} channel - Channel details object
     * @param {Array} messages - Channel messages array
     */
    function renderChannelDetails(channel, messages) {
        console.log('Rendering channel details for:', channel.name, 'with', messages ? messages.length : 0, 'messages');
        
        const channelDetailsEl = document.getElementById('channel-details');
        
        // Determine icon based on channel type
        let iconClass = 'fas fa-hashtag';
        if (channel.type === 'voice') {
            iconClass = 'fas fa-volume-up';
        }
        
        let permissionsHTML = '';
        if (channel.permissions && channel.permissions.length > 0) {
            permissionsHTML = `
                <div class="permissions-list">
                    <div class="permissions-header">
                        <i class="fas fa-shield-alt"></i> Bot Permissions
                    </div>
                    ${channel.permissions.map(perm => `
                        <div class="permission-item">
                            <span class="permission-name">
                                <i class="fas ${perm.allowed ? 'fa-check-circle' : 'fa-times-circle'}" style="margin-right: 8px;"></i>
                                ${perm.name}
                            </span>
                            <span class="permission-value ${perm.allowed ? 'allowed' : 'denied'}">
                                ${perm.allowed ? 'Allowed' : 'Denied'}
                            </span>
                        </div>
                    `).join('')}
                </div>
            `;
        }
        
        // Render messages HTML
        const messagesHTML = renderMessageItems(messages);
        const hasMessages = messages && messages.length > 0;
        console.log('Has messages:', hasMessages);
        
        // Clear the existing content first
        channelDetailsEl.innerHTML = '';
        
        // Create a container for channel info
        const channelInfoEl = document.createElement('div');
        channelInfoEl.className = 'channel-info';
        channelInfoEl.innerHTML = `
            <div class="channel-info-header">
                <i class="channel-info-icon ${iconClass}"></i>
                <span class="channel-info-name">${channel.name}</span>
                <button id="view-permissions-btn" class="view-permissions-btn">
                    <i class="fas fa-shield-alt"></i>
                    <span>View Permissions</span>
                </button>
            </div>
            ${channel.topic ? `
            <div class="channel-info-description">
                <i class="fas fa-info-circle" style="margin-right: 5px; opacity: 0.7;"></i>
                ${channel.topic}
            </div>` : ''}
            <div class="channel-info-meta">
                <div><i class="fas fa-tag" style="margin-right: 5px; opacity: 0.7;"></i> Type: ${channel.type.charAt(0).toUpperCase() + channel.type.slice(1)}</div>
                <div><i class="fas fa-fingerprint" style="margin-right: 5px; opacity: 0.7;"></i> ID: ${channel.id}</div>
            </div>
        `;
        
        // Create a container for messages
        const messagesEl = document.createElement('div');
        messagesEl.id = 'channel-messages';
        messagesEl.className = 'channel-messages';
        
        // Add headers and container
        let messagesContainerHTML = `
            <div class="messages-header">
                <i class="fas fa-comments" style="margin-right: 8px;"></i>
                Channel Messages
            </div>
            <div id="messages-container" class="messages-container">
        `;
        
        // If we have messages, add the load more button at the beginning
        if (hasMessages) {
            console.log('Adding load more button');
            messagesContainerHTML += `
                <div id="load-more-container" class="load-more-container">
                    <button id="load-more-messages" class="load-more-btn">
                        <i class="fas fa-arrow-up" style="margin-right: 5px;"></i>
                        Load more messages
                    </button>
                    <div class="loading-indicator">
                        <i class="fas fa-spinner fa-spin" style="margin-right: 5px;"></i>
                        Loading...
                    </div>
                </div>
            `;
        }
        
        // Add message content and close container
        messagesContainerHTML += `
                ${messagesHTML}
            </div>
            
            <!-- Message Input Box -->
            <div class="message-input-container">
                <div class="message-input-wrapper">
                    <textarea id="message-input" class="message-input" placeholder="Message ${channel.name}" rows="1"></textarea>
                    <button id="send-message-btn" class="send-message-btn">
                        <i class="fas fa-paper-plane"></i>
                    </button>
                </div>
            </div>
        `;
        
        messagesEl.innerHTML = messagesContainerHTML;
        
        // Create a container for permissions (initially hidden)
        const permissionsEl = document.createElement('div');
        permissionsEl.id = 'permissions-panel';
        permissionsEl.className = 'permissions-panel';
        permissionsEl.style.display = 'none';
        permissionsEl.innerHTML = permissionsHTML;
        
        // Append all elements to the channel details container
        channelDetailsEl.appendChild(channelInfoEl);
        channelDetailsEl.appendChild(messagesEl);
        channelDetailsEl.appendChild(permissionsEl);
        
        // Add direct event listener to the load more button (to ensure it works)
        setTimeout(() => {
            const loadMoreButton = document.getElementById('load-more-messages');
            if (loadMoreButton) {
                console.log('Adding direct event listener to load more button');
                loadMoreButton.addEventListener('click', () => {
                    console.log('Load more button direct click');
                    if (selectedChannelId) {
                        loadMoreMessages(selectedChannelId);
                    }
                });
            }
            
            // Add event listener for the send message button
            const sendMessageBtn = document.getElementById('send-message-btn');
            const messageInput = document.getElementById('message-input');
            
            if (sendMessageBtn && messageInput) {
                console.log('Adding event listener to send message button');
                
                // Auto-resize textarea as user types
                messageInput.addEventListener('input', function() {
                    this.style.height = 'auto';
                    this.style.height = (this.scrollHeight) + 'px';
                    
                    // Reset to default height if empty
                    if (this.value.trim() === '') {
                        this.style.height = '';
                    }
                });
                
                // Allow sending message with Enter (but Shift+Enter for new line)
                messageInput.addEventListener('keydown', function(e) {
                    if (e.key === 'Enter' && !e.shiftKey) {
                        e.preventDefault();
                        sendMessageBtn.click();
                    }
                });
                
                // Send message button click handler
                sendMessageBtn.addEventListener('click', () => {
                    const messageContent = messageInput.value.trim();
                    if (messageContent && selectedChannelId) {
                        sendMessage(selectedChannelId, messageContent);
                        messageInput.value = '';
                        messageInput.style.height = '';
                    }
                });
                
                // Initialize command suggestions
                initializeCommandSuggestions(messageInput);
            }
        }, 100);
        
        // Scroll to the bottom of the messages container to show the most recent messages
        setTimeout(() => {
            const messagesContainer = document.getElementById('messages-container');
            if (messagesContainer) {
                messagesContainer.scrollTop = messagesContainer.scrollHeight;
            }
        }, 100);
    }
    
    /**
     * Send a message to a channel using the bot
     * @param {string} channelId - ID of the channel to send the message to
     * @param {string} content - Message content
     */
    async function sendMessage(channelId, content) {
        if (!channelId || !content) {
            return;
        }
        
        // Check if it's a command (starts with / or configured prefix)
        if (content.startsWith('/') || isCommand(content)) {
            executeCommand(content);
            return;
        }
        
        try {
            const params = new URLSearchParams();
            params.append('content', content);
            
            const response = await fetch(`/api/channels/${channelId}/messages`, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded;charset=UTF-8'
                },
                body: params
            });
            
            if (!response.ok) {
                throw new Error(`HTTP error ${response.status}`);
            }
            
            const result = await response.json();
            
            if (result.success) {
                // Reload messages to show the new message
                const messagesResponse = await fetch(`/api/channels/${channelId}/messages?limit=25`);
                if (messagesResponse.ok) {
                    const messages = await messagesResponse.json();
                    
                    // Update the messages container
                    const messagesContainer = document.getElementById('messages-container');
                    if (messagesContainer) {
                        messagesContainer.innerHTML = renderMessageItems(messages);
                        
                        // Initialize interactive Markdown elements
                        initializeMarkdownInteractivity();
                        
                        // Scroll to the bottom
                        messagesContainer.scrollTop = messagesContainer.scrollHeight;
                    }
                }
            } else {
                console.error('Failed to send message:', result.message || 'Unknown error');
                
                // Show error toast
                if (typeof UI !== 'undefined' && typeof UI.showToast === 'function') {
                    UI.showToast('Failed to send message: ' + (result.message || 'Unknown error'), 'error');
                }
            }
        } catch (error) {
            console.error('Error sending message:', error);
            
            // Show error toast
            if (typeof UI !== 'undefined' && typeof UI.showToast === 'function') {
                UI.showToast('Error sending message: ' + error.message, 'error');
            }
        }
    }
    
    /**
     * Check if a message is a command based on configured prefix
     * @param {string} message - Message to check
     * @returns {boolean} True if it's a command
     */
    function isCommand(message) {
        // Get the bot prefix from configuration if available
        let prefix = '';
        if (typeof ConfigManager !== 'undefined' && ConfigManager.getConfig) {
            const config = ConfigManager.getConfig();
            if (config && config.prefix) {
                prefix = config.prefix;
            }
        }
        
        // Default prefix is "!" if not configured
        prefix = prefix || '!';
        
        // Check if message starts with the prefix
        return message.startsWith(prefix);
    }
    
    /**
     * Execute a command
     * @param {string} command - Command to execute
     */
    async function executeCommand(command) {
        if (!command) {
            return;
        }
        
        try {
            // Use the Commands module's function if available
            if (typeof Commands !== 'undefined' && Commands.executeDiscordCommand) {
                // Store current input value
                const messageInput = document.getElementById('message-input');
                const previousValue = messageInput.value;
                
                // Temporarily set the command in the command input
                const commandInput = document.getElementById('command-input');
                if (commandInput) {
                    commandInput.value = command;
                    
                    // Execute the command using Commands module
                    await Commands.executeDiscordCommand();
                    
                    // Reset message input and clear command input
                    messageInput.value = '';
                    commandInput.value = '';
                } else {
                    // If command input doesn't exist, execute directly
                    await executeCommandDirectly(command);
                }
            } else {
                // If Commands module is not available, execute directly
                await executeCommandDirectly(command);
            }
        } catch (error) {
            console.error('Error executing command:', error);
            
            // Show error toast
            if (typeof UI !== 'undefined' && typeof UI.showToast === 'function') {
                UI.showToast('Error executing command: ' + error.message, 'error');
            }
        }
    }
    
    /**
     * Execute command directly without using the Commands module
     * @param {string} command - Command to execute
     */
    async function executeCommandDirectly(command) {
        try {
            const formattedCommand = command.startsWith('/') ? command.substring(1) : command;
            const response = await fetch('/api/command/execute', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ command: formattedCommand })
            });
            
            const data = await response.json();
            
            // Show toast with command result
            if (typeof UI !== 'undefined' && typeof UI.showToast === 'function') {
                UI.showToast(data.message, data.success);
            }
            
            // If successful, update channel messages
            if (data.success && selectedChannelId) {
                // Reload messages to show results of command
                const messagesResponse = await fetch(`/api/channels/${selectedChannelId}/messages?limit=25`);
                if (messagesResponse.ok) {
                    const messages = await messagesResponse.json();
                    
                    // Update the messages container
                    const messagesContainer = document.getElementById('messages-container');
                    if (messagesContainer) {
                        messagesContainer.innerHTML = renderMessageItems(messages);
                        
                        // Initialize interactive Markdown elements
                        initializeMarkdownInteractivity();
                        
                        // Scroll to the bottom
                        messagesContainer.scrollTop = messagesContainer.scrollHeight;
                    }
                }
                
                // If it's a volume command, update the player status
                if (command.toLowerCase().startsWith('/vol') || command.toLowerCase().startsWith('vol')) {
                    if (typeof Player !== 'undefined' && Player.fetchStatus) {
                        Player.fetchStatus();
                    }
                }
                
                // If it's a play/skip/stop command, update the queue and status
                if (
                    command.toLowerCase().startsWith('/play') || 
                    command.toLowerCase().startsWith('play') || 
                    command.toLowerCase().startsWith('/skip') || 
                    command.toLowerCase().startsWith('skip') || 
                    command.toLowerCase().startsWith('/stop') || 
                    command.toLowerCase().startsWith('stop')
                ) {
                    if (typeof Player !== 'undefined') {
                        if (Player.fetchQueue) Player.fetchQueue();
                        if (Player.fetchStatus) Player.fetchStatus();
                    }
                }
            }
        } catch (error) {
            console.error('Error executing command directly:', error);
            
            // Show error toast
            if (typeof UI !== 'undefined' && typeof UI.showToast === 'function') {
                UI.showToast('Error executing command: ' + error.message, 'error');
            }
        }
    }
    
    /**
     * Render message items for a channel
     * @param {Array} messages - Array of message objects
     * @returns {string} HTML string of message items
     */
    function renderMessageItems(messages) {
        if (!messages || messages.length === 0) {
            return '<div class="no-messages"><i class="fas fa-comment-slash"></i> No messages in this channel</div>';
        }
        
        // Function to convert text with newlines to HTML with <br> tags
        function formatText(text) {
            if (!text) return '';
            
            // Create a temporary container for manipulation
            const div = document.createElement('div');
            
            // First handle code blocks (triple backticks) to prevent inner content from being formatted
            let codeBlockRegex = /```(\w*)\n([\s\S]*?)```/g;
            let codeBlocks = [];
            let match;
            let index = 0;
            
            // Use exec to get capture groups and detect language
            while ((match = codeBlockRegex.exec(text)) !== null) {
                const fullMatch = match[0];
                const language = match[1] ? match[1].trim() : '';
                const code = match[2] ? match[2].trim() : '';
                
                // Create placeholder for the code block
                let placeholder = `__CODE_BLOCK_${index}__`;
                text = text.replace(fullMatch, placeholder);
                
                // Prepare the HTML for the code block
                let codeHtml;
                if (language && language !== '') {
                    // If a language is specified, add the class for highlight.js
                    codeHtml = `<pre><code class="language-${language}">${escapeHtml(code)}</code></pre>`;
                } else {
                    codeHtml = `<pre><code>${escapeHtml(code)}</code></pre>`;
                }
                
                // Store the code block
                codeBlocks.push({
                    placeholder: placeholder,
                    content: codeHtml
                });
                
                index++;
                
                // Reset regex lastIndex to ensure we continue from the right position
                // after text replacement
                codeBlockRegex.lastIndex = 0;
            }
            
            // Handle inline code blocks (single backtick)
            let inlineCodeRegex = /`([^`]+)`/g;
            let inlineCodeBlocks = [];
            let inlineMatches = text.match(inlineCodeRegex);
            
            if (inlineMatches) {
                inlineMatches.forEach((match, index) => {
                    // Extract code content without the backticks
                    let code = match.replace(/`([^`]+)`/g, '$1');
                    
                    // Create placeholder for the inline code
                    let placeholder = `__INLINE_CODE_${index}__`;
                    text = text.replace(match, placeholder);
                    
                    // Store the inline code block
                    inlineCodeBlocks.push({
                        placeholder: placeholder,
                        content: `<code>${escapeHtml(code)}</code>`
                    });
                });
            }
            
            // Handle blockquotes (lines starting with >)
            text = text.replace(/^&gt;\s(.+)$/gm, '<blockquote>$1</blockquote>');
            text = text.replace(/\n&gt;\s(.+)/gm, '<blockquote>$1</blockquote>');
            
            // Bold (double asterisks or double underscores)
            text = text.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
            text = text.replace(/__(.+?)__/g, '<strong>$1</strong>');
            
            // Italics (single asterisk or single underscore)
            text = text.replace(/\*([^*]+)\*/g, '<em>$1</em>');
            text = text.replace(/_([^_]+)_/g, '<em>$1</em>');
            
            // Strikethrough (double tilde)
            text = text.replace(/~~(.+?)~~/g, '$1');
            
            // Underline (double underscore inside double underscores)
            text = text.replace(/__(.+?)__/g, '<u>$1</u>');
            
            // Markdown style links [text](url)
            text = text.replace(/\[([^\]]+)\]\(([^)]+)\)/g, '<a href="$2" target="_blank">$1</a>');
            
            // URLs (auto-linking) - avoid matching already formatted URLs in HTML tags
            text = text.replace(/(?<!["'=])(https?:\/\/[^\s<"']+)/g, '<a href="$1" target="_blank">$1</a>');
            
            // Discord mentions (#channel, @user, @role)
            text = text.replace(/<#(\d+)>/g, '<span class="mention channel-mention">#channel-$1</span>');
            text = text.replace(/<@!?(\d+)>/g, '<span class="mention user-mention">@user-$1</span>');
            text = text.replace(/<@&(\d+)>/g, '<span class="mention role-mention">@role-$1</span>');
            
            // Format bot commands (starting with ! or .)
            text = text.replace(/(\s|^)([!.][\w-]+)/g, '$1<span class="bot-command">$2</span>');
            
            // Format spoilers (||content||)
            text = text.replace(/\|\|(.+?)\|\|/g, '<span class="spoiler">$1</span>');
            
            // Handle ordered and unordered lists
            // Unordered lists
            text = text.replace(/^\s*[\*-]\s+(.+)$/gm, '<li>$1</li>');
            // Ordered lists
            text = text.replace(/^\s*\d+\.\s+(.+)$/gm, '<li>$1</li>');
            
            // Group consecutive list items
            text = text.replace(/(<li>.+?<\/li>)[\n\r]+(<li>.+?<\/li>)/g, '$1$2');
            
            // Wrap lists in <ul> or <ol> tags
            text = text.replace(/(<li>[^<]+<\/li>)+/g, '<ul>$&</ul>');
            
            // Handle newlines
            text = text.replace(/\n/g, '<br>');
            
            // Re-insert code blocks
            codeBlocks.forEach(block => {
                text = text.replace(block.placeholder, block.content);
            });
            
            // Re-insert inline code blocks
            inlineCodeBlocks.forEach(block => {
                text = text.replace(block.placeholder, block.content);
            });
            
            // Format custom Discord emojis <:name:id> and animated <a:name:id>
            text = text.replace(/<a?:([a-zA-Z0-9_]+):(\d+)>/g, (match, name, id) => {
                const isAnimated = match.startsWith('<a:');
                const extension = isAnimated ? 'gif' : 'png';
                return `<img src="https://cdn.discordapp.com/emojis/${id}.${extension}?v=1" alt="${name}" class="discord-emoji" title="${name}">`;
            });
            
            return text;
        }
        
        // Group messages by date for better visual separation
        const groupedMessages = {};
        messages.forEach(message => {
            const date = new Date(message.timestamp).toLocaleDateString();
            if (!groupedMessages[date]) {
                groupedMessages[date] = [];
            }
            groupedMessages[date].push(message);
        });
        
        // Render each group with a date separator
        let htmlOutput = '';
        
        Object.keys(groupedMessages).forEach(date => {
            htmlOutput += `<div class="message-date-separator"><span>${date}</span></div>`;
            
            htmlOutput += groupedMessages[date].map(message => {
                // Format the timestamp
                const timestamp = new Date(message.timestamp);
                const formattedTime = timestamp.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' });
                
                // Process message content for links, emojis, etc.
                let content = formatText(message.content);
                let isOnlyImageLink = false;
                const imageLinkRegex = /^(https?:\/\/[^\s<"']+\.(?:png|jpg|jpeg|gif|webp))$/i;
                const imageMatch = message.content.match(imageLinkRegex);

                if (imageMatch) {
                    if (message.embeds && message.embeds.length === 1 && message.embeds[0].url === imageMatch[0]) {
                        content = `<img src="${imageMatch[0]}" class="direct-image-attachment" alt="Image">`;
                        isOnlyImageLink = true; 
                    }
                }
                
                const author = message.author;
                const isBot = author.bot; // Use the new isBot field
                const authorNameColor = author.highestRoleColor || '#FFFFFF'; // Use the new highestRoleColor
                const messageClass = isBot ? 'message-item bot-message' : 'message-item';
                
                // Handle attachments
                let attachmentsHTML = '';
                if (message.attachments && message.attachments.length > 0) {
                    attachmentsHTML = `
                        <div class="message-attachments">
                            ${message.attachments.map(attachment => {
                                // Check if it's an image
                                if (attachment.contentType && attachment.contentType.startsWith('image/')) {
                                    return `<img src="${attachment.url}" alt="Attachment" class="message-attachment-img">`;
                                }
                                // Otherwise just a link
                                return `<a href="${attachment.url}" target="_blank" class="message-attachment-link">
                                    <i class="fas fa-paperclip"></i> ${attachment.filename || 'Attachment'}
                                </a>`;
                            }).join('')}
                        </div>
                    `;
                }
                
                // Handle embeds
                let embedsHTML = '';
                if (message.embeds && message.embeds.length > 0) {
                    embedsHTML = `
                        <div class="message-embeds">
                            ${message.embeds.map(embed => {
                                // If this is an auto-embed for an image we are already displaying directly, skip it.
                                if (isOnlyImageLink && embed.url === imageMatch[0]) {
                                    return ''; // Skip this embed
                                }

                                // Border color
                                const borderColor = embed.color ? `#${embed.color.toString(16).padStart(6, '0')}` : '#4f545c';
                                
                                // Author section
                                let authorHTML = '';
                                if (embed.author) {
                                    authorHTML = `
                                        <div class="embed-author">
                                            ${embed.author.iconUrl ? `<img src="${embed.author.iconUrl}" class="embed-author-icon" alt="Author">` : ''}
                                            ${embed.author.url ? 
                                                `<a href="${embed.author.url}" class="embed-author-name" target="_blank">${embed.author.name}</a>` : 
                                                `<span class="embed-author-name">${embed.author.name}</span>`
                                            }
                                        </div>
                                    `;
                                }
                                
                                // Title section
                                let titleHTML = '';
                                if (embed.title) {
                                    titleHTML = `
                                        <div class="embed-title">
                                            ${embed.url ? 
                                                `<a href="${embed.url}" target="_blank">${embed.title}</a>` : 
                                                embed.title
                                            }
                                        </div>
                                    `;
                                }
                                
                                // Description section
                                let descriptionHTML = '';
                                if (embed.description) {
                                    descriptionHTML = `<div class="embed-description">${formatText(embed.description)}</div>`;
                                }
                                
                                // Thumbnail
                                let thumbnailHTML = '';
                                if (embed.thumbnail && embed.thumbnail.url) {
                                    thumbnailHTML = `<img src="${embed.thumbnail.url}" class="embed-thumbnail" alt="Thumbnail">`;
                                }
                                
                                // Fields section
                                let fieldsHTML = '';
                                if (embed.fields && embed.fields.length > 0) {
                                    fieldsHTML = `
                                        <div class="embed-fields">
                                            ${embed.fields.map(field => `
                                                <div class="embed-field ${field.inline ? 'inline' : ''}">
                                                    <div class="embed-field-name">${field.name}</div>
                                                    <div class="embed-field-value">${formatText(field.value)}</div>
                                                </div>
                                            `).join('')}
                                        </div>
                                    `;
                                }
                                
                                // Image section
                                let imageHTML = '';
                                if (embed.image && embed.image.url) {
                                    imageHTML = `<img src="${embed.image.url}" class="embed-image" alt="Image">`;
                                }
                                
                                // Footer section
                                let footerHTML = '';
                                if (embed.footer || embed.timestamp) {
                                    const footerText = embed.footer ? embed.footer.text : '';
                                    const footerTime = embed.timestamp ? new Date(embed.timestamp).toLocaleString() : '';
                                    
                                    footerHTML = `
                                        <div class="embed-footer">
                                            ${embed.footer && embed.footer.iconUrl ? 
                                                `<img src="${embed.footer.iconUrl}" class="embed-footer-icon" alt="Footer">` : ''
                                            }
                                            <span>${footerText}${footerText && footerTime ? ' • ' : ''}${footerTime}</span>
                                        </div>
                                    `;
                                }
                                
                                // Combine all sections
                                return `
                                    <div class="message-embed" style="border-left-color: ${borderColor}">
                                        ${thumbnailHTML}
                                        ${authorHTML}
                                        ${titleHTML}
                                        ${descriptionHTML}
                                        ${fieldsHTML}
                                        ${imageHTML}
                                        ${footerHTML}
                                    </div>
                                `;
                            }).join('')}
                        </div>
                    `;
                }
                
                // Add bot badge if applicable
                const botBadge = isBot ? '<span class="bot-badge">BOT</span>' : '';
                
                return `
                    <div class="${messageClass}" data-message-id="${message.id}">
                        <div class="message-avatar">
                            <img src="${author.avatarUrl || 'img/default-avatar.png'}" alt="Avatar">
                        </div>
                        <div class="message-content">
                            <div class="message-header">
                                <span class="message-author" 
                                      style="color: ${authorNameColor}; cursor: pointer;" 
                                      data-author-id="${author.id}" 
                                      data-author-name="${escapeHtml(author.name)}" 
                                      title="View ${escapeHtml(author.name)}'s roles">
                                    ${escapeHtml(author.name)}
                                </span> 
                                ${botBadge}
                                <span class="message-timestamp" title="${new Date(message.timestamp).toLocaleString()}">
                                    ${formattedTime}
                                </span>
                            </div>
                            ${content ? `<div class="message-text">${content}</div>` : ''}
                            ${attachmentsHTML}
                            ${embedsHTML}
                        </div>
                    </div>
                `;
            }).join('');
        });
        
        return htmlOutput;
    }
    
    /**
     * Fetches and displays a user's complete profile in a popup.
     * @param {string} serverId The ID of the server.
     * @param {string} userId The ID of the user.
     * @param {string} userName The name of the user (used as fallback).
     * @param {HTMLElement} targetElement The element to position the popup near.
     */
    async function showUserProfilePopup(serverId, userId, userName, targetElement) {
        try {
            const response = await fetch(`/api/servers/${serverId}/members/${userId}/profile`);
            if (!response.ok) {
                const errorData = await response.json();
                throw new Error(errorData.error || `HTTP error ${response.status}`);
            }
            const profile = await response.json();

            // Remove existing popups
            const existingPopup = document.getElementById('user-profile-popup');
            if (existingPopup) {
                existingPopup.remove();
            }

            const popup = document.createElement('div');
            popup.id = 'user-profile-popup';
            popup.className = 'user-profile-popup'; // New class for detailed profile

            // Constructing the profile HTML
            let profileHtml = '';

            // Banner and Accent Color
            const bannerStyle = profile.bannerUrl ? `background-image: url('${profile.bannerUrl}');` : '';
            const accentColorStyle = profile.accentColorHex ? `background-color: ${profile.accentColorHex};` : '';
            
            profileHtml += `<div class="profile-banner" style="${bannerStyle || accentColorStyle}"></div>`;

            profileHtml += `<div class="profile-header">`;
            profileHtml += `  <div class="profile-avatar-wrapper">`;
            profileHtml += `    <img src="${profile.avatarUrl || 'img/default-avatar.png'}" alt="Avatar" class="profile-avatar">`;
            // Online status indicator could go here, absolutely positioned on avatar
            profileHtml += `    <span class="profile-status-indicator ${profile.onlineStatus ? profile.onlineStatus.toLowerCase() : 'offline'}"></span>`;
            profileHtml += `  </div>`;
            // Add badges area here if implemented
            profileHtml += `</div>`;

            profileHtml += `<div class="profile-content-wrapper">`; // Wrapper for scrolling content
            profileHtml += `<div class="profile-content">`;

            profileHtml += `  <div class="profile-username-details">`;
            profileHtml += `    <span class="profile-effective-name">${escapeHtml(profile.effectiveName)}</span>`;
            if (profile.username) {
                profileHtml += `    <span class="profile-username-discriminator">${escapeHtml(profile.username)}${profile.discriminator !== '0' ? `#${profile.discriminator}` : ''}</span>`;
            }
            if (profile.bot) {
                profileHtml += `    <span class="profile-bot-tag">BOT</span>`;
            }
            profileHtml += `  </div>`;

            // Custom Status / Activity
            if (profile.activities && profile.activities.length > 0) {
                const mainActivity = profile.activities[0]; // Display the primary activity
                profileHtml += `<div class="profile-activity">`;
                profileHtml += `  <strong>${escapeHtml(mainActivity.type.charAt(0).toUpperCase() + mainActivity.type.slice(1).toLowerCase())} ${escapeHtml(mainActivity.name)}</strong>`;
                if (mainActivity.details) profileHtml += `  <div class="activity-details">${escapeHtml(mainActivity.details)}</div>`;
                if (mainActivity.state) profileHtml += `  <div class="activity-state">${escapeHtml(mainActivity.state)}</div>`;
                // Could add images (large/small) if design allows
                profileHtml += `</div>`;
            }

            // Member Since
            if (profile.timeJoined) {
                const joinDate = new Date(profile.timeJoined);
                profileHtml += `<div class="profile-section">`;
                profileHtml += `  <div class="profile-section-header">Member Since</div>`;
                profileHtml += `  <div class="profile-section-content">${joinDate.toLocaleDateString()} (${timeSince(joinDate)})</div>`;
                profileHtml += `</div>`;
            }

            // Discord User Since
            if (profile.timeCreated) {
                const creationDate = new Date(profile.timeCreated);
                profileHtml += `<div class="profile-section">`;
                profileHtml += `  <div class="profile-section-header">Discord User Since</div>`;
                profileHtml += `  <div class="profile-section-content">${creationDate.toLocaleDateString()} (${timeSince(creationDate)})</div>`;
                profileHtml += `</div>`;
            }
            
            // Roles
            if (profile.roles && profile.roles.length > 0) {
                profileHtml += `<div class="profile-section">`;
                profileHtml += `  <div class="profile-section-header">Roles (${profile.roles.length})</div>`;
                profileHtml += `  <div class="profile-roles-list">`;
                profile.roles.forEach(role => {
                    profileHtml += `<span class="profile-role-item" style="border-color: ${role.color || '#FFFFFF'}; background-color: ${hexToRgba(role.color || '#5865F2', 0.1)}; color: ${role.color || '#FFFFFF'}">`;
                    profileHtml += `<span class="profile-role-dot" style="background-color: ${role.color || '#FFFFFF'}"></span>`;
                    profileHtml += escapeHtml(role.name);
                    profileHtml += `</span>`;
                });
                profileHtml += `  </div>`;
                profileHtml += `</div>`;
            }
            
            // Note section (placeholder)
            profileHtml += `<div class="profile-section">`;
            profileHtml += `  <div class="profile-section-header">Note</div>`;
            profileHtml += `  <textarea class="profile-note-input" placeholder="Add a note"></textarea>`;
            profileHtml += `</div>`;

            profileHtml += `</div>`; // End profile-content
            profileHtml += `</div>`; // End profile-content-wrapper

            popup.innerHTML = profileHtml;
            document.body.appendChild(popup);

            // Position the popup
            const rect = targetElement.getBoundingClientRect();
            let top = rect.bottom + window.scrollY + 5;
            let left = rect.left + window.scrollX;

            popup.style.top = `${top}px`;
            popup.style.left = `${left}px`;

            // Adjust if popup goes off screen
            const popupRect = popup.getBoundingClientRect();
            if (popupRect.right > window.innerWidth) {
                popup.style.left = `${window.innerWidth - popupRect.width - 10}px`; // 10px padding from edge
            }
            if (popupRect.bottom > window.innerHeight) {
                popup.style.top = `${window.innerHeight - popupRect.height - 10}px`;
            }
            if (popupRect.left < 0) {
                popup.style.left = '10px';
            }
             if (popupRect.top < 0) {
                popup.style.top = '10px';
            }

            // Close popup when clicking outside
            setTimeout(() => {
                document.addEventListener('click', function closePopup(event) {
                    if (!popup.contains(event.target) && event.target !== targetElement && !targetElement.contains(event.target)) {
                        popup.remove();
                        document.removeEventListener('click', closePopup);
                    }
                });
            }, 0);

        } catch (error) {
            console.error(`Error fetching profile for user ${userId}:`, error);
            if (typeof UI !== 'undefined' && UI.showToast) {
                UI.showToast(`Could not load profile for ${escapeHtml(userName)}: ${error.message}`, 'error');
            }
        }
    }

    // Helper function to calculate time since a date
    function timeSince(date) {
        const seconds = Math.floor((new Date() - date) / 1000);
        let interval = seconds / 31536000;
        if (interval > 1) return Math.floor(interval) + (Math.floor(interval) === 1 ? " year ago" : " years ago");
        interval = seconds / 2592000;
        if (interval > 1) return Math.floor(interval) + (Math.floor(interval) === 1 ? " month ago" : " months ago");
        interval = seconds / 86400;
        if (interval > 1) return Math.floor(interval) + (Math.floor(interval) === 1 ? " day ago" : " days ago");
        interval = seconds / 3600;
        if (interval > 1) return Math.floor(interval) + (Math.floor(interval) === 1 ? " hour ago" : " hours ago");
        interval = seconds / 60;
        if (interval > 1) return Math.floor(interval) + (Math.floor(interval) === 1 ? " minute ago" : " minutes ago");
        return Math.floor(seconds) + (Math.floor(seconds) === 1 ? " second ago" : " seconds ago");
    }

    // Helper function to convert hex color to rgba
    function hexToRgba(hex, alpha = 1) {
        if (!hex) return `rgba(88, 101, 242, ${alpha})`; // Default to Discord blurple
        const r = parseInt(hex.slice(1, 3), 16);
        const g = parseInt(hex.slice(3, 5), 16);
        const b = parseInt(hex.slice(5, 7), 16);
        return `rgba(${r}, ${g}, ${b}, ${alpha})`;
    }
    
    /**
     * Show an error message in the channels area
     * @param {string} message - Error message to display
     */
    function showError(message) {
        const channelCategoriesEl = document.getElementById('channel-categories');
        channelCategoriesEl.innerHTML = `<div class="error-message">${message}</div>`;
    }
    
    /**
     * Show an informational message in the channels area
     * @param {string} message - Message to display
     */
    function showMessage(message) {
        const channelCategoriesEl = document.getElementById('channel-categories');
        channelCategoriesEl.innerHTML = `<div class="info-message"><i class="fas fa-info-circle"></i><p>${message}</p></div>`;
    }
    
    // Public API
    return {
        initialize: initialize,
        loadChannelsForServer: loadChannelsForServer
    };
})(); 