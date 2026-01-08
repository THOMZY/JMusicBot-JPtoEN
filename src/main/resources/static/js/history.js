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
        type: 'all', // all, spotify, youtube, soundcloud, radio, gensokyo, local, instagram, tiktok, twitter
        timeRange: 'all', // all, today, week, month
        requester: 'all', // all, or specific requester name
        guildId: 'all' // all, or specific guild ID
    };

    // Date filter (single day or inclusive range)
    let dateFilterStart = null; // yyyy-MM-dd
    let dateFilterEnd = null;   // yyyy-MM-dd (null until second click)
    let datePickerViewYear = null;
    let datePickerViewMonth = null; // 0-11
    let currentGuildId = null;
    let servers = [];
    let uniqueRequesters = []; // Store unique requester names
    let isLoadingHistory = false;
    let hasMorePages = true;
    let infiniteScrollObserver = null;
    let historyFetchController = null;
    let requestersFetchController = null;
    let guildsFetchController = null;
    let currentInitToken = null;
    const infiniteScrollSentinelId = 'history-infinite-sentinel';

    const isHistoryViewActive = () => {
        const container = document.getElementById('history-container');
        return !!container && document.body.contains(container);
    };

    /**
     * Reset pagination and data state (used when entering view fresh)
     */
    const resetHistoryState = () => {
        currentInitToken = Symbol('history-init');
        disconnectInfiniteScrollObserver();
        if (historyFetchController) {
            historyFetchController.abort();
            historyFetchController = null;
        }
        if (requestersFetchController) {
            requestersFetchController.abort();
            requestersFetchController = null;
        }
        if (guildsFetchController) {
            guildsFetchController.abort();
            guildsFetchController = null;
        }
        historyData = [];
        totalRecords = 0;
        currentPage = 1;
        hasMorePages = true;
        isLoadingHistory = false;
    };

    // Proxy Instagram thumbnails to bypass referrer blocking while leaving local/static assets untouched
    const safeThumbnail = (url) => {
        if (!url) return url;
        const lower = url.toLowerCase();
        const isLocalAsset = lower.startsWith('/') || lower.startsWith('local_artwork/') || lower.startsWith('data:');
        if (isLocalAsset) return url;
        const isInstagram = lower.includes('instagram.com') || lower.includes('cdninstagram.com');
        if (isInstagram) {
            return `https://images.weserv.nl/?url=${encodeURIComponent(url)}&w=640&h=640&fit=inside`;
        }
        return url;
    };

    // DOM Elements
    const historyElements = {
        historyContainer: null,
        historyList: null,
        pageInfo: null,
        pagination: null,
        searchInput: null,
        filterButtons: null,
        contentWrapper: null,
        serverDropdownBtn: null,
        serverDropdownContent: null,
        selectedServerIcon: null,
        selectedServerName: null,
        requesterSelect: null,
        dateFilterBtn: null,
        dateFilterLabel: null,
        datePicker: null,
        datePrevBtn: null,
        dateNextBtn: null,
        dateClearBtn: null,
        dateMonthSelect: null,
        dateYearSelect: null,
        dateGrid: null,
        dateSelectionLabel: null,
        dateSelectionText: null
    };

    const pad2 = (n) => String(n).padStart(2, '0');

    const formatISODate = (date) => {
        const y = date.getFullYear();
        const m = pad2(date.getMonth() + 1);
        const d = pad2(date.getDate());
        return `${y}-${m}-${d}`;
    };

    const parseISODate = (iso) => {
        if (!iso) return null;
        const [y, m, d] = iso.split('-').map(Number);
        if (!y || !m || !d) return null;
        const dt = new Date(y, m - 1, d);
        if (Number.isNaN(dt.getTime())) return null;
        return dt;
    };

    const compareISO = (a, b) => {
        // Works because yyyy-MM-dd lexicographically sortable
        if (a === b) return 0;
        return a < b ? -1 : 1;
    };

    const getEffectiveDateEnd = () => {
        if (!dateFilterStart) return null;
        return dateFilterEnd || dateFilterStart;
    };

    const updateDateButtonState = () => {
        if (!historyElements.dateFilterBtn) return;
        if (dateFilterStart) {
            historyElements.dateFilterBtn.classList.add('active');
            return;
        }
        historyElements.dateFilterBtn.classList.remove('active');
    };

    const updateDateSelectionLabel = () => {
        const target = historyElements.dateSelectionText || historyElements.dateSelectionLabel;
        if (!target) return;
        if (!dateFilterStart) {
            target.textContent = 'No date filter';
            return;
        }
        const end = getEffectiveDateEnd();
        if (!dateFilterEnd || dateFilterEnd === dateFilterStart) {
            target.textContent = `Selected: ${dateFilterStart}`;
            return;
        }
        target.textContent = `Selected: ${dateFilterStart} → ${end}`;
    };

    const updateDateFilterButtonLabel = () => {
        if (!historyElements.dateFilterLabel) return;
        if (!dateFilterStart) {
            historyElements.dateFilterLabel.textContent = 'All';
            return;
        }
        const end = getEffectiveDateEnd();
        if (!dateFilterEnd || dateFilterEnd === dateFilterStart) {
            historyElements.dateFilterLabel.textContent = dateFilterStart;
            return;
        }
        historyElements.dateFilterLabel.textContent = `${dateFilterStart} → ${end}`;
    };

    const setDateFilter = (startIso, endIso) => {
        dateFilterStart = startIso || null;
        dateFilterEnd = endIso || null;
        updateDateButtonState();
        updateDateSelectionLabel();
        updateDateFilterButtonLabel();
    };

    const openDatePicker = () => {
        if (!historyElements.datePicker) return;
        historyElements.datePicker.hidden = false;
        // Initialize view month to selected date or today
        const base = parseISODate(dateFilterStart) || new Date();
        datePickerViewYear = base.getFullYear();
        datePickerViewMonth = base.getMonth();
        renderDatePicker();
    };

    const closeDatePicker = () => {
        if (!historyElements.datePicker) return;
        historyElements.datePicker.hidden = true;
    };

    const toggleDatePicker = () => {
        if (!historyElements.datePicker) return;
        if (historyElements.datePicker.hidden) {
            openDatePicker();
        } else {
            closeDatePicker();
        }
    };

    const moveDatePickerMonth = (deltaMonths) => {
        if (datePickerViewYear === null || datePickerViewMonth === null) {
            const now = new Date();
            datePickerViewYear = now.getFullYear();
            datePickerViewMonth = now.getMonth();
        }
        const next = new Date(datePickerViewYear, datePickerViewMonth + deltaMonths, 1);
        datePickerViewYear = next.getFullYear();
        datePickerViewMonth = next.getMonth();
        renderDatePicker();
    };

    const getStartOfWeekMondayIndex = (year, month0, day) => {
        // Return 0..6 where 0=Mon
        const dow = new Date(year, month0, day).getDay(); // 0=Sun
        return (dow + 6) % 7;
    };

    const populateDateDropdowns = () => {
        if (!historyElements.dateMonthSelect || !historyElements.dateYearSelect) return;

        // Populate months if empty
        if (historyElements.dateMonthSelect.options.length === 0) {
            const monthNames = [
                'January','February','March','April','May','June',
                'July','August','September','October','November','December'
            ];
            monthNames.forEach((name, index) => {
                const option = document.createElement('option');
                option.value = index;
                option.textContent = name;
                historyElements.dateMonthSelect.appendChild(option);
            });
        }

        // Populate years
        const currentYear = new Date().getFullYear();
        const startYear = 2025;
        const endYear = currentYear + 10;
        
        // Check if we need to repopulate (e.g. if empty)
        if (historyElements.dateYearSelect.options.length === 0) {
             for (let y = startYear; y <= endYear; y++) {
                const option = document.createElement('option');
                option.value = y;
                option.textContent = y;
                historyElements.dateYearSelect.appendChild(option);
             }
        }
        
        // If datePickerViewYear is outside the range, add it
        if (datePickerViewYear) {
             let found = false;
             for(let i=0; i<historyElements.dateYearSelect.options.length; i++) {
                 if (parseInt(historyElements.dateYearSelect.options[i].value) === datePickerViewYear) {
                     found = true;
                     break;
                 }
             }
             if (!found) {
                 const option = document.createElement('option');
                 option.value = datePickerViewYear;
                 option.textContent = datePickerViewYear;
                 // Insert in order
                 let inserted = false;
                 for(let i=0; i<historyElements.dateYearSelect.options.length; i++) {
                     if (parseInt(historyElements.dateYearSelect.options[i].value) > datePickerViewYear) {
                         historyElements.dateYearSelect.insertBefore(option, historyElements.dateYearSelect.options[i]);
                         inserted = true;
                         break;
                     }
                 }
                 if (!inserted) {
                     historyElements.dateYearSelect.appendChild(option);
                 }
             }
        }
    };

    const renderDatePicker = () => {
        if (!historyElements.dateGrid) return;
        if (datePickerViewYear === null || datePickerViewMonth === null) return;

        populateDateDropdowns();
        
        if (historyElements.dateMonthSelect) historyElements.dateMonthSelect.value = datePickerViewMonth;
        if (historyElements.dateYearSelect) historyElements.dateYearSelect.value = datePickerViewYear;

        const firstDayOffset = getStartOfWeekMondayIndex(datePickerViewYear, datePickerViewMonth, 1);
        const daysInMonth = new Date(datePickerViewYear, datePickerViewMonth + 1, 0).getDate();

        const prevMonthLastDay = new Date(datePickerViewYear, datePickerViewMonth, 0);
        const prevMonthDays = prevMonthLastDay.getDate();

        // Determine selection range (inclusive)
        const selectedStart = dateFilterStart;
        const selectedEnd = getEffectiveDateEnd();
        const rangeStart = selectedStart && selectedEnd ? (compareISO(selectedStart, selectedEnd) <= 0 ? selectedStart : selectedEnd) : null;
        const rangeEnd = selectedStart && selectedEnd ? (compareISO(selectedStart, selectedEnd) <= 0 ? selectedEnd : selectedStart) : null;

        const todayIso = formatISODate(new Date());

        historyElements.dateGrid.innerHTML = '';

        const totalCells = 42; // 6 weeks
        for (let i = 0; i < totalCells; i++) {
            const cell = document.createElement('button');
            cell.type = 'button';
            cell.className = 'history-date-cell';

            const dayNumber = i - firstDayOffset + 1;
            let cellDate;
            let muted = false;

            if (dayNumber < 1) {
                // previous month
                muted = true;
                const day = prevMonthDays + dayNumber;
                cellDate = new Date(datePickerViewYear, datePickerViewMonth - 1, day);
                cell.textContent = String(day);
            } else if (dayNumber > daysInMonth) {
                // next month
                muted = true;
                const day = dayNumber - daysInMonth;
                cellDate = new Date(datePickerViewYear, datePickerViewMonth + 1, day);
                cell.textContent = String(day);
            } else {
                cellDate = new Date(datePickerViewYear, datePickerViewMonth, dayNumber);
                cell.textContent = String(dayNumber);
            }

            const iso = formatISODate(cellDate);
            cell.dataset.iso = iso;
            if (muted) cell.classList.add('muted');

            if (iso === todayIso) cell.classList.add('today');

            if (selectedStart && iso === selectedStart) cell.classList.add('selected');
            if (dateFilterEnd && iso === dateFilterEnd) cell.classList.add('selected');

            if (rangeStart && rangeEnd && compareISO(iso, rangeStart) >= 0 && compareISO(iso, rangeEnd) <= 0) {
                // Avoid overriding selected styling; keep both when selected
                cell.classList.add('in-range');
            }

            cell.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                const clicked = cell.dataset.iso;
                if (!dateFilterStart || (dateFilterStart && dateFilterEnd)) {
                    // First click (or restart selection)
                    setDateFilter(clicked, null);
                } else {
                    // Second click -> range
                    if (compareISO(clicked, dateFilterStart) < 0) {
                        setDateFilter(clicked, dateFilterStart);
                    } else {
                        setDateFilter(dateFilterStart, clicked);
                    }
                }

                // Apply filter immediately
                currentPage = 1;
                loadHistory();
                renderDatePicker();
            });

            historyElements.dateGrid.appendChild(cell);
        }

        updateDateButtonState();
        updateDateSelectionLabel();
    };

    /**
     * Initialize the history module
     */
    const init = () => {
        console.log('Initializing history module...');

        // Fresh state each time we enter the view to avoid stale page counters
        resetHistoryState();
        
        // Initialize Bot Profile to update the header
        if (typeof BotProfile !== 'undefined') {
            console.log('Loading bot profile information...');
            BotProfile.fetchBotInfo();
        } else {
            console.error('BotProfile module not found');
        }
        
        // Cache DOM elements - Use a short delay to ensure the component is fully loaded
        setTimeout(() => {
            const initToken = currentInitToken;
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
                loadServerList(initToken).then(() => {
                    // Try to get the selected guild from the server
                    getSelectedGuild(initToken).then(guildId => {
                        if (initToken !== currentInitToken || !isHistoryViewActive()) return;
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
                        loadRequesterList(initToken).then(() => {
                            if (initToken !== currentInitToken || !isHistoryViewActive()) return;
                            // Load history with the selected guild
                            loadHistory();
                        });
                    });
                });
            } else {
                console.error('ServerManager module not loaded');
                // Load requester list for the filter first
                loadRequesterList(currentInitToken).then(() => {
                    if (initToken !== currentInitToken || !isHistoryViewActive()) return;
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
        historyElements.contentWrapper = document.querySelector('.history-content-wrapper');
        historyElements.requesterSelect = document.getElementById('requester-filter-select');

        // Date picker elements
        historyElements.dateFilterBtn = document.getElementById('history-date-filter-btn');
        historyElements.dateFilterLabel = document.getElementById('history-date-filter-label');
        historyElements.datePicker = document.getElementById('history-date-picker');
        historyElements.datePrevBtn = document.getElementById('history-date-prev');
        historyElements.dateNextBtn = document.getElementById('history-date-next');
        historyElements.dateClearBtn = document.getElementById('history-date-clear');
        // historyElements.dateMonthLabel = document.getElementById('history-date-month-label'); // Removed
        historyElements.dateMonthSelect = document.getElementById('history-date-month-select');
        historyElements.dateYearSelect = document.getElementById('history-date-year-select');
        historyElements.dateGrid = document.getElementById('history-date-grid');
        historyElements.dateSelectionLabel = document.getElementById('history-date-selection');
        historyElements.dateSelectionText = document.getElementById('history-date-selection-text');
        
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
        if (!historyElements.contentWrapper) console.error('History content wrapper not found!');
        if (!historyElements.dateFilterBtn) console.error('Date filter button not found!');
        if (!historyElements.dateFilterLabel) console.error('Date filter label not found!');
        if (!historyElements.datePicker) console.error('Date picker container not found!');
        
        return true;
    };

    /**
     * Set up event listeners
     */
    const setupEventListeners = () => {
        let searchDebounceTimer = null;

        // Search input
        historyElements.searchInput.addEventListener('keyup', (e) => {
            if (e.key === 'Enter') {
                if (searchDebounceTimer) {
                    clearTimeout(searchDebounceTimer);
                    searchDebounceTimer = null;
                }
                searchHistory();
            }
        });

        // Live search (refresh on each typed character)
        historyElements.searchInput.addEventListener('input', () => {
            if (searchDebounceTimer) {
                clearTimeout(searchDebounceTimer);
            }
            searchDebounceTimer = setTimeout(() => {
                searchDebounceTimer = null;
                searchHistory();
            }, 250);
        });
        
        document.getElementById('history-search-btn').addEventListener('click', () => {
            if (searchDebounceTimer) {
                clearTimeout(searchDebounceTimer);
                searchDebounceTimer = null;
            }
            searchHistory();
        });

        // Date filter calendar
        if (historyElements.dateFilterBtn) {
            historyElements.dateFilterBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                toggleDatePicker();
            });
        }
        if (historyElements.datePicker) {
            historyElements.datePicker.addEventListener('click', (e) => {
                e.stopPropagation();
            });
        }
        if (historyElements.datePrevBtn) {
            historyElements.datePrevBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                moveDatePickerMonth(-1);
            });
        }
        if (historyElements.dateNextBtn) {
            historyElements.dateNextBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                moveDatePickerMonth(1);
            });
        }
        if (historyElements.dateClearBtn) {
            historyElements.dateClearBtn.addEventListener('click', (e) => {
                e.stopPropagation();
                setDateFilter(null, null);
                closeDatePicker();
                currentPage = 1;
                loadHistory();
            });
        }

        if (historyElements.dateMonthSelect) {
            historyElements.dateMonthSelect.addEventListener('change', (e) => {
                e.stopPropagation();
                datePickerViewMonth = parseInt(e.target.value);
                renderDatePicker();
            });
            historyElements.dateMonthSelect.addEventListener('click', (e) => {
                e.stopPropagation();
            });
        }
        if (historyElements.dateYearSelect) {
            historyElements.dateYearSelect.addEventListener('change', (e) => {
                e.stopPropagation();
                datePickerViewYear = parseInt(e.target.value);
                renderDatePicker();
            });
            historyElements.dateYearSelect.addEventListener('click', (e) => {
                e.stopPropagation();
            });
        }

        // Close date picker on Escape
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape') {
                closeDatePicker();
            }
        });
        
        // Filter buttons
        historyElements.filterButtons.forEach(btn => {
            btn.addEventListener('click', function() {
                const filterType = this.getAttribute('data-filter');
                const filterValue = this.getAttribute('data-value');
                
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
            activeFilters.requester = this.value || 'all';

            // Reload history with new requester filter
            currentPage = 1;
            loadHistory();
        });
        
        // Server dropdown toggle
        historyElements.serverDropdownBtn.addEventListener('click', function() {
            historyElements.serverDropdownContent.classList.toggle('show');
        });
        
        // Close dropdown / date picker when clicking outside
        document.addEventListener('click', function(event) {
            if (historyElements.serverDropdownContent &&
                !event.target.matches('.server-select-btn') &&
                !event.target.closest('.server-select-btn') &&
                historyElements.serverDropdownContent.classList.contains('show')) {
                historyElements.serverDropdownContent.classList.remove('show');
            }

            if (historyElements.datePicker && !historyElements.datePicker.hidden) {
                const clickedInsideDateFilter = event.target.closest('.history-date-filter');
                if (!clickedInsideDateFilter) {
                    closeDatePicker();
                }
            }
        });
        
        // Pagination events will be added dynamically
    };

    const disconnectInfiniteScrollObserver = () => {
        if (infiniteScrollObserver) {
            infiniteScrollObserver.disconnect();
            infiniteScrollObserver = null;
        }
    };

    /**
     * Load the server list from the API
     */
    const loadServerList = async (token = currentInitToken) => {
        if (!isHistoryViewActive()) return [];
        try {
            if (guildsFetchController) guildsFetchController.abort();
            guildsFetchController = new AbortController();
            const response = await fetch('/api/guilds', { signal: guildsFetchController.signal });
            const data = await response.json();
            if (!isHistoryViewActive() || token !== currentInitToken) return [];
            
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
    const getSelectedGuild = async (token = currentInitToken) => {
        try {
            const response = await fetch('/api/guild/selected');
            const data = await response.json();
            if (!isHistoryViewActive() || token !== currentInitToken) return null;
            
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
    const loadRequesterList = async (token = currentInitToken) => {
        try {
            if (requestersFetchController) requestersFetchController.abort();
            requestersFetchController = new AbortController();
            const response = await fetch('/api/history/requesters', { signal: requestersFetchController.signal });
            const data = await response.json();
            if (!isHistoryViewActive() || token !== currentInitToken) return true;
            
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
        
        // Clear existing options and ensure default "Anyone" option
        select.innerHTML = '<option value="all">Anyone</option>';
        
        // Add requester options
        sortedRequesters.forEach(requester => {
            const option = document.createElement('option');
            option.value = requester;
            option.textContent = requester;
            select.appendChild(option);
        });
        
        // Restore selection if it still exists
        if (currentSelection && currentSelection !== 'all' && sortedRequesters.includes(currentSelection)) {
            select.value = currentSelection;
            return;
        }

        // Default selection
        select.value = 'all';
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
            historyElements.requesterSelect.value = 'all';
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
                historyElements.requesterSelect.value = 'all';
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
                    historyElements.requesterSelect.value = 'all';
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
     * Ensure a sentinel exists at the bottom of the list for intersection-based infinite scroll
     */
    const ensureInfiniteScrollSentinel = () => {
        if (!historyElements.historyList || !isHistoryViewActive()) return null;

        let sentinel = document.getElementById(infiniteScrollSentinelId);
        if (!sentinel) {
            sentinel = document.createElement('div');
            sentinel.id = infiniteScrollSentinelId;
            sentinel.className = 'history-sentinel';
            sentinel.textContent = 'Scroll to load more';
            historyElements.historyList.appendChild(sentinel);
        } else {
            // Move sentinel to the end to ensure proper observation order
            historyElements.historyList.appendChild(sentinel);
        }
        return sentinel;
    };

    /**
     * Update sentinel label based on loading state
     */
    const updateInfiniteScrollSentinelLabel = () => {
        if (!isHistoryViewActive()) return;
        const sentinel = document.getElementById(infiniteScrollSentinelId);
        if (!sentinel) return;

        if (isLoadingHistory) {
            sentinel.textContent = 'Loading more...';
            return;
        }

        if (hasMorePages) {
            sentinel.textContent = 'Scroll to load more';
        } else {
            sentinel.textContent = historyData.length ? 'All records loaded' : '';
        }
    };

    /**
     * Configure intersection observer to auto-load pages near the bottom
     */
    const setupInfiniteScrollObserver = () => {
        if (!historyElements.contentWrapper || !historyElements.historyList) return;

        const sentinel = ensureInfiniteScrollSentinel();
        if (!sentinel) return;

        if (infiniteScrollObserver) {
            infiniteScrollObserver.disconnect();
        }

        infiniteScrollObserver = new IntersectionObserver((entries) => {
            entries.forEach(entry => {
                if (entry.isIntersecting && hasMorePages && !isLoadingHistory) {
                    loadNextPage();
                }
            });
        }, {
            root: historyElements.contentWrapper,
            rootMargin: '200px',
            threshold: 0
        });

        infiniteScrollObserver.observe(sentinel);
    };

    /**
     * Load the next page when the sentinel is reached
     */
    const loadNextPage = () => {
        if (!isHistoryViewActive()) {
            return;
        }

        if (isLoadingHistory || !hasMorePages) {
            updateInfiniteScrollSentinelLabel();
            renderPagination();
            updatePageInfo();
            return;
        }

        const totalPages = Math.max(1, Math.ceil(totalRecords / recordsPerPage));
        if (currentPage >= totalPages) {
            hasMorePages = false;
            updateInfiniteScrollSentinelLabel();
            renderPagination();
            updatePageInfo();
            return;
        }

        currentPage += 1;
        loadHistory(true);
    };

    /**
     * Update the page info label to reflect loaded counts
     */
    const updatePageInfo = () => {
        if (!historyElements.pageInfo || !isHistoryViewActive()) return;
        const loadedCount = historyData.length;
        const total = totalRecords || loadedCount;

        historyElements.pageInfo.textContent = hasMorePages
            ? `Loaded ${loadedCount} of ${total} • scroll to load more`
            : `Loaded ${loadedCount} of ${total}`;
    };

    /**
     * Load history data from the API
     */
    const loadHistory = (append = false) => {
        if (!isHistoryViewActive()) {
            return;
        }

        if (isLoadingHistory) return;

        // Reset state when not appending
        if (!append) {
            historyData = [];
            hasMorePages = true;
            historyElements.historyList.innerHTML = '<div class="history-loading">Loading history...</div>';
        } else {
            const sentinel = ensureInfiniteScrollSentinel();
            if (sentinel) {
                sentinel.textContent = 'Loading more...';
            }
        }

        // Abort any previous history request to avoid racing with a stale DOM
        if (historyFetchController) {
            historyFetchController.abort();
        }

        const controller = new AbortController();
        historyFetchController = controller;
        const { signal } = controller;

        isLoadingHistory = true;
        
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

        // Add date range filter (single day or inclusive range)
        if (dateFilterStart) {
            const end = getEffectiveDateEnd();
            params += `&startDate=${encodeURIComponent(dateFilterStart)}&endDate=${encodeURIComponent(end)}`;
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

            // Add date range filter (single day or inclusive range)
            if (dateFilterStart) {
                const end = getEffectiveDateEnd();
                params += `&startDate=${encodeURIComponent(dateFilterStart)}&endDate=${encodeURIComponent(end)}`;
            }
        }
        
        // Fetch history data
        fetch(endpoint + params, { signal })
            .then(response => response.json())
            .then(data => {
                if (!isHistoryViewActive() || controller !== historyFetchController) return;

                if (data.success) {
                    const incomingRecords = data.history || [];
                    totalRecords = data.total || incomingRecords.length;

                    if (append) {
                        historyData = historyData.concat(incomingRecords);
                    } else {
                        historyData = incomingRecords;
                    }

                    hasMorePages = historyData.length < totalRecords;
                    
                    renderHistory(append, incomingRecords);
                    renderPagination();
                } else {
                    historyElements.historyList.innerHTML = `<div class="history-error">${data.message || 'Error loading history'}</div>`;
                    hasMorePages = false;
                }
            })
            .catch(error => {
                if (error && error.name === 'AbortError') {
                    return; // navigation away or refreshed request
                }
                console.error('Error fetching history:', error);
                if (isHistoryViewActive() && controller === historyFetchController) {
                    historyElements.historyList.innerHTML = '<div class="history-error">Failed to load history. Please try again later.</div>';
                }
                hasMorePages = false;
            })
            .finally(() => {
                if (historyFetchController === controller) {
                    historyFetchController = null;
                }
                // Ensure we clear loading flag even if aborted
                isLoadingHistory = false;
                if (!isHistoryViewActive()) return;
                updateInfiniteScrollSentinelLabel();
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
    const renderHistory = (append = false, newRecords = []) => {
        if (!historyElements.historyList || !isHistoryViewActive()) return;

        const recordsToRender = append ? newRecords : historyData;

        if (!append) {
            if (!recordsToRender || recordsToRender.length === 0) {
                if (isHistoryViewActive()) {
                    historyElements.historyList.innerHTML = '<div class="history-empty">No history records found</div>';
                    updatePageInfo();
                    updateInfiniteScrollSentinelLabel();
                }
                return;
            }
            // Clear the list for a fresh render
            historyElements.historyList.innerHTML = '';
        } else if (!recordsToRender || recordsToRender.length === 0) {
            updateInfiniteScrollSentinelLabel();
            return;
        }

        // Create a document fragment for better performance
        const fragment = document.createDocumentFragment();
        
        // Add history items
        recordsToRender.forEach(record => {
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
            // Check for YtDlp metadata
            else if (record.ytDlpData?.sourceType || record.ytDlpSourceType) {
                if (record.ytDlpData?.thumbnailUrl) {
                    thumbnailSrc = record.ytDlpData.thumbnailUrl;
                } else if (record.ytDlpThumbnailUrl) {
                    thumbnailSrc = record.ytDlpThumbnailUrl;
                }
                
                sourceType = record.ytDlpData?.sourceType || record.ytDlpSourceType || 'Unknown';
                
                // Set icon based on source type
                switch(sourceType.toLowerCase()) {
                    case 'instagram':
                        sourceIcon = '<i class="fab fa-instagram source-icon-instagram"></i>';
                        break;
                    case 'tiktok':
                        sourceIcon = '<i class="fab fa-tiktok source-icon-tiktok"></i>';
                        break;
                    case 'twitter':
                    case 'x':
                        sourceIcon = '<i class="fab fa-twitter source-icon-twitter"></i>';
                        break;
                    case 'bilibili':
                        sourceIcon = '<i class="fas fa-tv source-icon-bilibili"></i>';
                        break;
                    case 'vimeo':
                        sourceIcon = '<i class="fab fa-vimeo source-icon-vimeo"></i>';
                        break;
                    case 'twitch':
                        sourceIcon = '<i class="fab fa-twitch source-icon-twitch"></i>';
                        break;
                    case 'soundcloud':
                        sourceIcon = '<i class="fab fa-soundcloud source-icon-soundcloud"></i>';
                        break;
                    case 'youtube':
                        sourceIcon = '<i class="fab fa-youtube source-icon-youtube"></i>';
                        break;
                    default:
                        sourceIcon = '<i class="fas fa-globe source-icon-web"></i>';
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
            // Check for TikTok
            else if (record.url && record.url.includes('tiktok.com')) {
                sourceType = 'TikTok';
                sourceIcon = '<i class="fab fa-tiktok source-icon-tiktok"></i>';
                if (record.thumbnailUrl) {
                    thumbnailSrc = record.thumbnailUrl;
                }
            }
            // Check for Instagram
            else if (record.url && record.url.includes('instagram.com')) {
                sourceType = 'Instagram';
                sourceIcon = '<i class="fab fa-instagram source-icon-instagram"></i>';
                if (record.thumbnailUrl) {
                    thumbnailSrc = record.thumbnailUrl;
                }
            }
            // Check for Twitter/X
            else if (record.url && (record.url.includes('twitter.com') || record.url.includes('x.com'))) {
                sourceType = 'Twitter';
                sourceIcon = '<i class="fab fa-twitter source-icon-twitter"></i>';
                if (record.thumbnailUrl) {
                    thumbnailSrc = record.thumbnailUrl;
                }
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
            
            thumbnailSrc = safeThumbnail(thumbnailSrc);

            // Build HTML
            historyItem.innerHTML = `
                <div class="history-item-thumbnail">
                    <img src="${thumbnailSrc}" alt="${record.title}" referrerpolicy="no-referrer" onerror="this.src='https://cdn.discordapp.com/embed/avatars/0.png'">
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

        // Keep sentinel at the end for infinite scroll and refresh observer
        ensureInfiniteScrollSentinel();
        updateInfiniteScrollSentinelLabel();
        setupInfiniteScrollObserver();
        updatePageInfo();

        // Only reset scroll on a fresh render
        if (!append) {
            scrollToTop();
        }
    };

    /**
     * Scroll to the top of the history content
     */
    const scrollToTop = () => {
        const contentWrapper = document.querySelector('.history-content-wrapper');
        if (contentWrapper && isHistoryViewActive()) {
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
        if (!historyElements.pagination || !isHistoryViewActive()) return;

        const loadedCount = historyData.length;
        const total = totalRecords || loadedCount || 0;

        const message = hasMorePages
            ? `Scroll to load more • ${loadedCount}/${total || loadedCount} loaded`
            : (total ? `All ${total} records loaded` : 'No records found');

        historyElements.pagination.innerHTML = `<div class="pagination-info">${message}</div>`;
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