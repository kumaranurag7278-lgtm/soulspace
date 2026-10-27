package com.soulspace.web;

import com.soulspace.service.ChatService;
import com.soulspace.service.ConsultService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.security.Principal;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
class ChatRestController {
    public record ClientReq(@NotBlank(message = "Missing client id") String clientId) {}

    private final ChatService chat;
    private final com.soulspace.service.RateLimiter limiter;

    ChatRestController(ChatService chat, com.soulspace.service.RateLimiter limiter) {
        this.chat = chat;
        this.limiter = limiter;
    }

    @PostMapping("/match")
    public Map<String, Object> match(@Valid @RequestBody ClientReq r, jakarta.servlet.http.HttpServletRequest req) {
        String ip = req.getRemoteAddr();
        if (!limiter.allow("chat_match", ip + "_" + r.clientId(), 12, 60_000L)) {
            throw new org.springframework.web.server.ResponseStatusException(
                    HttpStatus.TOO_MANY_REQUESTS, "Please wait a moment before searching again.");
        }
        return chat.match(r.clientId());
    }

    @PostMapping("/leave")
    public Map<String, Object> leave(@Valid @RequestBody ClientReq r) {
        chat.leave(r.clientId());
        return Map.of("ok", true);
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        return chat.stats();
    }
}

@Controller
class ChatStompController {
    private final ChatService chat;
    private final ConsultService consults;
    private final SimpMessagingTemplate broker;

    ChatStompController(ChatService chat, ConsultService consults, SimpMessagingTemplate broker) {
        this.chat = chat;
        this.consults = consults;
        this.broker = broker;
    }

    @MessageMapping("/hello")
    public void hello(@Payload Map<String, String> p, @Header("simpSessionId") String sessionId) {
        chat.register(sessionId, p.get("clientId"));
    }

    @MessageMapping("/chat/{roomId}")
    public void message(@DestinationVariable String roomId, @Payload Map<String, String> p) {
        chat.send(roomId, p.get("clientId"), p.get("text"));
    }

    @MessageMapping("/consult/{code}")
    public void consult(@DestinationVariable String code, @Payload Map<String, String> p, Principal principal) {
        if (principal == null || !consults.canAccess(code, principal.getName())) return;
        String text = p.getOrDefault("text", "").strip();
        if (text.isEmpty()) return;
        if (text.length() > 1000) text = text.substring(0, 1000);
        broker.convertAndSend("/topic/consult/" + code,
                Map.of("type", "MSG", "from", "patient", "text", text, "ts", System.currentTimeMillis()));
    }

    @EventListener
    public void onDisconnect(SessionDisconnectEvent event) {
        chat.disconnected(event.getSessionId());
    }
}
