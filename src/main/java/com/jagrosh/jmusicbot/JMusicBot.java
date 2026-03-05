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
import dev.cosgy.jmusicbot.framework.jdautilities.command.Command;
import dev.cosgy.jmusicbot.framework.jdautilities.command.CommandClientBuilder;
import dev.cosgy.jmusicbot.framework.jdautilities.command.SlashCommand;
import dev.cosgy.jmusicbot.framework.jdautilities.commons.waiter.EventWaiter;
import com.jagrosh.jmusicbot.entities.Prompt;
import com.jagrosh.jmusicbot.gui.DelegatingPrintStream;
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
import net.dv8tion.jda.api.audio.AudioModuleConfig;
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
import java.lang.reflect.Method;
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
    
    // Delegating PrintStream for OAuth2 logs - can be updated when GUI is initialized
    private static DelegatingPrintStream originalOut;

    private static AudioModuleConfig createDaveAudioModuleConfig(Logger log, Prompt prompt) {
        try {
            AudioModuleConfig config = new AudioModuleConfig();
            Class<?> factoryClass = Class.forName("club.minnced.discord.jdave.interop.JDaveSessionFactory");
            Object factory = factoryClass.getDeclaredConstructor().newInstance();

            Method withDaveMethod = null;
            for (Method method : AudioModuleConfig.class.getMethods()) {
                if (!"withDaveSessionFactory".equals(method.getName()) || method.getParameterCount() != 1) {
                    continue;
                }
                if (method.getParameterTypes()[0].isAssignableFrom(factoryClass)) {
                    withDaveMethod = method;
                    break;
                }
            }

            if (withDaveMethod == null) {
                throw new NoSuchMethodException("AudioModuleConfig.withDaveSessionFactory not found");
            }

            Object configured = withDaveMethod.invoke(config, factory);
            if (configured instanceof AudioModuleConfig) {
                config = (AudioModuleConfig) configured;
            }
            log.info("DAVE voice encryption enabled.");
            return config;
        } catch (Throwable t) {
            log.warn("DAVE initialization failed. Falling back to default encryption: {}", t.toString());
            prompt.alert(Prompt.Level.WARNING, "DAVE",
                    "Failed to initialize DAVE. Continuing with default voice encryption. Details: "
                            + t.getClass().getSimpleName());
            return null;
        }
    }

    /**
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        // startup log
        Logger log = getLogger("Startup");

        printBanner();
        Prompt prompt = new Prompt("JMusicBot", "Switching to nogui mode. You can manually start in nogui mode by including the flag -Dnogui=true.");
        parseStartupArgs(args, prompt, log);

        String version = OtherUtil.checkVersion(prompt);
        if (version != null && !version.isEmpty()) {
            log.debug("Version check result: {}", version);
        }

        if (!System.getProperty("java.vm.name").contains("64")) {
            prompt.alert(Prompt.Level.WARNING, "Java Version", "You are using an unsupported Java version. Please use the 64-bit version of Java.");
        }

        checkPythonAvailability(prompt, log);

        BotConfig config = new BotConfig(prompt);
        config.load();
        if (!config.isValid()) {
            return;
        }

        GUI gui = initializeGui(prompt, log);
        installOAuthRefreshTokenCapture(config);

        enableCommandAuditIfConfigured(config, log);

        // set up the listener
        EventWaiter waiter = new EventWaiter();
        SettingsManager settings = new SettingsManager();

        Bot bot = new Bot(waiter, config, settings);
        Bot.INSTANCE = bot;
        
        // If GUI was created earlier, now set the bot and initialize the window
        final GUI finalGui = gui;
        if (finalGui != null) {
            finalGui.setBot(bot);
        }

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

        registerSlashCommands(cb, bot, config, aboutCommand);

        if (config.useEval())
            cb.addCommand(new EvalCmd(bot));
        boolean nogame = configureCommandClientPresence(cb, config);

        initializeGuiWindow(finalGui, bot, log);

        log.info("Loaded settings from {}", config.getConfigLocation());

        final JDA[] jdaRef = new JDA[1];
        JDA jda = startJdaOrExit(config, cb, waiter, bot, prompt, log, nogame);
        jdaRef[0] = jda;
        bot.setJDA(jda);

        startWebPanelIfEnabled(config, bot, log);
        registerShutdownHook(jdaRef, config, log);
    }

    private static void enableCommandAuditIfConfigured(BotConfig config, Logger log) {
        if (!config.getAuditCommands()) {
            return;
        }
        COMMAND_AUDIT_ENABLED = true;
        log.info("Command execution logging has been enabled.");
    }

    private static void initializeGuiWindow(GUI gui, Bot bot, Logger log) {
        if (gui == null) {
            return;
        }
        try {
            bot.setGUI(gui);
            gui.init();
            log.info("GUI window initialized and visible");
        } catch (Exception e) {
            log.error("Could not initialize the GUI window: " + e.getMessage());
        }
    }

    private static JDA startJdaOrExit(BotConfig config, CommandClientBuilder cb, EventWaiter waiter, Bot bot,
                                      Prompt prompt, Logger log, boolean nogame) {
        try {
            return startJda(config, cb, waiter, bot, prompt, log, nogame);
        } catch (InvalidTokenException ex) {
            prompt.alert(Prompt.Level.ERROR, "JMusicBot", ex + "\n"
                    + "Please ensure you are editing the correct configuration file. Failed to log in with the bot token."
                    + "Please enter the correct bot token. (Not the CLIENT SECRET!)\n"
                    + "Configuration file location: " + config.getConfigLocation());
            System.exit(1);
        } catch (IllegalArgumentException ex) {
            prompt.alert(Prompt.Level.ERROR, "JMusicBot", "Some settings are invalid:" + ex + "\n"
                    + "Location of the configuration file: " + config.getConfigLocation());
            System.exit(1);
        }
        return null;
    }

    private static void startWebPanelIfEnabled(BotConfig config, Bot bot, Logger log) {
        if (!config.isWebPanelEnabled()) {
            return;
        }

        try {
            log.info("Starting Web Panel on port " + config.getWebPanelPort());
            com.jagrosh.jmusicbot.webpanel.WebPanelApplication.start(bot, config.getWebPanelPort());
        } catch (Exception e) {
            log.error("Failed to start Web Panel", e);
        }
    }

    private static void registerShutdownHook(JDA[] jdaRef, BotConfig config, Logger log) {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            if (jdaRef[0] == null) {
                return;
            }
            jdaRef[0].shutdown();
            if (config.isWebPanelEnabled()) {
                log.info("Stopping Web Panel");
                com.jagrosh.jmusicbot.webpanel.WebPanelApplication.stop();
            }
        }));
    }

    private static void registerSlashCommands(CommandClientBuilder cb, Bot bot, BotConfig config, AboutCommand aboutCommand) {
        List<SlashCommand> slashCommandList = new ArrayList<>();
        slashCommandList.add(new HelpCmd(bot));
        slashCommandList.add(aboutCommand);
        if (config.isUseInviteCommand()) {
            slashCommandList.add(new InviteCommand());
        }
        slashCommandList.add(new PingCommand());
        slashCommandList.add(new SettingsCmd(bot));

        slashCommandList.add(new ServerInfo(bot));
        slashCommandList.add(new CashCmd(bot));
        slashCommandList.add(new StatsCommand(bot));

        slashCommandList.add(new LyricsCmd(bot));
        slashCommandList.add(new NowplayingCmd(bot));
        slashCommandList.add(new PlayCmd(bot));
        slashCommandList.add(new SpotifyCmd(bot));
        slashCommandList.add(new PlaylistsCmd(bot));
        slashCommandList.add(new MylistCmd(bot));
        slashCommandList.add(new QueueCmd(bot));
        slashCommandList.add(new RemoveCmd(bot));
        slashCommandList.add(new SearchCmd(bot));
        slashCommandList.add(new SCSearchCmd(bot));
        slashCommandList.add(new SeekCmd(bot));
        slashCommandList.add(new NicoSearchCmd(bot));
        slashCommandList.add(new ShuffleCmd(bot));
        slashCommandList.add(new SkipCmd(bot));
        slashCommandList.add(new VolumeCmd(bot));
        slashCommandList.add(new RadioCmd(bot));

        slashCommandList.add(new ForceRemoveCmd(bot));
        slashCommandList.add(new ForceskipCmd(bot));
        slashCommandList.add(new NextCmd(bot));
        slashCommandList.add(new MoveTrackCmd(bot));
        slashCommandList.add(new PauseCmd(bot));
        slashCommandList.add(new PlaynextCmd(bot));
        slashCommandList.add(new RepeatCmd(bot));
        slashCommandList.add(new SkipToCmd(bot));
        slashCommandList.add(new ForceToEnd(bot));
        slashCommandList.add(new StopCmd(bot));
        slashCommandList.add(new HistoryCmd(bot));

        slashCommandList.add(new PrefixCmd(bot));
        slashCommandList.add(new SetdjCmd(bot));
        slashCommandList.add(new SkipratioCmd(bot));
        slashCommandList.add(new SettcCmd(bot));
        slashCommandList.add(new SetvcCmd(bot));
        slashCommandList.add(new SetvcStatusCmd(bot));
        slashCommandList.add(new SettopicStatusCmd(bot));
        slashCommandList.add(new AutoplaylistCmd(bot));
        slashCommandList.add(new ServerListCmd(bot));

        slashCommandList.add(new DebugCmd(bot));
        slashCommandList.add(new SetavatarCmd(bot));
        slashCommandList.add(new SetgameCmd(bot));
        slashCommandList.add(new SetnameCmd(bot));
        slashCommandList.add(new SetstatusCmd(bot));
        slashCommandList.add(new PublistCmd(bot));
        slashCommandList.add(new ShutdownCmd(bot));
        slashCommandList.add(new LeaveCmd(bot));

        cb.addCommands(slashCommandList.toArray(new Command[0]));
    }

    private static boolean configureCommandClientPresence(CommandClientBuilder cb, BotConfig config) {
        boolean nogame = false;
        if (config.getStatus() != OnlineStatus.UNKNOWN) {
            cb.setStatus(config.getStatus());
        }
        if (config.getGame() == null) {
            cb.setActivity(Activity.playing("Check help with " + config.getPrefix() + config.getHelp()));
        } else if (config.getGame().getName().toLowerCase().matches("(none)")) {
            cb.setActivity(null);
            nogame = true;
        } else {
            cb.setActivity(config.getGame());
        }
        return nogame;
    }

    private static JDA startJda(BotConfig config, CommandClientBuilder cb, EventWaiter waiter, Bot bot,
                                Prompt prompt, Logger log, boolean nogame) {
        JDABuilder jdaBuilder = JDABuilder.create(config.getToken(), Arrays.asList(INTENTS))
                .enableCache(CacheFlag.MEMBER_OVERRIDES, CacheFlag.VOICE_STATE)
                .disableCache(CacheFlag.ACTIVITY, CacheFlag.CLIENT_STATUS, CacheFlag.EMOJI, CacheFlag.ONLINE_STATUS,
                        CacheFlag.STICKER, CacheFlag.SCHEDULED_EVENTS)
                .setActivity(nogame ? null : Activity.playing("Loading..."))
                .setStatus(config.getStatus() == OnlineStatus.INVISIBLE || config.getStatus() == OnlineStatus.OFFLINE
                        ? OnlineStatus.INVISIBLE : OnlineStatus.DO_NOT_DISTURB);

        AudioModuleConfig daveConfig = createDaveAudioModuleConfig(log, prompt);
        if (daveConfig != null) {
            jdaBuilder.setAudioModuleConfig(daveConfig);
        }

        JDA jda = jdaBuilder
                .addEventListeners(cb.build(), waiter, new Listener(bot))
                .setBulkDeleteSplittingEnabled(true)
                .build();

        String unsupportedReason = OtherUtil.getUnsupportedBotReason(jda);
        if (unsupportedReason != null)
        {
            prompt.alert(Prompt.Level.ERROR, "JMusicBot", "JMusicBot cannot be run with this Discord bot user: " + unsupportedReason);
            try{ Thread.sleep(5000);}catch(InterruptedException ignored){} // this is awful but until we have a better way...
            jda.shutdown();
            System.exit(1);
        }

        return jda;
    }

    private static void printBanner() {
        try {
            System.out.println(FigletFont.convertOneLine("JMusicBot v" + OtherUtil.getCurrentVersion()) + "\n" + "by THOMZY");
        } catch (IOException e) {
            System.out.println("JMusicBot v" + OtherUtil.getCurrentVersion() + "\nby THOMZY");
        }
    }

    private static void parseStartupArgs(String[] args, Prompt prompt, Logger log) {
        for (String arg : args) {
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
        }
    }

    private static void checkPythonAvailability(Prompt prompt, Logger log) {
        try {
            Process checkPython3 = Runtime.getRuntime().exec("python3 --version");
            int python3ExitCode = checkPython3.waitFor();
            if (python3ExitCode == 0) {
                log.info("Python (version 3.x) is installed");
                return;
            }

            log.info("Python3 is not installed. Checking for python.");
            Process checkPython = Runtime.getRuntime().exec("python --version");
            BufferedReader reader = new BufferedReader(new InputStreamReader(checkPython.getInputStream()));
            String pythonVersion = reader.readLine();
            int pythonExitCode = checkPython.waitFor();
            if (pythonExitCode == 0 && pythonVersion != null && pythonVersion.startsWith("Python 3")) {
                log.info(pythonVersion);
                return;
            }

            prompt.alert(Prompt.Level.WARNING, "Python", "Python (version 3.x) is not installed. Please install Python 3.");
        } catch (Exception e) {
            prompt.alert(Prompt.Level.WARNING, "Python", "An error occurred while checking the Python version. Please ensure Python 3 is installed.");
        }
    }

    private static GUI initializeGui(Prompt prompt, Logger log) {
        GUI gui = null;
        if (!prompt.isNoGUI()) {
            try {
                gui = new GUI();
                log.info("GUI console initialized - all logs will appear in the GUI");
            } catch (Exception e) {
                log.error("Could not create the GUI. The following factors may be causing this:\n"
                        + "Running on a server\n"
                        + "Running in an environment without a display\n"
                        + "To hide this error, use the -Dnogui=true flag to run in GUI-less mode.");
            }
        }
        return gui;
    }

    private static void installOAuthRefreshTokenCapture(BotConfig config) {
        originalOut = new DelegatingPrintStream(System.out);
        System.setOut(new PrintStream(new OutputStream() {
            private final StringBuilder buffer = new StringBuilder();
            @Override
            public void write(int b) throws IOException {
                if (b == '\n') {
                    String line = buffer.toString();
                    originalOut.println(line);
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
    }
}
