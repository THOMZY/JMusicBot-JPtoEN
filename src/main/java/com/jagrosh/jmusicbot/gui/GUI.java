/*
 * Copyright 2016 John Grosh <john.a.grosh@gmail.com>.
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
package com.jagrosh.jmusicbot.gui;

import com.jagrosh.jmusicbot.Bot;
import com.jagrosh.jmusicbot.BotConfig;

import javax.swing.*;
import java.awt.event.WindowEvent;
import java.awt.event.WindowListener;


/**
 * @author John Grosh <john.a.grosh@gmail.com>
 */
public class GUI extends JFrame {
    private final ConsolePanel console;
    private final Bot bot;

    public GUI(Bot bot) {
        super();
        this.bot = bot;
        console = new ConsolePanel();
    }

    public void init() {
        setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        setTitle("JMusicBot JP");
        JTabbedPane tabs = new JTabbedPane();
        tabs.add("Console", console);
        getContentPane().add(tabs);
        pack();
        setLocationRelativeTo(null);
        setVisible(true);
        addWindowListener(new WindowListener() {
            @Override
            public void windowOpened(WindowEvent e) { /* unused */ }

            @Override
            public void windowClosing(WindowEvent e) {
                try {
                    // First stop any running web panel if it exists
                    BotConfig config = bot.getConfig();
                    if (config != null && config.isWebPanelEnabled()) {
                        // Use a separate thread to avoid blocking GUI thread
                        new Thread(() -> {
                            try {
                                com.jagrosh.jmusicbot.webpanel.WebPanelApplication.stop();
                                // Small delay to let the web panel stop properly
                                Thread.sleep(500);
                                // Now shutdown the bot
                                bot.shutdown();
                            } catch (Exception ex) {
                                System.err.println("Error during shutdown: " + ex.getMessage());
                                System.exit(0);
                            }
                        }).start();
                    } else {
                        // Just shutdown the bot if no web panel
                        bot.shutdown();
                    }
                } catch (Exception ex) {
                    System.err.println("Error during shutdown: " + ex.getMessage());
                    System.exit(0);
                }
            }

            @Override
            public void windowClosed(WindowEvent e) { /* unused */ }

            @Override
            public void windowIconified(WindowEvent e) { /* unused */ }

            @Override
            public void windowDeiconified(WindowEvent e) { /* unused */ }

            @Override
            public void windowActivated(WindowEvent e) { /* unused */ }

            @Override
            public void windowDeactivated(WindowEvent e) { /* unused */ }
        });
    }
}
