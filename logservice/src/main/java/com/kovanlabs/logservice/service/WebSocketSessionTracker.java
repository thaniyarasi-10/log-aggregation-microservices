package com.kovanlabs.logservice.service;

import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class WebSocketSessionTracker {

    public static class UserSessionInfo {
        private final String email;
        private final String role;
        private final Set<String> services;

        public UserSessionInfo(String email, String role, String servicesStr) {
            this.email = email;
            this.role = role;
            this.services = new HashSet<>();
            if (servicesStr != null && !servicesStr.isBlank()) {
                for (String s : servicesStr.split(",")) {
                    this.services.add(s.trim().toLowerCase());
                }
            }
        }

        public String getEmail() { return email; }
        public String getRole() { return role; }
        public Set<String> getServices() { return services; }

        public boolean isAuthorizedForService(String serviceName) {
            if ("ADMIN".equalsIgnoreCase(role)) {
                return true;
            }
            if (serviceName == null || serviceName.isBlank()) {
                return false;
            }
            return services.contains(serviceName.trim().toLowerCase());
        }
    }

    // Maps sessionId -> UserSessionInfo
    private final Map<String, UserSessionInfo> activeSessions = new ConcurrentHashMap<>();

    @EventListener
    public void handleSessionConnect(SessionConnectEvent event) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headers.getSessionId();
        Map<String, Object> attributes = headers.getSessionAttributes();

        if (attributes != null && sessionId != null) {
            String email = (String) attributes.get("email");
            String role = (String) attributes.get("role");
            String servicesStr = (String) attributes.get("services");

            if (email != null) {
                activeSessions.put(sessionId, new UserSessionInfo(email, role, servicesStr));
            }
        }
    }

    @EventListener
    public void handleSessionDisconnect(SessionDisconnectEvent event) {
        String sessionId = event.getSessionId();
        if (sessionId != null) {
            activeSessions.remove(sessionId);
        }
    }

    public Map<String, UserSessionInfo> getActiveSessions() {
        return activeSessions;
    }
}
