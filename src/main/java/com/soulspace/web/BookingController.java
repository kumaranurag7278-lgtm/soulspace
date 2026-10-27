package com.soulspace.web;

import com.soulspace.model.Account;
import com.soulspace.model.ConsultationBooking;
import com.soulspace.model.Doctor;
import com.soulspace.repo.Repositories.BookingRepository;
import com.soulspace.repo.Repositories.DoctorRepository;
import com.soulspace.service.Codes;
import com.soulspace.service.Payments;
import com.soulspace.service.SlotService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@RestController
@RequestMapping("/api/bookings")
public class BookingController {

    public record BookReq(@NotBlank(message = "Choose a clinician") String doctorCode,
                          @NotNull(message = "Choose a time slot") Instant slot,
                          String paymentMethod, String upiId) {}

    private final BookingRepository bookings;
    private final DoctorRepository doctors;
    private final SlotService slots;
    private final SessionUser user;

    public BookingController(BookingRepository bookings, DoctorRepository doctors, SlotService slots, SessionUser user) {
        this.bookings = bookings;
        this.doctors = doctors;
        this.slots = slots;
        this.user = user;
    }

    @PostMapping
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> book(@Valid @RequestBody BookReq r) {
        Account me = user.require();
        Doctor d = doctors.findByCodeForUpdate(r.doctorCode())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clinician not found"));
        Instant slot = r.slot().truncatedTo(ChronoUnit.SECONDS);
        if (!slots.upcoming().contains(slot)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "That time is no longer available");
        }
        boolean taken = bookings.findByDoctorCodeAndSlotBetween(d.code, slot, slot).stream()
                .anyMatch(b -> !"CANCELLED".equals(b.status));
        if (taken) throw new ResponseStatusException(HttpStatus.CONFLICT, "Someone just took that slot, please pick another");

        boolean pass = me.hasActivePass();
        String method = pass ? "SOULPASS" : Payments.normalize(r.paymentMethod(), r.upiId());

        ConsultationBooking b = new ConsultationBooking();
        String code;
        do {
            code = "SS-APT-" + Codes.random(4);
        } while (bookings.existsByAppointmentCode(code));
        b.appointmentCode = code;
        b.doctorCode = d.code;
        b.doctorName = d.name;
        b.patientAlias = me.alias;
        b.slot = slot;
        b.coveredByPremium = pass;
        b.feePaid = pass ? 0 : d.fee;
        b.paymentMethod = method;
        b.status = "CONFIRMED";
        try {
            bookings.saveAndFlush(b);
        } catch (DataIntegrityViolationException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Someone just took that slot, please pick another");
        }
        return view(b);
    }

    @PostMapping("/{code}/cancel")
    @org.springframework.transaction.annotation.Transactional
    public Map<String, Object> cancel(@PathVariable String code) {
        Account me = user.require();
        ConsultationBooking b = bookings.findByAppointmentCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        if (!b.patientAlias.equalsIgnoreCase(me.alias)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found");
        }
        b.status = "CANCELLED";
        bookings.save(b);
        return Map.of("ok", true, "appointmentCode", code, "status", "CANCELLED");
    }

    @GetMapping("/mine")
    public List<Map<String, Object>> mine() {
        Account me = user.require();
        return bookings.findByPatientAliasOrderBySlotAsc(me.alias).stream()
                .filter(b -> !"CANCELLED".equals(b.status)).map(this::view).toList();
    }

    @GetMapping("/{code}")
    public Map<String, Object> one(@PathVariable String code) {
        Account me = user.require();
        ConsultationBooking b = bookings.findByAppointmentCode(code)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found"));
        if (!b.patientAlias.equalsIgnoreCase(me.alias)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Appointment not found");
        }
        return view(b);
    }

    private Map<String, Object> view(ConsultationBooking b) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("appointmentCode", b.appointmentCode);
        m.put("doctorCode", b.doctorCode);
        m.put("doctorName", b.doctorName);
        m.put("slot", b.slot);
        m.put("feePaid", b.feePaid);
        m.put("coveredByPremium", b.coveredByPremium);
        m.put("paymentMethod", b.paymentMethod);
        m.put("status", b.status);
        return m;
    }
}
