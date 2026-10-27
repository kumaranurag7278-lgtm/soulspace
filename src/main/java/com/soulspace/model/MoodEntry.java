package com.soulspace.model;

import jakarta.persistence.*;
import java.time.Instant;
import java.time.LocalDate;

@Entity
@Table(name = "mood_entries", uniqueConstraints = @UniqueConstraint(columnNames = {"account_alias", "entry_date"}))
public class MoodEntry {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "account_alias", nullable = false, length = 40)
    public String accountAlias;
    @Column(nullable = false, length = 20)
    public String mood;
    public int score;
    @Column(name = "entry_date", nullable = false)
    public LocalDate entryDate;
    public Instant createdAt = Instant.now();
}
