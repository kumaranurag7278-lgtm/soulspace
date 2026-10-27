package com.soulspace.web;

import com.soulspace.model.Account;
import com.soulspace.model.PremiumSubscription;
import com.soulspace.repo.Repositories.AccountRepository;
import com.soulspace.repo.Repositories.PremiumSubscriptionRepository;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api")
public class AuthController {

    public record RegisterReq(
            @NotBlank(message = "Email is required") @Email(message = "Enter a valid email") @Size(max = 160) String email,
            @NotBlank(message = "Pick an alias") @Size(min = 3, max = 24, message = "Alias must be 3 to 24 characters")
            @Pattern(regexp = "[A-Za-z0-9_\\- ]+", message = "Alias may use letters, numbers, spaces, - and _") String alias,
            @NotBlank(message = "Password is required") @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password,
            @NotNull(message = "Age is required") @Min(value = 13, message = "You must be at least 13") @Max(value = 120, message = "Enter a valid age") Integer age,
            String role) {}

    public record LoginReq(@NotBlank(message = "Email is required") String email,
                    @NotBlank(message = "Password is required") String password) {}

    private static final int MAX_FAILS = 8;
    private static final long WINDOW_MS = 10 * 60 * 1000L;

    private final AccountRepository accounts;
    private final PremiumSubscriptionRepository subs;
    private final PasswordEncoder encoder;
    private final AuthenticationManager authManager;
    private final SessionUser user;
    private final com.soulspace.service.RateLimiter limiter;
    private final SecurityContextRepository contextRepo = new HttpSessionSecurityContextRepository();
    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    public AuthController(AccountRepository accounts, PremiumSubscriptionRepository subs, PasswordEncoder encoder,
                          AuthenticationManager authManager, SessionUser user, com.soulspace.service.RateLimiter limiter) {
        this.accounts = accounts;
        this.subs = subs;
        this.encoder = encoder;
        this.authManager = authManager;
        this.user = user;
        this.limiter = limiter;
    }

    @GetMapping("/csrf")
    public Map<String, String> csrf(CsrfToken token) {
        return Map.of("header", token.getHeaderName(), "token", token.getToken());
    }

    @GetMapping("/me")
    public Map<String, Object> me() {
        return view(user.current().orElse(null));
    }

    @PostMapping("/auth/register")
    public Map<String, Object> register(@Valid @RequestBody RegisterReq r, HttpServletRequest req, HttpServletResponse res) {
        String ip = req.getRemoteAddr();
        if (!limiter.allow("auth_register", ip, 5, 15 * 60_000L)) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many registration attempts. Please try again later.");
        }
        String email = r.email().trim().toLowerCase(Locale.ROOT);
        String alias = r.alias().trim();
        if (accounts.existsByEmailIgnoreCase(email)) throw new ResponseStatusException(HttpStatus.CONFLICT, "An account with this email already exists");
        if (accounts.existsByAliasIgnoreCase(alias)) throw new ResponseStatusException(HttpStatus.CONFLICT, "That alias is taken, try another");
        Account a = new Account();
        a.email = email;
        a.alias = alias;
        a.passwordHash = encoder.encode(r.password());
        a.age = r.age();
        boolean pro = "PROFESSIONAL".equalsIgnoreCase(r.role());
        a.role = pro ? "PROFESSIONAL" : "USER";
        a.verificationStatus = pro ? "PENDING" : "NONE";
        accounts.save(a);
        signIn(email, r.password(), req, res);
        return view(a);
    }

    @PostMapping("/auth/login")
    public Map<String, Object> login(@Valid @RequestBody LoginReq r, HttpServletRequest req, HttpServletResponse res) {
        String email = r.email().trim().toLowerCase(Locale.ROOT);
        if (tooManyFailures(email)) throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Please wait a few minutes.");
        try {
            signIn(email, r.password(), req, res);
        } catch (AuthenticationException e) {
            recordFailure(email);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid email or password");
        }
        failures.remove(email);
        return view(user.current().orElse(null));
    }

    @PostMapping("/auth/logout")
    public Map<String, Object> logout(HttpServletRequest req, HttpServletResponse res) {
        new SecurityContextLogoutHandler().logout(req, res, SecurityContextHolder.getContext().getAuthentication());
        return Map.of("authenticated", false);
    }

    private void signIn(String email, String rawPassword, HttpServletRequest req, HttpServletResponse res) {
        Authentication auth = authManager.authenticate(new UsernamePasswordAuthenticationToken(email, rawPassword));
        if (req.getSession(false) != null) req.changeSessionId();
        SecurityContext ctx = SecurityContextHolder.createEmptyContext();
        ctx.setAuthentication(auth);
        SecurityContextHolder.setContext(ctx);
        contextRepo.saveContext(ctx, req, res);
    }

    private boolean tooManyFailures(String email) {
        Deque<Long> d = failures.get(email);
        if (d == null) return false;
        synchronized (d) {
            long cutoff = System.currentTimeMillis() - WINDOW_MS;
            while (!d.isEmpty() && d.peekFirst() < cutoff) d.pollFirst();
            return d.size() >= MAX_FAILS;
        }
    }

    private void recordFailure(String email) {
        Deque<Long> d = failures.computeIfAbsent(email, k -> new ArrayDeque<>());
        synchronized (d) { d.addLast(System.currentTimeMillis()); }
    }

    private Map<String, Object> view(Account a) {
        Map<String, Object> m = new LinkedHashMap<>();
        if (a == null) {
            m.put("authenticated", false);
            return m;
        }
        boolean pass = a.hasActivePass();
        m.put("authenticated", true);
        m.put("alias", a.alias);
        m.put("email", a.email);
        m.put("role", a.role);
        m.put("verificationStatus", a.verificationStatus);
        m.put("passActive", pass);
        if (pass) {
            long secs = a.premiumExpiry.getEpochSecond() - Instant.now().getEpochSecond();
            m.put("passDaysLeft", (long) Math.ceil(secs / 86400.0));
            m.put("passExpiry", a.premiumExpiry);
            subs.findFirstByUserEmailIgnoreCaseAndActiveTrueOrderByExpiresAtDesc(a.email)
                    .map(s -> s.subscriptionCode).ifPresent(c -> m.put("passCode", c));
        }
        return m;
    }
}
