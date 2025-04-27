# Global Changelog
---

# 26 April 2025

### ▶️ Youtube playback fixed with **[`youtube-source 1.13.0`](https://github.com/lavalink-devs/youtube-source/releases/tag/1.13.0)**  

### 🚀 New Features
- Added `/radio` command that searches and plays radio stations from onlineradiobox.com.
- Added `/stats` command to view total play time and songs played. Stats are saved in 'serversettings.json'.

### 🔧 Changes
- `/nowplaying` now have more and cleaner informations depending on the media played.  
- `/play` now work with Spotify links instead of returning "Unknown file format." error.  
- `/lyrics` his now more robust finding lyrics.  
- Fixed Youtube livestreams bug at 30sec. Stream now auto reload if fail.  
- Fixed special Gensokyo Radio support.  
- Fixed crash if Spotify API is down at bot startup.  
- Capture YouTube `refreshToken` and update config.txt automatically.    
- Reactive buttons instead of emoji reactions for selecting a song.  
- Removed kotlin files and dependencies.  (`ForceToEnd.kt` translated to .java)  

### 📦 Dependency Updates
- **youtube-source**: `1.12.0` → `1.13.0`  
- **logback-classic**: `1.5.17` → `1.5.18`  
- **junit-jupiter-engine**: `5.12.1` → `5.13.0-M2`  
- **unirest-java-core**: `4.4.5` → `4.4.6`  
- **commons-io**: `2.18.0` → `2.19.0`  

---

# 26 March 2025  

### ▶️ Youtube playback fixed with **[`youtube-source 1.12.0`](https://github.com/lavalink-devs/youtube-source/releases/tag/1.12.0)**  

### 🚀 New Features
- Added the ability to queue multiple local files in a single message.

### 🔧 Changes
- Reverted `logback.xml` to basic info mode to reduce console spam.

### 📦 Dependency Updates
- **youtube-source**: `1.11.5` → `1.12.0`
- **net.dv8tion-JDA**: `5.2.2` → `5.3.0`
- **chew-m2**: `2.0` → `2.1`
- **logback-classic**: `1.4.14` → `1.5.17`
- **slf4j-api**: `2.0.7` → `2.0.17`
- **config**: `1.4.2` → `1.4.3`
- **commons-io**: `2.11.0` → `2.18.0`
- **junit-jupiter-api**: `4.13.2` → `5.12.1`
- **hamcrest**: `2.2` → `3.0`
- **msgpack-core**: `0.9.1` → `0.9.9`
- **jackson-dataformat-msgpack**: `0.9.1` → `0.9.9`
- **jsoup**: `1.15.3` → `1.19.1`
- **unirest-java-core**: `3.14.5` → `4.4.5`

---