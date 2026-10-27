package com.soulspace.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "doctor_reviews", indexes = @Index(columnList = "doctor_code"))
public class DoctorReview {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "doctor_code", nullable = false, length = 20)
    public String doctorCode;
    @Column(length = 40)
    public String authorAlias;
    public int rating;
    @Column(name = "review_text", length = 600)
    public String comment;
    public Instant createdAt = Instant.now();
}
