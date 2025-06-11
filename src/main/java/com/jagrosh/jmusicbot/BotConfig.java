/*
 * Copyright 2018 John Grosh (jagrosh).
 * Edit 2025 THOMZY
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jagrosh.jmusicbot;

import com.jagrosh.jmusicbot.entities.Prompt;
import com.jagrosh.jmusicbot.utils.FormatUtil;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.typesafe.config.Config;
import com.typesafe.config.ConfigException;
import com.typesafe.config.ConfigFactory;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.entities.Activity;
import org.apache.commons.io.FileUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

/**
 * @author John Grosh (jagrosh)
 */
public class BotConfig {
    private final static String CONTEXT = "Config";
    private final static String START_TOKEN = "/// START OF JMUSICBOT-JP CONFIG ///";
    private final static String END_TOKEN = "/// END OF JMUSICBOT-JP CONFIG ///";
    private final Prompt prompt;
    private Path path = null;
    // [JMusicBot-JP] added nicoEmail, nicoPass
    private String token;
    private String prefix;
    private String altprefix;
    private String helpWord;
    private String playlistsFolder;
    private String mylistfolder;
    private String publistFolder;
    private String successEmoji;
    private String warningEmoji;
    private String errorEmoji;
    private String loadingEmoji;
    private String searchingEmoji;
    private String nicoEmail;
    private String nicoPass;
    private static String nicoTwoFactor;
    private String ytEmail;
    private String ytPass;
    private String ytRefreshToken;
    private String spClientId;
    private String spClientSecret;
    // WebPanel settings
    private int webPanelPort;
    // [JMusicBot-JP] added useNicoNico, changeNickName, pauseNoUsers, resumeJoined, stopNoUsers, cosgyDevHost, helpToDm, officialInvite
    private boolean useNicoNico, changeNickName, stayInChannel, pauseNoUsers, resumeJoined, stopNoUsers, songInGame, npImages, updatealerts, useEval, dbots, cosgyDevHost, helpToDm, autoStopQueueSave, auditCommands, officialInvite, useinvitecommand, webPanelEnabled, enableHistory;
    private long owner, maxSeconds, aloneTimeUntilStop;
    private OnlineStatus status;
    private Activity game;
    private Config aliases, transforms;

    private boolean valid = false;

    public BotConfig(Prompt prompt) {
        this.prompt = prompt;
    }

    public void load() {
        valid = false;

        // Load settings from file
        try {
            // Get configuration path (default config.txt)
            path = OtherUtil.getPath(System.getProperty("config.file", System.getProperty("config", "config.txt")));
            if (path.toFile().exists()) {
                if (System.getProperty("config.file") == null)
                    System.setProperty("config.file", System.getProperty("config", path.toAbsolutePath().toString()));
                ConfigFactory.invalidateCaches();
            }

            // Loaded into configuration file and default values added
            // Config config = ConfigFactory.parseFile(path.toFile()).withFallback(ConfigFactory.load());
            Config config = ConfigFactory.load();
            // Setting value
            token = config.getString("token");
            prefix = config.getString("prefix");
            altprefix = config.getString("altprefix");
            helpWord = config.getString("help");
            owner = (config.getAnyRef("owner") instanceof String ? 0L : config.getLong("owner"));
            successEmoji = config.getString("success");
            warningEmoji = config.getString("warning");
            errorEmoji = config.getString("error");
            loadingEmoji = config.getString("loading");
            searchingEmoji = config.getString("searching");
            game = OtherUtil.parseGame(config.getString("game"));
            status = OtherUtil.parseStatus(config.getString("status"));
            stayInChannel = config.getBoolean("stayinchannel");
            songInGame = config.getBoolean("songinstatus");
            npImages = config.getBoolean("npimages");
            updatealerts = config.getBoolean("updatealerts");
            useEval = config.getBoolean("eval");
            maxSeconds = config.getLong("maxtime");
            aloneTimeUntilStop = config.getLong("alonetimeuntilstop");
            playlistsFolder = config.getString("playlistsfolder");
            mylistfolder = config.getString("mylistfolder");
            publistFolder = config.getString("publistfolder");
            aliases = config.getConfig("aliases");
            transforms = config.getConfig("transforms");
            dbots = owner == 334091398263341056L;


            // [JMusicBot-JP]
            useNicoNico = config.getBoolean("useniconico");
            nicoEmail = config.getString("nicomail");
            nicoPass = config.getString("nicopass");
            nicoTwoFactor = config.getString("nicotwofactor");
            pauseNoUsers = config.getBoolean("pausenousers");
            resumeJoined = config.getBoolean("resumejoined");
            stopNoUsers = config.getBoolean("stopnousers");
            changeNickName = config.getBoolean("changenickname");
            helpToDm = config.getBoolean("helptodm");
            autoStopQueueSave = config.getBoolean("autostopqueuesave");
            auditCommands = config.getBoolean("auditcommands");
            officialInvite = config.getBoolean("officialinvite");
            useinvitecommand = config.getBoolean("useinvitecommand");
            ytEmail = config.getString("ytemail");
            ytPass = config.getString("ytpass");
            ytRefreshToken = config.hasPath("ytrefreshtoken") ? config.getString("ytrefreshtoken") : null; // [YouTube OAuth] Read refresh token from config
            spClientId = config.getString("spclient");
            spClientSecret = config.getString("spsecret");
            enableHistory = config.hasPath("enablehistory") ? config.getBoolean("enablehistory") : true;


            cosgyDevHost = false;
            // [JMusicBot-JP] End

            // WebPanel settings
            webPanelEnabled = config.hasPath("webpanelenabled") ? config.getBoolean("webpanelenabled") : false;
            webPanelPort = config.hasPath("webpanelport") ? config.getInt("webpanelport") : 8080;

            // we may need to write a new config file
            boolean write = false;

            // validate bot token
            if (token == null || token.isEmpty() || token.matches("(BOT_TOKEN_HERE|Paste the bot token here|BOTトークンを入力してください)")) {
                token = prompt.prompt("Please enter the BOT token."
                        + "\nYou can obtain the token from here:"
                        + "\nhttps://github.com/jagrosh/MusicBot/wiki/Getting-a-Bot-Token."
                        + "\nBOT Token: ");
                if (token == null) {
                    prompt.alert(Prompt.Level.WARNING, CONTEXT, "Token not entered! Exiting.\n\nConfiguration file location: " + path.toAbsolutePath());
                    return;
                } else {
                    write = true;
                }
            }

            // validate bot owner
            if (owner <= 0) {
                try {
                    owner = Long.parseLong(prompt.prompt("The owner user ID is not set or is invalid."
                            + "\nPlease enter the BOT owner's user ID."
                            + "\nYou can obtain the user ID from here:"
                            + "\nhttps://github.com/jagrosh/MusicBot/wiki/Finding-Your-User-ID"
                            + "\nOwner user ID: "));
                } catch (NumberFormatException | NullPointerException ex) {
                    owner = 0;
                }
                if (owner <= 0) {
                    prompt.alert(Prompt.Level.ERROR, CONTEXT, "Invalid user ID! Exiting.\n\nConfiguration file location: " + path.toAbsolutePath());
                    System.exit(0);
                } else {
                    write = true;
                }
            }

            if (write) {
                String original = OtherUtil.loadResource(this, "/reference.conf");
                String mod;
                if (original == null) {
                    mod = ("token = " + token + "\r\nowner = " + owner);
                } else {
                    mod = original.substring(original.indexOf(START_TOKEN) + START_TOKEN.length(), original.indexOf(END_TOKEN))
                    .replace("BOT_TOKEN_HERE", token).replace("Paste the bot token here", token)
                    .replace("0 // OWNER ID", Long.toString(owner)).replace("Paste the owner ID here", Long.toString(owner))
                            .trim();
                }

                FileUtils.writeStringToFile(path.toFile(), mod, StandardCharsets.UTF_8);
            }

            // if we get through the whole config, it's good to go
            valid = true;
        } catch (ConfigException | IOException ex) {
            prompt.alert(Prompt.Level.ERROR, CONTEXT, ex + ": " + ex.getMessage() + "\n\nConfiguration file location: " + path.toAbsolutePath());
        }
    }

    public boolean isValid() {
        return valid;
    }

    public String getConfigLocation() {
        return path.toFile().getAbsolutePath();
    }

    public String getPrefix() {
        return prefix;
    }

    public String getAltPrefix() {
        return "NONE".equalsIgnoreCase(altprefix) ? null : altprefix;
    }

    public String getToken() {
        return token;
    }

    public long getOwnerId() {
        return owner;
    }

    public String getSuccess() {
        return successEmoji;
    }

    public String getWarning() {
        return warningEmoji;
    }

    public String getError() {
        return errorEmoji;
    }

    public String getLoading() {
        return loadingEmoji;
    }

    public String getSearching() {
        return searchingEmoji;
    }

    public Activity getGame() {
        return game;
    }

    public OnlineStatus getStatus() {
        return status;
    }

    public String getHelp() {
        return helpWord;
    }

    public boolean getStay() {
        return stayInChannel;
    }

    public boolean getNoUserPause() {
        return pauseNoUsers;
    }

    public boolean getResumeJoined() {
        return resumeJoined;
    }

    public boolean getNoUserStop() {
        return stopNoUsers;
    }

    public long getAloneTimeUntilStop() {
        return aloneTimeUntilStop;
    }

    public boolean getChangeNickName() {
        return changeNickName;
    }

    public boolean getSongInStatus() {
        return songInGame;
    }

    public String getPlaylistsFolder() {
        return playlistsFolder;
    }

    public String getMylistfolder() {
        return mylistfolder;
    }

    public String getPublistFolder() {
        return publistFolder;
    }

    public boolean getDBots() {
        return dbots;
    }

    public boolean useUpdateAlerts() {
        return updatealerts;
    }

    public boolean useEval() {
        return useEval;
    }

    public boolean useNPImages() {
        return npImages;
    }

    public long getMaxSeconds() {
        return maxSeconds;
    }

    public String getMaxTime() {
        return FormatUtil.formatTime(maxSeconds * 1000);
    }

    public boolean isTooLong(AudioTrack track) {
        if (maxSeconds <= 0)
            return false;
        return Math.round(track.getDuration() / 1000.0) > maxSeconds;
    }

    public String[] getAliases(String command) {
        try {
            return aliases.getStringList(command).toArray(new String[0]);
        } catch (NullPointerException | ConfigException.Missing e) {
            return new String[0];
        }
    }

    // [JMusicBot-JP] new function: support niconico play
    public boolean isNicoNicoEnabled() {
        return useNicoNico;
    }

    public String getNicoNicoEmailAddress() {
        return nicoEmail;
    }

    public String getNicoNicoPassword() {
        return nicoPass;
    }

    public static String getNicoNicoTwoFactor(){ return nicoTwoFactor; }

    public String getYouTubeEmailAddress() {
        return ytEmail;
    }

    public String getYouTubePassword() {
        return ytPass;
    }

    public String getYouTubeRefreshToken() {
        return ytRefreshToken;
    }

    public void setYouTubeRefreshToken(String refreshToken) {
        this.ytRefreshToken = refreshToken;
        // Persist to config file
        try {
            String configContent = FileUtils.readFileToString(path.toFile(), StandardCharsets.UTF_8);
            // Replace or add the ytrefreshtoken line
            if (configContent.contains("ytrefreshtoken")) {
                configContent = configContent.replaceAll("(?m)^ytrefreshtoken\\s*=.*$", "ytrefreshtoken = \"" + refreshToken + "\"");
            } else {
                configContent += "\nytrefreshtoken = \"" + refreshToken + "\"\n";
            }
            FileUtils.writeStringToFile(path.toFile(), configContent, StandardCharsets.UTF_8);
        } catch (IOException e) {
            prompt.alert(Prompt.Level.ERROR, CONTEXT, "Failed to write refresh token to config: " + e.getMessage());
        }
    }

    public String getSpotifyClientId(){return spClientId;}

    public String getSpotifyClientSecret(){return spClientSecret;}

    // [JMusicBot-JP] End

    public boolean getCosgyDevHost() {
        return cosgyDevHost;
    }

    public boolean getHelpToDm() {
        return helpToDm;
    }

    public boolean getAutoStopQueueSave() {
        return autoStopQueueSave;
    }

    public boolean getAuditCommands() {
        return auditCommands;
    }

    public Config getTransforms() {
        return transforms;
    }

    public boolean isOfficialInvite() {
        return officialInvite;
    }

    public boolean isUseInviteCommand() {
        return useinvitecommand;
    }

    public boolean isWebPanelEnabled() {
        return webPanelEnabled;
    }

    public int getWebPanelPort() {
        return webPanelPort;
    }

    public boolean isHistoryEnabled() {
        return enableHistory;
    }
}
