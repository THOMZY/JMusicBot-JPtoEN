/*
 * Copyright 2018-2020 Cosgy Dev
 * Edit 2025 THOMZY
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */
package com.jagrosh.jmusicbot.entities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.swing.*;
import java.util.Scanner;

/**
 * @author John Grosh (john.a.grosh@gmail.com)
 */
public class Prompt {
    private final String title;
    private final String noguiMessage;
    private final boolean noprompt;
    private boolean nogui;
    private Scanner scanner;

    public Prompt(String title) {
        this(title, null);
    }

    public Prompt(String title, String noguiMessage) {
        this(title, noguiMessage, "true".equalsIgnoreCase(System.getProperty("nogui")), "true".equalsIgnoreCase(System.getProperty("noprompt")));
    }

    public Prompt(String title, String noguiMessage, boolean nogui, boolean noprompt) {
        this.title = title;
        this.noguiMessage = noguiMessage == null ? "Switching to nogui mode. You can manually start in nogui mode by including the -nogui=true flag." : noguiMessage;
        this.nogui = nogui;
        this.noprompt = noprompt;
    }

    public boolean isNoGUI() {
        return nogui;
    }

    public void alert(Level level, String context, String message) {
        if (nogui) {
            Logger log = LoggerFactory.getLogger(context);
            switch (level) {
                case WARNING:
                    log.warn(message);
                    break;
                case ERROR:
                    log.error(message);
                    break;
                case INFO:
                default:
                    log.info(message);
                    break;
            }
        } else {
            try {
                int option = 0;
                switch (level) {
                    case INFO:
                        option = JOptionPane.INFORMATION_MESSAGE;
                        break;
                    case WARNING:
                        option = JOptionPane.WARNING_MESSAGE;
                        break;
                    case ERROR:
                        option = JOptionPane.ERROR_MESSAGE;
                        break;
                    default:
                        option = JOptionPane.PLAIN_MESSAGE;
                        break;
                }
                JOptionPane.showMessageDialog(null, message, title, option);
            } catch (Exception e) {
                nogui = true;
                alert(Level.WARNING, context, noguiMessage);
                alert(level, context, message);
            }
        }
    }

    public String prompt(String content) {
        if (noprompt)
            return null;
        if (nogui) {
            if (scanner == null)
                scanner = new Scanner(System.in);
            try {
                System.out.println(content);
                if (scanner.hasNextLine())
                    return scanner.nextLine();
                return null;
            } catch (Exception e) {
                alert(Level.ERROR, title, "Unable to read input from the command line.");
                e.printStackTrace();
                return null;
            }
        } else {
            try {
                return JOptionPane.showInputDialog(null, content, title, JOptionPane.QUESTION_MESSAGE);
            } catch (Exception e) {
                nogui = true;
                alert(Level.WARNING, title, noguiMessage);
                return prompt(content);
            }
        }
    }

    public enum Level {
        INFO, WARNING, ERROR
    }
}
