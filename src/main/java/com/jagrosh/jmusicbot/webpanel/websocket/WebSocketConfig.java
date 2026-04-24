/*
 * Copyright 2026 THOMZY
 */
package com.jagrosh.jmusicbot.webpanel.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final CursorHandler cursorHandler;
    private final EventsHandler eventsHandler;

    public WebSocketConfig(CursorHandler cursorHandler, EventsHandler eventsHandler) {
        this.cursorHandler = cursorHandler;
        this.eventsHandler = eventsHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(cursorHandler, "/ws/cursors")
                .setAllowedOriginPatterns("*");
        registry.addHandler(eventsHandler, "/ws/events")
                .setAllowedOriginPatterns("*");
    }
}
