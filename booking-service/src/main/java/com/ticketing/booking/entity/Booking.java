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

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId; // Reference to User Service — no FK constraint, microservices own their data

    @Column(nullable = false)
    private Long eventId; // Reference to Event Service — no FK constraint, microservices own their data

    private String eventName; // Snapshot from Event Service at booking time — for notifications/receipts

    // Populated after Stripe PaymentIntent is created — null if event validation failed before we reached Stripe.
    // Stored as a string because Stripe IDs are prefixed strings (e.g. "pi_3OqX..."), not numeric.
    private String stripePaymentIntentId;

    @Column(nullable = false)
    private int seatsBooked; // Snapshot at booking time — needed for compensation event on payment failure

    @Column(nullable = false)
    private double pricePerSeat; // Snapshot at booking time — event price may change later, we lock in the price at booking moment

    @Column(nullable = false)
    private String paymentMethod;

    private String recipientEmail; // Programmatically populated from request or fallback to user account email

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private BookingStatus bookingStatus;

    private String failureReason; // Populated only when status=FAILED — system-generated reason (e.g. payment failed, event not found)

    private String cancellationReason; // Populated only when status=CANCELLED — user-driven reason

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
