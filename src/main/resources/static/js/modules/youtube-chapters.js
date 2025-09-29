/**
 * YouTube Chapters Module - Handles YouTube video chapters functionality
 */

const YouTubeChapters = (function() {
    // Initialize local variables to store state
    let currentChapters = [];
    let currentChapterIndex = -1;

    /**
     * Fetch YouTube chapters for the current track
     * @returns {Promise<void>}
     */
    async function fetchYouTubeChapters() {
        if (!window.currentStatus || !window.currentStatus.playing) {
            hideChaptersContainer();
            return;
        }

        // Only fetch chapters for YouTube tracks
        if (window.currentStatus.sourceType !== 'YouTube') {
            hideChaptersContainer();
            return;
        }

        try {
            const response = await fetch('/api/track/chapters');
            const data = await response.json();
            
            if (data.success && data.chapters && data.chapters.length > 0) {
                currentChapters = data.chapters;
                displayYouTubeChapters(data.chapters);
                showChaptersContainer();
                updateCurrentChapter();
            } else {
                hideChaptersContainer();
                currentChapters = [];
            }
        } catch (error) {
            console.error('Error fetching YouTube chapters:', error);
            hideChaptersContainer();
            currentChapters = [];
        }
    }

    /**
     * Display YouTube chapters in the chapters container
     * @param {Array} chapters - The chapters to display
     */
    function displayYouTubeChapters(chapters) {
        const chaptersList = document.getElementById('chapters-list');
        chaptersList.innerHTML = '';
        
        document.getElementById('chapters-info').textContent = `${chapters.length} chapter${chapters.length !== 1 ? 's' : ''}`;
        
        // Reset chapter index when displaying new chapters
        currentChapterIndex = -1;
        
        chapters.forEach((chapter, index) => {
            const chapterItem = document.createElement('div');
            chapterItem.className = 'chapter-item';
            chapterItem.setAttribute('data-index', index);
            chapterItem.setAttribute('data-time', chapter.startTimeMs);
            
            chapterItem.innerHTML = `
                <div class="chapter-time">${UI.formatTime(chapter.startTimeMs)}</div>
                <div class="chapter-title">${chapter.name}</div>
            `;
            
            chapterItem.addEventListener('click', () => {
                seekToChapter(chapter.startTimeMs);
            });
            
            chaptersList.appendChild(chapterItem);
        });
    }

    /**
     * Show the chapters container
     */
    function showChaptersContainer() {
        document.getElementById('chapters-container').style.display = 'block';
    }

    /**
     * Hide the chapters container
     */
    function hideChaptersContainer() {
        document.getElementById('chapters-container').style.display = 'none';
    }

    /**
     * Seek to a specific chapter
     * @param {number} timeMs - The time to seek to in milliseconds
     */
    async function seekToChapter(timeMs) {
        try {
            const seconds = Math.floor(timeMs / 1000);
            const response = await fetch(`/api/player/seek/${seconds}`, {
                method: 'POST'
            });
            
            const data = await response.json();
            if (data.success) {
                // Update progress bar and current time immediately for better UX
                const progressPercentage = (timeMs / window.currentStatus.currentTrackDuration) * 100;
                document.getElementById('progress-bar').style.width = `${progressPercentage}%`;
                document.getElementById('current-time').textContent = UI.formatTime(timeMs);
                
                // Update current status after seek
                setTimeout(Player.fetchStatus, 500);
                
                // Show success toast
                UI.showToast(`Jumped to ${UI.formatTime(timeMs)}`, true);
            } else {
                UI.showToast(data.message || 'Failed to seek', false);
            }
        } catch (error) {
            console.error('Error seeking to chapter:', error);
            UI.showToast('Error seeking to chapter', false);
        }
    }

    /**
     * Update the current chapter based on current track position
     */
    function updateCurrentChapter() {
        if (!window.currentStatus || !window.currentStatus.playing || currentChapters.length === 0) {
            return;
        }
        
        const currentPosition = window.currentStatus.currentTrackPosition;
        let newChapterIndex = -1;
        
        // Find the current chapter
        for (let i = 0; i < currentChapters.length; i++) {
            const chapter = currentChapters[i];
            const nextChapter = i < currentChapters.length - 1 ? currentChapters[i + 1] : null;
            
            if (currentPosition >= chapter.startTimeMs && 
                (!nextChapter || currentPosition < nextChapter.startTimeMs)) {
                newChapterIndex = i;
                break;
            }
        }
        
        // If chapter changed, update UI
        if (newChapterIndex !== currentChapterIndex) {
            currentChapterIndex = newChapterIndex;
            
            // Remove active class from all chapter items
            document.querySelectorAll('.chapter-item').forEach(item => {
                item.classList.remove('active');
            });
            
            // Add active class to current chapter
            if (currentChapterIndex >= 0) {
                const currentChapterItem = document.querySelector(`.chapter-item[data-index="${currentChapterIndex}"]`);
                if (currentChapterItem) {
                    currentChapterItem.classList.add('active');
                    
                    // Scroll to the current chapter if not visible
                    currentChapterItem.scrollIntoView({ behavior: 'smooth', block: 'nearest' });
                }
            }
        }
    }

    // Public API
    return {
        fetchYouTubeChapters,
        displayYouTubeChapters,
        showChaptersContainer,
        hideChaptersContainer,
        seekToChapter,
        updateCurrentChapter
    };
})(); 