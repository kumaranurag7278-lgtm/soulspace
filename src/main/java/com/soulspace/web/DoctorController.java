package com.soulspace.web;

import com.soulspace.model.Account;
import com.soulspace.model.Doctor;
import com.soulspace.model.DoctorReview;
import com.soulspace.repo.Repositories.BookingRepository;
import com.soulspace.repo.Repositories.DoctorRepository;
import com.soulspace.repo.Repositories.DoctorReviewRepository;
import com.soulspace.service.SlotService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/doctors")
public class DoctorController {

    public record ReviewView(String author, int rating, String comment) {}

    public record DoctorView(String code, String name, String title, String specialty, int experience, int fee,
                      int effectiveFee, double ratingAverage, int totalReviews, String badge, String avatar,
                      Instant nextSlot, ReviewView latestReview) {}

    public record ReviewReq(@Min(value = 1, message = "Pick 1 to 5 stars") @Max(value = 5, message = "Pick 1 to 5 stars") int rating,
                     @NotBlank(message = "Please write a few words") @Size(max = 500, message = "Keep it under 500 characters") String comment) {}

    private final DoctorRepository doctors;
    private final DoctorReviewRepository reviews;
    private final BookingRepository bookings;
    private final SlotService slots;
    private final SessionUser user;
    private final boolean requireBooking;

    public DoctorController(DoctorRepository doctors, DoctorReviewRepository reviews, BookingRepository bookings,
                            SlotService slots, SessionUser user,
                            @Value("${soulspace.reviews.require-booking:true}") boolean requireBooking) {
        this.doctors = doctors;
        this.reviews = reviews;
        this.bookings = bookings;
        this.slots = slots;
        this.user = user;
        this.requireBooking = requireBooking;
    }

    @GetMapping
    public List<DoctorView> list() {
        boolean pass = user.current().map(Account::hasActivePass).orElse(false);
        List<Instant> upcoming = slots.upcoming();
        return doctors.findAllByOrderByFeeAsc().stream().map(d -> {
            ReviewView rv = reviews.findFirstByDoctorCodeOrderByCreatedAtDesc(d.code)
                    .map(r -> new ReviewView(r.authorAlias, r.rating, r.comment)).orElse(null);
            Set<Instant> taken = taken(d.code, upcoming);
            Instant next = upcoming.stream().filter(s -> !taken.contains(s)).findFirst().orElse(null);
            return new DoctorView(d.code, d.name, d.title, d.specialty, d.experience, d.fee, pass ? 0 : d.fee,
                    Math.round(d.ratingAverage * 10) / 10.0, d.totalReviews, d.badge, d.avatar, next, rv);
        }).toList();
    }

    @GetMapping("/{code}/slots")
    public List<Map<String, Object>> slots(@PathVariable String code) {
        doctors.findByCode(code).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clinician not found"));
        List<Instant> upcoming = slots.upcoming();
        Set<Instant> taken = taken(code, upcoming);
        List<Map<String, Object>> out = new ArrayList<>();
        for (Instant s : upcoming) out.add(Map.of("slot", s, "taken", taken.contains(s)));
        return out;
    }

    @PostMapping("/{code}/reviews")
    @Transactional
    public Map<String, Object> review(@PathVariable String code, @Valid @RequestBody ReviewReq r) {
        Account me = user.require();
        Doctor d = doctors.findByCodeForUpdate(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clinician not found"));
        if (requireBooking && !bookings.existsByPatientAliasAndDoctorCodeAndStatusNot(me.alias, code, "CANCELLED")) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Book a session with this clinician before leaving a review");
        }
        DoctorReview rv = new DoctorReview();
        rv.doctorCode = code;
        rv.authorAlias = me.alias;
        rv.rating = r.rating();
        rv.comment = r.comment().trim();
        reviews.save(rv);
        double total = d.ratingAverage * d.totalReviews + r.rating();
        d.totalReviews += 1;
        d.ratingAverage = total / d.totalReviews;
        doctors.save(d);
        return Map.of("ratingAverage", Math.round(d.ratingAverage * 10) / 10.0, "totalReviews", d.totalReviews);
    }

    private Set<Instant> taken(String code, List<Instant> upcoming) {
        if (upcoming.isEmpty()) return Set.of();
        return bookings.findByDoctorCodeAndSlotBetween(code, upcoming.get(0), upcoming.get(upcoming.size() - 1)).stream()
                .filter(b -> !"CANCELLED".equals(b.status)).map(b -> b.slot).collect(Collectors.toSet());
    }
}
