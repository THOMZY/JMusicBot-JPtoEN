/**
 * Player Module - Handles music playback functionalities
 */

const Player = (function() {
    // Initialize global player state
    window.currentStatus = {
        playing: false,
        paused: false,
        currentTrackPosition: 0,
        currentTrackDuration: 0,
        currentTrackTitle: 'No track playing',
        currentTrackAuthor: '',
        sourceType: '',
        volume: 100,
        queueSize: 0
    };
    
    window.currentChapters = [];
    let progressInterval;
    let lastSeekTime = 0;
    let lastSeekPosition = 0;
    let lastServerUpdateTime = 0;
    let lastServerPosition = 0;

    const isPlayerViewActive = () => {
        const container = document.getElementById('player-component');
        return !!container && document.body.contains(container);
    };

    const getPlayerEls = () => ({
        progressBar: document.getElementById('progress-bar'),
        currentTime: document.getElementById('current-time'),
        totalTime: document.getElementById('total-time')
    });

    // Instagram CDN blocks hotlinks with referrers; wrap those URLs through a proxy while leaving local/static paths untouched
    function makeSafeThumbnail(url) {
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
    }

    function resolveSpotifyTrackUrl(track) {
        if (track.uri && track.uri.includes('spotify')) {
            return track.uri;
        }
        if (track.spotifyInfo && track.spotifyInfo.trackId) {
            return 'https://open.spotify.com/track/' + track.spotifyInfo.trackId;
        }
        return '';
    }

    function resolveRadioAuthorUrl(track) {
        if (track.radioCountry && track.radioAlias) {
            return `https://onlineradiobox.com/${track.radioCountry}/${track.radioAlias}/`;
        }
        return track.radioStationUrl || '';
    }

    function resolveQueueTrackLinks(track) {
        let trackSourceUrl = track.uri || '';
        let queueAuthorSourceUrl = '';

        switch (track.sourceType) {
            case 'Spotify':
                trackSourceUrl = resolveSpotifyTrackUrl(track);
                break;
            case 'Radio':
                trackSourceUrl = '';
                queueAuthorSourceUrl = resolveRadioAuthorUrl(track);
                break;
            case 'Gensokyo Radio':
                trackSourceUrl = 'https://gensokyoradio.net/playing/';
                break;
            default:
                break;
        }

        if (track.sourceType === 'Stream' && track.uri && track.uri.includes('stream.gensokyoradio.net')) {
            trackSourceUrl = 'https://gensokyoradio.net/playing/';
        }

        return { trackSourceUrl, queueAuthorSourceUrl };
    }

    function buildQueueItemElement(track, index) {
        const item = document.createElement('div');
        item.className = 'queue-item';
        item.setAttribute('data-index', index);
        item.setAttribute('draggable', 'true');

        const isLocalArtworkThumbnail = (url) => {
            if (!url) return false;
            return url.startsWith('local_artwork/') || url.includes('/local_artwork/');
        };

        let thumbnailUrl = makeSafeThumbnail(track.thumbnail);
        if (!thumbnailUrl) {
            thumbnailUrl = makeSafeThumbnail(getDefaultThumbnail(track.sourceType));
        } else if (track.sourceType === 'Local File' && !isLocalArtworkThumbnail(thumbnailUrl)) {
            console.warn('[fetchQueue] Local File track.thumbnail does not seem to be a local_artwork path:', thumbnailUrl);
            thumbnailUrl = makeSafeThumbnail('https://cdn-icons-png.flaticon.com/512/4725/4725478.png');
        }

        const thumbnailClass = track.sourceType
            ? `queue-item-thumbnail ${track.sourceType.toLowerCase().replace(/\s+/g, '-')}-thumbnail`
            : 'queue-item-thumbnail';

        const sourceIcon = UI.getSourceIcon(track.sourceType);
        const sourceIconHtml = track.sourceIconUrl
            ? `<img src="${track.sourceIconUrl}" alt="${track.source}" class="custom-source-icon" style="width: 1em; height: 1em; vertical-align: -0.125em; margin-right: 4px;">`
            : `<i class="${sourceIcon}"></i>`;

        const requesterInfo = track.requester
            ? ` • ${track.requesterAvatar ? `<img src="${track.requesterAvatar}" class="requester-avatar" alt="" style="width:16px;height:16px;margin:0 4px;vertical-align:text-bottom;">` : ''}Requested by: ${track.requester}`
            : '';

        const { trackSourceUrl, queueAuthorSourceUrl } = resolveQueueTrackLinks(track);
        const displaySource = (track.sourceType === 'Stream' && track.uri && track.uri.includes('stream.gensokyoradio.net'))
            ? 'Gensokyo Radio'
            : track.source;

        const titleElement = trackSourceUrl
            ? `<div class="queue-item-title">${index + 1}. <a href="${trackSourceUrl}" target="_blank" class="track-title-link">${track.title}</a></div>`
            : `<div class="queue-item-title">${index + 1}. ${track.title}</div>`;

        const authorElement = queueAuthorSourceUrl
            ? `<span class="queue-item-author"><a href="${queueAuthorSourceUrl}" target="_blank" class="track-author-link">${track.author}</a></span>`
            : `<span class="queue-item-author">${track.author}</span>`;

        item.innerHTML = `
            <div class="drag-handle" title="Drag to reorder">
                <i class="fas fa-grip-lines"></i>
            </div>
            <div class="${thumbnailClass}">
                <img src="${thumbnailUrl}" alt="${track.title}" referrerpolicy="no-referrer" onerror="handleImageError(this)">
            </div>
            <div class="queue-item-info">
                ${titleElement}
                <div class="queue-item-meta">
                    ${authorElement}
                    <span class="queue-item-source">${sourceIconHtml} ${displaySource}</span>
                    <span class="queue-item-requester">${requesterInfo}</span>
                </div>
            </div>
            <div class="queue-item-duration">${UI.formatTime(track.duration)}</div>
            <div class="queue-item-actions">
                <button class="move-top-btn" data-index="${index}" title="Move to top">
                    <i class="fas fa-arrow-up"></i>
                </button>
                <button class="move-bottom-btn" data-index="${index}" title="Move to bottom">
                    <i class="fas fa-arrow-down"></i>
                </button>
                <button class="remove-btn" data-index="${index}" title="Remove from queue">
                    <i class="fas fa-trash"></i>
                </button>
            </div>
        `;

        return item;
    }

    // Centralized default thumbnails per source.
    function getDefaultThumbnail(sourceType) {
        if (!sourceType || sourceType === 'Unknown') {
            return 'https://camo.githubusercontent.com/05bfda98ab36433a3ee7437a031f93b5c5bc02be3508ad1910e5ad44e5d7d3b7/68747470733a2f2f692e696d6775722e636f6d2f4b413073316d6e2e706e67';
        }

        const defaults = {
            'YouTube': 'https://www.gstatic.com/youtube/img/branding/youtubelogo/svg/youtubelogo.svg',
            'SoundCloud': 'https://developers.soundcloud.com/assets/logo_big_white-65c2b096da68dd533db18b5a2bcfbcce.png',
            'Spotify': 'https://www.freepnglogos.com/uploads/spotify-logo-png/file-spotify-logo-png-4.png',
            'TikTok': 'https://cdn-icons-png.flaticon.com/512/3046/3046121.png',
            'Local': 'https://cdn-icons-png.flaticon.com/512/4725/4725478.png',
            'Local File': 'https://cdn-icons-png.flaticon.com/512/4725/4725478.png',
            'Radio': 'https://static.semrush.com/power-pages/media/favicons/onlineradiobox-com-favicon-7dd1a612.png',
            'Stream': 'https://cdn-icons-png.flaticon.com/128/11796/11796884.png',
            'Gensokyo Radio': 'https://stream.gensokyoradio.net/images/logo.png',
            'Instagram': 'https://www.svgrepo.com/show/303145/instagram-2-1-logo.svg',
            'Twitter': 'https://www.svgrepo.com/show/494187/twitter.svg',
            'Twitch': 'https://www.svgrepo.com/show/448251/twitch.svg',
            'Vimeo': 'https://www.svgrepo.com/show/494322/vimeo-rounded.svg',
            'Bilibili': 'https://www.svgrepo.com/show/515022/bilibili.svg'
        };
        
        // Return specific default or fallback to Globe icon for generic websites
        return defaults[sourceType] || 'https://www.svgrepo.com/show/522133/globe.svg';
    }
    
    // Update the progress bar
    function updateProgress() {
        if (!window.currentStatus || !window.currentStatus.playing || window.currentStatus.paused) return;

        // Bail if player view is gone (SPA navigation)
        if (!isPlayerViewActive()) return;

        const { progressBar, currentTime } = getPlayerEls();
        if (!progressBar || !currentTime) return;
        if (isGensokyoTimedTrack(window.currentStatus)) {
            updateGensokyoProgress(progressBar, currentTime);
            return;
        }

        updateRegularProgress(progressBar, currentTime);
    }

    function isGensokyoTimedTrack(status) {
        const isGensokyoSource = status.sourceType === 'Gensokyo Radio';
        const isGensokyoStream = status.sourceType === 'Stream'
            && status.currentTrackUri
            && status.currentTrackUri.includes('stream.gensokyoradio.net');
        return (isGensokyoSource || isGensokyoStream)
            && status.spotifyInfo
            && status.spotifyInfo.gensokyoDuration;
    }

    function updateGensokyoProgress(progressBar, currentTime) {
        if (window.currentStatus.spotifyInfo.gensokyoPlayed === undefined) {
            return;
        }

        if (window.currentStatus.spotifyInfo.lastServerGensokyoPlayed === undefined) {
            window.currentStatus.spotifyInfo.lastServerGensokyoPlayed = window.currentStatus.spotifyInfo.gensokyoPlayed;
            window.currentStatus.spotifyInfo.localOffset = 0;
        }

        window.currentStatus.spotifyInfo.localOffset += 100;
        const displayTime = window.currentStatus.spotifyInfo.lastServerGensokyoPlayed
            + window.currentStatus.spotifyInfo.localOffset;
        currentTime.textContent = UI.formatTime(displayTime);

        const progressPercentage = (displayTime / window.currentStatus.spotifyInfo.gensokyoDuration) * 100;
        if (progressPercentage <= 100) {
            progressBar.style.width = `${progressPercentage}%`;
        }
    }

    function updateRegularProgress(progressBar, currentTime) {
        const now = Date.now();
        const timeElapsed = now - lastServerUpdateTime;
        const calculatedPosition = lastServerPosition + timeElapsed;

        window.currentStatus.currentTrackPosition = calculatedPosition;
        const progressPercentage = (window.currentStatus.currentTrackPosition / window.currentStatus.currentTrackDuration) * 100;
        if (progressPercentage > 100) {
            return;
        }

        progressBar.style.width = `${progressPercentage}%`;
        currentTime.textContent = UI.formatTime(window.currentStatus.currentTrackPosition);

        if (window.currentStatus.sourceType === 'YouTube' && typeof YouTubeChapters !== 'undefined') {
            YouTubeChapters.updateCurrentChapter();
        }
    }
    
    // Seek to a position in the track
    function seekTrack(e) {
        if (!window.currentStatus.playing || !window.currentStatus.currentTrackDuration) return;
        
        const progressContainer = document.getElementById('progress-container');
        const rect = progressContainer.getBoundingClientRect();
        const clickPosition = (e.clientX - rect.left) / rect.width;
        const seekPosition = Math.floor(clickPosition * window.currentStatus.currentTrackDuration);
        
        // Update UI instantly for better user experience
        const progressBar = document.getElementById('progress-bar');
        progressBar.style.width = `${clickPosition * 100}%`;
        document.getElementById('current-time').textContent = UI.formatTime(seekPosition);
        
        // Update the current status immediately for a more responsive feel
        window.currentStatus.currentTrackPosition = seekPosition;
        
        // Store the seek time and position to avoid reverting in fetchStatus
        lastSeekTime = Date.now();
        lastSeekPosition = seekPosition;
        
        // Animation effect for click feedback
        const progressHandle = document.getElementById('progress-handle');
        progressHandle.style.opacity = '1';
        progressHandle.style.transform = 'translate(-50%, -50%) scale(1.2)';
        progressHandle.style.backgroundColor = '#ffffff';
        
        setTimeout(() => {
            progressHandle.style.transform = 'translate(-50%, -50%)';
        }, 200);
        
        // Send seek request to API
        fetch('/api/seek?position=' + seekPosition, { method: 'POST' })
            .then(response => response.json())
            .then(data => {
                if (data.success) {
                    // Only fetch status after a small delay to avoid 
                    // the progress bar jumping back and forth
                    setTimeout(() => {
                        fetchStatus();
                    }, 300);
                } else {
                    // Revert UI changes if seek failed
                    UI.showToast('Failed to seek: ' + (data.message || 'Unknown error'), false);
                    // Reset seek tracking
                    lastSeekTime = 0;
                    lastSeekPosition = 0;
                    // Fetch real position from server
                    fetchStatus();
                }
            })
            .catch(error => {
                console.error('Error seeking:', error);
                UI.showToast('Error seeking to position', false);
                // Reset seek tracking
                lastSeekTime = 0;
                lastSeekPosition = 0;
                // Fetch real position from server
                fetchStatus();
            });
    }
    
    // Add progress bar interactivity
    function setupProgressBarInteraction() {
        const progressContainer = document.getElementById('progress-container');
        const progressHandle = document.getElementById('progress-handle');
        const progressPreview = document.getElementById('progress-preview');
        
        if (!progressContainer || !progressHandle || !progressPreview) {
            return;
        }
        
        // Initialize preview with default values when page loads
        if (window.currentStatus && window.currentStatus.currentTrackDuration) {
            progressPreview.textContent = `0:00 / ${UI.formatTime(window.currentStatus.currentTrackDuration)}`;
        } else {
            progressPreview.textContent = '0:00 / 0:00';
        }
        
        progressContainer.addEventListener('mousemove', (e) => {
            if (!window.currentStatus.currentTrackDuration) return;
            
            const rect = progressContainer.getBoundingClientRect();
            const position = (e.clientX - rect.left) / rect.width;
            const previewPosition = Math.floor(position * window.currentStatus.currentTrackDuration);
            
            // Update handle position
            progressHandle.style.left = `${position * 100}%`;
            
            // Update preview position and text
            progressPreview.style.left = `${position * 100}%`;
            progressPreview.textContent = `${UI.formatTime(previewPosition)} / ${UI.formatTime(window.currentStatus.currentTrackDuration)}`;
            progressPreview.style.opacity = '1';
        });
        
        progressContainer.addEventListener('mouseleave', () => {
            // Hide handle and preview when mouse leaves
            progressHandle.style.opacity = '0';
            progressPreview.style.opacity = '0';
        });
        
        progressContainer.addEventListener('mouseenter', (e) => {
            // Show handle and preview when mouse enters
            progressHandle.style.opacity = '1';
            progressPreview.style.opacity = '1';
        });
        
        progressContainer.addEventListener('click', (e) => {
            seekTrack(e);
        });
    }

    // Set up player control buttons
    function setupPlayerControls() {
        const playBtn = document.getElementById('play-button');
        const pauseBtn = document.getElementById('pause-button');
        const skipBtn = document.getElementById('skip-button');
        const stopBtn = document.getElementById('stop-button');
        
        if (playBtn) {
            // Remove existing listeners to avoid duplicates
            const newPlayBtn = playBtn.cloneNode(true);
            playBtn.parentNode.replaceChild(newPlayBtn, playBtn);
            newPlayBtn.addEventListener('click', playTrack);
        }
        
        if (pauseBtn) {
            const newPauseBtn = pauseBtn.cloneNode(true);
            pauseBtn.parentNode.replaceChild(newPauseBtn, pauseBtn);
            newPauseBtn.addEventListener('click', pauseTrack);
        }
        
        if (skipBtn) {
            const newSkipBtn = skipBtn.cloneNode(true);
            skipBtn.parentNode.replaceChild(newSkipBtn, skipBtn);
            newSkipBtn.addEventListener('click', skipTrack);
        }
        
        if (stopBtn) {
            const newStopBtn = stopBtn.cloneNode(true);
            stopBtn.parentNode.replaceChild(newStopBtn, stopBtn);
            newStopBtn.addEventListener('click', stopTrack);
        }
        
        setupVolumeControl();
    }

    // Setup volume control slider
    function setupVolumeControl() {
        const volumeSlider = document.getElementById('volume-slider');
        const volumeText = document.getElementById('track-volume');
        const volumeIcon = document.getElementById('volume-icon');
        
        if (!volumeSlider) return;
        
        // Update volume text while dragging
        volumeSlider.addEventListener('input', (e) => {
            const vol = e.target.value;
            if (volumeText) volumeText.textContent = `${vol}%`;
            
            // Update icon based on volume
            if (volumeIcon) {
                volumeIcon.className = 'fas';
                if (vol > 60) volumeIcon.classList.add('fa-volume-up');
                else if (vol > 30) volumeIcon.classList.add('fa-volume-down');
                else if (vol > 0) volumeIcon.classList.add('fa-volume-off');
                else volumeIcon.classList.add('fa-volume-mute');
            }
        });
        
        // Send API request when slider is released or changed
        volumeSlider.addEventListener('change', async (e) => {
            const vol = e.target.value;
            try {
                const response = await fetch('/api/volume?volume=' + vol, { method: 'POST' });
                const data = await response.json();
                if (!data.success) {
                     if (typeof UI !== 'undefined') UI.showToast('Failed to set volume', false);
                     fetchStatus(); // Revert on failure
                }
            } catch (error) {
                console.error('Error setting volume:', error);
                if (typeof UI !== 'undefined') UI.showToast('Error setting volume', false);
            }
        });
    }
    
    // Fetch current status from the API
    async function fetchStatus() {
        return fetchStatusInternal();
    }

    async function fetchStatusInternal() {
        try {
            await fetchStatusInternalImpl();
        } catch (error) {
            console.error('Error fetching status:', error);
            const statusElement = document.getElementById('status-message');
            if (statusElement) {
                statusElement.textContent = 'Error connecting to server';
                statusElement.classList.remove('status-playing', 'status-paused', 'status-idle');
                statusElement.classList.add('status-error');
            }
        }
    }

    async function fetchStatusInternalImpl() {
            if (!ensureStatusUpdateReady()) {
                return;
            }

            const response = await fetch('/api/status');
            const data = await response.json();
            console.log('[fetchStatus] Data received from /api/status:', JSON.parse(JSON.stringify(data)));

            if (!isPlayerViewActive() || !hasRequiredStatusElements()) {
                return;
            }

            const previousSourceType = window.currentStatus.sourceType;
            const previousTrackId = window.currentStatus.currentTrackUri;

            syncStatusTimingData(data);
            const trackIdentity = getDisplayTrackIdentity(data);
            const sourceUrls = getTrackSourceUrls(data);

            renderTrackIdentity(data, trackIdentity, sourceUrls);
            updateSourceText(data);
            updateSourceIcon(data);
            updateRequesterInfo(data);
            updateVolumeState(data);
            updateOptionalMetadataPanels(data);
            updateAlbumArtClass(data);
            updateRadioAndStreamLayout(data);
            updateTrackThumbnail(data);
            updatePlaybackTimeAndProgress(data);
            updatePlaybackButtonsAndStatus(data);

            await updateYouTubeChapters(previousSourceType, previousTrackId, data);
    }

    function ensureStatusUpdateReady() {
        if (!isPlayerViewActive()) {
            if (progressInterval) {
                clearInterval(progressInterval);
                progressInterval = null;
            }
            return false;
        }
        return true;
    }

    function hasRequiredStatusElements() {
        const requiredIds = [
            'track-title', 'track-author', 'queue-info', 'track-source-text', 'track-source-icon',
            'track-requester', 'track-volume', 'spotify-info-container', 'gensokyo-info-container',
            'localfile-info-container', 'track-thumbnail', 'progress-bar', 'status-message',
            'radio-logo-container', 'station-logo', 'live-indicator', 'progress-container'
        ];
        const missingEl = requiredIds.find(id => !document.getElementById(id));
        if (missingEl) {
            console.warn('[fetchStatus] Missing expected element:', missingEl);
            return false;
        }
        return true;
    }

    function syncStatusTimingData(data) {
        const justSeeked = (Date.now() - lastSeekTime) < 1000;
        const preservedPosition = justSeeked ? lastSeekPosition : null;

        window.currentStatus = data;

        if (preservedPosition !== null) {
            window.currentStatus.currentTrackPosition = preservedPosition;
            lastServerPosition = preservedPosition;
            lastServerUpdateTime = Date.now();
            return;
        }

        lastServerPosition = data.currentTrackPosition || 0;
        lastServerUpdateTime = Date.now();
    }

    function isGensokyoStream(data) {
        return data.sourceType === 'Stream' && data.currentTrackUri && data.currentTrackUri.includes('stream.gensokyoradio.net');
    }

    function getDisplayTrackIdentity(data) {
        let displayTitle = data.currentTrackTitle || 'No track playing';
        const displayAuthor = data.currentTrackAuthor || '';

        if (data.sourceType === 'Radio') {
            const titleParts = displayTitle.split('|');
            if (titleParts.length > 1) {
                displayTitle = titleParts[0].trim();
            }
        }

        return { displayTitle, displayAuthor };
    }

    function getTrackSourceUrls(data) {
        if (data.sourceType === 'Spotify') {
            return {
                trackSourceUrl: resolveSpotifyTrackUrl({ uri: data.currentTrackUri, spotifyInfo: data.spotifyInfo }),
                authorSourceUrl: ''
            };
        }
        if (data.sourceType === 'Radio') {
            return {
                trackSourceUrl: '',
                authorSourceUrl: resolveRadioAuthorUrl(data)
            };
        }
        if (data.sourceType === 'Gensokyo Radio' || isGensokyoStream(data)) {
            return {
                trackSourceUrl: 'https://gensokyoradio.net/playing/',
                authorSourceUrl: ''
            };
        }
        return {
            trackSourceUrl: data.currentTrackUri || '',
            authorSourceUrl: ''
        };
    }

    function renderTrackIdentity(data, trackIdentity, sourceUrls) {
        const trackTitleElement = document.getElementById('track-title');
        if (sourceUrls.trackSourceUrl) {
            trackTitleElement.innerHTML = `<a href="${sourceUrls.trackSourceUrl}" target="_blank" class="track-title-link">${trackIdentity.displayTitle}</a>`;
            if (data.sourceType === 'Spotify' && data.spotifyInfo && data.spotifyInfo.releaseYear) {
                trackTitleElement.querySelector('a').innerHTML += ` <span class="release-year">(${data.spotifyInfo.releaseYear})</span>`;
            }
        } else {
            trackTitleElement.textContent = trackIdentity.displayTitle;
        }

        const trackAuthorElement = document.getElementById('track-author');
        if (sourceUrls.authorSourceUrl) {
            trackAuthorElement.innerHTML = `<a href="${sourceUrls.authorSourceUrl}" target="_blank" class="track-author-link">${trackIdentity.displayAuthor}</a>`;
        } else {
            trackAuthorElement.textContent = trackIdentity.displayAuthor;
        }

        document.getElementById('queue-info').textContent = `Queue: ${data.queueSize} track${data.queueSize !== 1 ? 's' : ''}`;
    }

    function updateSourceText(data) {
        const sourceText = document.getElementById('track-source-text');
        if (data.source && data.sourceType === '📻 Radio') {
            sourceText.textContent = 'Radio';
        } else if (data.sourceType === 'Gensokyo Radio' || isGensokyoStream(data)) {
            sourceText.innerHTML = '<a href="https://gensokyoradio.net/playing/" target="_blank">Gensokyo Radio</a>';
        } else if (data.sourceType === 'Radio') {
            sourceText.textContent = 'Radio';
        } else if (data.source) {
            sourceText.textContent = `${data.source}`;
        } else {
            sourceText.textContent = 'Source: Unknown';
        }
    }

    function updateSourceIcon(data) {
        if (data.sourceIconUrl) {
            const imageIcon = ensureSourceIconElement('IMG', data.source);
            if (!imageIcon) {
                return;
            }
            imageIcon.src = data.sourceIconUrl;
            imageIcon.style.display = 'inline-block';
            return;
        }

        const fontIcon = ensureSourceIconElement('I');
        if (!fontIcon) {
            return;
        }

        fontIcon.className = UI.getSourceIcon(data.sourceType);
        const sourceClass = getSourceTypeIconClass(data);
        if (sourceClass) {
            fontIcon.classList.add(sourceClass);
        }
    }

    function ensureSourceIconElement(tagName, sourceName) {
        let sourceIconElement = document.getElementById('track-source-icon');
        if (!sourceIconElement) {
            return null;
        }

        if (sourceIconElement.tagName !== tagName) {
            const replacement = document.createElement(tagName.toLowerCase());
            replacement.id = 'track-source-icon';
            if (tagName === 'IMG') {
                replacement.className = 'custom-source-icon';
                replacement.alt = sourceName || '';
                replacement.style.width = '16px';
                replacement.style.height = '16px';
                replacement.style.marginRight = '5px';
                replacement.style.verticalAlign = 'text-bottom';
            }
            sourceIconElement.replaceWith(replacement);
            sourceIconElement = replacement;
        }

        return sourceIconElement;
    }

    function getSourceTypeIconClass(data) {
        const sourceTypeLower = (data.sourceType || '').toLowerCase();
        const iconClassMap = {
            youtube: 'source-icon-youtube',
            spotify: 'source-icon-spotify',
            soundcloud: 'source-icon-soundcloud',
            tiktok: 'source-icon-tiktok',
            instagram: 'source-icon-instagram',
            twitter: 'source-icon-twitter',
            radio: 'source-icon-radio'
        };

        if (sourceTypeLower === 'gensokyo radio' || (sourceTypeLower === 'stream' && isGensokyoStream(data))) {
            return 'source-icon-gensokyoradio';
        }
        if (sourceTypeLower === 'local file' || sourceTypeLower === 'local') {
            return 'source-icon-local';
        }

        return iconClassMap[sourceTypeLower] || '';
    }

    function updateRequesterInfo(data) {
        const requesterEl = document.getElementById('track-requester');
        if (!requesterEl) {
            return;
        }
        const container = requesterEl.parentElement;
        if (data.requester) {
            const avatarHtml = data.requesterAvatar
                ? `<img src="${data.requesterAvatar}" class="requester-avatar" alt="${data.requester}">`
                : '<i class="fas fa-user"></i>';
            container.innerHTML = `${avatarHtml} <span id="track-requester">Requested by: ${data.requester}</span>`;
        } else {
            container.innerHTML = '<i class="fas fa-user"></i> <span id="track-requester">Requested by: Unknown</span>';
        }
    }

    function updateVolumeState(data) {
        const volumeSlider = document.getElementById('volume-slider');
        const volumeText = document.getElementById('track-volume');
        const volumeIcon = document.getElementById('volume-icon');
        const volume = data.volume !== undefined ? data.volume : 100;
        if (volumeText) volumeText.textContent = `${volume}%`;
        if (volumeSlider && !volumeSlider.matches(':active')) {
            volumeSlider.value = volume;
        }
        if (!volumeIcon) {
            return;
        }
        volumeIcon.className = 'fas';
        if (volume > 60) volumeIcon.classList.add('fa-volume-up');
        else if (volume > 30) volumeIcon.classList.add('fa-volume-down');
        else if (volume > 0) volumeIcon.classList.add('fa-volume-off');
        else volumeIcon.classList.add('fa-volume-mute');
    }

    function updateOptionalMetadataPanels(data) {
        const spotifyInfoContainer = document.getElementById('spotify-info-container');
        const gensokyoInfoContainer = document.getElementById('gensokyo-info-container');
        const localfileInfoContainer = document.getElementById('localfile-info-container');

        hideOptionalMetadataPanels(spotifyInfoContainer, gensokyoInfoContainer, localfileInfoContainer);

        if (data.sourceType === 'Spotify' && data.spotifyInfo) {
            populateSpotifyPanel(data.spotifyInfo, spotifyInfoContainer);
            return;
        }

        if ((data.sourceType === 'Gensokyo Radio' || isGensokyoStream(data)) && data.spotifyInfo) {
            populateGensokyoPanel(data.spotifyInfo, gensokyoInfoContainer);
            return;
        }

        if (data.sourceType === 'Local File') {
            populateLocalFilePanel(data, localfileInfoContainer);
        }
    }

    function hideOptionalMetadataPanels(spotifyInfoContainer, gensokyoInfoContainer, localfileInfoContainer) {
        spotifyInfoContainer.style.display = 'none';
        gensokyoInfoContainer.style.display = 'none';
        localfileInfoContainer.style.display = 'none';
    }

    function setMetadataText(id, value, label, unknownValue) {
        document.getElementById(id).textContent = value ? `${label}: ${value}` : `${label}: ${unknownValue}`;
    }

    function populateSpotifyPanel(spotifyInfo, spotifyInfoContainer) {
        setMetadataText('track-album-text', spotifyInfo.albumName, 'Album', 'Unknown');
        setMetadataText('track-year-text', spotifyInfo.releaseYear, 'Released', 'Unknown');
        spotifyInfoContainer.style.display = 'flex';
    }

    function populateGensokyoPanel(spotifyInfo, gensokyoInfoContainer) {
        setMetadataText('gensokyo-album-text', spotifyInfo.albumName, 'Album', 'Unknown');
        setMetadataText('gensokyo-circle-text', spotifyInfo.circleName, 'Circle', 'Unknown');
        setMetadataText('gensokyo-year-text', spotifyInfo.releaseYear, 'Year', 'Unknown');
        gensokyoInfoContainer.style.display = 'flex';
    }

    function populateLocalFilePanel(data, localfileInfoContainer) {
        const album = data.localAlbum && data.localAlbum !== 'Unknown Album' ? data.localAlbum : '';
        const genre = data.localGenre && data.localGenre !== 'Unknown Genre' ? data.localGenre : '';
        const year = data.localYear && data.localYear !== '' ? data.localYear : '';

        setMetadataText('localfile-album-text', album, 'Album', 'Unknown');
        setMetadataText('localfile-genre-text', genre, 'Genre', 'Unknown');
        setMetadataText('localfile-year-text', year, 'Year', 'Unknown');
        localfileInfoContainer.style.display = 'flex';
    }

    function updateAlbumArtClass(data) {
        console.log('[fetchStatus] Before thumbnail logic: currentTrackThumbnail:', data.currentTrackThumbnail, 'sourceType:', data.sourceType, 'currentTrackUri:', data.currentTrackUri);
        const albumArt = document.querySelector('.album-art');
        albumArt.className = 'album-art';
        if (data.sourceType) {
            albumArt.classList.add(`${data.sourceType.toLowerCase().replace(/\s+/g, '-')}-thumbnail`);
        }
    }

    function getOrCreateLiveIndicatorContainer() {
        const progressContainer = document.querySelector('.progress-container');
        const controlsContainer = document.querySelector('.controls');
        let liveIndicatorContainer = document.getElementById('live-indicator-container');
        if (!liveIndicatorContainer) {
            liveIndicatorContainer = document.createElement('div');
            liveIndicatorContainer.id = 'live-indicator-container';
            liveIndicatorContainer.className = 'time-display time-display-hidden';
            progressContainer.parentNode.insertBefore(liveIndicatorContainer, controlsContainer);
        }
        return liveIndicatorContainer;
    }

    function moveLiveIndicatorToContainer(liveIndicator) {
        const liveIndicatorContainer = getOrCreateLiveIndicatorContainer();
        if (liveIndicator.parentNode !== liveIndicatorContainer) {
            liveIndicatorContainer.innerHTML = '';
            liveIndicatorContainer.appendChild(liveIndicator);
        }
        liveIndicatorContainer.style.display = 'flex';
    }

    function moveLiveIndicatorToTimeDisplay(liveIndicator) {
        const timeDisplay = document.querySelector('.time-display');
        if (liveIndicator.parentNode && liveIndicator.parentNode.id === 'live-indicator-container') {
            timeDisplay.appendChild(liveIndicator);
        }
    }

    function updateRadioAndStreamLayout(data) {
        const radioLogoContainer = document.getElementById('radio-logo-container');
        const stationLogo = document.getElementById('station-logo');
        const liveIndicator = document.getElementById('live-indicator');
        const currentTime = document.getElementById('current-time');
        const totalTime = document.getElementById('total-time');
        const timeDisplay = document.querySelector('.time-display');

        if (data.sourceType === 'Radio') {
            radioLogoContainer.style.display = 'flex';
            stationLogo.src = data.radioLogoUrl || 'https://static.semrush.com/power-pages/media/favicons/onlineradiobox-com-favicon-7dd1a612.png';
            liveIndicator.style.display = 'inline-flex';
            currentTime.style.display = 'none';
            totalTime.style.display = 'none';
            timeDisplay.classList.add('time-display-hidden');
            timeDisplay.style.display = 'none';
            moveLiveIndicatorToContainer(liveIndicator);
            return;
        }

        if (data.sourceType === 'Gensokyo Radio' || isGensokyoStream(data)) {
            radioLogoContainer.style.display = 'flex';
            stationLogo.src = 'https://stream.gensokyoradio.net/images/logo.png';
            liveIndicator.style.display = 'inline-flex';
            currentTime.style.display = 'inline';
            totalTime.style.display = 'inline';
            timeDisplay.classList.add('with-live-indicator');
            timeDisplay.style.display = 'flex';
            moveLiveIndicatorToTimeDisplay(liveIndicator);
            updateGensokyoTimeData(data, currentTime, totalTime);
            return;
        }

        radioLogoContainer.style.display = 'none';
        timeDisplay.classList.remove('time-display-hidden');
        timeDisplay.style.display = 'flex';
        const liveIndicatorContainer = document.getElementById('live-indicator-container');
        if (liveIndicatorContainer) {
            liveIndicatorContainer.style.display = 'none';
        }
        moveLiveIndicatorToTimeDisplay(liveIndicator);

        const isStream = data.sourceType === 'Stream' || data.stream === true || data.isStream === true;
        if (isStream) {
            liveIndicator.style.display = 'inline-flex';
            currentTime.style.display = 'none';
            totalTime.style.display = 'none';
            timeDisplay.classList.add('time-display-hidden');
            timeDisplay.style.display = 'none';
            moveLiveIndicatorToContainer(liveIndicator);

            if (isGensokyoStream(data)) {
                document.getElementById('track-source-text').textContent = 'Source: Gensokyo Radio';
                if (!data.currentTrackThumbnail) {
                    document.getElementById('track-thumbnail').src = makeSafeThumbnail('https://stream.gensokyoradio.net/images/logo.png');
                }
            }
            return;
        }

        liveIndicator.style.display = 'none';
        currentTime.style.display = 'inline';
        totalTime.style.display = 'inline';
        timeDisplay.classList.remove('with-live-indicator');
        timeDisplay.classList.remove('time-display-hidden');
        timeDisplay.style.justifyContent = 'space-between';
    }

    function updateGensokyoTimeData(data, currentTime, totalTime) {
        if (!window.currentStatus.spotifyInfo) {
            window.currentStatus.spotifyInfo = {};
        }
        if (data.spotifyInfo && data.spotifyInfo.gensokyoDuration) {
            totalTime.textContent = UI.formatTime(data.spotifyInfo.gensokyoDuration);
            window.currentStatus.spotifyInfo.gensokyoDuration = data.spotifyInfo.gensokyoDuration;
            if (data.spotifyInfo.gensokyoPlayed !== undefined) {
                currentTime.textContent = UI.formatTime(data.spotifyInfo.gensokyoPlayed);
                window.currentStatus.spotifyInfo.lastServerGensokyoPlayed = data.spotifyInfo.gensokyoPlayed;
                window.currentStatus.spotifyInfo.localOffset = 0;
                window.currentStatus.spotifyInfo.gensokyoPlayed = data.spotifyInfo.gensokyoPlayed;
                const progress = (data.spotifyInfo.gensokyoPlayed / data.spotifyInfo.gensokyoDuration) * 100;
                document.getElementById('progress-bar').style.width = `${progress}%`;
            }
        } else {
            totalTime.textContent = '--:--';
            currentTime.textContent = '--:--';
        }

        if (data.spotifyInfo && data.spotifyInfo.gensokyoRemaining !== undefined) {
            window.currentStatus.spotifyInfo.gensokyoRemaining = data.spotifyInfo.gensokyoRemaining;
        }
    }

    function updateTrackThumbnail(data) {
        const thumbnail = document.getElementById('track-thumbnail');
        if (data.sourceType === 'Local' && data.currentTrackUri) {
            console.log('[fetchStatus] Local track detected. currentTrackUri:', data.currentTrackUri);
            const normalizedUri = data.currentTrackUri.replace(/\\/g, '/');
            let artworkFilename = '';
            try {
                artworkFilename = new URL(normalizedUri).pathname.split('/').pop();
            } catch (e) {
                artworkFilename = normalizedUri.split('/').pop();
            }

            if (artworkFilename && artworkFilename.trim() !== '') {
                console.log('[fetchStatus] Using artwork filename (fetchStatus):', artworkFilename);
                thumbnail.src = makeSafeThumbnail(`/api/artwork/${encodeURIComponent(artworkFilename)}`);
            } else {
                console.warn('[fetchStatus] Could not determine artwork filename for local track (fetchStatus):', data.currentTrackUri);
                thumbnail.src = makeSafeThumbnail('https://cdn-icons-png.flaticon.com/512/4725/4725478.png');
            }
        } else if (data.currentTrackThumbnail) {
            thumbnail.src = makeSafeThumbnail(data.currentTrackThumbnail);
        } else {
            thumbnail.src = makeSafeThumbnail(getDefaultThumbnail(data.sourceType));
        }

        if (data.sourceType === 'Radio' && data.radioSongImageUrl) {
            thumbnail.src = makeSafeThumbnail(data.radioSongImageUrl);
        }

        if ((data.sourceType === 'Gensokyo Radio' || isGensokyoStream(data)) && data.spotifyInfo && data.spotifyInfo.albumImageUrl) {
            thumbnail.src = makeSafeThumbnail(data.spotifyInfo.albumImageUrl);
        }
    }

    function updatePlaybackTimeAndProgress(data) {
        const currentTime = document.getElementById('current-time');
        const totalTime = document.getElementById('total-time');
        if (!(data.sourceType === 'Gensokyo Radio' || isGensokyoStream(data))) {
            if (currentTime) currentTime.textContent = UI.formatTime(window.currentStatus.currentTrackPosition || 0);
            if (totalTime) totalTime.textContent = UI.formatTime(data.currentTrackDuration || 0);
        }

        const duration = data.currentTrackDuration || 1;
        if (!(data.sourceType === 'Gensokyo Radio' || isGensokyoStream(data))) {
            const progressPercentage = data.currentTrackDuration ? (window.currentStatus.currentTrackPosition / duration) * 100 : 0;
            document.getElementById('progress-bar').style.width = `${progressPercentage}%`;
        }
    }

    function updatePlaybackButtonsAndStatus(data) {
        document.getElementById('play-button').disabled = !data.paused || !data.playing;
        document.getElementById('pause-button').disabled = data.paused || !data.playing;
        document.getElementById('skip-button').disabled = !data.hasNext && !data.playing;
        document.getElementById('stop-button').disabled = !data.playing;

        const statusElement = document.getElementById('status-message');
        statusElement.classList.remove('status-playing', 'status-paused', 'status-idle', 'status-error');
        if (data.playing) {
            if (data.paused) {
                statusElement.textContent = 'Paused';
                statusElement.classList.add('status-paused');
            } else {
                statusElement.textContent = 'Playing';
                statusElement.classList.add('status-playing');
            }
        } else {
            statusElement.textContent = 'Idle';
            statusElement.classList.add('status-idle');
        }

        clearInterval(progressInterval);
        if (data.playing && !data.paused) {
            progressInterval = setInterval(updateProgress, 100);
        }
    }

    async function updateYouTubeChapters(previousSourceType, previousTrackId, data) {
        const trackChanged = previousTrackId !== data.currentTrackUri;
        if ((previousSourceType !== data.sourceType && data.sourceType === 'YouTube') || (data.sourceType === 'YouTube' && trackChanged)) {
            console.log('YouTube track detected, fetching chapters...');
            try {
                if (typeof YouTubeChapters !== 'undefined' && YouTubeChapters.fetchYouTubeChapters) {
                    await YouTubeChapters.fetchYouTubeChapters();
                } else {
                    console.error('YouTubeChapters module not found or fetchYouTubeChapters not available');
                }
            } catch (chapterError) {
                console.error('Error fetching YouTube chapters:', chapterError);
            }
            return;
        }

        if (data.sourceType === 'YouTube') {
            try {
                if (typeof YouTubeChapters !== 'undefined' && YouTubeChapters.updateCurrentChapter) {
                    YouTubeChapters.updateCurrentChapter();
                }
            } catch (chapterError) {
                console.error('Error updating YouTube chapter:', chapterError);
            }
            return;
        }

        try {
            if (typeof YouTubeChapters !== 'undefined' && YouTubeChapters.hideChaptersContainer) {
                YouTubeChapters.hideChaptersContainer();
            }
        } catch (chapterError) {
            console.error('Error hiding YouTube chapters:', chapterError);
        }
    }
    
    // Fetch queue from the API
    async function fetchQueue() {
        try {
            if (!isPlayerViewActive()) return;

            const response = await fetch('/api/queue');
            const data = await response.json();

            const queueList = document.getElementById('queue-list');
            if (!queueList) return;
            queueList.innerHTML = '';
            
            if (data.length === 0) {
                queueList.innerHTML = '<div style="text-align: center; padding: 20px;">Queue is empty</div>';
                return;
            }
            
            data.forEach((track, index) => {
                queueList.appendChild(buildQueueItemElement(track, index));
            });
            
            // Add event listeners to remove buttons
            document.querySelectorAll('.remove-btn').forEach(button => {
                button.addEventListener('click', async (e) => {
                    const index = e.target.closest('.remove-btn').getAttribute('data-index');
                    await removeFromQueue(index);
                });
            });
            
            // Add event listeners to move-top buttons
            document.querySelectorAll('.move-top-btn').forEach(button => {
                button.addEventListener('click', async (e) => {
                    const index = parseInt(e.target.closest('.move-top-btn').getAttribute('data-index'));
                    if (index > 0) {
                        await moveTrack(index, 0);
                    }
                });
            });
            
            // Add event listeners to move-bottom buttons
            document.querySelectorAll('.move-bottom-btn').forEach(button => {
                button.addEventListener('click', async (e) => {
                    const index = parseInt(e.target.closest('.move-bottom-btn').getAttribute('data-index'));
                    const queueSize = data.length;
                    if (index < queueSize - 1) {
                        await moveTrack(index, queueSize - 1);
                    }
                });
            });
            
            // Set up drag and drop functionality
            setupDragAndDrop();
            
        } catch (error) {
            console.error('Error fetching queue:', error);
        }
    }
    
    // Set up drag and drop for queue items
    function setupDragAndDrop() {
        const queueItems = document.querySelectorAll('.queue-item');
        let draggedItem = null;
        
        queueItems.forEach(item => {
            // Drag start event
            item.addEventListener('dragstart', function(e) {
                draggedItem = this;
                setTimeout(() => {
                    this.classList.add('dragging');
                }, 0);
            });
            
            // Drag end event
            item.addEventListener('dragend', function(e) {
                this.classList.remove('dragging');
                draggedItem = null;
                document.querySelectorAll('.drag-over').forEach(item => {
                    item.classList.remove('drag-over');
                });
            });
            
            // Drag over event
            item.addEventListener('dragover', function(e) {
                e.preventDefault();
                if (this !== draggedItem) {
                    this.classList.add('drag-over');
                }
            });
            
            // Drag leave event
            item.addEventListener('dragleave', function(e) {
                this.classList.remove('drag-over');
            });
            
            // Drop event
            item.addEventListener('drop', async function(e) {
                e.preventDefault();
                if (draggedItem && this !== draggedItem) {
                    const fromIndex = parseInt(draggedItem.getAttribute('data-index'));
                    const toIndex = parseInt(this.getAttribute('data-index'));
                    
                    this.classList.remove('drag-over');
                    await moveTrack(fromIndex, toIndex);
                }
            });
        });
    }
    
    // Move track in queue
    async function moveTrack(fromIndex, toIndex) {
        try {
            // Add 1 to indexes because API uses 1-based indexing (while JavaScript uses 0-based)
            const response = await fetch(`/api/queue/move?from=${fromIndex + 1}&to=${toIndex + 1}`, {
                method: 'POST'
            });
            
            const data = await response.json();
            if (data.success) {
                fetchQueue();
                UI.showToast(`Track moved from position ${fromIndex + 1} to ${toIndex + 1}`, true);
            } else {
                UI.showToast(data.message || 'Failed to move track', false);
            }
        } catch (error) {
            console.error('Error moving track:', error);
            UI.showToast('Error moving track', false);
        }
    }
    
    // Control functions
    async function playTrack() {
        try {
            const response = await fetch('/api/play', { method: 'POST' });
            const data = await response.json();
            if (data.success) {
                fetchStatus();
            }
        } catch (error) {
            console.error('Error playing track:', error);
            UI.showToast('Error playing track', false);
        }
    }
    
    async function pauseTrack() {
        try {
            const response = await fetch('/api/pause', { method: 'POST' });
            const data = await response.json();
            if (data.success) {
                fetchStatus();
            }
        } catch (error) {
            console.error('Error pausing track:', error);
            UI.showToast('Error pausing track', false);
        }
    }
    
    async function skipTrack() {
        try {
            const response = await fetch('/api/skip', { method: 'POST' });
            const data = await response.json();
            if (data.success) {
                fetchStatus();
                fetchQueue();
            }
        } catch (error) {
            console.error('Error skipping track:', error);
            UI.showToast('Error skipping track', false);
        }
    }
    
    async function stopTrack() {
        try {
            const response = await fetch('/api/stop', { method: 'POST' });
            const data = await response.json();
            if (data.success) {
                fetchStatus();
                fetchQueue();
            }
        } catch (error) {
            console.error('Error stopping playback:', error);
            UI.showToast('Error stopping playback', false);
        }
    }
    
    async function removeFromQueue(index) {
        try {
            const response = await fetch(`/api/queue/${index}`, { method: 'DELETE' });
            const data = await response.json();
            if (data.success) {
                fetchQueue();
                fetchStatus();
                UI.showToast('Track removed from queue', true);
            }
        } catch (error) {
            console.error('Error removing track from queue:', error);
            UI.showToast('Error removing track from queue', false);
        }
    }

    /**
     * Add a track to the queue
     * @param {string} url - The track URL
     * @param {boolean} addNext - Whether to add the track next in queue (true) or at the end (false)
     */
    async function addToQueue(url, addNext = false) {
        try {
            if (!url || url.trim() === '') {
                UI.showToast('Please enter a valid URL or search term', false);
                return;
            }
            
            // Show loading indicator
            const addResult = document.getElementById('add-result');
            if (addResult) {
                addResult.innerHTML = '<div class="loading-spinner"></div>';
                addResult.style.display = 'block';
            }
            
            // Determine API endpoint based on addNext flag
            const endpoint = addNext ? '/api/queue/playnext' : '/api/queue/add';
            
            const response = await fetch(endpoint, {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/x-www-form-urlencoded'
                },
                body: `url=${encodeURIComponent(url)}`
            });
            
            const data = await response.json();
            
            if (addResult) {
                addResult.style.display = 'none';
            }
            
            if (data.success) {
                UI.showToast(data.message || `Track ${addNext ? 'will play next' : 'added to queue'}`, true);
                // Refresh the queue display
                fetchQueue();
            } else {
                UI.showToast(data.message || 'Failed to add track to queue', false);
            }
        } catch (error) {
            console.error('Error adding to queue:', error);
            UI.showToast('Error adding track to queue', false);
            
            const addResult = document.getElementById('add-result');
            if (addResult) {
                addResult.style.display = 'none';
            }
        }
    }

    // Initialize the player
    function initialize() {
        try {
            console.log('Initializing Player module...');
            
            // Initial fetch of status and queue
            fetchStatus().catch(err => {
                console.error('Initial status fetch error:', err);
                document.getElementById('status-message').textContent = 'Error connecting to server';
            });
            
            fetchQueue().catch(err => {
                console.error('Initial queue fetch error:', err);
            });
            
            // Set up periodic refresh
            setInterval(() => {
                fetchStatus().catch(err => {
                    console.error('Periodic status fetch error:', err);
                });
            }, 5000);
            
            setInterval(() => {
                fetchQueue().catch(err => {
                    console.error('Periodic queue fetch error:', err);
                });
            }, 10000);
            
            console.log('Player module initialized');
        } catch (error) {
            console.error('Error initializing Player module:', error);
        }
    }

    // Clear the entire queue
    async function clearQueue() {
        if (!confirm('Are you sure you want to clear the entire queue?')) return;
        
        try {
            const response = await fetch('/api/queue/clear', { method: 'POST' });
            const data = await response.json();
            
            if (data.success) {
                if (typeof UI !== 'undefined') UI.showToast('Queue cleared', true);
                fetchQueue();
            } else {
                if (typeof UI !== 'undefined') UI.showToast('Failed to clear queue: ' + (data.message || 'Unknown error'), false);
            }
        } catch (error) {
            console.error('Error clearing queue:', error);
            if (typeof UI !== 'undefined') UI.showToast('Error clearing queue', false);
        }
    }

    // Public API
    return {
        initialize,
        updateProgress,
        seekTrack,
        setupProgressBarInteraction,
        setupPlayerControls,
        fetchStatus,
        fetchQueue,
        setupDragAndDrop,
        moveTrack,
        playTrack,
        pauseTrack,
        skipTrack,
        stopTrack,
        removeFromQueue,
        addToQueue,
        clearQueue
    };
})();

// Expose to window for inline event handlers
window.Player = Player;