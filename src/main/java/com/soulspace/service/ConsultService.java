package com.soulspace.service;

import com.soulspace.repo.Repositories.AccountRepository;
import com.soulspace.repo.Repositories.BookingRepository;
import org.springframework.stereotype.Service;

@Service
public class ConsultService {
    private final AccountRepository accounts;
    private final BookingRepository bookings;

    public ConsultService(AccountRepository accounts, BookingRepository bookings) {
        this.accounts = accounts;
        this.bookings = bookings;
    }

    /** Only the patient who holds the appointment may open its private room. */
    public boolean canAccess(String code, String email) {
        if (code == null || email == null) return false;
        return bookings.findByAppointmentCode(code)
                .flatMap(b -> accounts.findByEmailIgnoreCase(email)
                        .map(a -> a.alias.equalsIgnoreCase(b.patientAlias)))
                .orElse(false);
    }
}
