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
            // Note: Bot status updates are now started in initializeApp() in script.js
            // This ensures BotProfile module is loaded before we call it
            break;
        case 'modals':
            setupModalEvents();
            break;
        case 'player':
            if (typeof Player !== 'undefined') {
                Player.fetchStatus();
                Player.fetchQueue();
                Player.setupProgressBarInteraction();
                Player.setupPlayerControls();
            }
            break;
        // Add other component-specific initializations as needed
    }
});

// Setup events for header navigation buttons
function setupHeaderEvents() {
    console.log('Setting up header events...');
    
    // Helper to handle navigation
    const handleNav = (e, route) => {
        console.log(`Navigation requested to: ${route}`);
        e.preventDefault();
        e.stopPropagation();
        
        if (typeof Router !== 'undefined') {
            Router.navigateTo(route);
        } else {
            console.error('Router is undefined! Fallback to reload.');
            window.location.href = route === 'player' ? 'index.html' : `${route}.html`;
        }
    };

    // Player button
    const playerBtn = document.getElementById('player-btn');
    if (playerBtn) {
        // Remove old listeners by cloning
        const newBtn = playerBtn.cloneNode(true);
        playerBtn.parentNode.replaceChild(newBtn, playerBtn);
        
        newBtn.addEventListener('click', function(e) {
            if (this.hasAttribute('disabled')) return;
            handleNav(e, 'player');
        });
    }
    
    // Channels button
    const channelsBtn = document.getElementById('channels-btn');
    if (channelsBtn) {
        const newBtn = channelsBtn.cloneNode(true);
        channelsBtn.parentNode.replaceChild(newBtn, channelsBtn);
        
        newBtn.addEventListener('click', function(e) {
            if (this.hasAttribute('disabled')) return;
            handleNav(e, 'channels');
        });
    }
    
    // History button
    const historyBtn = document.getElementById('history-btn');
    if (historyBtn) {
        const newBtn = historyBtn.cloneNode(true);
        historyBtn.parentNode.replaceChild(newBtn, historyBtn);
        
        newBtn.addEventListener('click', function(e) {
            if (this.hasAttribute('disabled')) return;
            handleNav(e, 'history');
        });
    }
    
    // Advanced dropdown
    const advancedDropdownBtn = document.getElementById('advanced-dropdown-btn');
    const advancedDropdownContent = document.getElementById('advanced-dropdown-content');
    if (advancedDropdownBtn && advancedDropdownContent) {
        // Clone to remove existing listeners
        const newBtn = advancedDropdownBtn.cloneNode(true);
        advancedDropdownBtn.parentNode.replaceChild(newBtn, advancedDropdownBtn);
        
        newBtn.addEventListener('click', function(e) {
            e.stopPropagation();
            advancedDropdownContent.classList.toggle('show');
        });
        
        // Close the dropdown when clicking outside of it
        document.addEventListener('click', function(e) {
            if (!newBtn.contains(e.target)) {
                advancedDropdownContent.classList.remove('show');
            }
        });
    }
    
    // Commands button - Handled by UI.js
    /*
    const commandsBtn = document.getElementById('commands-btn');
    const commandsModal = document.getElementById('commands-modal');
    if (commandsBtn && commandsModal) {
        commandsBtn.addEventListener('click', function() {
            commandsModal.style.display = 'flex';
            advancedDropdownContent.classList.remove('show'); // Hide dropdown after click
        });
    }
    */
    
    // Console button - Handled by UI.js
    /*
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
    */
    
    // Config button - Handled by UI.js
    /*
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
    */
    
    // Reboot button - Handled by UI.js
    /*
    const rebootBtn = document.getElementById('reboot-btn');
    if (rebootBtn) {
        rebootBtn.addEventListener('click', function() {
            advancedDropdownContent.classList.remove('show'); // Hide dropdown after click
            if (confirm('Are you sure you want to reboot the bot?')) {
                // ...
            }
        });
    }
    */
    
    // Bot profile button - Handled by UI.js
    /*
    const botProfileBtn = document.getElementById('bot-profile-btn');
    const botProfileModal = document.getElementById('bot-profile-modal');
    if (botProfileBtn && botProfileModal) {
        botProfileBtn.addEventListener('click', function() {
            botProfileModal.style.display = 'flex';
        });
    }
    */
    
    // Initialize server dropdown via UI module if available
    if (typeof UI !== 'undefined' && typeof UI.initializeServerDropdown === 'function') {
        // Let the UI module handle the server dropdown
        // UI.initializeServerDropdown(); 
        // Commented out to avoid double initialization as UI.initializeUI() calls this too
    }
}

// Setup events for modal close buttons and other modal functionality
function setupModalEvents() {
    // This function is called when 'modals' component is loaded
    // We can leave the close button setup here as it's specific to the modals component
    // But opening buttons should be handled by UI.js to ensure they work globally
    
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