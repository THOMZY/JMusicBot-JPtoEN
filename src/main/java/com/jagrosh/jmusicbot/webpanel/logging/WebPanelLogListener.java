/*
 * Copyright 2025 THOMZY
 */

package com.jagrosh.jmusicbot.webpanel.logging;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import com.jagrosh.jmusicbot.webpanel.service.ConsoleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Custom log appender that captures logs and sends them to the ConsoleService
 */
@Component
public class WebPanelLogListener extends AppenderBase<ILoggingEvent> {

    private static ConsoleService consoleService;

    @Autowired
    public void setConsoleService(ConsoleService service) {
        WebPanelLogListener.consoleService = service;
        start(); // Start the appender once we have the console service
    }

    @Override
    protected void append(ILoggingEvent event) {
        if (consoleService != null) {
            String logLevel = event.getLevel().toString();
            String formattedMessage = String.format("[%s] %s: %s", 
                    logLevel, 
                    event.getLoggerName(), 
                    event.getFormattedMessage());
            
            consoleService.addLogMessage(formattedMessage);
        }
    }
} 