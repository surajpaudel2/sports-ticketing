package com.suraj.sport.paymentservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Reference to Booking Service — no FK constraint, microservices own their data
    @Column(nullable = false)
    private Long bookingId;

    // Reference to Event Service — stored for auditing and reporting purposes
    @Column(nullable = false)
    private Long eventId;

    // Reference to User Service — who made the payment
    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private double amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus paymentStatus;

    // Payment method used — e.g. CREDIT_CARD, DEBIT_CARD, UPI, NET_BANKING
    // TODO: Convert to enum once all supported payment methods are finalized
    private String paymentMethod;

    // Transaction ID from payment gateway (Stripe, Razorpay etc) — null until payment processed
    private String transactionId;

    // Receipt URL generated after successful payment — null until payment succeeds
    private String receiptUrl;

    // Refund transaction reference — null until refund is processed
    private String refundId;

    // Reason for refund — null if not refunded
    private String refundReason;

    // Timestamp when refund was processed — null if not refunded
    private LocalDateTime refundedAt;

    @Builder.Default
    @Column(name = "is_deleted", nullable = false)
    private boolean isDeleted = false;

    private LocalDateTime deletedAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}