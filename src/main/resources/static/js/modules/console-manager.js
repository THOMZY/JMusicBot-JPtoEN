/**
 * Console Manager Module - Handles bot console logs and interactions
 */

const ConsoleManager = (function() {
    // Load console logs
    async function loadConsoleLogs() {
        try {
            const response = await fetch('/api/console/logs');
            const logs = await response.json();
            
            const consoleLog = document.getElementById('console-log');
            consoleLog.innerHTML = '';
            
            if (logs.length === 0) {
                consoleLog.innerHTML = '<div style="color: #B9BBBE;">No logs available</div>';
                return;
            }
            
            logs.forEach(log => {
                const logLine = document.createElement('div');
                logLine.className = 'console-log-entry';
                
                // Add specific styling based on log content
                if (log.startsWith('>')) {
                    logLine.className += ' command';
                } else if (log.toLowerCase().includes('error') || log.toLowerCase().includes('exception')) {
                    logLine.className += ' error';
                } else if (log.toLowerCase().includes('info') || log.toLowerCase().includes('loaded') || log.toLowerCase().includes('started')) {
                    logLine.className += ' info';
                }
                
                logLine.textContent = log;
                consoleLog.appendChild(logLine);
            });
            
            // Auto-scroll to bottom if enabled
            if (document.getElementById('auto-scroll-checkbox').checked) {
                consoleLog.scrollTop = consoleLog.scrollHeight;
            }
            
        } catch (error) {
            console.error('Error loading console logs:', error);
            const consoleLog = document.getElementById('console-log');
            consoleLog.innerHTML = '<div class="console-log-entry error">Error loading console logs: ' + error.message + '</div>';
        }
    }

    // Public API
    return {
        loadConsoleLogs
    };
})(); 