package com.suraj.sport.paymentservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

/**
 * Represents a single payment gateway transaction attempt.
 *
 * Each time a payment is attempted through the gateway (Stripe, Razorpay etc),
 * a new Transaction record is created. This allows tracking of:
 *   - How many times a user attempted payment
 *   - Which attempts failed and why
 *   - The gateway's response for each attempt
 *   - The final successful transaction ID
 *
 * Relationships:
 *   - Many Transactions → One Payment (each attempt belongs to one payment)
 *
 * Note: This is an internal audit trail — not exposed directly to end users.
 * Users only see the payment status, not individual transaction attempts.
 */
@Entity
@Table(name = "transactions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // The payment this transaction attempt belongs to
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    // Transaction ID returned by the payment gateway — null if attempt failed before gateway response
    private String gatewayTransactionId;

    // Amount attempted in this transaction
    @Column(nullable = false)
    private double amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TransactionStatus transactionStatus;

    // Raw response from payment gateway — useful for debugging failed transactions
    // TODO: Consider encrypting this field for security compliance
    @Column(columnDefinition = "TEXT")
    private String gatewayResponse;

    // Failure reason if transaction failed — null if successful
    private String failureReason;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;
}