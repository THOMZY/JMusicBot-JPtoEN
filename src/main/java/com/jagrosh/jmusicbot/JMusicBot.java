/*
 * Copyright 2016 John Grosh (jagrosh).
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

import com.github.lalyos.jfiglet.FigletFont;
import com.jagrosh.jdautilities.command.Command;
import com.jagrosh.jdautilities.command.CommandClientBuilder;
import com.jagrosh.jdautilities.command.SlashCommand;
import com.jagrosh.jdautilities.commons.waiter.EventWaiter;
import com.jagrosh.jmusicbot.entities.Prompt;
import com.jagrosh.jmusicbot.gui.GUI;
import com.jagrosh.jmusicbot.settings.SettingsManager;
import com.jagrosh.jmusicbot.utils.OtherUtil;
import dev.cosgy.jmusicbot.slashcommands.admin.*;
import dev.cosgy.jmusicbot.slashcommands.dj.*;
import dev.cosgy.jmusicbot.slashcommands.general.*;
import dev.cosgy.jmusicbot.slashcommands.listeners.CommandAudit;
import dev.cosgy.jmusicbot.slashcommands.music.*;
import dev.cosgy.jmusicbot.slashcommands.owner.*;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.OnlineStatus;
import net.dv8tion.jda.api.Permission;
import net.dv8tion.jda.api.entities.Activity;
import net.dv8tion.jda.api.exceptions.InvalidTokenException;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.slf4j.Logger;

import java.awt.*;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintStream;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * @author John Grosh (jagrosh)
 */
public class JMusicBot {
    public final static String PLAY_EMOJI = "▶"; // ▶
    public final static String PAUSE_EMOJI = "⏸"; // ⏸
    public final static String STOP_EMOJI = "⏹"; // ⏹
    public final static Permission[] RECOMMENDED_PERMS = {Permission.MESSAGE_SEND, Permission.MESSAGE_HISTORY, Permission.MESSAGE_ADD_REACTION,
            Permission.MESSAGE_EMBED_LINKS, Permission.MESSAGE_ATTACH_FILES, Permission.MESSAGE_MANAGE, Permission.MESSAGE_EXT_EMOJI,
            Permission.VOICE_CONNECT, Permission.VOICE_SPEAK, Permission.NICKNAME_CHANGE, Permission.VOICE_SET_STATUS};
    public final static GatewayIntent[] INTENTS = {GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MESSAGES, GatewayIntent.GUILD_MESSAGE_REACTIONS, GatewayIntent.GUILD_VOICE_STATES, GatewayIntent.MESSAGE_CONTENT}; // , GatewayIntent.MESSAGE_CONTENT
    public static boolean CHECK_UPDATE = true;
    public static boolean COMMAND_AUDIT_ENABLED = false;

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // startup log
        Logger log = getLogger("Startup");

        try {
            System.out.println(FigletFont.convertOneLine("JMusicBot v" + OtherUtil.getCurrentVersion()) + "\n" + "by THOMZY");
        } catch (IOException e) {
            System.out.println("JMusicBot v" + OtherUtil.getCurrentVersion() + "\nby THOMZY");
        }


        // create prompt to handle startup
        Prompt prompt = new Prompt("JMusicBot", "Switching to nogui mode. You can manually start in nogui mode by including the flag -Dnogui=true.");
        // check deprecated nogui mode (new way of setting it is -Dnogui=true)
        for (String arg : args)
            if ("-nogui".equalsIgnoreCase(arg)) {
                prompt.alert(Prompt.Level.WARNING, "GUI", "-nogui flag is deprecated. "
                        + "Please use the -Dnogui=true flag before the jar name. Example: java -jar -Dnogui=true JMusicBot.jar");
            } else if ("-nocheckupdates".equalsIgnoreCase(arg)) {
                CHECK_UPDATE = false;
                log.info("Disabled update check");
            } else if ("-auditcommands".equalsIgnoreCase(arg)) {
                COMMAND_AUDIT_ENABLED = true;
                log.info("Enabled command audit logging.");
            }

        // get and check latest version
        String version = OtherUtil.checkVersion(prompt);

        if (!System.getProperty("java.vm.name").contains("64"))
            prompt.alert(Prompt.Level.WARNING, "Java Version", "You are using an unsupported Java version. Please use the 64-bit version of Java.");

        try {
            Process checkPython3 = Runtime.getRuntime().exec("python3 --version");
            int python3ExitCode = checkPython3.waitFor();

            if (python3ExitCode != 0) {
                log.info("Python3 is not installed. Checking for python.");
                Process checkPython = Runtime.getRuntime().exec("python --version");
                BufferedReader reader = new BufferedReader(new InputStreamReader(checkPython.getInputStream()));
                String pythonVersion = reader.readLine();
                int pythonExitCode = checkPython.waitFor();

                if (pythonExitCode == 0 && pythonVersion != null && pythonVersion.startsWith("Python 3")) {
                    log.info(pythonVersion);
                } else {
                    prompt.alert(Prompt.Level.WARNING, "Python", "Python (version 3.x) is not installed. Please install Python 3.");
                }
            } else {
                log.info("Python (version 3.x) is installed");
            }
        } catch (Exception e) {
            prompt.alert(Prompt.Level.WARNING, "Python", "An error occurred while checking the Python version. Please ensure Python 3 is installed.");
        }



        // load config
        BotConfig config = new BotConfig(prompt);
        config.load();

        if (!config.isValid())
            return;

        // --- YouTube OAuth2: Log Listener for Refresh Token ---
        // Redirect System.out to capture refresh token log and update config.txt automatically.
        PrintStream originalOut = System.out;
        System.setOut(new PrintStream(new OutputStream() {
            private final StringBuilder buffer = new StringBuilder();
            @Override
            public void write(int b) throws IOException {
                // Only treat '\n' as a line ending (not '\r')
                if (b == '\n') {
                    String line = buffer.toString();
                    originalOut.println(line); // Always print the original line as a true line
                    if (line.contains("OAUTH INTEGRATION: Token retrieved successfully. Store your refresh token as this can be reused.")) {
                        int start = line.indexOf('(');
                        int end = line.indexOf(')', start);
                        if (start != -1 && end != -1 && end > start + 1) {
                            String token = line.substring(start + 1, end);
                            if (token.startsWith("1//")) {
                                config.setYouTubeRefreshToken(token);
                                LocalTime now = LocalTime.now();
                                String timestamp = String.format("[%02d:%02d:%02d]", now.getHour(), now.getMinute(), now.getSecond());
                                originalOut.println(timestamp + " [INFO] [YoutubeOauth2Handler] Refresh token saved to config.txt");
                            }
                        }
                    }
                    buffer.setLength(0);
                } else if (b != '\r') {
                    buffer.append((char) b);
                }
            }
            @Override
            public void write(byte[] b, int off, int len) throws IOException {
                for (int i = off; i < off + len; i++) {
                    write(b[i]);
                }
            }
        }, true));

        if (config.getAuditCommands()) {
            COMMAND_AUDIT_ENABLED = true;
            log.info("Command execution logging has been enabled.");
        }

        // set up the listener
        EventWaiter waiter = new EventWaiter();
        SettingsManager settings = new SettingsManager();
        Bot bot = new Bot(waiter, config, settings);
        Bot.INSTANCE = bot;

        AboutCommand aboutCommand = new AboutCommand(Color.BLUE.brighter(),
                "[JMusicBot (v" + version + ")](https://github.com/THOMZY/JMusicBot-JPtoEN)",
                new String[]{"High-quality music playback", "FairQueue™ Technology", "Easily host it yourself"},
                RECOMMENDED_PERMS);
        aboutCommand.setIsAuthor(false);
        aboutCommand.setReplacementCharacter("\uD83C\uDFB6"); // 

        // set up the command client
        CommandClientBuilder cb = new CommandClientBuilder()
                .setPrefix(config.getPrefix())
                .setAlternativePrefix(config.getAltPrefix())
                .setOwnerId(Long.toString(config.getOwnerId()))
                .setEmojis(config.getSuccess(), config.getWarning(), config.getError())
                .useHelpBuilder(false)
                .setLinkedCacheSize(200)
                .setGuildSettingsManager(settings)
                .setListener(new CommandAudit());

        if (config.isOfficialInvite()) {
            cb.setServerInvite("https://discord.gg/MjNfC6TK2y");
        }

        // Implementing the slash command
        List<SlashCommand> slashCommandList = new ArrayList<>() {{
            add(new HelpCmd(bot));
            add(aboutCommand);
            if (config.isUseInviteCommand()) {
                add(new InviteCommand());
            }
            add(new PingCommand());
            add(new SettingsCmd(bot));
            //if (config.getCosgyDevHost()) add(new InfoCommand(bot));
            // General
            add(new ServerInfo(bot));
            //add(new UserInfo());
            add(new CashCmd(bot));
            add(new StatsCommand(bot));
            // Music
            add(new LyricsCmd(bot));
            add(new NowplayingCmd(bot));
            add(new PlayCmd(bot));
            add(new SpotifyCmd(bot));
            add(new PlaylistsCmd(bot));
            add(new MylistCmd(bot));
            //add(new QueueCmd(bot));
            add(new QueueCmd(bot));
            add(new RemoveCmd(bot));
            add(new SearchCmd(bot));
            add(new SCSearchCmd(bot));
            add(new SeekCmd(bot));
            add(new NicoSearchCmd(bot));
            add(new ShuffleCmd(bot));
            add(new SkipCmd(bot));
            add(new VolumeCmd(bot));
            add(new RadioCmd(bot));
            // DJ
            add(new ForceRemoveCmd(bot));
            add(new ForceskipCmd(bot));
            add(new NextCmd(bot));
            add(new MoveTrackCmd(bot));
            add(new PauseCmd(bot));
            add(new PlaynextCmd(bot));
            //add(new RepeatCmd(bot));
            add(new RepeatCmd(bot));
            add(new SkipToCmd(bot));
            add(new ForceToEnd(bot));
            add(new StopCmd(bot));
            add(new HistoryCmd(bot));
            //add(new VolumeCmd(bot));
            // Admin
            //add(new ActivateCmd(bot));
            add(new PrefixCmd(bot));
            add(new SetdjCmd(bot));
            add(new SkipratioCmd(bot));
            add(new SettcCmd(bot));
            add(new SetvcCmd(bot));
            add(new SetvcStatusCmd(bot));
            add(new SettopicStatusCmd(bot));
            add(new AutoplaylistCmd(bot));
            add(new ServerListCmd(bot));
            // Owner
            add(new DebugCmd(bot));
            add(new SetavatarCmd(bot));
            add(new SetgameCmd(bot));
            add(new SetnameCmd(bot));
            add(new SetstatusCmd(bot));
            add(new PublistCmd(bot));
            add(new ShutdownCmd(bot));
            //add(new LeaveCmd(bot));
        }};

        cb.addCommands(slashCommandList.toArray(new Command[0]));
        cb.addSlashCommands(slashCommandList.toArray(new SlashCommand[0]));

        if (config.useEval())
            cb.addCommand(new EvalCmd(bot));
        boolean nogame = false;
        if (config.getStatus() != OnlineStatus.UNKNOWN)
            cb.setStatus(config.getStatus());
        if (config.getGame() == null)
            cb.setActivity(Activity.playing("Check help with " + config.getPrefix() + config.getHelp()));
        else if (config.getGame().getName().toLowerCase().matches("(none|なし)")) {
            cb.setActivity(null);
            nogame = true;
        } else
            cb.setActivity(config.getGame());
        if (!prompt.isNoGUI()) {
            try {
                GUI gui = new GUI(bot);
                bot.setGUI(gui);
                gui.init();
            } catch (Exception e) {
                log.error("Could not open the GUI. The following factors may be causing this:\n"
                        + "Running on a server\n"
                        + "Running in an environment without a display\n"
                        + "To hide this error, use the -Dnogui=true flag to run in GUI-less mode.");
            }
        }

        log.info("Loaded settings from {}", config.getConfigLocation());

        // attempt to log in and start
        final JDA[] jdaRef = new JDA[1];
        try {
            JDA jda = JDABuilder.create(config.getToken(), Arrays.asList(INTENTS))
                    .enableCache(CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE)
                    .disableCache(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.EMOJI, CacheFlag.ONLINE_STATUS, CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS)
                    .setActivity(nogame ? null : Activity.playing("Loading..."))
                    .setStatus(config.getStatus() == OnlineStatus.INVISIBLE || config.getStatus() == OnlineStatus.OFFLINE
                            ? OnlineStatus.INVISIBLE : OnlineStatus.DO_NOT_DISTURB)
                    .addEventListeners(cb.build(), waiter, new Listener(bot))
                    .setBulkDeleteSplittingEnabled(true)
                    .build();
            jdaRef[0] = jda;
            bot.setJDA(jda);

            String unsupportedReason = OtherUtil.getUnsupportedBotReason(jda);
            if (unsupportedReason != null)
            {
                prompt.alert(Prompt.Level.ERROR, "JMusicBot", "JMusicBot cannot be run with this Discord bot user: " + unsupportedReason);
                try{ Thread.sleep(5000);}catch(InterruptedException ignored){} // this is awful but until we have a better way...
                jda.shutdown();
                System.exit(1);
            }

            // other check that will just be a warning now but may be required in the future
            // check if the user has changed the prefix and provide info about the
            // message content intent
            /*if(!"@mention".equals(config.getPrefix()))
            {
                prompt.alert(Prompt.Level.INFO, "JMusicBot", "A custom prefix is currently set. "
                        + "If the custom prefix does not work, make sure that 'MESSAGE CONTENT INTENT' is enabled. "
                        + "https://discord.com/developers/applications/" + jda.getSelfUser().getId() + "/bot");
            }*/

        }
        catch (InvalidTokenException ex) {
            //ex.getCause().getMessage();
            prompt.alert(Prompt.Level.ERROR, "JMusicBot", ex + "\n" +
                    "Please ensure you are editing the correct configuration file. Failed to log in with the bot token." +
                    "Please enter the correct bot token. (Not the CLIENT SECRET!)\n" +
                    "Configuration file location: " + config.getConfigLocation());
            System.exit(1);

        } catch (IllegalArgumentException ex) {

            prompt.alert(Prompt.Level.ERROR, "JMusicBot", "Some settings are invalid:" + ex + "\n" +
                    "Location of the configuration file: " + config.getConfigLocation());
            System.exit(1);
        }

        // Start web panel if enabled
        if (config.isWebPanelEnabled()) {
            try {
                log.info("Starting Web Panel on port " + config.getWebPanelPort());
                com.jagrosh.jmusicbot.webpanel.WebPanelApplication.start(bot, config.getWebPanelPort());
            } catch (Exception e) {
                log.error("Failed to start Web Panel", e);
            }
        }
        
        // Shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (jdaRef[0] == null)
                return;
            jdaRef[0].shutdown();
            if (config.isWebPanelEnabled()) {
                log.info("Stopping Web Panel");
                com.jagrosh.jmusicbot.webpanel.WebPanelApplication.stop();
            }
        }));
    }
}
