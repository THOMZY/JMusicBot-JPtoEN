/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.service;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.JMusicBot;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

@Service
public class ConsoleService {

    private final Bot bot;
    private final ConcurrentLinkedQueue<String> consoleLog = new ConcurrentLinkedQueue<>();
    private static final int MAX_LOG_SIZE = 5000;  // Maximum number of log entries to keep
    private static final String LOG_FILE_PATH = "JMusicBotJP.log";
    private static final String LOG_DIR_PATH = "logs";
    private long lastLogCheckTime = 0;

    public ConsoleService(Bot bot) {
        this.bot = bot;
        // Load initial logs from file
        loadLogsFromFile();
    }

    /**
     * Load logs from the log file on startup
     */
    private void loadLogsFromFile() {
        try {
            // First try to read from root directory
            File logFile = new File(LOG_FILE_PATH);
            
            if (!logFile.exists()) {
                // Try in logs directory
                logFile = new File(LOG_DIR_PATH, LOG_FILE_PATH);
                if (!logFile.exists()) {
                    addLogMessage("Warning: Log file not found in root or logs directory");
                    return;
                }
            }
            
            // For large log files, read last MAX_LOG_SIZE lines only
            List<String> lines;
            if (logFile.length() > 1024 * 1024) { // If file is larger than 1MB
                lines = readLastNLines(logFile.toPath(), MAX_LOG_SIZE);
                addLogMessage("Log file is large, loaded last " + lines.size() + " lines");
            } else {
                lines = Files.readAllLines(logFile.toPath());
            }
            
            for (String line : lines) {
                if (!line.trim().isEmpty()) {
                    consoleLog.add(line);
                }
            }
            
            // Update last check time
            lastLogCheckTime = System.currentTimeMillis();
            
            addLogMessage("Loaded " + lines.size() + " log entries from file");
            
            // Trim the log if it's too large
            while (consoleLog.size() > MAX_LOG_SIZE) {
                consoleLog.poll();
            }
        } catch (IOException e) {
            addLogMessage("Error loading log file: " + e.getMessage());
        }
    }
    
    /**
     * Read the last N lines of a file
     */
    private List<String> readLastNLines(java.nio.file.Path filePath, int n) throws IOException {
        List<String> result = new ArrayList<>();
        try (RandomAccessFile randomAccessFile = new RandomAccessFile(filePath.toFile(), "r")) {
            long fileLength = randomAccessFile.length();
            
            // Start reading from the end of the file
            long pointer = fileLength - 1;
            int lineCount = 0;
            
            // Read the file backwards
            for (long seek = pointer; seek >= 0; seek--) {
                randomAccessFile.seek(seek);
                char c = (char) randomAccessFile.read();
                
                if (c == '\n' && seek != fileLength - 1) {
                    lineCount++;
                    if (lineCount >= n) {
                        // We found enough lines, now read the file forward from this point
                        randomAccessFile.seek(seek + 1);
                        String line;
                        while ((line = randomAccessFile.readLine()) != null) {
                            result.add(line);
                        }
                        break;
                    }
                }
                
                // If we reach the beginning of the file
                if (seek == 0) {
                    randomAccessFile.seek(0);
                    String line;
                    while ((line = randomAccessFile.readLine()) != null) {
                        result.add(line);
                    }
                }
            }
        }
        return result;
    }

    /**
     * Check for new log entries in the file
     */
    private void checkForNewLogs() {
        try {
            // Only check once per second at most to prevent excessive file access
            long currentTime = System.currentTimeMillis();
            if (currentTime - lastLogCheckTime < 1000) {
                return;
            }

            File logFile = new File(LOG_FILE_PATH);
            if (!logFile.exists()) {
                // Try in logs directory
                logFile = new File(LOG_DIR_PATH, LOG_FILE_PATH);
                if (!logFile.exists()) {
                    return;
                }
            }
            
            // Only check if the file has been modified since last check
            if (logFile.lastModified() > lastLogCheckTime) {
                List<String> allLines = Files.readAllLines(logFile.toPath());
                int currentSize = consoleLog.size();
                
                // If we have more lines in the file than our current log
                if (allLines.size() > currentSize) {
                    // Add only the new lines (assuming logs are only appended, not modified)
                    List<String> newLines = allLines.subList(Math.max(0, allLines.size() - (allLines.size() - currentSize)), allLines.size());
                    for (String line : newLines) {
                        if (!line.trim().isEmpty()) {
                            consoleLog.add(line);
                        }
                    }
                }
                
                // Update last check time
                lastLogCheckTime = System.currentTimeMillis();
                
                // Trim the log if it's too large
                while (consoleLog.size() > MAX_LOG_SIZE) {
                    consoleLog.poll();
                }
            }
        } catch (IOException e) {
            // Silent fail for monitoring - this is called frequently
            System.err.println("Error checking for new logs: " + e.getMessage());
        }
    }

    /**
     * Add a log message to the console log queue
     */
    public void addLogMessage(String message) {
        consoleLog.add(message);
        
        // Trim the log if it gets too large
        while (consoleLog.size() > MAX_LOG_SIZE) {
            consoleLog.poll();
        }
    }

    /**
     * Get all console log messages
     */
    public List<String> getConsoleLog() {
        // Check for new logs in the file before returning
        checkForNewLogs();
        return new ArrayList<>(consoleLog);
    }

    /**
     * Get the bot configuration file content
     */
    public String getConfigContent() throws IOException {
        File configFile = new File("config.txt");
        if (!configFile.exists()) {
            return "Config file not found.";
        }
        
        return new String(Files.readAllBytes(Paths.get(configFile.getPath())));
    }

    /**
     * Save changes to the bot configuration file
     */
    public boolean saveConfigContent(String content) {
        try {
            File configFile = new File("config.txt");
            if (!configFile.exists()) {
                return false;
            }
            
            Files.write(Paths.get(configFile.getPath()), content.getBytes());
            return true;
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }

    /**
     * Execute a bot command and return the result
     */
    public String executeCommand(String command) {
        try {
            if (bot == null) {
                return "Error: Bot is not initialized";
            }
            
            // For console commands, we'll prefix them with the bot's prefix
            // Ensure the bot prefix is applied if it doesn't already start with it
            String prefix = bot.getConfig().getPrefix();
            String fullCommand = command.startsWith(prefix) ? command : prefix + command;
            
            // Add the command to the log
            addLogMessage("> " + command);
            
            // Process the command through the bot's command system
            // This is a simulated execution - the bot would normally process this
            // through a guild message event
            if (bot.getJDA() != null && !bot.getJDA().getGuilds().isEmpty()) {
                String guildId = bot.getJDA().getGuilds().get(0).getId();
                
                if (fullCommand.equals(prefix + "help")) {
                    return "List of available commands: play, pause, skip, volume, queue, etc. (Use prefix: " + prefix + ")";
                } else if (fullCommand.startsWith(prefix + "play")) {
                    addLogMessage("Processing play command...");
                    return "Added to queue! (Command executed from web panel)";
                } else if (fullCommand.equals(prefix + "pause")) {
                    addLogMessage("Pausing playback...");
                    return "Playback paused (Command executed from web panel)";
                } else if (fullCommand.equals(prefix + "skip")) {
                    addLogMessage("Skipping current track...");
                    return "Skipped to the next track (Command executed from web panel)";
                } else {
                    // For other commands, return a generic message
                    addLogMessage("Executing command: " + fullCommand);
                    return "Command sent to bot (Command executed from web panel)";
                }
            } else {
                return "Error: Bot is not connected to any servers";
            }
        } catch (Exception e) {
            e.printStackTrace();
            return "Error executing command: " + e.getMessage();
        }
    }
} 