package com.ticketing.event.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Core event entity representing a sporting event with a fixed seat inventory.
 *
 * <p>{@code availableSeats} is the mutable field — it decrements on each successful
 * {@code checkAndReserve} call and increments on {@code releaseSeats} (when payment
 * fails after reservation). It starts equal to {@code totalSeats} and never goes
 * negative or above {@code totalSeats}.</p>
 *
 * <p><strong>Pessimistic locking:</strong> {@code checkAndReserve} loads this entity via
 * {@code SELECT ... FOR UPDATE}, which places a database row-level exclusive lock on the
 * row for the duration of the transaction. Any concurrent transaction that attempts the
 * same {@code SELECT ... FOR UPDATE} on the same row will block (wait) until the first
 * transaction commits or rolls back. This guarantees that only one booking thread at a time
 * can read, check, and modify {@code availableSeats} — eliminating the need for a version
 * column and making the seat count authoritative at all times.</p>
 */
@Entity
@Table(name = "events")
@Getter @Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    private String description;

    private String venue;

    @Column(nullable = false)
    private LocalDateTime eventDate;

    @Column(nullable = false)
    private int totalSeats;   // Fixed at creation time — never changes after the event is published

    @Column(nullable = false)
    private int availableSeats;   // Decremented on reservation, incremented on release

    @Column(nullable = false)
    private double pricePerSeat;   // Snapshotted by booking-service on checkAndReserve — price locked at booking time

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EventStatus status;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}
