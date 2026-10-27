package com.soulspace.service;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;

/** Fully in-memory, ephemeral matchmaking and relay. Nothing is persisted or logged. */
@Service
public class ChatService {
    private static final Pattern ID = Pattern.compile("^[A-Za-z0-9\\-]{8,64}$");

    private final SimpMessagingTemplate broker;
    private final ChatAccessService access;
    private final Deque<String> queue = new ArrayDeque<>();
    private final Map<String, String> clientRoom = new HashMap<>();
    private final Map<String, String[]> rooms = new HashMap<>();
    private final Map<String, String> names = new HashMap<>();

    public ChatService(SimpMessagingTemplate broker, ChatAccessService access) {
        this.broker = broker;
        this.access = access;
    }

    public static boolean validId(String id) {
        return id != null && ID.matcher(id).matches();
    }

    public synchronized Map<String, Object> match(String clientId) {
        if (!validId(clientId)) throw new IllegalArgumentException("Invalid client id");
        leaveInternal(clientId);
        String peerId = null;
        while (!queue.isEmpty()) {
            String c = queue.poll();
            if (!c.equals(clientId)) {
                peerId = c;
                break;
            }
        }
        if (peerId == null) {
            queue.add(clientId);
            return Map.of("status", "WAITING");
        }
        String roomId = UUID.randomUUID().toString();
        String myName = AliasGenerator.next();
        String peerName;
        do {
            peerName = AliasGenerator.next();
        } while (peerName.equals(myName));
        names.put(clientId, myName);
        names.put(peerId, peerName);
        rooms.put(roomId, new String[]{clientId, peerId});
        clientRoom.put(clientId, roomId);
        clientRoom.put(peerId, roomId);
        access.assignRoom(roomId, clientId, peerId);
        broker.convertAndSend("/topic/seeker/" + peerId,
                Map.of("type", "MATCHED", "roomId", roomId, "you", peerName, "peer", myName));
        return Map.of("status", "MATCHED", "roomId", roomId, "you", myName, "peer", peerName);
    }

    public synchronized void leave(String clientId) {
        if (!validId(clientId)) return;
        leaveInternal(clientId);
    }

    private void leaveInternal(String clientId) {
        queue.remove(clientId);
        names.remove(clientId);
        access.removeClient(clientId);
        String roomId = clientRoom.remove(clientId);
        if (roomId == null) return;
        String[] pair = rooms.remove(roomId);
        if (pair != null) {
            for (String c : pair) {
                if (!c.equals(clientId)) {
                    clientRoom.remove(c);
                    names.remove(c);
                    access.removeClient(c);
                }
            }
            broker.convertAndSend("/topic/room/" + roomId, Map.of("type", "PEER_LEFT"));
        }
    }

    public synchronized void send(String roomId, String clientId, String text) {
        if (!validId(clientId)) return;
        String[] pair = rooms.get(roomId);
        if (pair == null || !(pair[0].equals(clientId) || pair[1].equals(clientId))) return;
        String clean = text == null ? "" : text.strip();
        if (clean.isEmpty()) return;
        if (clean.length() > 500) clean = clean.substring(0, 500);
        broker.convertAndSend("/topic/room/" + roomId,
                Map.of("type", "MSG", "from", names.getOrDefault(clientId, "Someone"), "text", clean,
                        "ts", System.currentTimeMillis()));
        if (Safety.isCrisis(clean)) {
            broker.convertAndSend("/topic/seeker/" + clientId, Map.of("type", "CRISIS_SUPPORT"));
        }
    }

    public synchronized void register(String sessionId, String clientId) {
        access.registerSession(sessionId, clientId);
    }

    public synchronized void disconnected(String sessionId) {
        String clientId = access.removeSession(sessionId);
        if (clientId != null) leaveInternal(clientId);
    }

    public synchronized Map<String, Object> stats() {
        return Map.of("waiting", queue.size(), "activeChats", rooms.size());
    }
}
