package com.ticketing.payment.entity;

import com.ticketing.payment.schedular.OutboxEventSchedular;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

/**
 * Persistent record of an outbound domain event waiting to be published to RabbitMQ.
 *
 * <p>Part of the <em>Transactional Outbox Pattern</em>: instead of publishing directly to
 * RabbitMQ inside a Stripe webhook handler (which risks losing the event if the broker is
 * unavailable), the service writes an {@code OutboxEvent} row in the same database
 * transaction as any other state change. A background poller
 * ({@link OutboxEventSchedular}) then reads {@code PENDING} rows
 * and delivers them to RabbitMQ, retrying on failure.</p>
 *
 * <p><strong>Event lifecycle:</strong> {@code PENDING → PUBLISHED} on success,
 * {@code PENDING → FAILED} after {@code retryCount} reaches the poller's configured limit.</p>
 */
@Entity
@Table(name = "outbox_events")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OutboxEvent {

    /**
     * Surrogate primary key — auto-generated UUID, unique per event row.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Stripe event type string (e.g. {@code payment_intent.succeeded}) used by the
     * {@link OutboxEventSchedular} to determine which RabbitMQ
     * publish method to call.
     */
    @Column(nullable = false)
    private String eventType;

    /**
     * JSON-serialized event DTO ({@code PaymentSuccessEvent} or {@code PaymentFailedEvent}).
     * Stored as {@code TEXT} to accommodate arbitrarily-sized JSON payloads.
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String payload;

    /**
     * Current lifecycle state of this outbox event.
     * Stored as its string name (e.g. {@code "PENDING"}) for readability in the database.
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OutboxStatus status;

    /**
     * Number of times the poller has attempted (and failed) to publish this event.
     * Starts at {@code 0}; incremented on each failed delivery attempt.
     */
    @Column(nullable = false)
    private int retryCount;

    /**
     * Timestamp at which this event row was created — set once at insert time.
     */
    @Column(nullable = false)
    private Instant createdAt;

    /**
     * Timestamp at which the event was successfully published to RabbitMQ.
     * {@code null} until the event transitions to {@code PUBLISHED}.
     */
    private Instant publishedAt;
}
