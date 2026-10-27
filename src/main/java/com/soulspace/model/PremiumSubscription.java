package com.soulspace.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "premium_subscriptions", uniqueConstraints = @UniqueConstraint(columnNames = "subscription_code"))
public class PremiumSubscription {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "subscription_code", nullable = false, length = 30)
    public String subscriptionCode;
    @Column(length = 40)
    public String userAlias;
    @Column(length = 160)
    public String userEmail;
    public int amountPaid;
    public boolean active = true;
    public Instant startDate = Instant.now();
    public Instant expiresAt;
    @Column(length = 20)
    public String paymentMethod;
}
