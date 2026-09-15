package com.jacolp.module.system.biz.web.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jacolp.module.system.biz.application.vo.MonitorDataVO;
import com.jacolp.module.system.biz.application.service.SystemMonitorService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.ConcurrentHashMap;

@Component
@Slf4j
public class MonitorWebSocketHandler extends TextWebSocketHandler {

    private final SystemMonitorService systemMonitorService;
    private final ObjectMapper objectMapper;
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public MonitorWebSocketHandler(SystemMonitorService systemMonitorService, ObjectMapper objectMapper) {
        this.systemMonitorService = systemMonitorService;
        this.objectMapper = objectMapper;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        sessions.put(session.getId(), session);
        log.info("Monitor WebSocket connected: {}, total: {}", session.getId(), sessions.size());
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        sessions.remove(session.getId());
        log.info("Monitor WebSocket disconnected: {}, total: {}", session.getId(), sessions.size());
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        sessions.remove(session.getId());
        log.error("Monitor WebSocket transport error: {}", session.getId(), exception);
    }

    @Scheduled(fixedRate = 1000)
    public void broadcastMetrics() {
        if (sessions.isEmpty()) {
            systemMonitorService.resetQPSCounter();
            return;
        }

        try {
            MonitorDataVO data = systemMonitorService.collect();
            String json = objectMapper.writeValueAsString(data);
            TextMessage message = new TextMessage(json);

            sessions.values().forEach(session -> {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(message);
                    } else {
                        sessions.remove(session.getId());
                    }
                } catch (Exception e) {
                    sessions.remove(session.getId());
                    log.debug("Failed to send to session: {}", session.getId());
                }
            });
        } catch (Exception e) {
            log.error("Broadcast metrics error", e);
        }
    }
}
