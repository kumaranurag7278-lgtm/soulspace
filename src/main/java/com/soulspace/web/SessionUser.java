package com.soulspace.web;

import com.soulspace.model.Account;
import com.soulspace.repo.Repositories.AccountRepository;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Optional;

@Component
public class SessionUser {
    private final AccountRepository accounts;

    public SessionUser(AccountRepository accounts) {
        this.accounts = accounts;
    }

    public Optional<Account> current() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated() || a instanceof AnonymousAuthenticationToken) return Optional.empty();
        return accounts.findByEmailIgnoreCase(a.getName());
    }

    public Account require() {
        return current().orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Please sign in first"));
    }
}
