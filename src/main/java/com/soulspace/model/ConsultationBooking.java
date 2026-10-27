package com.soulspace.model;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "consultation_bookings", uniqueConstraints = {
        @UniqueConstraint(columnNames = "appointment_code")})
public class ConsultationBooking {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    @Column(name = "appointment_code", nullable = false, length = 20)
    public String appointmentCode;
    @Column(name = "doctor_code", nullable = false, length = 20)
    public String doctorCode;
    public String doctorName;
    @Column(length = 40)
    public String patientAlias;
    @Column(nullable = false)
    public Instant slot;
    public int feePaid;
    public boolean coveredByPremium;
    @Column(length = 20)
    public String paymentMethod;
    @Column(length = 20)
    public String status = "CONFIRMED";
    public Instant createdAt = Instant.now();
}
