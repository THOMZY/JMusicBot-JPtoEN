/*
 * Copyright 2026 THOMZY
 */
package com.jagrosh.jmusicbot.webpanel.websocket;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Broadcasts cursor positions between every client connected to the web panel.
 * The protocol is intentionally tiny:
 *  - server -> new client : {"type":"hello","id":"<sid>"}
 *  - client -> server     : {"x":0.42,"y":0.18,"name":"...","color":"#abcdef"}
 *  - server -> others     : {"type":"move","id":"<sid>","x":..,"y":..,"name":..,"color":..}
 *  - server -> others     : {"type":"leave","id":"<sid>"} on disconnect
 *
 * Coordinates are normalized (0..1) by the client so screens of different sizes
 * still line up reasonably well.
 */
@Component
public class CursorHandler extends TextWebSocketHandler {

    private final Map<String, WebSocketSession> sessions = new ConcurrentHashMap<>();
    private final ObjectMapper mapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {
        sessions.put(session.getId(), session);
        ObjectNode hello = mapper.createObjectNode();
        hello.put("type", "hello");
        hello.put("id", session.getId());
        session.sendMessage(new TextMessage(mapper.writeValueAsString(hello)));
    }

    /** Numeric fields that may be present on a client move message. */
    private static final String[] NUMBER_FIELDS = {
            "rx", "ry", "vx", "vy",
            // Legacy fields kept so older clients still interoperate.
            "x", "y", "dx", "dy", "w", "h"
    };

    /** Text fields that may be present on a client move message. */
    private static final String[] TEXT_FIELDS = { "sel", "name", "color", "page" };

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node = parseMessage(message);
        if (node == null) return;

        ObjectNode out = mapper.createObjectNode();
        out.put("type", "move");
        out.put("id", session.getId());
        copyFields(node, out);
        broadcast(out, session.getId());
    }

    private JsonNode parseMessage(TextMessage message) {
        try {
            JsonNode node = mapper.readTree(message.getPayload());
            return (node != null && node.isObject()) ? node : null;
        } catch (Exception ex) {
            return null;
        }
    }

    private static void copyFields(JsonNode in, ObjectNode out) {
        for (String f : NUMBER_FIELDS) {
            if (in.hasNonNull(f)) out.put(f, in.get(f).asDouble());
        }
        for (String f : TEXT_FIELDS) {
            if (in.hasNonNull(f)) out.put(f, in.get(f).asText());
        }
        if (in.hasNonNull("click")) out.put("click", in.get("click").asBoolean());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) throws Exception {
        sessions.remove(session.getId());
        ObjectNode leave = mapper.createObjectNode();
        leave.put("type", "leave");
        leave.put("id", session.getId());
        broadcast(leave, session.getId());
    }

    private void broadcast(ObjectNode payload, String exceptId) {
        String text;
        try {
            text = mapper.writeValueAsString(payload);
        } catch (Exception ex) {
            return;
        }
        TextMessage msg = new TextMessage(text);
        for (WebSocketSession s : sessions.values()) {
            if (s.getId().equals(exceptId)) continue;
            if (!s.isOpen()) continue;
            try {
                synchronized (s) {
                    s.sendMessage(msg);
                }
            } catch (Exception ignored) {
                // drop silently; the close handler will clean up
            }
        }
    }
}
