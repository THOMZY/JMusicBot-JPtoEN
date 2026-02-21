/**
 * Console Manager Module - Handles bot console logs and interactions
 */

const ConsoleManager = (function() {
    
    let autoRefreshInterval = null;
    const REFRESH_INTERVAL = 2000; // Refresh every 2 seconds
    
    // Helper to escape HTML to prevent XSS
    function escapeHtml(unsafe) {
        return unsafe
             .replace(/&/g, "&amp;")
             .replace(/</g, "&lt;")
             .replace(/>/g, "&gt;")
             .replace(/"/g, "&quot;")
             .replace(/'/g, "&#039;");
    }

    // Load console logs
    async function loadConsoleLogs() {
        try {
            const response = await fetch('/api/console/logs');
            const logs = await response.json();
            
            const consoleLog = document.getElementById('console-log');
            consoleLog.innerHTML = '';
            
            if (logs.length === 0) {
                consoleLog.innerHTML = '<div style="color: #858585; padding: 10px; text-align: center;">No logs available</div>';
                return;
            }
            
            let previousEntry = null;
            let stackTraceContainer = null;

            logs.forEach(log => {
                // Check if this line looks like part of a stack trace
                // Starts with whitespace+at (Java) or Caused by
                const isStackLine = /^\s+at\s/.test(log) || /^\s*Caused by:/.test(log) || /^\s+\.\.\.\s\d+\smore/.test(log);

                if (isStackLine && previousEntry) {
                    // This is a stack trace line and belongs to the previous entry
                    
                    if (!stackTraceContainer) {
                        // Initialize container if it doesn't exist for this group
                        stackTraceContainer = document.createElement('div');
                        stackTraceContainer.className = 'console-stack-trace';
                        stackTraceContainer.style.display = 'none';
                        
                        // Mark the parent entry as expandable
                        previousEntry.classList.add('has-stack-trace');
                        // Use a closure to capture the container
                        const container = stackTraceContainer;
                        previousEntry.onclick = function() {
                            const isHidden = container.style.display === 'none';
                            container.style.display = isHidden ? 'block' : 'none';
                            this.classList.toggle('expanded', isHidden);
                        };
                        
                        // Insert after the parent
                        previousEntry.insertAdjacentElement('afterend', stackTraceContainer);
                    }
                    
                    // Create and append the stack line
                    const stackLine = document.createElement('div');
                    stackLine.className = 'console-log-entry';
                    stackLine.innerHTML = escapeHtml(log);
                    stackTraceContainer.appendChild(stackLine);
                    
                } else {
                    // Not a stack line, or no previous entry to attach to
                    // This starts a new potential group
                    stackTraceContainer = null;
                    
                    const logLine = document.createElement('div');
                    logLine.className = 'console-log-entry';
                    
                    // Escape HTML content first
                    let content = escapeHtml(log);
                    
                    // Extract and style timestamp if present (format [HH:mm:ss])
                    const timeMatch = content.match(/^\[(\d{2}:\d{2}:\d{2})\]/);
                    if (timeMatch) {
                        content = content.replace(timeMatch[0], `<span class="console-log-timestamp">${timeMatch[0]}</span>`);
                    }

                    // Add specific styling based on log content
                    const lowerLog = log.toLowerCase();
                    
                    if (log.trim().startsWith('>')) {
                        logLine.classList.add('command');
                    } else if (lowerLog.includes('error') || lowerLog.includes('exception') || lowerLog.includes('fail')) {
                        logLine.classList.add('error');
                    } else if (lowerLog.includes('warn')) {
                        logLine.classList.add('warn');
                    } else if (lowerLog.includes('success') || lowerLog.includes('loaded') || lowerLog.includes('connected')) {
                        logLine.classList.add('success');
                    } else if (lowerLog.includes('info')) {
                        logLine.classList.add('info');
                    }
                    
                    logLine.innerHTML = content;
                    consoleLog.appendChild(logLine);
                    previousEntry = logLine;
                }
            });
            
            // Auto-scroll to bottom if enabled
            if (document.getElementById('auto-scroll-checkbox').checked) {
                // Smooth scroll
                consoleLog.scrollTo({
                    top: consoleLog.scrollHeight,
                    behavior: 'smooth'
                });
            }
            
        } catch (error) {
            console.error('Error loading console logs:', error);
            const consoleLog = document.getElementById('console-log');
            consoleLog.innerHTML = '<div class="console-log-entry error">Error loading console logs: ' + error.message + '</div>';
        }
    }

    // Start auto-refresh when console modal is opened
    function startAutoRefresh() {
        // Stop any existing interval first
        stopAutoRefresh();
        
        // Load logs immediately
        loadConsoleLogs();
        
        // Set up interval to refresh logs
        autoRefreshInterval = setInterval(() => {
            loadConsoleLogs();
        }, REFRESH_INTERVAL);
        
        console.log('Console auto-refresh started');
    }

    // Stop auto-refresh when console modal is closed
    function stopAutoRefresh() {
        if (autoRefreshInterval) {
            clearInterval(autoRefreshInterval);
            autoRefreshInterval = null;
            console.log('Console auto-refresh stopped');
        }
    }

    // Public API
    return {
        loadConsoleLogs,
        startAutoRefresh,
        stopAutoRefresh
    };
})(); 