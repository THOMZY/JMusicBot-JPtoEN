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

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) throws Exception {
        JsonNode node;
        try {
            node = mapper.readTree(message.getPayload());
        } catch (Exception ex) {
            return;
        }
        if (node == null || !node.isObject()) return;

        ObjectNode out = mapper.createObjectNode();
        out.put("type", "move");
        out.put("id", session.getId());
        if (node.hasNonNull("sel")) out.put("sel", node.get("sel").asText());
        if (node.hasNonNull("rx")) out.put("rx", node.get("rx").asDouble());
        if (node.hasNonNull("ry")) out.put("ry", node.get("ry").asDouble());
        if (node.hasNonNull("vx")) out.put("vx", node.get("vx").asDouble());
        if (node.hasNonNull("vy")) out.put("vy", node.get("vy").asDouble());
        // Legacy fields kept so older clients still interoperate.
        if (node.hasNonNull("x")) out.put("x", node.get("x").asDouble());
        if (node.hasNonNull("y")) out.put("y", node.get("y").asDouble());
        if (node.hasNonNull("dx")) out.put("dx", node.get("dx").asDouble());
        if (node.hasNonNull("dy")) out.put("dy", node.get("dy").asDouble());
        if (node.hasNonNull("w")) out.put("w", node.get("w").asDouble());
        if (node.hasNonNull("h")) out.put("h", node.get("h").asDouble());
        if (node.hasNonNull("name")) out.put("name", node.get("name").asText());
        if (node.hasNonNull("color")) out.put("color", node.get("color").asText());
        if (node.hasNonNull("click")) out.put("click", node.get("click").asBoolean());
        if (node.hasNonNull("page")) out.put("page", node.get("page").asText());

        broadcast(out, session.getId());
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
