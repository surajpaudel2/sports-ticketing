package com.suraj.sport.paymentservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Represents a refund record for a payment.
 *
 * Supports both partial and full refunds:
 *   - Full refund: one Refund record with full payment amount
 *   - Partial refund: multiple Refund records each with partial amount
 *     e.g. user booked 3 seats at $100 each, cancels 1 seat → one Refund record for $100
 *
 * Refund flow:
 *   1. Booking Service requests refund on cancellation
 *   2. Payment Service creates Refund record with PENDING status
 *   3. Payment Service calls gateway with refundAmount against original transactionId
 *   4. Gateway processes refund → update status to SUCCESS or FAILED
 *   5. Notify user via Notification Service
 *
 * Note: The gateway does not need to know about your internal booking logic.
 * You simply tell it "refund $X from transaction Y" and it processes it.
 *
 * Relationships:
 *   - Many Refunds → One Payment (one payment can have multiple refunds)
 */
@Entity
@Table(name = "refunds")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Refund {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The payment this refund belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    // Refund transaction ID from payment gateway — null until gateway processes refund
    private String gatewayRefundId;

    // Amount being refunded — can be partial or full
    @Column(nullable = false)
    private double refundAmount;

    // Reason for refund — e.g. "Booking cancelled by user", "Event cancelled by organizer"
    @Column(nullable = false)
    private String refundReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RefundStatus refundStatus;

    // Failure reason if refund failed — null if successful
    private String failureReason;

    // Timestamp when refund was successfully processed by gateway
    // Null until refund succeeds
    private LocalDateTime refundedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}