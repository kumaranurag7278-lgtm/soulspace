package com.soulspace.web;

import com.soulspace.model.Account;
import com.soulspace.model.MoodEntry;
import com.soulspace.repo.Repositories.MoodEntryRepository;
import com.soulspace.service.SlotService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

@RestController
@RequestMapping("/api/mood")
public class MoodController {
    private static final Map<String, Integer> SCORES = Map.of(
            "joyful", 5, "calm", 4, "neutral", 3, "anxious", 2, "overwhelmed", 1);

    public record MoodReq(@NotBlank(message = "Pick a mood") String mood) {}

    private final MoodEntryRepository moods;
    private final SessionUser user;

    public MoodController(MoodEntryRepository moods, SessionUser user) {
        this.moods = moods;
        this.user = user;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        Account me = user.require();
        List<MoodEntry> rows = new ArrayList<>(moods.findTop30ByAccountAliasOrderByEntryDateDesc(me.alias));
        Collections.reverse(rows);
        List<Map<String, Object>> out = new ArrayList<>();
        for (MoodEntry e : rows) out.add(Map.of("date", e.entryDate.toString(), "mood", e.mood, "score", e.score));
        return out;
    }

    @PostMapping
    public Map<String, Object> checkIn(@Valid @RequestBody MoodReq r) {
        Account me = user.require();
        String mood = r.mood().toLowerCase(Locale.ROOT);
        Integer score = SCORES.get(mood);
        if (score == null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown mood");
        LocalDate today = LocalDate.now(SlotService.IST);
        MoodEntry e = moods.findByAccountAliasAndEntryDate(me.alias, today).orElseGet(MoodEntry::new);
        e.accountAlias = me.alias;
        e.entryDate = today;
        e.mood = mood;
        e.score = score;
        e.createdAt = Instant.now();
        moods.save(e);
        return Map.of("date", today.toString(), "mood", mood, "score", score);
    }
}
