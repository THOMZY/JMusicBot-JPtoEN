# Global Changelog

---

# 05 March 2026

## ▶️ Fixed slash commands. ( merge original fork [Cosgy](https://github.com/Cosgy-Dev/JMusicBot-JP) )

### 🔧 Changes
- Minor code clean.

### 📦 Dependency Updates
- Removed **jda-chewtils** for Cosgy implementation.


---

# 04 March 2026

## ▶️ Fixed bot join/quit loop. ( merge original fork [Cosgy](https://github.com/Cosgy-Dev/JMusicBot-JP) )

### 🔧 Changes
- Added DAVE (jdave) dependencies.
- Updated to Java 25.

### 📦 Dependency Updates
- **youtube-source**: `1.17.0` → `1.18.0`
- **dev.arbjerg-lavaplayer**: `2.2.4` → `2.2.6`
- **net.dv8tion-JDA**: `5.6.1` → `6.3.1`
- **spring-boot-starter-web**: `3.3.0` → `4.1.0-M2`
- **spring-boot-starter-websocket**: `3.3.0` → `4.1.0-M2`
---

# 21 February 2026

### 🚀 New Features
- IPv6 rotation implementation.

### 🔧 Changes
- Better console in webpanel.
- Fixed yt-dlp for linux download.

### 📦 Dependency Updates
- **dev.arbjerg-lavaplayer**: `2.2.4` → `2.2.6`
+ **dev.arbjerg-lavaplayer-ext-youtube-rotator**: `2.2.6`

---

# 06 February 2026

### 🔧 Changes
- Webpanel :
    - Volume control slider in player.
    - Discord users avatars caching.
    - Favicon for all yt-dlp sources.

---

# 29 January 2026

### 🚀 New Features
- `/shuffle` now have 2 modes, "all" tracks in queue or "mytracks" only.
- yt-dlp support for all **[`supported sites`](https://github.com/yt-dlp/yt-dlp/blob/master/supportedsites.md)**.

### 🔧 Changes
- Webpanel : Responsive mobile version, better layout.

### 📦 Dependency Updates
- **youtube-source**: `1.16.0` → `1.17.0`

---

# 08 January 2026

### 🔧 Changes
- yt-dlp JS runtime (Deno) + cookies
- Better web panel history : 
infinite scroll, instant search and time range calendar

---

# 16 December 2025

## ▶️ Added yt-dlp support for 3 new sources : Instagram, Tiktok, X / Twitter.

### 🚀 New Features
- yt-dlp support for Instagram, Tiktok, Twitter.

### 🔧 Changes
- Clear yt-dlp cache when bot leave VC.
- Web panel, playing status and history support for the new sources.

---

# 12 December 2025

## ▶️ Added Youtube fallback if youtube-source fail with **[`yt-dlp`](https://github.com/yt-dlp/yt-dlp)**

### 🚀 New Features
- Added yt-dlp fallback.

### 🔧 Changes
- Switched Web panel to SPA, interface now more responsive.
- Fixed minors bugs and visuals in web panel.
- Added `/SetTopicStatus` command. ( to use with `/SetTC set` )
- Fixed `/SetVCStatus` not saving in "serversettings.json".

---

# 25 November 2025

## ▶️ Youtube playback fixed by not refreshing "ytRefreshToken". (YouTube OAuth2)

### 🔧 Changes
- History fixes ( Removed history size limit + Youtube livestream added multiples times )
- Added "LIVE" indicator under player for Youtube streams.
- Fixed country flag emoji in radio station search results.

---

# 05 November 2025

## ▶️ Youtube playback fixed with **[`youtube-source 1.16.0`](https://github.com/lavalink-devs/youtube-source/releases/tag/1.16.0)**

Don't forget to update [`yt-cipher`](https://github.com/kikkia/yt-cipher)**

### 📦 Dependency Updates
- **youtube-source**: `ff19b6f1751262ecba7b81fcf391b961008962d1-SNAPSHOT` → `1.16.0`

---

# 29 September 2025

## ▶️ Youtube playback fixed with **[`yt-cipher`](https://github.com/kikkia/yt-cipher)**

### 🔧 Changes
- Web panel update : history fixes ( search pages and time range filter ) + Youtube chapters highlight

### 📦 Dependency Updates
- **youtube-source**: `1.13.5` → `ff19b6f1751262ecba7b81fcf391b961008962d1-SNAPSHOT`
- **net.dv8tion-JDA**: `5.5.0` → `5.6.1` 
- **dev.arbjerg-lavaplayer**: `2.2.3` → `2.2.4`

---

# 23 August 2025

## ▶️ Youtube playback fixed with **[`youtube-source 1.13.5`](https://github.com/lavalink-devs/youtube-source/releases/tag/1.13.5)**

### 📦 Dependency Updates
- **youtube-source**: `1.13.4` → `1.13.5`

---

# 02 August 2025

### ▶️ Youtube playback fixed with **[`youtube-source 1.13.4`](https://github.com/lavalink-devs/youtube-source/releases/tag/1.13.4)**

### 🔧 Changes
- Web panel update : small visuals fix

### 📦 Dependency Updates
- **youtube-source**: `1.13.3` → `1.13.4`

---

# 11 June 2025

### ▶️ Youtube playback fixed with **[`youtube-source 1.13.3`](https://github.com/lavalink-devs/youtube-source/releases/tag/1.13.3)**  

### 🚀 New Features
- Added `/history` command that track played songs.  

### 🔧 Changes
- Web panel update : working player and visuals for different sources, history page, ...
- **Java version**: `11` → `17`

### 📦 Dependency Updates
- **youtube-source**: `1.13.1` → `1.13.3` 

---

# 03 May 2025

### ▶️ Youtube playback fixed with **[`youtube-source 1.13.1`](https://github.com/lavalink-devs/youtube-source/releases/tag/1.13.1)**  

### 🚀 New Features
- Added YouTube chapters supports in `/nowplaying`.
- Added a web panel for music / bot management. ( Backend : `Spring Boot` / Frontend : `React` ) [ V1, more to come...]  

### 📦 Dependency Updates
- **youtube-source**: `1.13.0` → `1.13.1` 
- **net.dv8tion-JDA**: `5.3.0` → `5.5.0`   

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