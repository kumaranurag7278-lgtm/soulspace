package com.soulspace.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "accounts", uniqueConstraints = {
        @UniqueConstraint(columnNames = "email"),
        @UniqueConstraint(columnNames = "alias_name")})
public class Account {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false, length = 160)
    public String email;
    @Column(name = "alias_name", nullable = false, length = 40)
    public String alias;
    @Column(nullable = false)
    public String passwordHash;
    @Column(name = "user_role", nullable = false, length = 20)
    public String role = "USER";
    public Integer age;
    @Column(length = 20)
    public String verificationStatus = "NONE";
    public boolean premiumPass;
    public Instant premiumExpiry;
    public Instant createdAt = Instant.now();

    public boolean hasActivePass() {
        return premiumPass && premiumExpiry != null && premiumExpiry.isAfter(Instant.now());
    }
}
