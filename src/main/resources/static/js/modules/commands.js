/**
 * Commands Module - Handles Discord command execution and suggestions
 */

const Commands = (function() {
    // Private variables
    let availableCommands = [];
    let selectedCommandIndex = -1;
    let currentInputValue = '';
    let commandHistory = [];
    
    // Initialize the command system with available commands
    function initializeCommandSystem() {
        loadCommandHistory();
        displayCommandHistory();
        
        // Initialize available commands
        availableCommands = [
            {
                name: 'help',
                help: 'Displays the list of available commands',
                description: 'Displays the complete list of available commands and their descriptions',
                category: 'General'
            },
            {
                name: 'about',
                help: 'Displays information about the bot',
                description: 'Shows detailed information about the bot, including version and uptime',
                category: 'General'
            },
            {
                name: 'ping',
                help: 'Checks the bot\'s latency',
                description: 'Tests the bot\'s response time and API latency',
                category: 'General'
            },
            {
                name: 'settings',
                help: 'Displays the bot\'s settings',
                description: 'Shows the current configuration settings for the bot on this server',
                category: 'General'
            },
            {
                name: 'serverinfo',
                help: 'Displays information about the server',
                description: 'Shows detailed information about the Discord server',
                category: 'General'
            },
            {
                name: 'cache',
                help: 'Displays the songs saved in the cache',
                description: 'Lists all songs currently stored in the bot\'s cache',
                category: 'General'
            },
            {
                name: 'stats',
                help: 'Shows music bot statistics for this server',
                description: 'Displays playback statistics and other metrics for the current server',
                category: 'General'
            },
            {
                name: 'lyrics',
                arguments: '[song name]',
                help: 'Displays the lyrics of a song',
                description: 'Searches for and displays lyrics of the current or specified song',
                category: 'Music',
                args: [
                    { name: 'song name', description: 'Optional song name to search for', required: false }
                ]
            },
            {
                name: 'nowplaying',
                aliases: ['np'],
                help: 'Displays the currently playing track',
                description: 'Shows information about the song that is currently playing',
                category: 'Music'
            },
            {
                name: 'play',
                arguments: '<title|URL|subcommand>',
                help: 'Plays the specified song',
                description: 'Plays a song, playlist, or stream from the provided URL or search term',
                category: 'Music',
                args: [
                    { name: 'query', description: 'YouTube URL, SoundCloud URL, or search term', required: true }
                ]
            },
            {
                name: 'spotify',
                arguments: '<title|URL|subcommand>',
                help: 'Plays the specified Spotify track',
                description: 'Plays music from Spotify using a URL or search term',
                category: 'Music',
                args: [
                    { name: 'query', description: 'Spotify URL or search term', required: true }
                ]
            },
            {
                name: 'playlists',
                arguments: '<play|append|delete|make|show>',
                help: 'Displays available playlists',
                description: 'Manage server playlists with various subcommands',
                category: 'Music',
                args: [
                    { name: 'subcommand', description: 'Action to perform with playlists', required: true }
                ]
            },
            {
                name: 'mylist',
                arguments: '<append|delete|make|all|show>',
                help: 'Manage your personal playlist',
                description: 'Create and manage your personal music playlists',
                category: 'Music',
                args: [
                    { name: 'subcommand', description: 'Action to perform with your playlist', required: true }
                ]
            },
            {
                name: 'queue',
                arguments: '[page]',
                help: 'Displays the list of songs in the queue',
                description: 'Shows all songs currently in the playback queue',
                category: 'Music',
                args: [
                    { name: 'page', description: 'Page number to view', required: false }
                ]
            },
            {
                name: 'remove',
                arguments: '<queue number|all|ALL>',
                help: 'Removes a song from the queue',
                description: 'Removes specified song(s) from the playback queue',
                category: 'Music',
                args: [
                    { name: 'position', description: 'Queue position, "all", or "ALL"', required: true }
                ]
            },
            {
                name: 'search',
                arguments: '<query>',
                help: 'Searches YouTube for videos',
                description: 'Searches YouTube for videos matching the query',
                category: 'Music',
                args: [
                    { name: 'query', description: 'Search term for YouTube', required: true }
                ]
            },
            {
                name: 'scsearch',
                arguments: '<query>',
                help: 'Searches Soundcloud using the specified string',
                description: 'Searches SoundCloud for tracks matching the query',
                category: 'Music',
                args: [
                    { name: 'query', description: 'Search term for SoundCloud', required: true }
                ]
            },
            {
                name: 'seek',
                arguments: '[+ | -] <HH:MM:SS | MM:SS | SS>|<0h0m0s | 0m0s | 0s>',
                help: 'Changes the playback position',
                description: 'Changes the current playback position of the playing track',
                category: 'Music',
                args: [
                    { name: 'position', description: 'Time position in various formats', required: true }
                ]
            },
            {
                name: 'ncsearch',
                arguments: '<search term>',
                help: 'Searches for videos on Nico Nico Douga',
                description: 'Searches for videos on Nico Nico Douga using the specified term',
                category: 'Music',
                args: [
                    { name: 'query', description: 'Search term for Nico Nico Douga', required: true }
                ]
            },
            {
                name: 'shuffle',
                help: 'Shuffle the added tracks',
                description: 'Randomly rearranges the songs in the queue',
                category: 'Music'
            },
            {
                name: 'skip',
                help: 'Request to skip the currently playing track',
                description: 'Votes to skip the currently playing song',
                category: 'Music'
            },
            {
                name: 'volume',
                aliases: ['vol'],
                arguments: '[0-150]',
                help: 'Sets or displays the volume',
                description: 'Changes or displays the current playback volume',
                category: 'Music',
                args: [
                    { name: 'level', description: 'Volume level (0-150)', required: false }
                ]
            },
            {
                name: 'radio',
                arguments: '<station name>',
                help: 'Play a radio station',
                description: 'Searches for radio stations on onlineradiobox.com and plays them',
                category: 'Music',
                args: [
                    { name: 'station', description: 'Name of the radio station', required: true }
                ]
            },
            {
                name: 'forceremove',
                arguments: '<user>',
                help: 'Removes entries of a user from the queue',
                description: 'Removes all tracks added by the specified user from the queue',
                category: 'DJ',
                args: [
                    { name: 'user', description: 'User to remove tracks for', required: true }
                ]
            },
            {
                name: 'forceskip',
                help: 'Skips the current song',
                description: 'Forces the bot to skip the current song without a vote',
                category: 'DJ'
            },
            {
                name: 'next',
                help: 'Skip without removing from queue in repeat mode',
                description: 'Skips to the next track without removing the current one if repeat is enabled',
                category: 'DJ'
            },
            {
                name: 'movetrack',
                arguments: '<from> <to>',
                help: 'Changes play order in the queue',
                description: 'Moves a track from one position to another in the queue',
                category: 'DJ',
                args: [
                    { name: 'from', description: 'Current position in queue', required: true },
                    { name: 'to', description: 'New position in queue', required: true }
                ]
            },
            {
                name: 'pause',
                help: 'Pauses the current track',
                description: 'Pauses playback of the current song',
                category: 'DJ'
            },
            {
                name: 'playnext',
                arguments: '<title|URL>',
                help: 'Specify the next song to play',
                description: 'Adds a song to play immediately after the current one',
                category: 'DJ',
                args: [
                    { name: 'query', description: 'Title or URL of the song', required: true }
                ]
            },
            {
                name: 'repeat',
                arguments: '[all|on|single|one|off]',
                help: 'Set the repeat mode',
                description: 'Sets how the bot should repeat songs after they finish',
                category: 'DJ',
                args: [
                    { name: 'mode', description: 'all, on, single, one, or off', required: false }
                ]
            },
            {
                name: 'skipto',
                arguments: '<position>',
                help: 'Skips to the specified track',
                description: 'Skips to a specific song in the queue by position',
                category: 'DJ',
                args: [
                    { name: 'position', description: 'Position in the queue to skip to', required: true }
                ]
            },
            {
                name: 'forcetoend',
                help: 'Toggle fair/normal song addition mode',
                description: 'Toggles between fair and normal song addition modes',
                category: 'DJ'
            },
            {
                name: 'stop',
                help: 'Stops playback and clears the queue',
                description: 'Stops all playback and clears the queue',
                category: 'DJ'
            },
            {
                name: 'prefix',
                arguments: '<prefix|NONE>',
                help: 'Set a server-specific prefix',
                description: 'Changes the command prefix for this server',
                category: 'Admin',
                args: [
                    { name: 'prefix', description: 'New prefix or NONE for default', required: true }
                ]
            },
            {
                name: 'setdj',
                arguments: '<role name|NONE>',
                help: 'Set the DJ role for bot commands',
                description: 'Specifies which role can use DJ commands',
                category: 'Admin',
                args: [
                    { name: 'role', description: 'Role name or NONE', required: true }
                ]
            },
            {
                name: 'setskip',
                arguments: '<0 - 100>',
                help: 'Set the skip ratio for the server',
                description: 'Sets the percentage of users needed to skip a song',
                category: 'Admin',
                args: [
                    { name: 'ratio', description: 'Percentage (0-100)', required: true }
                ]
            },
            {
                name: 'settc',
                arguments: '<channel name|NONE>',
                help: 'Set the bot\'s command channel',
                description: 'Restricts the bot to only accept commands in the specified channel',
                category: 'Admin',
                args: [
                    { name: 'channel', description: 'Channel name or NONE', required: true }
                ]
            },
            {
                name: 'setvc',
                arguments: '<channel name|NONE>',
                help: 'Fix the voice channel for playback',
                description: 'Sets a specific voice channel for the bot to use',
                category: 'Admin',
                args: [
                    { name: 'channel', description: 'Voice channel name or NONE', required: true }
                ]
            },
            {
                name: 'setvcstatus',
                arguments: '<true|false>',
                help: 'Set whether to display \'Playing\' in VC status',
                description: 'Toggles display of the "Playing" status in voice channel',
                category: 'Admin',
                args: [
                    { name: 'enabled', description: 'true or false', required: true }
                ]
            },
            {
                name: 'autoplaylist',
                arguments: '<name|NONE|なし>',
                help: 'Set the server\'s autoplaylist',
                description: 'Sets a playlist to play automatically when queue is empty',
                category: 'Admin',
                args: [
                    { name: 'name', description: 'Playlist name, NONE, or なし', required: true }
                ]
            },
            {
                name: 'slist',
                help: 'Sets the DJ role for bot commands',
                description: 'Allows setting the DJ role for using bot commands',
                category: 'Owner'
            },
            {
                name: 'debug',
                help: 'Displays debug information',
                description: 'Shows detailed debug information about the bot',
                category: 'Owner'
            },
            {
                name: 'setavatar',
                arguments: '<url>',
                help: 'Sets the bot\'s avatar',
                description: 'Changes the bot\'s profile picture',
                category: 'Owner',
                args: [
                    { name: 'url', description: 'URL of the new avatar image', required: true }
                ]
            },
            {
                name: 'setgame',
                arguments: '[action] [game]',
                help: 'Sets the game the bot is playing',
                description: 'Changes the bot\'s status message',
                category: 'Owner',
                args: [
                    { name: 'action', description: 'Activity type', required: false },
                    { name: 'game', description: 'Game name to display', required: false }
                ]
            },
            {
                name: 'setname',
                arguments: '<name>',
                help: 'Sets the bot\'s name',
                description: 'Changes the bot\'s display name',
                category: 'Owner',
                args: [
                    { name: 'name', description: 'New name for the bot', required: true }
                ]
            },
            {
                name: 'setstatus',
                arguments: '<status>',
                help: 'Sets the status that the bot displays',
                description: 'Changes the bot\'s online status',
                category: 'Owner',
                args: [
                    { name: 'status', description: 'Status to display', required: true }
                ]
            },
            {
                name: 'publist',
                arguments: '<append|delete|make|all|show>',
                help: 'Playlist Management',
                description: 'Manage public playlists with various subcommands',
                category: 'Owner',
                args: [
                    { name: 'subcommand', description: 'Action to perform with public playlists', required: true }
                ]
            },
            {
                name: 'shutdown',
                help: 'Shutdown safely',
                description: 'Safely shuts down the bot',
                category: 'Owner'
            }
        ];
        
        // Focus the command input
        document.getElementById('command-input').focus();
    }

    // Handle command input for autocomplete
    function handleCommandInput(e) {
        const input = e.target;
        const commandText = input.value.trim();
        currentInputValue = commandText;
        
        // Hide preview by default
        hideCommandPreview();
        
        // Skip empty input
        if (!commandText) {
            hideCommandSuggestions();
            return;
        }
        
        // If input starts with /, remove it for processing
        const processedText = commandText.startsWith('/') ? commandText.substring(1) : commandText;
        const parts = processedText.split(' ');
        const commandPart = parts[0].toLowerCase();
        
        // Find matching commands
        const matches = availableCommands.filter(cmd => 
            cmd.name.toLowerCase().startsWith(commandPart) ||
            (cmd.aliases && cmd.aliases.some(alias => alias.toLowerCase().startsWith(commandPart)))
        );
        
        if (matches.length > 0) {
            // If we have exact command match and more than one part, show command preview
            const exactMatch = matches.find(cmd => cmd.name.toLowerCase() === commandPart || 
                (cmd.aliases && cmd.aliases.some(alias => alias.toLowerCase() === commandPart)));
            
            if (exactMatch && parts.length > 1) {
                // Show preview for the command
                showCommandPreview(exactMatch, parts.slice(1).join(' '));
                hideCommandSuggestions();
            } else {
                // Show command suggestions
                showCommandSuggestions(matches);
            }
        } else {
            hideCommandSuggestions();
        }
    }

    // Handle keyboard navigation in command suggestions
    function handleCommandKeyDown(e) {
        const autocomplete = document.getElementById('command-autocomplete');
        const suggestions = autocomplete.querySelectorAll('.command-suggestion');
        
        // If no suggestions or autocomplete is hidden, return
        if (suggestions.length === 0 || !autocomplete.classList.contains('show')) {
            return;
        }
        
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
            case 'Enter':
                if (selectedCommandIndex >= 0 && selectedCommandIndex < suggestions.length) {
                    e.preventDefault();
                    applySuggestion(suggestions[selectedCommandIndex]);
                }
                break;
            case 'Escape':
                e.preventDefault();
                hideCommandSuggestions();
                break;
        }
    }

    // Navigate through suggestions with keyboard
    function navigateSuggestions(direction, suggestions) {
        // Clear current selection
        suggestions.forEach(s => s.classList.remove('selected'));
        
        // Update selected index
        selectedCommandIndex += direction;
        
        // Handle bounds
        if (selectedCommandIndex < 0) {
            selectedCommandIndex = suggestions.length - 1;
        } else if (selectedCommandIndex >= suggestions.length) {
            selectedCommandIndex = 0;
        }
        
        // Apply new selection
        if (selectedCommandIndex >= 0) {
            const selected = suggestions[selectedCommandIndex];
            selected.classList.add('selected');
            selected.scrollIntoView({ block: 'nearest' });
        }
    }

    // Show command suggestions in the dropdown
    function showCommandSuggestions(commands) {
        const autocomplete = document.getElementById('command-autocomplete');
        autocomplete.innerHTML = '';
        
        commands.forEach((cmd, index) => {
            const suggestion = document.createElement('div');
            suggestion.className = 'command-suggestion';
            suggestion.setAttribute('data-command', cmd.name);
            
            // Add class if this is selected
            if (index === selectedCommandIndex) {
                suggestion.classList.add('selected');
            }
            
            // Create suggestion content
            const nameContent = document.createElement('div');
            
            const nameSpan = document.createElement('span');
            nameSpan.className = 'command-suggestion-name';
            nameSpan.textContent = '/' + cmd.name;
            
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
                applySuggestion(suggestion);
            });
            
            autocomplete.appendChild(suggestion);
        });
        
        // Show the autocomplete
        autocomplete.classList.add('show');
        
        // Reset selected index if needed
        if (selectedCommandIndex < 0 || selectedCommandIndex >= commands.length) {
            selectedCommandIndex = 0;
            const firstSuggestion = autocomplete.querySelector('.command-suggestion');
            if (firstSuggestion) {
                firstSuggestion.classList.add('selected');
            }
        }
    }

    // Hide command suggestions
    function hideCommandSuggestions() {
        const autocomplete = document.getElementById('command-autocomplete');
        autocomplete.classList.remove('show');
        selectedCommandIndex = -1;
    }

    // Apply a suggestion to the input
    function applySuggestion(suggestionElement) {
        const commandName = suggestionElement.getAttribute('data-command');
        const inputElement = document.getElementById('command-input');
        
        // Get the command from available commands
        const command = availableCommands.find(cmd => cmd.name === commandName);
        
        // Replace only the command part and keep any arguments
        const currentValue = inputElement.value;
        const parts = currentValue.split(' ');
        
        if (currentValue.startsWith('/')) {
            inputElement.value = '/' + commandName + (parts.length > 1 ? ' ' + parts.slice(1).join(' ') : '');
        } else {
            inputElement.value = commandName + (parts.length > 1 ? ' ' + parts.slice(1).join(' ') : '');
        }
        
        // Set cursor position after command name
        const cursorPos = (currentValue.startsWith('/') ? 1 : 0) + commandName.length + 1;
        inputElement.setSelectionRange(cursorPos, cursorPos);
        
        // Hide suggestions
        hideCommandSuggestions();
        
        // Show command preview
        showCommandPreview(command, parts.length > 1 ? parts.slice(1).join(' ') : '');
        
        // Focus input
        inputElement.focus();
    }

    // Show command preview
    function showCommandPreview(command, args) {
        const previewElement = document.getElementById('command-preview');
        
        // Create preview content
        let html = `
            <div class="preview-title">
                <i class="fas fa-terminal"></i> /${command.name}
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
        previewElement.innerHTML = html;
        previewElement.classList.add('show');
    }

    // Hide command preview
    function hideCommandPreview() {
        const previewElement = document.getElementById('command-preview');
        previewElement.classList.remove('show');
    }

    // Execute Discord command
    async function executeDiscordCommand() {
        const commandInput = document.getElementById('command-input');
        const command = commandInput.value.trim();
        
        if (!command) {
            UI.showToast('Please enter a command', false);
            return;
        }
        
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
            
            // Add to command history
            addToCommandHistory(command, data.message);
            
            // Show toast with command result
            UI.showToast(data.message, data.success);
            
            // Clear input on success
            if (data.success) {
                commandInput.value = '';
                
                // Hide autocomplete and preview
                hideCommandSuggestions();
                hideCommandPreview();
                
                // If it's a volume command, update the displayed volume
                if (command.toLowerCase().startsWith('/vol') || command.toLowerCase().startsWith('vol')) {
                    Player.fetchStatus();
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
                    Player.fetchQueue();
                    Player.fetchStatus();
                }
            }
        } catch (error) {
            console.error('Error executing command:', error);
            UI.showToast('Error executing command: ' + error.message, false);
        }
    }

    // Add a command and its response to history
    function addToCommandHistory(command, response) {
        const timestamp = new Date().toLocaleTimeString();
        const historyItem = { command, response, timestamp };
        
        // Add to the beginning of the array
        commandHistory.unshift(historyItem);
        
        // Limit history to last 20 commands
        if (commandHistory.length > 20) {
            commandHistory = commandHistory.slice(0, 20);
        }
        
        // Save to localStorage
        saveCommandHistory();
        
        // Update the UI
        displayCommandHistory();
    }

    // Save command history to localStorage
    function saveCommandHistory() {
        localStorage.setItem('jmusicbot_command_history', JSON.stringify(commandHistory));
    }

    // Load command history from localStorage
    function loadCommandHistory() {
        const savedHistory = localStorage.getItem('jmusicbot_command_history');
        if (savedHistory) {
            try {
                commandHistory = JSON.parse(savedHistory);
                displayCommandHistory();
            } catch (error) {
                console.error('Error loading command history:', error);
                commandHistory = [];
            }
        }
    }

    // Clear command history
    function clearCommandHistory() {
        commandHistory = [];
        saveCommandHistory();
        displayCommandHistory();
        UI.showToast('Command history cleared', true);
    }

    // Display command history in the UI
    function displayCommandHistory() {
        const historyList = document.getElementById('command-history-list');
        historyList.innerHTML = '';
        
        if (commandHistory.length === 0) {
            historyList.innerHTML = '<div style="color: #B9BBBE; text-align: center; padding: 10px;">No command history</div>';
            return;
        }
        
        commandHistory.forEach(item => {
            const historyItem = document.createElement('div');
            historyItem.className = 'command-history-item';
            
            const commandText = document.createElement('div');
            commandText.className = 'command-history-command';
            commandText.textContent = `${item.command} (${item.timestamp})`;
            
            const responseText = document.createElement('div');
            responseText.className = 'command-history-response';
            responseText.textContent = item.response;
            
            historyItem.appendChild(commandText);
            historyItem.appendChild(responseText);
            
            // Allow clicking on a history item to use that command again
            historyItem.addEventListener('click', () => {
                document.getElementById('command-input').value = item.command;
                document.getElementById('command-input').focus();
            });
            
            historyList.appendChild(historyItem);
        });
    }

    // Public API
    return {
        initializeCommandSystem,
        handleCommandInput,
        handleCommandKeyDown,
        navigateSuggestions,
        showCommandSuggestions,
        hideCommandSuggestions,
        applySuggestion,
        showCommandPreview,
        hideCommandPreview,
        executeDiscordCommand,
        addToCommandHistory,
        saveCommandHistory,
        loadCommandHistory,
        clearCommandHistory,
        displayCommandHistory,
        
        // Add a function to get available commands for external usage
        getAvailableCommands: function() {
            return availableCommands;
        }
    };
})(); 