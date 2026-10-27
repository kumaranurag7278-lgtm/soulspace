package com.soulspace.web;

import com.soulspace.model.SoulBoardPost;
import com.soulspace.repo.Repositories.SoulBoardPostRepository;
import com.soulspace.service.AliasGenerator;
import com.soulspace.service.Safety;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/board")
public class BoardController {
    private static final Set<String> CATEGORIES = Set.of("Venting", "Anxiety", "Lonely", "Small win", "Gratitude");

    public record PostReq(@NotBlank(message = "Write a few words first") @Size(max = 500, message = "Keep it under 500 characters") String content,
                          String category) {}
    public record ReactReq(@NotBlank String type) {}
    public record PostView(Long id, String alias, String category, String content, int hugs, int tea, int peace, Instant createdAt) {}

    private final SoulBoardPostRepository posts;
    private final SimpMessagingTemplate broker;
    private final com.soulspace.service.RateLimiter limiter;

    public BoardController(SoulBoardPostRepository posts, SimpMessagingTemplate broker, com.soulspace.service.RateLimiter limiter) {
        this.posts = posts;
        this.broker = broker;
        this.limiter = limiter;
    }

    @GetMapping
    public List<PostView> feed() {
        return posts.findTop50ByOrderByCreatedAtDesc().stream().map(BoardController::view).toList();
    }

    @PostMapping
    public Map<String, Object> post(@Valid @RequestBody PostReq r, jakarta.servlet.http.HttpServletRequest req) {
        String ip = req.getRemoteAddr();
        if (!limiter.allow("board_post", ip, 5, 60_000L)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "You're writing a little fast. Please pause for a moment.");
        }
        SoulBoardPost p = new SoulBoardPost();
        p.alias = AliasGenerator.next();
        p.category = r.category() != null && CATEGORIES.contains(r.category()) ? r.category() : "Venting";
        p.content = r.content().trim();
        posts.save(p);
        PostView v = view(p);
        broker.convertAndSend("/topic/board", v);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("post", v);
        out.put("crisis", Safety.isCrisis(p.content));
        return out;
    }

    @PostMapping("/{id}/react")
    @Transactional
    public PostView react(@PathVariable Long id, @Valid @RequestBody ReactReq r, jakarta.servlet.http.HttpServletRequest req) {
        String ip = req.getRemoteAddr();
        if (!limiter.allow("board_react", ip, 40, 60_000L)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Slow down your reactions a bit.");
        }
        int changed = switch (r.type()) {
            case "hug" -> posts.addHug(id);
            case "tea" -> posts.addTea(id);
            case "peace" -> posts.addPeace(id);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown reaction");
        };
        if (changed == 0) throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found");
        SoulBoardPost p = posts.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Post not found"));
        PostView v = view(p);
        broker.convertAndSend("/topic/board", v);
        return v;
    }

    private static PostView view(SoulBoardPost p) {
        return new PostView(p.id, p.alias, p.category, p.content, p.hugsCount, p.teaCount, p.peaceCount, p.createdAt);
    }
}
