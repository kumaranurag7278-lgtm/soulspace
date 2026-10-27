package com.soulspace.web;

import com.soulspace.service.AriaService;
import com.soulspace.service.AriaService.ChatTurn;
import com.soulspace.service.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/aria")
public class AriaController {
    public record HistoryEntry(String role, String content) {}

    public record AriaReq(
            @NotBlank(message = "Say something first") @Size(max = 1000, message = "Please keep it under 1000 characters") String message,
            Integer turn,
            List<HistoryEntry> history) {}

    private final AriaService aria;
    private final RateLimiter limiter;

    public AriaController(AriaService aria, RateLimiter limiter) {
        this.aria = aria;
        this.limiter = limiter;
    }

    @PostMapping
    public Map<String, Object> talk(@Valid @RequestBody AriaReq r, HttpServletRequest req) {
        String ip = req.getRemoteAddr();
        if (!limiter.allow("aria", ip, 25, 60_000L)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Aria is catching her breath. Please take a slow breath and try again in a moment.");
        }

        List<ChatTurn> turns = null;
        if (r.history() != null) {
            turns = r.history().stream()
                    .filter(h -> h != null && h.content() != null)
                    .map(h -> new ChatTurn(h.role(), h.content()))
                    .toList();
        }

        return aria.reply(r.message(), r.turn() == null ? 0 : r.turn(), turns);
    }
}
