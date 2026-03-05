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
        type: [], // all, spotify, youtube, soundcloud, radio, gensokyo, local, instagram, tiktok, twitter
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
        
        // Reset search and filters
        searchQuery = '';
        activeFilters = {
            type: [],
            timeRange: 'all',
            requester: 'all',
            guildId: 'all'
        };
        
        // Reset date filters
        dateFilterStart = null;
        dateFilterEnd = null;
    };

    // Proxy Instagram thumbnails to bypass referrer blocking while leaving local/static assets untouched
    const safeThumbnail = (url) => {
        if (!url) return url;
        const lower = url.toLowerCase();
        const isLocalAsset = lower.startsWith('/') || lower.startsWith('local_artwork/') || lower.startsWith('data:');
        if (isLocalAsset) return url;

        // Check if we have a failure strategy for this URL
        if (window.thumbnailStrategies && window.thumbnailStrategies.has(url)) {
            const stage = window.thumbnailStrategies.get(url);
            if (window.getStrategyUrl) {
                return window.getStrategyUrl(url, stage);
            }
        }

        // Default behavior: Try Original First
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
            const cell = buildDatePickerCell(i, firstDayOffset, prevMonthDays, daysInMonth);
            const iso = cell.dataset.iso;
            applyDateCellSelectionState(cell, iso, todayIso, selectedStart, rangeStart, rangeEnd);
            attachDateCellClickHandler(cell);

            historyElements.dateGrid.appendChild(cell);
        }

        updateDateButtonState();
        updateDateSelectionLabel();
    };

    const buildDatePickerCell = (index, firstDayOffset, prevMonthDays, daysInMonth) => {
        const cell = document.createElement('button');
        cell.type = 'button';
        cell.className = 'history-date-cell';

        const dayNumber = index - firstDayOffset + 1;
        let cellDate;

        if (dayNumber < 1) {
            const day = prevMonthDays + dayNumber;
            cellDate = new Date(datePickerViewYear, datePickerViewMonth - 1, day);
            cell.textContent = String(day);
            cell.classList.add('muted');
        } else if (dayNumber > daysInMonth) {
            const day = dayNumber - daysInMonth;
            cellDate = new Date(datePickerViewYear, datePickerViewMonth + 1, day);
            cell.textContent = String(day);
            cell.classList.add('muted');
        } else {
            cellDate = new Date(datePickerViewYear, datePickerViewMonth, dayNumber);
            cell.textContent = String(dayNumber);
        }

        cell.dataset.iso = formatISODate(cellDate);
        return cell;
    };

    const applyDateCellSelectionState = (cell, iso, todayIso, selectedStart, rangeStart, rangeEnd) => {
        if (iso === todayIso) cell.classList.add('today');
        if (selectedStart && iso === selectedStart) cell.classList.add('selected');
        if (dateFilterEnd && iso === dateFilterEnd) cell.classList.add('selected');
        if (rangeStart && rangeEnd && compareISO(iso, rangeStart) >= 0 && compareISO(iso, rangeEnd) <= 0) {
            cell.classList.add('in-range');
        }
    };

    const applyDateSelectionFromCell = (clicked) => {
        if (!dateFilterStart || (dateFilterStart && dateFilterEnd)) {
            setDateFilter(clicked, null);
            return;
        }

        if (compareISO(clicked, dateFilterStart) < 0) {
            setDateFilter(clicked, dateFilterStart);
        } else {
            setDateFilter(dateFilterStart, clicked);
        }
    };

    const attachDateCellClickHandler = (cell) => {
        cell.addEventListener('click', (e) => {
            e.preventDefault();
            e.stopPropagation();

            applyDateSelectionFromCell(cell.dataset.iso);
            currentPage = 1;
            loadHistory();
            renderDatePicker();
        });
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
        
        // Reset UI elements to default state
        resetUIElements();
        
        return true;
    };

    /**
     * Reset UI elements to their default state
     */
    const resetUIElements = () => {
        // Clear search input
        if (historyElements.searchInput) {
            historyElements.searchInput.value = '';
        }
        
        // Reset filter buttons to 'all'
        if (historyElements.filterButtons) {
            historyElements.filterButtons.forEach(btn => {
                if (btn.getAttribute('data-filter') === 'all') {
                    btn.classList.add('active');
                } else {
                    btn.classList.remove('active');
                }
            });
        }
        
        // Reset requester select to 'all'
        if (historyElements.requesterSelect) {
            historyElements.requesterSelect.value = 'all';
        }
        
        // Reset date filter button and labels
        updateDateButtonState();
        updateDateSelectionLabel();
        updateDateFilterButtonLabel();
        
        // Close date picker if open
        closeDatePicker();
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
                
                if (filterType === 'type') {
                    if (filterValue === 'all') {
                        activeFilters.type = [];
                    } else {
                        if (!Array.isArray(activeFilters.type)) {
                            activeFilters.type = [];
                        }
                        
                        const index = activeFilters.type.indexOf(filterValue);
                        if (index > -1) {
                            // Toggle OFF
                            activeFilters.type.splice(index, 1);
                        } else {
                            // Toggle ON
                            activeFilters.type.push(filterValue);
                        }
                    }
                } else {
                    // Update active filters
                    activeFilters[filterType] = filterValue;
                }
                
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
        
        // Create mobile source filter select
        createMobileSourceFilter();
    };
    
    /**
     * Create a select dropdown for source filters on mobile
     */
    const createMobileSourceFilter = () => {
        // Check if we're on mobile (viewport width)
        const isMobile = window.innerWidth <= 768;
        
        if (!isMobile) return;
        
        const filterOptions = document.querySelector('.history-filters .filter-options');
        if (!filterOptions) return;
        
        // Check if select already exists
        let mobileSelect = document.getElementById('source-filter-mobile-select');
        
        if (!mobileSelect) {
            // Create the select element
            mobileSelect = document.createElement('select');
            mobileSelect.id = 'source-filter-mobile-select';
            mobileSelect.className = 'source-filter-mobile-select';
            
            // Add options from buttons
            const filterButtons = document.querySelectorAll('.history-filter-btn');
            filterButtons.forEach(btn => {
                const value = btn.getAttribute('data-value');
                const text = btn.textContent.trim();
                const option = document.createElement('option');
                option.value = value;
                option.textContent = text;
                if (btn.classList.contains('active')) {
                    option.selected = true;
                }
                mobileSelect.appendChild(option);
            });
            
            // Add change event
            mobileSelect.addEventListener('change', function() {
                const selectedValue = this.value;
                if (selectedValue === 'all') {
                    activeFilters.type = [];
                } else {
                    activeFilters.type = [selectedValue];
                }
                
                // Update button states for consistency
                updateFilterUI();
                
                // Reload history
                currentPage = 1;
                loadHistory();
            });
            
            // Insert the select before the buttons
            filterOptions.insertBefore(mobileSelect, filterOptions.firstChild);
        }
        
        // Update select value based on active filter
        if (mobileSelect) {
            if (Array.isArray(activeFilters.type) && activeFilters.type.length > 0) {
                mobileSelect.value = activeFilters.type[0];
            } else {
                mobileSelect.value = 'all';
            }
        }
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
        // Store requesters (now objects {name, avatar})
        uniqueRequesters = requesters;
        
        // Get the native dropdown
        const select = historyElements.requesterSelect;
        const currentSelection = select.value;
        
        // Populate native select for compatibility
        select.innerHTML = '<option value="all">Anyone</option>';
        requesters.forEach(requester => {
            const option = document.createElement('option');
            // Check if requester is object or string (backward compatibility)
            const name = typeof requester === 'string' ? requester : requester.name;
            option.value = name;
            option.textContent = name;
            select.appendChild(option);
        });

        // Initialize Custom Dropdown if needed
        let customSelect = document.getElementById('custom-requester-select');
        let optionsContainer, triggerSpan;
        
        if (!customSelect) {
            // Create wrapper
            const wrapper = document.createElement('div');
            wrapper.id = 'custom-requester-select';
            wrapper.className = 'custom-requester-select';
            
            // Insert custom select after the original select and hide original
            select.parentNode.insertBefore(wrapper, select.nextSibling);
            select.style.display = 'none';
            
            // Create Trigger
            const trigger = document.createElement('div');
            trigger.className = 'custom-requester-select-trigger';
            trigger.innerHTML = '<span>Anyone</span><div class="arrow"></div>';
            triggerSpan = trigger.querySelector('span');
            
            // Create Options Container
            optionsContainer = document.createElement('div');
            optionsContainer.className = 'custom-requester-options';
            
            wrapper.appendChild(trigger);
            wrapper.appendChild(optionsContainer);
            
            customSelect = wrapper;
            
            // Event Listeners
            trigger.addEventListener('click', (e) => {
                e.stopPropagation();
                customSelect.classList.toggle('open');
            });
            
            document.addEventListener('click', (e) => {
                if (!customSelect.contains(e.target)) {
                    customSelect.classList.remove('open');
                }
            });
        } else {
            select.style.display = 'none';
            optionsContainer = customSelect.querySelector('.custom-requester-options');
            triggerSpan = customSelect.querySelector('.custom-requester-select-trigger span');
        }
        
        // Decide Current Selection
        let selectedValue = 'all';
        if (currentSelection && currentSelection !== 'all') {
            const name = typeof requesters[0] === 'string' ? currentSelection : currentSelection; 
            // Simplified check: just check if value exists in options
            if (Array.from(select.options).some(o => o.value === currentSelection)) {
                selectedValue = currentSelection;
            }
        }
        select.value = selectedValue;

        // Update Custom Options
        optionsContainer.innerHTML = '';
        
        const createOption = (val, label, avatar) => {
            const div = document.createElement('div');
            div.className = 'custom-requester-option';
            div.dataset.value = val;
            if (val === selectedValue) div.classList.add('selected');
            
            if (val === 'all') {
                 div.innerHTML = `<span style="width: 100%; text-align: center;">${label}</span>`;
            } else {
                 const avatarHtml = avatar 
                    ? `<img src="${avatar}" class="custom-requester-avatar" alt="">` 
                    : `<div class="custom-requester-avatar no-img"><i class="fas fa-user"></i></div>`;
                 div.innerHTML = `${avatarHtml}<span>${label}</span>`;
            }
            
            div.addEventListener('click', () => {
                select.value = val;
                triggerSpan.textContent = label;
                
                // Update active state
                customSelect.querySelectorAll('.custom-requester-option').forEach(el => {
                    if (el.dataset.value === val) el.classList.add('selected');
                    else el.classList.remove('selected');
                });
                
                customSelect.classList.remove('open');
                select.dispatchEvent(new Event('change', { bubbles: true }));
            });
            
            return div;
        };

        // Add 'Anyone'
        optionsContainer.appendChild(createOption('all', 'Anyone', null));
        
        // Add Requesters
        requesters.forEach(r => {
            const name = typeof r === 'string' ? r : r.name;
            const avatar = typeof r === 'string' ? null : r.avatar;
            optionsContainer.appendChild(createOption(name, name, avatar));
        });

        // Set initial trigger text
        if (selectedValue === 'all') {
            triggerSpan.textContent = 'Anyone';
        } else {
            triggerSpan.textContent = selectedValue;
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
            activeFilters.type = []
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
            
            if (filterType === 'type') {
                // Handle multiple selection for type
                if (Array.isArray(activeFilters.type)) {
                    if (activeFilters.type.includes(filterValue)) {
                        btn.classList.add('active');
                    } else if (activeFilters.type.length === 0 && filterValue === 'all') {
                        // If no filter selected, "all" is implicitly active (if we kept the button)
                        btn.classList.add('active');
                    }
                } else if (activeFilters.type === filterValue) {
                     btn.classList.add('active');
                }
            } else if (activeFilters[filterType] === filterValue) {
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

    const appendSharedHistoryParams = (params) => {
        if (activeFilters.guildId !== 'all' && currentGuildId) {
            params += `&guildId=${currentGuildId}`;
        }

        if (Array.isArray(activeFilters.type) && activeFilters.type.length > 0) {
            params += `&type=${activeFilters.type.join(',')}`;
        } else if (activeFilters.type && activeFilters.type !== 'all' && !Array.isArray(activeFilters.type)) {
            params += `&type=${activeFilters.type}`;
        }

        if (activeFilters.requester !== 'all') {
            params += `&requester=${encodeURIComponent(activeFilters.requester)}`;
        }

        if (activeFilters.timeRange !== 'all') {
            params += `&timeRange=${encodeURIComponent(activeFilters.timeRange)}`;
        }

        if (dateFilterStart) {
            const end = getEffectiveDateEnd();
            params += `&startDate=${encodeURIComponent(dateFilterStart)}&endDate=${encodeURIComponent(end)}`;
        }

        return params;
    };

    const buildHistoryRequestConfig = (offset) => {
        if (searchQuery) {
            const params = appendSharedHistoryParams(`?query=${encodeURIComponent(searchQuery)}&limit=${recordsPerPage}&offset=${offset}`);
            return {
                endpoint: '/api/history/search',
                params
            };
        }

        const params = appendSharedHistoryParams(`?limit=${recordsPerPage}&offset=${offset}`);
        return {
            endpoint: '/api/history',
            params
        };
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
        
        const { endpoint, params } = buildHistoryRequestConfig(offset);
        
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

    const getSourceIconFromType = (sourceType) => {
        switch (sourceType.toLowerCase()) {
            case 'instagram':
                return '<i class="fab fa-instagram source-icon-instagram"></i>';
            case 'tiktok':
                return '<i class="fab fa-tiktok source-icon-tiktok"></i>';
            case 'twitter':
            case 'x':
                return '<i class="fab fa-twitter source-icon-twitter"></i>';
            case 'bilibili':
                return '<i class="fas fa-tv source-icon-bilibili"></i>';
            case 'vimeo':
                return '<i class="fab fa-vimeo source-icon-vimeo"></i>';
            case 'twitch':
                return '<i class="fab fa-twitch source-icon-twitch"></i>';
            case 'soundcloud':
                return '<i class="fab fa-soundcloud source-icon-soundcloud"></i>';
            case 'youtube':
                return '<i class="fab fa-youtube source-icon-youtube"></i>';
            default:
                return '<i class="fas fa-globe source-icon-web"></i>';
        }
    };

    const createDefaultPresentation = (record) => ({
        title: record.title || 'Unknown Title',
        thumbnailSrc: 'https://cdn.discordapp.com/embed/avatars/0.png',
        sourceType: 'Unknown',
        sourceIcon: '',
        hasStationLogo: false,
        stationLogoUrl: '',
        additionalMetadata: []
    });

    const tryResolveGensokyoPresentation = (record, presentation) => {
        if (!record.gensokyoTitle) return false;
        presentation.thumbnailSrc = record.gensokyoAlbumArtUrl || 'https://stream.gensokyoradio.net/images/logo.png';
        presentation.sourceType = 'Gensokyo Radio';
        presentation.sourceIcon = '<i class="fas fa-music source-icon-gensokyoradio"></i>';
        presentation.hasStationLogo = true;
        presentation.stationLogoUrl = 'https://stream.gensokyoradio.net/images/logo.png';
        if (record.gensokyoAlbum) presentation.additionalMetadata.push({ icon: 'fas fa-compact-disc', text: record.gensokyoAlbum });
        if (record.gensokyoCircle) presentation.additionalMetadata.push({ icon: 'fas fa-users', text: record.gensokyoCircle });
        if (record.gensokyoYear) presentation.additionalMetadata.push({ icon: 'fas fa-calendar-alt', text: record.gensokyoYear });
        return true;
    };

    const tryResolveYtDlpPresentation = (record, presentation) => {
        if (!(record.ytDlpData?.sourceType || record.ytDlpSourceType)) return false;
        presentation.thumbnailSrc = record.ytDlpData?.thumbnailUrl || record.ytDlpThumbnailUrl || presentation.thumbnailSrc;
        presentation.sourceType = record.ytDlpData?.sourceType || record.ytDlpSourceType || 'Unknown';
        const customIconUrl = record.ytDlpData?.sourceIconUrl || record.ytDlpSourceIconUrl;
        presentation.sourceIcon = customIconUrl
            ? `<img src="${customIconUrl}" alt="${presentation.sourceType}" style="width: 1em; height: 1em; vertical-align: -0.125em;">`
            : getSourceIconFromType(presentation.sourceType);
        return true;
    };

    const tryResolveSpotifyPresentation = (record, presentation) => {
        if (!record.spotifyAlbumImageUrl) return false;
        presentation.thumbnailSrc = record.spotifyAlbumImageUrl;
        presentation.sourceType = 'Spotify';
        presentation.sourceIcon = '<i class="fab fa-spotify source-icon-spotify"></i>';
        if (record.spotifyAlbumName) presentation.additionalMetadata.push({ icon: 'fas fa-compact-disc', text: record.spotifyAlbumName });
        if (record.spotifyReleaseYear) presentation.additionalMetadata.push({ icon: 'fas fa-calendar-alt', text: record.spotifyReleaseYear });
        return true;
    };

    const tryResolveYoutubePresentation = (record, presentation) => {
        if (!record.youtubeVideoId) return false;
        presentation.thumbnailSrc = `https://img.youtube.com/vi/${record.youtubeVideoId}/mqdefault.jpg`;
        presentation.sourceType = 'YouTube';
        presentation.sourceIcon = '<i class="fab fa-youtube source-icon-youtube"></i>';
        return true;
    };

    const tryResolveRadioPresentation = (record, presentation) => {
        if (!record.radioLogoUrl) return false;
        presentation.thumbnailSrc = record.radioSongImageUrl || record.radioLogoUrl;
        if (record.title) {
            const pipeIndex = record.title.lastIndexOf(' | ');
            presentation.title = pipeIndex > 0 ? record.title.substring(0, pipeIndex).trim() : record.title;
        }
        presentation.sourceType = 'Radio';
        presentation.sourceIcon = '<i class="fas fa-broadcast-tower source-icon-radio"></i>';
        presentation.hasStationLogo = true;
        presentation.stationLogoUrl = record.radioLogoUrl;
        return true;
    };

    const tryResolveUrlBasedPresentation = (record, presentation) => {
        if (!record.url) return false;
        if (record.url.includes('tiktok.com')) {
            presentation.sourceType = 'TikTok';
            presentation.sourceIcon = '<i class="fab fa-tiktok source-icon-tiktok"></i>';
            if (record.thumbnailUrl) presentation.thumbnailSrc = record.thumbnailUrl;
            return true;
        }
        if (record.url.includes('instagram.com')) {
            presentation.sourceType = 'Instagram';
            presentation.sourceIcon = '<i class="fab fa-instagram source-icon-instagram"></i>';
            if (record.thumbnailUrl) presentation.thumbnailSrc = record.thumbnailUrl;
            return true;
        }
        if (record.url.includes('twitter.com') || record.url.includes('x.com')) {
            presentation.sourceType = 'Twitter';
            presentation.sourceIcon = '<i class="fab fa-twitter source-icon-twitter"></i>';
            if (record.thumbnailUrl) presentation.thumbnailSrc = record.thumbnailUrl;
            return true;
        }
        if (record.url.includes('soundcloud.com') || record.soundCloudArtworkUrl) {
            presentation.thumbnailSrc = record.soundCloudArtworkUrl || 'https://developers.soundcloud.com/assets/logo_big_white-65c2b096da68dd533db18b5b56e1e432.png';
            presentation.sourceType = 'SoundCloud';
            presentation.sourceIcon = '<i class="fab fa-soundcloud source-icon-soundcloud"></i>';
            return true;
        }
        return false;
    };

    const tryResolveLocalPresentation = (record, presentation) => {
        if (!(record.localAlbum || record.localGenre || record.localYear || record.localArtworkHash)) return false;
        presentation.sourceType = 'Local File';
        presentation.sourceIcon = '<i class="fas fa-file-audio source-icon-local"></i>';
        presentation.thumbnailSrc = record.localArtworkHash
            ? `/${record.localArtworkHash}`
            : 'https://cdn-icons-png.flaticon.com/512/4725/4725478.png';
        if (record.localAlbum && record.localAlbum !== 'Unknown Album') presentation.additionalMetadata.push({ icon: 'fas fa-compact-disc', text: record.localAlbum });
        if (record.localGenre && record.localGenre !== 'Unknown Genre') presentation.additionalMetadata.push({ icon: 'fas fa-tag', text: record.localGenre });
        if (record.localYear && record.localYear !== '') presentation.additionalMetadata.push({ icon: 'fas fa-calendar-alt', text: record.localYear });
        return true;
    };

    const resolveHistoryRecordPresentation = (record) => {
        const presentation = createDefaultPresentation(record);
        const resolvers = [
            tryResolveGensokyoPresentation,
            tryResolveYtDlpPresentation,
            tryResolveSpotifyPresentation,
            tryResolveYoutubePresentation,
            tryResolveRadioPresentation,
            tryResolveUrlBasedPresentation,
            tryResolveLocalPresentation
        ];

        for (const resolver of resolvers) {
            if (resolver(record, presentation)) {
                return presentation;
            }
        }

        return presentation;
    };

    const buildAdditionalMetadataHtml = (additionalMetadata) => {
        if (!additionalMetadata || additionalMetadata.length === 0) {
            return '';
        }

        let html = '<div class="additional-metadata">';
        additionalMetadata.forEach(meta => {
            html += `
                <div class="metadata-item">
                    <i class="${meta.icon}"></i> ${meta.text}
                </div>
            `;
        });
        html += '</div><div class="metadata-separator"></div>';
        return html;
    };

    const buildRequesterHtml = (record) => {
        if (!record.requesterName) return '';
        return `
            <div class="metadata-item requester-item" data-requester="${record.requesterName}">
                ${record.requesterAvatar
                    ? `<img src="${record.requesterAvatar}" alt="${record.requesterName}" class="requester-avatar">`
                    : '<i class="fas fa-user"></i>'}
                ${record.requesterName}
            </div>
        `;
    };

    const bindHistoryItemActions = (historyItem) => {
        historyItem.querySelector('.play-again-btn').addEventListener('click', function() {
            const url = this.getAttribute('data-url');
            const title = this.getAttribute('data-title');
            const artist = this.getAttribute('data-artist');
            const sourceType = this.getAttribute('data-source-type');

            if (sourceType === 'Radio' || sourceType === 'Gensokyo Radio') {
                addToQueueViaSearch(title, artist, sourceType);
            } else {
                addToQueue(url);
            }
        });

        historyItem.querySelector('.open-url-btn').addEventListener('click', function() {
            const url = this.getAttribute('data-url');
            window.open(url, '_blank');
        });
    };

    const buildHistoryItemHtml = (record, presentation) => {
        const additionalMetadataHtml = buildAdditionalMetadataHtml(presentation.additionalMetadata);
        const requesterHtml = buildRequesterHtml(record);
        const safeThumb = safeThumbnail(presentation.thumbnailSrc);

        return `
            <div class="history-item-thumbnail">
                <img src="${safeThumb}" alt="${presentation.title}" referrerpolicy="no-referrer" onerror="handleImageError(this)">
            </div>
            <div class="history-item-info">
                <div class="history-item-title">${presentation.title || 'Unknown Title'}</div>
                <div class="history-item-artist">${record.artist || 'Unknown Artist'}</div>
                ${additionalMetadataHtml}
                <div class="history-item-metadata">
                    ${(presentation.sourceType !== 'Radio' && presentation.sourceType !== 'Gensokyo Radio') ? `
                    <div class="metadata-item">
                        <i class="fas fa-clock"></i> ${record.formattedDuration}
                    </div>` : ''}
                    <div class="metadata-item">
                        ${presentation.sourceIcon} ${presentation.sourceType}
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
            ${presentation.hasStationLogo ? `
            <div class="station-logo-container">
                <img src="${presentation.stationLogoUrl}" alt="Station Logo" class="station-logo">
            </div>` : ''}
            <div class="history-item-actions">
                <button class="history-action-btn play-again-btn" data-url="${record.spotifyTrackId ? `https://open.spotify.com/track/${record.spotifyTrackId}` : record.url}" data-title="${presentation.title || ''}" data-artist="${record.artist || ''}" data-source-type="${presentation.sourceType}" title="Add to queue">
                    <i class="fas fa-plus"></i>
                </button>
                <button class="history-action-btn open-url-btn" data-url="${record.url}" title="Open URL">
                    <i class="fas fa-external-link-alt"></i>
                </button>
            </div>
        `;
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
            const presentation = resolveHistoryRecordPresentation(record);
            historyItem.innerHTML = buildHistoryItemHtml(record, presentation);
            bindHistoryItemActions(historyItem);
            
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