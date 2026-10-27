package com.soulspace.service;

import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class SlotService {
    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int[] HOURS = {10, 12, 15, 18, 20};

    /** The next bookable appointment times (IST clinic hours), at least 30 minutes from now. */
    public List<Instant> upcoming() {
        ZonedDateTime cutoff = ZonedDateTime.now(IST).plusMinutes(30);
        List<Instant> out = new ArrayList<>();
        for (int d = 0; d < 5 && out.size() < 8; d++) {
            LocalDate day = cutoff.toLocalDate().plusDays(d);
            for (int h : HOURS) {
                ZonedDateTime z = day.atTime(h, 0).atZone(IST);
                if (z.isAfter(cutoff) && out.size() < 8) out.add(z.toInstant());
            }
        }
        return out;
    }
}
