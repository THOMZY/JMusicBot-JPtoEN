/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.JMusicBot;
import com.jagrosh.jmusicbot.webpanel.service.ConsoleService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

import java.io.PrintStream;
import java.util.concurrent.CountDownLatch;

@SpringBootApplication
public class WebPanelApplication {

    private static ConsoleService consoleService;
    private static PrintStream originalOut;
    private static PrintStream originalErr;
    private static ConfigurableApplicationContext context;
    
    // Make Bot instance available as a bean
    @Bean
    public Bot jmusicBot() {
        // The Bot is initialized and started by the JMusicBot class
        // We just return the singleton instance
        return Bot.INSTANCE;
    }
    
    /**
     * Start the web panel with the given bot and port
     * @param bot The bot instance
     * @param port The port to run on
     */
    public static void start(Bot bot, int port) {
        // Start the Spring Boot application
        String[] args = new String[]{"--server.port=" + port};
        context = SpringApplication.run(WebPanelApplication.class, args);
        
        // Get the ConsoleService from the ApplicationContext
        consoleService = context.getBean(ConsoleService.class);
        
        // Set up console redirection to capture logs
        setupConsoleRedirection();
        
        // Log startup message
        consoleService.addLogMessage("Web Panel started successfully on port " + port);
        
        System.out.println("JMusicBot Web Panel is running on port " + port + "!");
    }
    
    /**
     * Stop the web panel
     */
    public static void stop() {
        if (context != null) {
            try {
                // Restore original output streams
                restoreOriginalStreams();
                
                // Close the application context
                context.close();
                
                // Wait a moment for resources to be released
                try {
                    Thread.sleep(100);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                
                System.out.println("Web Panel stopped successfully");
                context = null;
            } catch (Exception e) {
                System.err.println("Error stopping Web Panel: " + e.getMessage());
            }
        }
    }

    public static void main(String[] args) {
        // Start the JMusicBot in a separate thread
        Thread botThread = new Thread(() -> {
            try {
                // Start the JMusicBot normally
                JMusicBot.main(args);
            } catch (Exception e) {
                System.err.println("Error starting JMusicBot: " + e.getMessage());
                e.printStackTrace();
            }
        });
        
        // Start the bot thread
        botThread.start();
        
        // Wait a moment for the bot to initialize
        try {
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        
        // Check if the bot was initialized
        if (Bot.INSTANCE == null) {
            System.err.println("Failed to initialize JMusicBot. Web panel may not function correctly.");
        } else {
            System.out.println("JMusicBot initialized successfully, starting web panel...");
        }
        
        // Start the Spring Boot application
        context = SpringApplication.run(WebPanelApplication.class, args);
        
        // Get the ConsoleService from the ApplicationContext
        consoleService = context.getBean(ConsoleService.class);
        
        // Set up console redirection to capture logs
        setupConsoleRedirection();
        
        // Log startup message
        consoleService.addLogMessage("Web Panel started successfully");
        
        System.out.println("JMusicBot Web Panel is running!");
    }
    
    private static void setupConsoleRedirection() {
        // Save original streams
        originalOut = System.out;
        originalErr = System.err;
        
        // Create a PrintStream that redirects to both the original and our log
        PrintStream customOut = new PrintStream(originalOut) {
            @Override
            public void println(String x) {
                originalOut.println(x);
                if (consoleService != null) {
                    consoleService.addLogMessage(x);
                }
            }
            
            @Override
            public void println(Object x) {
                originalOut.println(x);
                if (consoleService != null && x != null) {
                    consoleService.addLogMessage(x.toString());
                }
            }
        };
        
        // Create a PrintStream that redirects errors to both the original and our log
        PrintStream customErr = new PrintStream(originalErr) {
            @Override
            public void println(String x) {
                originalErr.println(x);
                if (consoleService != null) {
                    consoleService.addLogMessage("[ERROR] " + x);
                }
            }
            
            @Override
            public void println(Object x) {
                originalErr.println(x);
                if (consoleService != null && x != null) {
                    consoleService.addLogMessage("[ERROR] " + x.toString());
                }
            }
        };
        
        // Replace the system streams
        System.setOut(customOut);
        System.setErr(customErr);
    }
    
    // Method to restore original streams if needed
    public static void restoreOriginalStreams() {
        if (originalOut != null) {
            System.setOut(originalOut);
        }
        if (originalErr != null) {
            System.setErr(originalErr);
        }
    }
} 