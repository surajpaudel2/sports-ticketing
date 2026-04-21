package com.ticketing.booking.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Core booking entity representing a user's reservation for an event.
 *
 * <p>{@code userId} and {@code eventId} are stored as plain {@code Long} references — not JPA
 * foreign keys. This is intentional: in a microservices architecture each service owns its own
 * data, so referential integrity is enforced at the application level rather than at the database
 * level.</p>
 *
 * <p>{@code stripePaymentIntentId} is a nullable {@code String} populated after the Stripe
 * PaymentIntent is created. It is null when event validation fails before Stripe is reached.</p>
 */
@Entity
@Table(name = "bookings")
@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Booking {

    // ==========================================
    // 1. PRIMARY KEY & REFERENCES
    // ==========================================

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId; // Reference to User Service — no FK constraint, microservices own their data

    @Column(nullable = false)
    private Long eventId; // Reference to Event Service — no FK constraint, microservices own their data

    // ==========================================
    // 2. CORE DOMAIN DATA
    // ==========================================

    @Column(nullable = false)
    private LocalDateTime eventDate;
// Snapshot at booking time — for audit trail and display purposes ONLY
// NEVER used for refund policy calculation — live Feign call used instead
// (event may be rescheduled after booking — snapshot would be stale for money decisions)
// Must be set in createPendingBooking() from Event Service response

    @Column(nullable = false)
    private int seatsBooked; // Snapshot at booking time — needed for compensation event on payment failure

    @Column(nullable = false)
    private int activeSeatCount;
// Starts equal to seatsBooked at booking creation time
// Decreases on each partial cancellation
// When activeSeatCount == 0 → booking status = CANCELLED
// Stays CONFIRMED if activeSeatCount > 0 after partial cancel
// Must be set in createPendingBooking() — activeSeatCount = seatsBooked

    @Column(nullable = false)
    private double pricePerSeat; // Snapshot at booking time — event price may change later, we lock in the price at booking moment

    // ==========================================
    // 3. PAYMENT & CONTACT INFO
    // ==========================================

    private String recipientEmail; // Programmatically populated from request or fallback to user account email

    @Column(nullable = false)
    private String paymentMethod;

    // Populated after Stripe PaymentIntent is created — null if event validation failed before we reached Stripe.
    // Stored as a string because Stripe IDs are prefixed strings (e.g. "pi_3OqX..."), not numeric.
    private String stripePaymentIntentId;

    // ==========================================
    // 4. STATUS & WORKFLOW
    // ==========================================

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus bookingStatus;

    private String failureReason; // Populated only when status=FAILED — system-generated reason (e.g. payment failed, event not found)

    private String cancellationReason; // Populated only when status=CANCELLED — user-driven reason

    // ==========================================
    // 5. SYSTEM FLAGS
    // ==========================================

    @Column(nullable = false)
    @Builder.Default
    private boolean seatsReleased = false;
// Set to true by FailedBookingSeatReleaseScheduler after successful seat release
// Prevents double-release on scheduler retry

    @Column(nullable = false)
    @Builder.Default
    private boolean reminderSent = false;
// Set to true after payment reminder email is sent
// Prevents duplicate reminder emails on scheduler retry

    // ==========================================
    // 6. AUDIT & METADATA
    // ==========================================

    @Version
    private Long version;
// Optimistic locking — detects concurrent modification between read and write
// Prevents stale eventDate being used for refund calculation if a reschedule
// happens between cancelBooking() read and save
// Throws OptimisticLockException on conflict → catch and retry with fresh read

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}