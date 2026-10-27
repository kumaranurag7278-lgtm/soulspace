package com.soulspace.service;

import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Validates WebSocket topic access for peer chat rooms and seeker alerts.
 * Decoupled from SimpMessagingTemplate to prevent circular Spring configuration cycles.
 */
@Service
public class ChatAccessService {
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9\\-]{8,64}$");

    private final Map<String, String> sessionClient = new ConcurrentHashMap<>();
    private final Map<String, String> clientRoom = new ConcurrentHashMap<>();

    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    public void registerSession(String sessionId, String clientId) {
        if (sessionId != null && validId(clientId)) {
            sessionClient.put(sessionId, clientId);
        }
    }

    public String removeSession(String sessionId) {
        if (sessionId == null) return null;
        return sessionClient.remove(sessionId);
    }

    public void assignRoom(String roomId, String clientId1, String clientId2) {
        if (roomId != null) {
            if (clientId1 != null) clientRoom.put(clientId1, roomId);
            if (clientId2 != null) clientRoom.put(clientId2, roomId);
        }
    }

    public void removeClient(String clientId) {
        if (clientId != null) {
            clientRoom.remove(clientId);
        }
    }

    public boolean canAccessSeeker(String sessionId, String seekerClientId) {
        if (seekerClientId == null || sessionId == null || !validId(seekerClientId)) return false;
        String registered = sessionClient.get(sessionId);
        if (registered == null) {
            sessionClient.put(sessionId, seekerClientId);
            return true;
        }
        return registered.equals(seekerClientId);
    }

    public boolean canAccessRoom(String sessionId, String roomId) {
        if (roomId == null || sessionId == null) return false;
        String clientId = sessionClient.get(sessionId);
        if (clientId == null) return false;
        String userRoom = clientRoom.get(clientId);
        return roomId.equals(userRoom);
    }
}
