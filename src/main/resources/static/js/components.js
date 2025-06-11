/**
 * JMusicBot Web Panel - Component System
 * This file provides utilities for loading and managing modular UI components
 */

// Store references to loaded components
const loadedComponents = {};

/**
 * Load an HTML component from components/[name].html and inject it into the specified container
 * @param {string} componentName - Name of the component (filename without extension)
 * @param {string|HTMLElement} targetSelector - CSS selector or DOM element to inject the component into
 * @param {Object} options - Additional options for the component
 * @returns {Promise<HTMLElement>} - Promise that resolves with the injected component element
 */
async function loadComponent(componentName, targetSelector, options = {}) {
    try {
        console.log(`Loading component: ${componentName}`);
        
        // Get target element
        const targetElement = typeof targetSelector === 'string' 
            ? document.querySelector(targetSelector) 
            : targetSelector;
        
        if (!targetElement) {
            throw new Error(`Target element "${targetSelector}" not found`);
        }
        
        // Fetch the component HTML
        const response = await fetch(`components/${componentName}.html`);
        if (!response.ok) {
            throw new Error(`Failed to load component ${componentName}: ${response.status} ${response.statusText}`);
        }
        
        // Get the HTML content
        const html = await response.text();
        
        // Set the inner HTML of the target element
        targetElement.innerHTML = html;
        
        // Store a reference to the component
        loadedComponents[componentName] = {
            element: targetElement,
            name: componentName,
            options: options
        };
        
        // Ensure styles and scripts have time to be processed
        await new Promise(resolve => setTimeout(resolve, 50));
        
        // If an initialization function is specified
        if (options.init && typeof options.init === 'function') {
            options.init(targetElement);
        }
        
        // Dispatch a component loaded event
        const event = new CustomEvent('component:loaded', {
            detail: {
                name: componentName,
                element: targetElement
            }
        });
        document.dispatchEvent(event);
        
        console.log(`Component ${componentName} loaded successfully`);
        return targetElement;
    } catch (error) {
        console.error(`Component loading error for ${componentName}:`, error);
        // Retry once if we get a network error (helpful for Linux environments)
        if (error.name === 'TypeError' && componentName !== 'retry') {
            console.warn(`Retrying component ${componentName} in 200ms...`);
            await new Promise(resolve => setTimeout(resolve, 200));
            return loadComponent(componentName, targetSelector, {...options, retry: true});
        }
        throw error;
    }
}

/**
 * Creates a standardized page structure with header, content area, and modals
 * @param {string} pageTitle - Title for the page
 * @param {string} activeNav - ID of the active navigation button
 * @param {Array} additionalScripts - Array of additional script files to load
 */
async function initPageStructure(pageTitle, activeNav, additionalScripts = []) {
    document.title = pageTitle + ' - JMusicBot Web Panel';
    
    // Load the header component
    await loadComponent('header', '#header-container');
    
    // Set the active navigation button
    if (activeNav) {
        const navBtn = document.getElementById(activeNav);
        if (navBtn) navBtn.setAttribute('disabled', 'true');
    }
    
    // Load the modals
    await loadComponent('modals', '#modals-container');
    
    // Load additional scripts if needed
    if (additionalScripts && additionalScripts.length > 0) {
        for (const script of additionalScripts) {
            await loadScript(`js/${script}`);
        }
    }
    
    // Dispatch a page initialized event
    const event = new CustomEvent('page:initialized', {
        detail: {
            title: pageTitle,
            activeNav: activeNav
        }
    });
    document.dispatchEvent(event);
}

/**
 * Dynamically load a JavaScript file
 * @param {string} src - Source path of the script to load
 * @returns {Promise} - Promise that resolves when the script is loaded
 */
function loadScript(src) {
    return new Promise((resolve, reject) => {
        const script = document.createElement('script');
        script.src = src;
        script.onload = resolve;
        script.onerror = reject;
        document.head.appendChild(script);
    });
}

// Listen for component loaded events to handle specific component initializations
document.addEventListener('component:loaded', function(e) {
    const componentName = e.detail.name;
    const element = e.detail.element;
    
    // Handle specific component initializations
    switch(componentName) {
        case 'header':
            setupHeaderEvents();
            break;
        case 'modals':
            setupModalEvents();
            break;
        case 'player':
            if (typeof Player !== 'undefined') {
                Player.updateUI();
            }
            break;
        // Add other component-specific initializations as needed
    }
});

// Setup events for header navigation buttons
function setupHeaderEvents() {
    // Player button
    const playerBtn = document.getElementById('player-btn');
    if (playerBtn && !playerBtn.hasAttribute('disabled')) {
        playerBtn.addEventListener('click', function() {
            window.location.href = 'index.html';
        });
    }
    
    // Channels button
    const channelsBtn = document.getElementById('channels-btn');
    if (channelsBtn && !channelsBtn.hasAttribute('disabled')) {
        channelsBtn.addEventListener('click', function() {
            window.location.href = 'channels.html';
        });
    }
    
    // History button
    const historyBtn = document.getElementById('history-btn');
    if (historyBtn && !historyBtn.hasAttribute('disabled')) {
        historyBtn.addEventListener('click', function() {
            window.location.href = 'history.html';
        });
    }
    
    // Advanced dropdown
    const advancedDropdownBtn = document.getElementById('advanced-dropdown-btn');
    const advancedDropdownContent = document.getElementById('advanced-dropdown-content');
    if (advancedDropdownBtn && advancedDropdownContent) {
        advancedDropdownBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            advancedDropdownContent.classList.toggle('show');
        });
        
        // Close the dropdown when clicking outside of it
        document.addEventListener('click', function(e) {
            if (!advancedDropdownBtn.contains(e.target)) {
                advancedDropdownContent.classList.remove('show');
            }
        });
    }
    
    // Commands button
    const commandsBtn = document.getElementById('commands-btn');
    const commandsModal = document.getElementById('commands-modal');
    if (commandsBtn && commandsModal) {
        commandsBtn.addEventListener('click', function() {
            commandsModal.style.display = 'flex';
            advancedDropdownContent.classList.remove('show'); // Hide dropdown after click
        });
    }
    
    // Console button
    const consoleBtn = document.getElementById('console-btn');
    const consoleModal = document.getElementById('console-modal');
    if (consoleBtn && consoleModal) {
        consoleBtn.addEventListener('click', function() {
            consoleModal.style.display = 'flex';
            advancedDropdownContent.classList.remove('show'); // Hide dropdown after click
            if (typeof ConsoleManager !== 'undefined') {
                ConsoleManager.refreshLogs();
            }
        });
    }
    
    // Config button
    const configBtn = document.getElementById('config-btn');
    const configModal = document.getElementById('config-modal');
    if (configBtn && configModal) {
        configBtn.addEventListener('click', function() {
            configModal.style.display = 'flex';
            advancedDropdownContent.classList.remove('show'); // Hide dropdown after click
            if (typeof ConfigManager !== 'undefined') {
                ConfigManager.loadConfig();
            }
        });
    }
    
    // Reboot button
    const rebootBtn = document.getElementById('reboot-btn');
    if (rebootBtn) {
        rebootBtn.addEventListener('click', function() {
            advancedDropdownContent.classList.remove('show'); // Hide dropdown after click
            if (confirm('Are you sure you want to reboot the bot?')) {
                fetch('/api/reboot', { method: 'POST' })
                    .then(response => {
                        if (response.ok) {
                            if (typeof UI !== 'undefined') {
                                UI.showToast('Bot is rebooting...', 'info');
                            } else {
                                alert('Bot is rebooting...');
                            }
                        } else {
                            throw new Error('Failed to reboot the bot');
                        }
                    })
                    .catch(error => {
                        console.error('Reboot error:', error);
                        if (typeof UI !== 'undefined') {
                            UI.showToast('Failed to reboot the bot', 'error');
                        } else {
                            alert('Failed to reboot the bot');
                        }
                    });
            }
        });
    }
    
    // Bot profile button
    const botProfileBtn = document.getElementById('bot-profile-btn');
    const botProfileModal = document.getElementById('bot-profile-modal');
    if (botProfileBtn && botProfileModal) {
        botProfileBtn.addEventListener('click', function() {
            botProfileModal.style.display = 'flex';
        });
    }
    
    // Initialize server dropdown via UI module if available
    if (typeof UI !== 'undefined' && typeof UI.initializeServerDropdown === 'function') {
        // Let the UI module handle the server dropdown
        UI.initializeServerDropdown();
        console.log('Server dropdown initialization delegated to UI module');
    }
}

// Setup events for modal close buttons and other modal functionality
function setupModalEvents() {
    // Console modal close
    const consoleModalClose = document.getElementById('console-modal-close');
    const consoleModal = document.getElementById('console-modal');
    if (consoleModalClose && consoleModal) {
        consoleModalClose.addEventListener('click', function() {
            consoleModal.style.display = 'none';
        });
    }
    
    // Commands modal close
    const commandsModalClose = document.getElementById('commands-modal-close');
    const commandsModal = document.getElementById('commands-modal');
    if (commandsModalClose && commandsModal) {
        commandsModalClose.addEventListener('click', function() {
            commandsModal.style.display = 'none';
        });
    }
    
    // Config modal close
    const configModalClose = document.getElementById('config-modal-close');
    const configModal = document.getElementById('config-modal');
    if (configModalClose && configModal) {
        configModalClose.addEventListener('click', function() {
            configModal.style.display = 'none';
        });
    }
    
    // Bot profile modal close
    const botProfileModalClose = document.getElementById('bot-profile-modal-close');
    const botProfileModal = document.getElementById('bot-profile-modal');
    if (botProfileModalClose && botProfileModal) {
        botProfileModalClose.addEventListener('click', function() {
            botProfileModal.style.display = 'none';
        });
    }
    
    // Close all modals when clicking outside the modal content
    const allModals = document.querySelectorAll('.modal');
    window.addEventListener('click', function(event) {
        allModals.forEach(modal => {
            if (event.target === modal) {
                modal.style.display = 'none';
            }
        });
    });
} 