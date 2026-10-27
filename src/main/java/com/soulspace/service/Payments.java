package com.soulspace.service;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Locale;
import java.util.regex.Pattern;

/** Payment SIMULATION only. No real charge is made and no card data is ever collected. */
public final class Payments {
    private Payments() {}
    private static final Pattern UPI = Pattern.compile("^[A-Za-z0-9._\\-]{2,64}@[A-Za-z]{2,32}$");

    public static String normalize(String method, String upiId) {
        String m = method == null ? "" : method.toUpperCase(Locale.ROOT);
        switch (m) {
            case "UPI":
                if (upiId == null || !UPI.matcher(upiId.trim()).matches()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Enter a valid UPI ID, e.g. name@upi");
                }
                return "UPI";
            case "CARD":
                return "CARD";
            default:
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose UPI or Card as the payment method");
        }
    }
}
