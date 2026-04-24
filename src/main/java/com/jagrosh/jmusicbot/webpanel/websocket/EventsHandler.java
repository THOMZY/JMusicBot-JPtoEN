/*
 * Copyright 2026 THOMZY
 */
package com.jagrosh.jmusicbot.webpanel.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Lightweight push channel used to invalidate client-side caches
 * (queue / status / filters) the moment server state changes.
 *
 * Protocol:
 *   server -> client : {"type":"queue"}    -- queue contents changed
 *   server -> client : {"type":"status"}   -- player status changed
 *   server -> client : {"type":"filters"}  -- audio filters changed
 *
 * The client just re-fetches the corresponding REST endpoint, so the
 * payload is intentionally tiny and free of authentication concerns.
 */
@Component
public class EventsHandler extends TextWebSocketHandler {

    private final Set<WebSocketSession> sessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.add(session);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session);
    }

    public boolean hasClients() {
        return !sessions.isEmpty();
    }

    public void broadcast(String type) {
        if (sessions.isEmpty()) return;
        ObjectNode node = mapper.createObjectNode();
        node.put("type", type);
        TextMessage msg;
        try {
            msg = new TextMessage(mapper.writeValueAsString(node));
        } catch (Exception ex) {
            return;
        }
        for (WebSocketSession s : sessions) {
            if (!s.isOpen()) continue;
            try {
                synchronized (s) {
                    s.sendMessage(msg);
                }
            } catch (Exception ignored) {
                // closed-handler will clean up
            }
        }
    }
}
