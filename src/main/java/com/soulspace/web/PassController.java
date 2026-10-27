package com.soulspace.web;

import com.soulspace.model.Account;
import com.soulspace.model.PremiumSubscription;
import com.soulspace.repo.Repositories.AccountRepository;
import com.soulspace.repo.Repositories.PremiumSubscriptionRepository;
import com.soulspace.service.Codes;
import com.soulspace.service.Payments;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/pass")
public class PassController {
    public static final int PRICE = 899;

    public record PayReq(String paymentMethod, String upiId) {}

    private final PremiumSubscriptionRepository subs;
    private final AccountRepository accounts;
    private final SessionUser user;

    public PassController(PremiumSubscriptionRepository subs, AccountRepository accounts, SessionUser user) {
        this.subs = subs;
        this.accounts = accounts;
        this.user = user;
    }

    @PostMapping("/purchase")
    public synchronized Map<String, Object> purchase(@RequestBody PayReq r) {
        Account me = user.require();
        if (me.hasActivePass()) throw new ResponseStatusException(HttpStatus.CONFLICT, "Your SoulPass is already active");
        String method = Payments.normalize(r.paymentMethod(), r.upiId());
        String code;
        do {
            code = "PASS-899-" + Codes.random(8);
        } while (subs.existsBySubscriptionCode(code));
        Instant now = Instant.now();
        PremiumSubscription s = new PremiumSubscription();
        s.subscriptionCode = code;
        s.userAlias = me.alias;
        s.userEmail = me.email;
        s.amountPaid = PRICE;
        s.active = true;
        s.startDate = now;
        s.expiresAt = now.plus(30, ChronoUnit.DAYS);
        s.paymentMethod = method;
        subs.save(s);
        me.premiumPass = true;
        me.premiumExpiry = s.expiresAt;
        accounts.save(me);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("subscriptionCode", code);
        m.put("amountPaid", PRICE);
        m.put("expiresAt", s.expiresAt);
        m.put("daysLeft", 30);
        return m;
    }
}
