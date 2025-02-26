<img align="right" src="https://i.imgur.com/zrE80HY.png" height="200" width="200" alt="ロゴ">
  
#### Youtube links working again ( 2025/02/26 )  
##### Dependencies update :  
- Lavalink : 1.11.4 > 1.11.5  
- net.dv8tion-JDA : 5.2.2 > 5.3.0  



  
# JMusicBotJPtoEN_

MusicBot uses a simple and user-friendly UI. Both setup and launch are easy.
<br><br>This is an English translation of JMusicBotJP.
<br>This fork only aims to translate strings while keeping most of the code intact.
<br>If some fixes are needed, they are very small fixes and are mentioned below.
### Changes
* Translated all strings (and some comments), reference.conf and this README from Japanese to English
* Modified the code for reading config.txt to accept settings from both JP and this fork
* Fix spotify command not working when "valence" value is empty.
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
* Supports many sites including NicoNico Douga, YouTube, and Soundcloud
* Supports numerous online radios/streams
* Playback of local files
* Playlist support
* Create server and personal playlists

# Setting up

This bot requires Java version 11 or higher.
If Java is not installed, download it from [here](https://www.oracle.com/jp/java/technologies/downloads/).  
To start this bot yourself, refer to the [Cosgy Dev Official Page](https://www.cosgy.dev/2019/09/06/jmusicbot-setup/).  
You can check the official website by Jagrosh too. [Jagrosh original website](https://jmusicbot.com/)  
You can find the raw 'config.txt' file [here](https://raw.githubusercontent.com/THOMZY/JMusicBot-JPtoEN/refs/heads/develop/src/main/resources/reference.conf)  

# Setup Using Docker

You can start this bot yourself using Docker without having to install Java and other dependencies.
If using Docker, refer to [here](https://hub.docker.com/r/cyberrex/jmusicbot-jp).

# Build the .jar on windows :

* You need Java ( check [Setting up](https://github.com/THOMZY/JMusicBot-JPtoEN_?tab=readme-ov-file#setting-up) ) and [Maven](https://maven.apache.org/download.cgi). - [How to install Maven](https://phoenixnap.com/kb/install-maven-windows)  
* You also need Java JDK : [Adoptium](https://adoptium.net/) OR [Oracle](https://www.oracle.com/java/technologies/downloads/?er=221886).
  
Download this repo using Git or [download the sources](https://github.com/THOMZY/JMusicBot-JPtoEN_/archive/refs/heads/develop.zip).
Open a command line window in the downloaded and unziped folder and type :  
```
mvn --batch-mode --update-snapshots verify
```
You will find ```JMusicBot-XXXX.XX.XX-All.jar``` in the "target" folder. 
   
Or just download the latest working provided .jar [here](https://github.com/THOMZY/JMusicBot-JPtoEN/releases/latest).  
  
# Note

This bot cannot be used as a public bot.
It is recommended for personal or small server use.

# Questions/Suggestions/Bug Reports

**Please read the list of recommended/planned features before suggesting any.**<br>
If you would like to suggest changes to the bot's functionality, recommend customization options, or report bugs, please open an Issue in this repository or join the [Discord server](https://discord.gg/RBpkHxf).
(Note: We do not accept feature requests that require additional API keys or non-music-related features).
<br>If you like this bot, we would appreciate it if you could star this repository.
Additionally, please consider starring the essential dependent libraries for this bot's development: [JDA](https://github.com/DV8FromTheWorld/JDA) and [lavaplayer](https://github.com/lavalink-devs/lavaplayer).

# Example of Commands

![Example](https://i.imgur.com/tevrtKt.png)
