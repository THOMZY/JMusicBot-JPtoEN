[![Downloads](https://img.shields.io/github/downloads/THOMZY/JMusicBot-JPtoEN/total.svg)](https://github.com/THOMZY/JMusicBot-JPtoEN/releases/latest)
[![Stars](https://img.shields.io/github/stars/THOMZY/JMusicBot-JPtoEN.svg)](https://github.com/THOMZY/JMusicBot-JPtoEN/stargazers)
[![Release](https://img.shields.io/github/release/THOMZY/JMusicBot-JPtoEN.svg)](https://github.com/THOMZY/JMusicBot-JPtoEN/releases/latest)
[![License](https://img.shields.io/github/license/THOMZY/JMusicBot-JPtoEN.svg)](https://github.com/THOMZY/JMusicBot-JPtoEN/blob/develop/LICENSE)
[![CodeFactor](https://www.codefactor.io/repository/github/thomzy/jmusicbot-jptoen/badge)](https://www.codefactor.io/repository/github/thomzy/jmusicbot-jptoen)
[![CircleCI](https://dl.circleci.com/status-badge/img/gh/THOMZY/JMusicBot-JPtoEN/tree/develop.svg?style=svg)](https://dl.circleci.com/status-badge/redirect/gh/THOMZY/JMusicBot-JPtoEN/tree/develop)
[![Build and Test](https://github.com/THOMZY/JMusicBot-JPtoEN/actions/workflows/release.yml/badge.svg)](https://github.com/THOMZY/JMusicBot-JPtoEN/actions/workflows/release.yml)  
<img align="right" src="https://i.imgur.com/9ZXIsiB.png" height="290" width="290">  
  
#### Useless fork to try and update dependencies as soon as the bot stop working...  
  
#### Youtube links working again ( 26 March 2025 )  
##### Dependencies update :  
- Lavalink : 1.11.5 > 1.12.0  
- Almost all dependencies ( maybe useless, check Release changelog )


  
# JMusicBotJPtoEN

MusicBot uses a simple and user-friendly UI. Both setup and launch are easy.
<br><br>This is an English translation of JMusicBotJP.
<br>This fork only aims to translate strings while keeping most of the code intact.
<br>If some fixes are needed, they are very small fixes and are mentioned below.
### Changes
* Translated all strings (and some comments), reference.conf and this README from Japanese to English
* Modified the code for reading config.txt to accept settings from both JP and this fork

### What I can't fix
* The help command is broken due to Discord's 1000-character limit. This is yet to be fixed upstream.
  
# Bot Features

* Easy setup
* Fast music loading
* Setup with only a Discord Bot token
* Smooth playback with minimal lag
* Unique permission system with DJs
* Simple and easy-to-use UI
* Playback bar displayed in channel topics
* Supports many sites including NicoNico Douga, YouTube, Spotify and Soundcloud
* Supports numerous online radios/streams
* Playback of local files
* Playlist support
* Create server and personal playlists

# Setting up

This bot requires Java version 11 or higher.
If Java is not installed, download it from [here](https://www.oracle.com/jp/java/technologies/downloads/).  
To start this bot yourself, refer to the [official Jagrosh website](https://jmusicbot.com/setup/).  
You can check the [Cosgy Dev Official Page](https://www.cosgy.dev/2019/09/06/jmusicbot-setup/) too.  
You can find the raw 'config.txt' file [here](https://raw.githubusercontent.com/THOMZY/JMusicBot-JPtoEN/refs/heads/develop/src/main/resources/reference.conf) or in release tab.  

# Setup Using Docker

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
  
# Note

This bot cannot be used as a public bot.
It is recommended for personal or small server use.  

# Example of Commands

![Example](https://i.imgur.com/tevrtKt.png)