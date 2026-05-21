package com.argusiq.tracing.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;
import org.springframework.web.socket.messaging.SessionSubscribeEvent;

@Component
public class WebSocketTraceEventListener {

    private static final Logger logger = LoggerFactory.getLogger(WebSocketTraceEventListener.class);

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
        logger.info("STOMP connected: sessionId={}", headers.getSessionId());
    }

    @EventListener
    public void handleSessionSubscribe(SessionSubscribeEvent event) {
        SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(event.getMessage());
        logger.info(
                "STOMP subscribed: sessionId={}, destination={}",
                headers.getSessionId(),
                headers.getDestination()
        );
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        logger.info("STOMP disconnected: sessionId={}", event.getSessionId());
    }
}
