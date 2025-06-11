/**
 * Config Manager Module - Handles bot configuration
 */

const ConfigManager = (function() {
    // Load bot configuration
    async function loadConfig() {
        try {
            const response = await fetch('/api/console/config');
            const data = await response.json();
            
            const configEditor = document.getElementById('config-editor');
            
            if (data.success) {
                configEditor.value = data.content;
            } else {
                configEditor.value = data.message || 'Error loading configuration';
            }
            
        } catch (error) {
            console.error('Error loading config:', error);
            document.getElementById('config-editor').value = 'Error loading configuration: ' + error.message;
        }
    }

    // Save bot configuration
    async function saveConfig() {
        const configContent = document.getElementById('config-editor').value;
        const configResult = document.getElementById('config-result');
        
        try {
            const response = await fetch('/api/console/config', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json'
                },
                body: JSON.stringify({ content: configContent })
            });
            
            const data = await response.json();
            
            UI.showToast(data.success ? 'Configuration saved successfully' : (data.message || 'Failed to save configuration'), data.success);
            
        } catch (error) {
            console.error('Error saving config:', error);
            UI.showToast('Error saving configuration: ' + error.message, false);
        }
    }

    // Public API
    return {
        loadConfig,
        saveConfig
    };
})(); 