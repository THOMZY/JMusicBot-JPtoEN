<h3 align="center">

[![Downloads](https://img.shields.io/github/downloads/THOMZY/JMusicBot-JPtoEN/total.svg)](https://github.com/THOMZY/JMusicBot-JPtoEN/releases/latest)
[![Stars](https://img.shields.io/github/stars/THOMZY/JMusicBot-JPtoEN.svg)](https://github.com/THOMZY/JMusicBot-JPtoEN/stargazers)
[![Release](https://img.shields.io/github/release/THOMZY/JMusicBot-JPtoEN.svg)](https://github.com/THOMZY/JMusicBot-JPtoEN/releases/latest)
[![License](https://img.shields.io/github/license/THOMZY/JMusicBot-JPtoEN.svg)](https://github.com/THOMZY/JMusicBot-JPtoEN/blob/main/LICENSE)
[![CodeFactor](https://www.codefactor.io/repository/github/thomzy/jmusicbot-jptoen/badge/main)](https://www.codefactor.io/repository/github/thomzy/jmusicbot-jptoen/overview/main)
[![Build and Test](https://github.com/THOMZY/JMusicBot-JPtoEN/actions/workflows/release.yml/badge.svg)](https://github.com/THOMZY/JMusicBot-JPtoEN/actions/workflows/release.yml)
</h3>

# JMusicBot
<img align="right" src="https://i.imgur.com/KA0s1mn.png" height="290">

JMusicBot uses a simple and user-friendly UI. Both setup and launch are easy.

This is an English translation of [JMusicBotJP](https://github.com/Cosgy-Dev/JMusicBot-JP).

This fork aims to updates dependencies when YouTube playback breaks and adds minor new features.

### Changes :

* **[`yt-dlp`](https://github.com/yt-dlp/yt-dlp)** fallback and support.
* Added a web panel for music / bot management.  
* Added `/history` command that track played songs.  
* Added `/radio` command that searches and plays radio stations.  
* Added `/stats` command to view total play time and songs played.  
* Added `/settopicstatus` command to toggle text channel playing status.  
* `/nowplaying` now have more and cleaner informations.  
* `/lyrics` his now more robust finding lyrics.  
* `/shuffle` now have 2 modes, "all" tracks in queue or "mytracks" only.  
* `/play` now work with Spotify links instead of returning "Unknown file format." error.   
* Fixed Youtube livestreams fail at 30sec. 
* Added the ability to queue multiple local files in a single message. ( 10 max, Discord upload limit )

Check the [CHANGELOG](https://github.com/THOMZY/JMusicBot-JPtoEN/blob/main/CHANGELOG.md) to view all the updated dependencies.
  
# Bot Features :

* Easy setup
* Fast music loading
* Setup with only a Discord Bot token ( need tokens for Youtube Premium and Spotify )
* Smooth playback with minimal lag
* Optional web panel for management
* Unique permission system with DJs
* Simple and easy-to-use UI
* Playback bar displayed in channel topics
* Supports many sites including YouTube, Spotify, Soundcloud and NicoNico Douga and now Instagram, Tiktok, X / Twitter
* Supports numerous online radios/streams
* Playback of local files
* Playlist support
* Create server and personal playlists

# Setting up :

This bot requires Java version 17 or higher.
If Java is not installed, download it from [here](https://www.oracle.com/jp/java/technologies/downloads/#java17).  
To start this bot yourself, refer to the [official Jagrosh website](https://jmusicbot.com/setup/).  
You can check the [Cosgy Dev Official Page](https://www.cosgy.dev/2019/09/06/jmusicbot-setup/) too.  
You can find the raw 'config.txt' file [here](https://raw.githubusercontent.com/THOMZY/JMusicBot-JPtoEN/refs/heads/main/src/main/resources/reference.conf) or in release tab.  

# Youtube cipher server :
It becomes harder and harder to keep up with YouTube's cipher changes, as they become more frequent and complex.  
To help with this, you can use a remote cipher server to handle signature deciphering for you.

Follow instructions to setup a simple Deno server for those pesky Youtube errors :  
https://github.com/kikkia/yt-cipher ( Thx Kikkia )

# Setup Using Docker :

You can start this bot yourself using Docker without having to install Java and other dependencies.
If using Docker, refer to [here](https://hub.docker.com/r/cyberrex/jmusicbot-jp).

# Build the .jar on windows :

* You need Java ( check [Setting up](https://github.com/THOMZY/JMusicBot-JPtoEN?tab=readme-ov-file#setting-up) ) and [Maven](https://maven.apache.org/download.cgi). - [How to install Maven](https://phoenixnap.com/kb/install-maven-windows)  
* You also need Java JDK : [Adoptium](https://adoptium.net/) OR [Oracle](https://www.oracle.com/java/technologies/downloads/?er=221886).
  
Download this repo using Git or [download the sources](https://github.com/THOMZY/JMusicBot-JPtoEN/archive/refs/heads/develop.zip).
Open a command line window in the downloaded and unziped folder and type :  
```
mvn clean package
```
You will find ```JMusicBot-XXXX.XX.XX-All.jar``` in the "target" folder. 
   
Or just download the latest working provided .jar [here](https://github.com/THOMZY/JMusicBot-JPtoEN/releases/latest).  
  
# Note :

This bot cannot be used as a public bot.
It is recommended for personal or small server use.  

# Example of Commands

![Example](https://i.imgur.com/y0WQd4V.gif)