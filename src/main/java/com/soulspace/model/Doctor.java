package com.soulspace.model;

import jakarta.persistence.*;

@Entity
@Table(name = "doctors", uniqueConstraints = @UniqueConstraint(columnNames = "code"))
public class Doctor {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(nullable = false, length = 20)
    public String code;
    public String name;
    public String title;
    public String specialty;
    public int experience;
    public int fee;
    public double ratingAverage;
    public int totalReviews;
    public String badge;
    public String avatar;
    public String nextSlot;
}
