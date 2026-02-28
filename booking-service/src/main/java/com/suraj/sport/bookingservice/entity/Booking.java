package com.suraj.sport.bookingservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "bookings")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Booking {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Reference to User Service — no FK constraint, microservices own their data
    @Column(nullable = false)
    private Long userId;

    // Reference to Event Service — no FK constraint, microservices own their data
    @Column(nullable = false)
    private Long eventId;

    // Reference to Payment Service — nullable until payment is confirmed
    private Long paymentId;

    @Column(nullable = false)
    private int seatsBooked;

    // Snapshot of price at booking time — stored because event price may change later
    @Column(nullable = false)
    private double pricePerSeat;

    // Calculated and stored at booking time — seatsBooked * pricePerSeat
    @Column(nullable = false)
    private double totalAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus bookingStatus;

    // Populated when booking is cancelled — useful for auditing and user communication
    private String cancellationReason;

    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    // Populated when soft deleted — null if not deleted
    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}