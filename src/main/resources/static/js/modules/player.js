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
    
    // Update the progress bar
    function updateProgress() {
        if (!window.currentStatus || !window.currentStatus.playing || window.currentStatus.paused) return;
        
        // Handle Gensokyo Radio tracks separately since they have their own timing
        if ((window.currentStatus.sourceType === 'Gensokyo Radio' || 
            (window.currentStatus.sourceType === 'Stream' && 
             window.currentStatus.currentTrackUri && 
             window.currentStatus.currentTrackUri.includes('stream.gensokyoradio.net'))) && 
            window.currentStatus.spotifyInfo && 
            window.currentStatus.spotifyInfo.gensokyoDuration) {
            
            // For Gensokyo Radio, we increment the time smoothly between server updates
            // but we don't modify the base value that comes from the server
            
            // Only update the display if we have valid timing data
            if (window.currentStatus.spotifyInfo.gensokyoPlayed !== undefined) {
                // Store the last server value if not already stored
                if (window.currentStatus.spotifyInfo.lastServerGensokyoPlayed === undefined) {
                    window.currentStatus.spotifyInfo.lastServerGensokyoPlayed = window.currentStatus.spotifyInfo.gensokyoPlayed;
                    window.currentStatus.spotifyInfo.localOffset = 0;
                }
                
                // Increment local offset by 100ms
                window.currentStatus.spotifyInfo.localOffset += 100;
                
                // Calculate the current display time based on the last server value plus our local offset
                const displayTime = window.currentStatus.spotifyInfo.lastServerGensokyoPlayed + 
                                    window.currentStatus.spotifyInfo.localOffset;
                
                // Update the display with the calculated time
                document.getElementById('current-time').textContent = UI.formatTime(displayTime);
                
                // Calculate progress percentage based on the display time
                const progressPercentage = 
                    (displayTime / window.currentStatus.spotifyInfo.gensokyoDuration) * 100;
                
                // Update progress bar
                if (progressPercentage <= 100) {
                    document.getElementById('progress-bar').style.width = `${progressPercentage}%`;
                }
            }
            
            // Return early since we've handled the update
            return;
        }
        
        // Regular track handling (existing code)
        // Update currentStatus
        window.currentStatus.currentTrackPosition += 100; // Add 100ms (interval time)
        
        // Calculate percentage
        const progressPercentage = (window.currentStatus.currentTrackPosition / window.currentStatus.currentTrackDuration) * 100;
        if (progressPercentage <= 100) {
            document.getElementById('progress-bar').style.width = `${progressPercentage}%`;
            document.getElementById('current-time').textContent = UI.formatTime(window.currentStatus.currentTrackPosition);
            
            // Update current chapter
            if (window.currentStatus.sourceType === 'YouTube' && typeof YouTubeChapters !== 'undefined') {
                YouTubeChapters.updateCurrentChapter();
            }
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
                    progressBar.style.width = `${(window.currentStatus.currentTrackPosition / window.currentStatus.currentTrackDuration) * 100}%`;
                    document.getElementById('current-time').textContent = UI.formatTime(window.currentStatus.currentTrackPosition);
                }
            })
            .catch(error => {
                console.error('Error seeking:', error);
                UI.showToast('Error seeking to position', false);
                // Revert UI changes
                progressBar.style.width = `${(window.currentStatus.currentTrackPosition / window.currentStatus.currentTrackDuration) * 100}%`;
                document.getElementById('current-time').textContent = UI.formatTime(window.currentStatus.currentTrackPosition);
            });
    }
    
    // Add progress bar interactivity
    function setupProgressBarInteraction() {
        const progressContainer = document.getElementById('progress-container');
        const progressHandle = document.getElementById('progress-handle');
        const progressPreview = document.getElementById('progress-preview');
        
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
    
    // Fetch current status from the API
    async function fetchStatus() {
        try {
            const response = await fetch('/api/status');
            const data = await response.json();
            console.log('[fetchStatus] Data received from /api/status:', JSON.parse(JSON.stringify(data))); // Log a deep copy

            const previousSourceType = window.currentStatus.sourceType;
            const previousTrackId = window.currentStatus.currentTrackUri; // Store previous track ID
            window.currentStatus = data;
            
            // For Radio tracks, clean up the title by removing the station name
            let displayTitle = data.currentTrackTitle || 'No track playing';
            let displayAuthor = data.currentTrackAuthor || '';
            
            if (data.sourceType === 'Radio') {
                // Remove station name from title if it exists (format: "Title | STATION NAME")
                const titleParts = displayTitle.split('|');
                if (titleParts.length > 1) {
                    displayTitle = titleParts[0].trim();
                }
            }
            
            // Get the appropriate source URL for the track link
            let trackSourceUrl = '';
            let authorSourceUrl = '';
            
            if (data.sourceType === 'YouTube' && data.currentTrackUri) {
                trackSourceUrl = data.currentTrackUri;
            } else if (data.sourceType === 'Spotify') {
                // For Spotify tracks, check if we have the original Spotify URL
                if (data.currentTrackUri && data.currentTrackUri.includes('spotify')) {
                    // If the currentTrackUri already contains a Spotify URL, use it directly
                    trackSourceUrl = data.currentTrackUri;
                } else if (data.spotifyInfo && data.spotifyInfo.trackId) {
                    // Otherwise construct a Spotify URL from the track ID
                    trackSourceUrl = 'https://open.spotify.com/track/' + data.spotifyInfo.trackId;
                }
            } else if (data.sourceType === 'Radio') {
                // For Radio tracks, don't make the title clickable, but make the author (station name) clickable
                trackSourceUrl = ''; // Remove track title link for radio
                
                // Create proper OnlineRadioBox URL for the station
                if (data.radioCountry && data.radioAlias) {
                    // Construct the correct URL using country and alias format: https://onlineradiobox.com/country/alias/
                    authorSourceUrl = `https://onlineradiobox.com/${data.radioCountry}/${data.radioAlias}/`;
                } else if (data.radioStationUrl) {
                    // Fallback to radioStationUrl if provided
                    authorSourceUrl = data.radioStationUrl;
                }
            } else if (data.sourceType === 'Gensokyo Radio') {
                // Use Gensokyo Radio official "now playing" page URL
                trackSourceUrl = 'https://gensokyoradio.net/playing/';
            } else if (data.sourceType === 'SoundCloud' && data.currentTrackUri) {
                trackSourceUrl = data.currentTrackUri;
            } else if (data.currentTrackUri) {
                trackSourceUrl = data.currentTrackUri;
            }
            
            // Create a clickable title element if we have a URL
            const trackTitleElement = document.getElementById('track-title');
            if (trackSourceUrl) {
                // Create an anchor element with the track title
                trackTitleElement.innerHTML = `<a href="${trackSourceUrl}" target="_blank" class="track-title-link">${displayTitle}</a>`;
                
                // For Spotify tracks with release year, add it after the link
                if (data.sourceType === 'Spotify' && data.spotifyInfo && data.spotifyInfo.releaseYear) {
                    trackTitleElement.querySelector('a').innerHTML += ` <span class="release-year">(${data.spotifyInfo.releaseYear})</span>`;
                }
            } else {
                // No URL available, just display the text
                trackTitleElement.textContent = displayTitle;
            }
            
            // Create clickable author element if we have a URL for it (mainly for radio stations)
            const trackAuthorElement = document.getElementById('track-author');
            if (authorSourceUrl) {
                // Create an anchor element with the author name (radio station)
                trackAuthorElement.innerHTML = `<a href="${authorSourceUrl}" target="_blank" class="track-author-link">${displayAuthor}</a>`;
            } else {
                // Regular text for author when no special link is needed
                trackAuthorElement.textContent = displayAuthor;
            }
            
            document.getElementById('queue-info').textContent = `Queue: ${data.queueSize} track${data.queueSize !== 1 ? 's' : ''}`;
            
            // Update metadata with more specific info we added to the API
            if (data.source && data.sourceType === '📻 Radio') {
                document.getElementById('track-source-text').textContent = `Radio`;
            } else if (data.sourceType === 'Stream' && data.currentTrackUri && data.currentTrackUri.includes('stream.gensokyoradio.net')) {
                document.getElementById('track-source-text').innerHTML = `<a href="https://gensokyoradio.net/playing/" target="_blank">Gensokyo Radio</a>`;
            } else if (data.sourceType === 'Gensokyo Radio') {
                document.getElementById('track-source-text').innerHTML = `<a href="https://gensokyoradio.net/playing/" target="_blank">Gensokyo Radio</a>`;
            } else if (data.sourceType === 'Radio') {
                document.getElementById('track-source-text').textContent = `Radio`;
            } else if (data.source) {
                document.getElementById('track-source-text').textContent = `${data.source}`;
            } else {
                document.getElementById('track-source-text').textContent = 'Source: Unknown';
            }
            
            // Update source icon based on the source type
            const sourceIconElement = document.getElementById('track-source-icon');
            if (sourceIconElement) {
                // Remove all existing classes except 'fas' or 'fab'
                sourceIconElement.className = '';
                // Set the appropriate icon based on source type
                const sourceIcon = UI.getSourceIcon(data.sourceType);
                sourceIconElement.className = sourceIcon;
            }
            
            // Update requester from API data
            if (data.requester) {
                document.getElementById('track-requester').textContent = `Requested by: ${data.requester}`;
            } else {
                document.getElementById('track-requester').textContent = 'Requested by: Unknown';
            }
            
            // Update volume from API data
            document.getElementById('track-volume').textContent = `Volume: ${data.volume || 100}%`;
            
            // Show Spotify information if available
            const spotifyInfoContainer = document.getElementById('spotify-info-container');
            const gensokyoInfoContainer = document.getElementById('gensokyo-info-container');
            const localfileInfoContainer = document.getElementById('localfile-info-container'); // Get local file container

            // Hide all optional info containers by default
            spotifyInfoContainer.style.display = 'none';
            gensokyoInfoContainer.style.display = 'none';
            localfileInfoContainer.style.display = 'none';
            
            if (data.sourceType === 'Spotify' && data.spotifyInfo) {
                // Update album name
                if (data.spotifyInfo.albumName) {
                    document.getElementById('track-album-text').textContent = `Album: ${data.spotifyInfo.albumName}`;
                } else {
                    document.getElementById('track-album-text').textContent = 'Album: Unknown';
                }
                
                // Update release year
                if (data.spotifyInfo.releaseYear) {
                    document.getElementById('track-year-text').textContent = `Released: ${data.spotifyInfo.releaseYear}`;
                } else {
                    document.getElementById('track-year-text').textContent = 'Released: Unknown';
                }
                
                // Show the Spotify info container and hide Gensokyo container
                spotifyInfoContainer.style.display = 'flex';
                // gensokyoInfoContainer.style.display = 'none'; // Already hidden by default
                // localfileInfoContainer.style.display = 'none'; // Already hidden by default
            } else if ((data.sourceType === 'Gensokyo Radio' || 
                       (data.sourceType === 'Stream' && data.currentTrackUri && 
                        data.currentTrackUri.includes('stream.gensokyoradio.net'))) && 
                       data.spotifyInfo) {
                
                // Show Gensokyo Radio info container
                gensokyoInfoContainer.style.display = 'flex';
                // spotifyInfoContainer.style.display = 'none'; // Already hidden
                // localfileInfoContainer.style.display = 'none'; // Already hidden
                
                // Update album name
                if (data.spotifyInfo.albumName) {
                    document.getElementById('gensokyo-album-text').textContent = `Album: ${data.spotifyInfo.albumName}`;
                } else {
                    document.getElementById('gensokyo-album-text').textContent = 'Album: Unknown';
                }
                
                // Update circle name
                if (data.spotifyInfo.circleName) {
                    document.getElementById('gensokyo-circle-text').textContent = `Circle: ${data.spotifyInfo.circleName}`;
                } else {
                    document.getElementById('gensokyo-circle-text').textContent = 'Circle: Unknown';
                }
                
                // Update release year
                if (data.spotifyInfo.releaseYear) {
                    document.getElementById('gensokyo-year-text').textContent = `Year: ${data.spotifyInfo.releaseYear}`;
                } else {
                    document.getElementById('gensokyo-year-text').textContent = 'Year: Unknown';
                }
            } else if (data.sourceType === 'Local File') {
                // Show Local File info container
                localfileInfoContainer.style.display = 'flex';
                // spotifyInfoContainer.style.display = 'none'; // Already hidden
                // gensokyoInfoContainer.style.display = 'none'; // Already hidden

                // Update local file metadata
                if (data.localAlbum && data.localAlbum !== "Unknown Album") {
                    document.getElementById('localfile-album-text').textContent = `Album: ${data.localAlbum}`;
                } else {
                    document.getElementById('localfile-album-text').textContent = 'Album: Unknown';
                }
                if (data.localGenre && data.localGenre !== "Unknown Genre") {
                    document.getElementById('localfile-genre-text').textContent = `Genre: ${data.localGenre}`;
                } else {
                    document.getElementById('localfile-genre-text').textContent = 'Genre: Unknown';
                }
                if (data.localYear && data.localYear !== "") {
                    document.getElementById('localfile-year-text').textContent = `Year: ${data.localYear}`;
                } else {
                    document.getElementById('localfile-year-text').textContent = 'Year: Unknown';
                }
            } else {
                // Hide all optional info containers for other track types (already done by default hide)
            }
            
            // Log values just before thumbnail logic
            console.log('[fetchStatus] Before thumbnail logic: currentTrackThumbnail:', data.currentTrackThumbnail, 'sourceType:', data.sourceType, 'currentTrackUri:', data.currentTrackUri);

            // Update album art container class based on source type
            const albumArt = document.querySelector('.album-art');
            albumArt.className = 'album-art'; // Reset classes
            
            if (data.sourceType) {
                // Replace spaces with hyphens for CSS class names
                const cssFriendlySourceType = data.sourceType.toLowerCase().replace(/\s+/g, '-');
                albumArt.classList.add(`${cssFriendlySourceType}-thumbnail`);
            }
            
            // Manage radio station logo and live indicator for Radio tracks
            const radioLogoContainer = document.getElementById('radio-logo-container');
            const stationLogo = document.getElementById('station-logo');
            const liveIndicator = document.getElementById('live-indicator');
            const currentTime = document.getElementById('current-time');
            const totalTime = document.getElementById('total-time');
            
            if (data.sourceType === 'Radio') {
                // Show the radio logo container
                radioLogoContainer.style.display = 'flex';
                
                // Show the station logo if we have one
                if (data.radioLogoUrl) {
                    stationLogo.src = data.radioLogoUrl;
                } else {
                    stationLogo.src = 'https://static.semrush.com/power-pages/media/favicons/onlineradiobox-com-favicon-7dd1a612.png';
                }
                
                // Show LIVE indicator instead of time display for radio
                liveIndicator.style.display = 'inline-flex';
                currentTime.style.display = 'none';
                totalTime.style.display = 'none';
                
                // Add special class for radio streams that don't show time display
                const timeDisplay = document.querySelector('.time-display');
                timeDisplay.classList.add('time-display-hidden');
                timeDisplay.style.display = 'none';
                
                // Move the LIVE indicator to its own container after the progress bar
                const progressContainer = document.querySelector('.progress-container');
                const controlsContainer = document.querySelector('.controls');
                
                // Create or use an existing container for the live indicator
                let liveIndicatorContainer = document.getElementById('live-indicator-container');
                if (!liveIndicatorContainer) {
                    liveIndicatorContainer = document.createElement('div');
                    liveIndicatorContainer.id = 'live-indicator-container';
                    liveIndicatorContainer.className = 'time-display time-display-hidden';
                    progressContainer.parentNode.insertBefore(liveIndicatorContainer, controlsContainer);
                }
                
                // Move the live indicator to the container if it's not already there
                if (liveIndicator.parentNode !== liveIndicatorContainer) {
                    liveIndicatorContainer.innerHTML = '';
                    liveIndicatorContainer.appendChild(liveIndicator);
                }
                
                liveIndicatorContainer.style.display = 'flex';
            } else if (data.sourceType === 'Gensokyo Radio' || 
                      (data.sourceType === 'Stream' && data.currentTrackUri && 
                       data.currentTrackUri.includes('stream.gensokyoradio.net'))) {
                // Show the radio logo container for Gensokyo Radio
                radioLogoContainer.style.display = 'flex';
                
                // Use Gensokyo Radio logo
                stationLogo.src = 'https://stream.gensokyoradio.net/images/logo.png';
                
                // Show LIVE indicator for Gensokyo Radio
                liveIndicator.style.display = 'inline-flex';
                currentTime.style.display = 'inline';
                totalTime.style.display = 'inline';
                
                // Add class to time display container for proper styling
                document.querySelector('.time-display').classList.add('with-live-indicator');
                document.querySelector('.time-display').style.display = 'flex';
                
                // Move the live indicator back to the time display if needed
                if (liveIndicator.parentNode.id === 'live-indicator-container') {
                    document.querySelector('.time-display').appendChild(liveIndicator);
                }
                
                // Save the timing information to our currentStatus object for the progress updates
                if (!window.currentStatus.spotifyInfo) {
                    window.currentStatus.spotifyInfo = {};
                }
                
                // Update total time for Gensokyo Radio
                if (data.spotifyInfo && data.spotifyInfo.gensokyoDuration) {
                    totalTime.textContent = UI.formatTime(data.spotifyInfo.gensokyoDuration);
                    window.currentStatus.spotifyInfo.gensokyoDuration = data.spotifyInfo.gensokyoDuration;
                    
                    // Update current time if available
                    if (data.spotifyInfo.gensokyoPlayed !== undefined) {
                        currentTime.textContent = UI.formatTime(data.spotifyInfo.gensokyoPlayed);
                        
                        // Reset the local offset when we get new server data
                        window.currentStatus.spotifyInfo.lastServerGensokyoPlayed = data.spotifyInfo.gensokyoPlayed;
                        window.currentStatus.spotifyInfo.localOffset = 0;
                        
                        window.currentStatus.spotifyInfo.gensokyoPlayed = data.spotifyInfo.gensokyoPlayed;
                        
                        // Update progress bar
                        const progress = (data.spotifyInfo.gensokyoPlayed / data.spotifyInfo.gensokyoDuration) * 100;
                        document.getElementById('progress-bar').style.width = `${progress}%`;
                    }
                } else {
                    // Default values if timing info not available
                    totalTime.textContent = '--:--';
                    currentTime.textContent = '--:--';
                }
                
                // Store remaining time if available
                if (data.spotifyInfo && data.spotifyInfo.gensokyoRemaining !== undefined) {
                    window.currentStatus.spotifyInfo.gensokyoRemaining = data.spotifyInfo.gensokyoRemaining;
                }
            } else {
                // Hide the radio logo container for non-radio tracks
                radioLogoContainer.style.display = 'none';
                
                // Reset the time display to default
                const timeDisplay = document.querySelector('.time-display');
                timeDisplay.classList.remove('time-display-hidden');
                timeDisplay.style.display = 'flex';
                
                // Hide the separate live indicator container if it exists
                const liveIndicatorContainer = document.getElementById('live-indicator-container');
                if (liveIndicatorContainer) {
                    liveIndicatorContainer.style.display = 'none';
                }
                
                // Move the live indicator back to the time display if needed
                if (liveIndicator.parentNode.id === 'live-indicator-container') {
                    document.querySelector('.time-display').appendChild(liveIndicator);
                }
                
                // For regular Stream sources, show LIVE indicator
                if (data.sourceType === 'Stream') {
                    liveIndicator.style.display = 'inline-flex';
                    currentTime.style.display = 'none';
                    totalTime.style.display = 'none';
                    
                    // Remove special class from time display if it exists
                    document.querySelector('.time-display').classList.remove('with-live-indicator');
                    
                    // Special handling for Gensokyo Radio streams that are showing as 'Stream' type
                    if (data.currentTrackUri && data.currentTrackUri.includes('stream.gensokyoradio.net')) {
                        // Set specific source text for Gensokyo Radio streams
                        document.getElementById('track-source-text').textContent = 'Source: Gensokyo Radio';
                        
                        // Use Gensokyo Radio logo if no custom thumbnail
                        if (!data.currentTrackThumbnail) {
                            document.getElementById('track-thumbnail').src = 'https://stream.gensokyoradio.net/images/logo.png';
                        }
                    }
                } else {
                    // Hide LIVE indicator for non-radio/stream tracks
                    liveIndicator.style.display = 'none';
                    currentTime.style.display = 'inline';
                    totalTime.style.display = 'inline';
                    
                    // Remove special class from time display
                    document.querySelector('.time-display').classList.remove('with-live-indicator');
                }
            }
            
            // Update thumbnail based on data from API
            // Prioritize local artwork logic if sourceType is Local
            if (data.sourceType === 'Local' && data.currentTrackUri) {
                console.log('[fetchStatus] Local track detected. currentTrackUri:', data.currentTrackUri);
                let rawUri = data.currentTrackUri;
                let artworkFilename = '';

                if (rawUri) {
                    const normalizedUri = rawUri.replace(/\\/g, '/');
                    try {
                        const trackUrl = new URL(normalizedUri); 
                        artworkFilename = trackUrl.pathname.split('/').pop();
                    } catch (e) {
                        artworkFilename = normalizedUri.split('/').pop();
                    }
                }

                if (artworkFilename && artworkFilename.trim() !== '') {
                    console.log('[fetchStatus] Using artwork filename (fetchStatus):', artworkFilename);
                    document.getElementById('track-thumbnail').src = `/api/artwork/${encodeURIComponent(artworkFilename)}`;
                } else {
                    console.warn('[fetchStatus] Could not determine artwork filename for local track (fetchStatus):', rawUri);
                    document.getElementById('track-thumbnail').src = 'https://cdn-icons-png.flaticon.com/512/4725/4725478.png';
                }
            } else if (data.currentTrackThumbnail) { // This will now be checked only if not Local or Local without URI
                document.getElementById('track-thumbnail').src = data.currentTrackThumbnail;
            } else {
                // Set a default thumbnail based on source type (if not Local and no specific thumbnail)
                const defaultThumbnails = {
                    'YouTube': 'https://www.gstatic.com/youtube/img/branding/youtubelogo/svg/youtubelogo.svg',
                    'SoundCloud': 'https://developers.soundcloud.com/assets/logo_big_white-65c2b096da68dd533db18b5a2bcfbcce.png',
                    'Spotify': 'https://www.freepnglogos.com/uploads/spotify-logo-png/file-spotify-logo-png-4.png',
                    'Local': 'https://cdn-icons-png.flaticon.com/512/4725/4725478.png',
                    'Radio': 'https://static.semrush.com/power-pages/media/favicons/onlineradiobox-com-favicon-7dd1a612.png',
                    'Stream': 'https://cdn-icons-png.flaticon.com/128/11796/11796884.png',
                    'Gensokyo Radio': 'https://stream.gensokyoradio.net/images/logo.png'
                };
                
                const defaultImage = defaultThumbnails[data.sourceType] || 'https://cdn.discordapp.com/embed/avatars/0.png';
                document.getElementById('track-thumbnail').src = defaultImage;
            }
            
            // For radio tracks, if we have a song image, use it as the main thumbnail and keep the station logo separate
            if (data.sourceType === 'Radio' && data.radioSongImageUrl) {
                document.getElementById('track-thumbnail').src = data.radioSongImageUrl;
            }
            
            // For Gensokyo Radio, we might have albumImageUrl in the spotifyInfo object
            if ((data.sourceType === 'Gensokyo Radio' || 
                (data.sourceType === 'Stream' && data.currentTrackUri && 
                data.currentTrackUri.includes('stream.gensokyoradio.net'))) && 
                data.spotifyInfo && data.spotifyInfo.albumImageUrl) {
                document.getElementById('track-thumbnail').src = data.spotifyInfo.albumImageUrl;
            }
            
            // Update the current status safely - except for Gensokyo Radio tracks which handle their own time display
            if (data.sourceType !== 'Gensokyo Radio' && 
                !(data.sourceType === 'Stream' && data.currentTrackUri && 
                  data.currentTrackUri.includes('stream.gensokyoradio.net'))) {
                
                if (currentTime) currentTime.textContent = UI.formatTime(data.currentTrackPosition || 0);
                if (totalTime) totalTime.textContent = UI.formatTime(data.currentTrackDuration || 0);
            }
            
            // Protect against division by zero - ensure we have a valid duration
            const duration = data.currentTrackDuration || 1; // Use 1ms as fallback to avoid division by zero
            
            // Only update progress bar for non-Gensokyo Radio tracks (Gensokyo uses its own timing)
            if (!(data.sourceType === 'Gensokyo Radio' || 
                (data.sourceType === 'Stream' && data.currentTrackUri && 
                 data.currentTrackUri.includes('stream.gensokyoradio.net')))) {
                const progressPercentage = data.currentTrackDuration ? 
                    (data.currentTrackPosition / duration) * 100 : 0;
                    
                document.getElementById('progress-bar').style.width = `${progressPercentage}%`;
            }
            
            // Enable/disable buttons based on playback state
            document.getElementById('play-button').disabled = !data.paused || !data.playing;
            document.getElementById('pause-button').disabled = data.paused || !data.playing;
            document.getElementById('skip-button').disabled = !data.hasNext && !data.playing;
            document.getElementById('stop-button').disabled = !data.playing;
            
            // Update status message with appropriate class
            const statusElement = document.getElementById('status-message');
            
            // Remove all status classes first
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
            
            // Use the new inVoiceChannel property from the API response
            UI.updateBotStatusIndicator(data.inVoiceChannel);
            
            // Fetch chapters if source type changed to YouTube OR if track changed while type is still YouTube
            const trackChanged = previousTrackId !== data.currentTrackUri;
            
            if ((previousSourceType !== data.sourceType && data.sourceType === 'YouTube') || 
                (data.sourceType === 'YouTube' && trackChanged)) {
                console.log('YouTube track detected, fetching chapters...');
                try {
                    if (typeof YouTubeChapters !== 'undefined' && YouTubeChapters.fetchYouTubeChapters) {
                        await YouTubeChapters.fetchYouTubeChapters();
                    } else {
                        console.error('YouTubeChapters module not found or fetchYouTubeChapters not available');
                    }
                } catch (chapterError) {
                    console.error('Error fetching YouTube chapters:', chapterError);
                    // Continue execution even if chapters fail
                }
            } else if (data.sourceType === 'YouTube') {
                // Just update current chapter if already showing chapters
                try {
                    if (typeof YouTubeChapters !== 'undefined' && YouTubeChapters.updateCurrentChapter) {
                        YouTubeChapters.updateCurrentChapter();
                    }
                } catch (chapterError) {
                    console.error('Error updating YouTube chapter:', chapterError);
                }
            } else {
                // Hide chapters if not YouTube
                try {
                    if (typeof YouTubeChapters !== 'undefined' && YouTubeChapters.hideChaptersContainer) {
                        YouTubeChapters.hideChaptersContainer();
                    }
                } catch (chapterError) {
                    console.error('Error hiding YouTube chapters:', chapterError);
                }
            }
        } catch (error) {
            console.error('Error fetching status:', error);
            const statusElement = document.getElementById('status-message');
            statusElement.textContent = 'Error connecting to server';
            statusElement.classList.remove('status-playing', 'status-paused', 'status-idle');
            statusElement.classList.add('status-error');
        }
    }
    
    // Fetch queue from the API
    async function fetchQueue() {
        try {
            const response = await fetch('/api/queue');
            const data = await response.json();
            
            const queueList = document.getElementById('queue-list');
            queueList.innerHTML = '';
            
            if (data.length === 0) {
                queueList.innerHTML = '<div style="text-align: center; padding: 20px;">Queue is empty</div>';
                return;
            }
            
            data.forEach((track, index) => {
                const item = document.createElement('div');
                item.className = 'queue-item';
                item.setAttribute('data-index', index);
                item.setAttribute('draggable', 'true');
                
                // For thumbnails in queue, use the right approach based on source type
                let thumbnailUrl = track.thumbnail; // This should now have local_artwork/hash.ext for local files
                
                // If no thumbnail provided, or for specific source types, override with defaults
                // The logic for local files is now primarily handled by the backend providing the correct track.thumbnail path.
                // We just need a fallback here if track.thumbnail is somehow empty.
                if (!thumbnailUrl || thumbnailUrl === '') {
                    // Fallback for non-local types or if local thumbnail path is missing
                    const defaultThumbnails = {
                        'YouTube': 'https://www.gstatic.com/youtube/img/branding/youtubelogo/svg/youtubelogo.svg',
                        'SoundCloud': 'https://developers.soundcloud.com/assets/logo_big_white-65c2b096da68dd533db18b5a2bcfbcce.png',
                        'Spotify': 'https://www.freepnglogos.com/uploads/spotify-logo-png/file-spotify-logo-png-4.png',
                        'Local File': 'https://cdn-icons-png.flaticon.com/512/4725/4725478.png', // Ensure sourceType matches 'Local File'
                        'Radio': 'https://static.semrush.com/power-pages/media/favicons/onlineradiobox-com-favicon-7dd1a612.png',
                        'Stream': 'https://cdn-icons-png.flaticon.com/128/11796/11796884.png',
                        'Gensokyo Radio': 'https://stream.gensokyoradio.net/images/logo.png'
                    };
                    thumbnailUrl = defaultThumbnails[track.sourceType] || 'https://cdn.discordapp.com/embed/avatars/0.png';
                } else if (track.sourceType === 'Local File' && !thumbnailUrl.startsWith('local_artwork/')){
                    // If it's a local file but the thumbnail isn't the expected path, use fallback
                    // This handles cases where backend might not have populated it correctly for some reason
                    console.warn('[fetchQueue] Local File track.thumbnail does not seem to be a local_artwork path:', thumbnailUrl);
                    thumbnailUrl = 'https://cdn-icons-png.flaticon.com/512/4725/4725478.png'; 
                }
                
                // Add source-specific class for thumbnail styling
                const thumbnailClass = track.sourceType ? 
                    `queue-item-thumbnail ${track.sourceType.toLowerCase().replace(/\s+/g, '-')}-thumbnail` : 
                    'queue-item-thumbnail';
                
                // Include source icon and requester info
                const sourceIcon = UI.getSourceIcon(track.sourceType);
                const requesterInfo = track.requester ? ` • Requested by: ${track.requester}` : '';
                
                // Get appropriate source URL for the track
                let trackSourceUrl = '';
                let queueAuthorSourceUrl = '';
                
                if (track.sourceType === 'YouTube' && track.uri) {
                    trackSourceUrl = track.uri;
                } else if (track.sourceType === 'Spotify') {
                    // For Spotify tracks, check if we have the original Spotify URL
                    if (track.uri && track.uri.includes('spotify')) {
                        // If the uri already contains a Spotify URL, use it directly
                        trackSourceUrl = track.uri;
                    } else if (track.spotifyInfo && track.spotifyInfo.trackId) {
                        // Otherwise construct a Spotify URL from the track ID
                        trackSourceUrl = 'https://open.spotify.com/track/' + track.spotifyInfo.trackId;
                    }
                } else if (track.sourceType === 'Radio') {
                    // For Radio tracks, don't make the title clickable, but make the author (station name) clickable
                    trackSourceUrl = ''; // Remove track title link for radio
                    
                    // Create proper OnlineRadioBox URL for the station
                    if (track.radioCountry && track.radioAlias) {
                        // Construct the correct URL using country and alias format: https://onlineradiobox.com/country/alias/
                        queueAuthorSourceUrl = `https://onlineradiobox.com/${track.radioCountry}/${track.radioAlias}/`;
                    } else if (track.radioStationUrl) {
                        // Fallback to radioStationUrl if provided
                        queueAuthorSourceUrl = track.radioStationUrl;
                    }
                } else if (track.sourceType === 'Gensokyo Radio') {
                    trackSourceUrl = 'https://gensokyoradio.net/playing/';
                } else if (track.sourceType === 'SoundCloud' && track.uri) {
                    trackSourceUrl = track.uri;
                } else if (track.uri) {
                    trackSourceUrl = track.uri;
                }
                
                // Special handling for Stream type with Gensokyo Radio URL
                if (track.sourceType === 'Stream' && track.uri && track.uri.includes('stream.gensokyoradio.net')) {
                    // Override source to display "Gensokyo Radio" instead of "Web Stream"
                    track.source = 'Gensokyo Radio';
                    trackSourceUrl = 'https://gensokyoradio.net/playing/';
                }
                
                // Create title element based on whether we have a source URL
                const titleElement = trackSourceUrl ? 
                    `<div class="queue-item-title">${index + 1}. <a href="${trackSourceUrl}" target="_blank" class="track-title-link">${track.title}</a></div>` :
                    `<div class="queue-item-title">${index + 1}. ${track.title}</div>`;
                
                // Create author element based on whether we have an author source URL
                const authorElement = queueAuthorSourceUrl ?
                    `<span class="queue-item-author"><a href="${queueAuthorSourceUrl}" target="_blank" class="track-author-link">${track.author}</a></span>` :
                    `<span class="queue-item-author">${track.author}</span>`;
                
                item.innerHTML = `
                    <div class="drag-handle" title="Drag to reorder">
                        <i class="fas fa-grip-lines"></i>
                    </div>
                    <div class="${thumbnailClass}">
                        <img src="${thumbnailUrl}" alt="${track.title}">
                    </div>
                    <div class="queue-item-info">
                        ${titleElement}
                        <div class="queue-item-meta">
                            ${authorElement}
                            <span class="queue-item-source"><i class="${sourceIcon}"></i> ${track.source}</span>
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
                
                queueList.appendChild(item);
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

    // Public API
    return {
        initialize,
        updateProgress,
        seekTrack,
        setupProgressBarInteraction,
        fetchStatus,
        fetchQueue,
        setupDragAndDrop,
        moveTrack,
        playTrack,
        pauseTrack,
        skipTrack,
        stopTrack,
        removeFromQueue,
        addToQueue
    };
})(); 