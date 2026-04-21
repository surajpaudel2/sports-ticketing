package com.ticketing.booking.messaging.payload;

import lombok.Builder;

/**
 * Inbound RabbitMQ event consumed from the Payment Service when a Stripe payment fails.
 *
 * <p>Published by Payment Service on routing key
 * {@code sports.ticketing.payment.failed} and consumed by
 * {@code BookingEventListener#handlePaymentFailed}.</p>
 *
 * <p>No seat rollback is required on this path — seats are never deducted until
 * {@code payment_intent.succeeded} is confirmed, so a failed payment leaves seat
 * counts in the Event Service untouched.</p>
 */
@Builder
public record PaymentFailedEvent(

        // Identifies which booking this failure belongs to — used to look up the Booking entity
        Long bookingId,

        // Human-readable failure reason from Stripe (e.g. "Your card was declined.")
        // Stored in Booking.failureReason and forwarded to Notification Service
        String reason

) {}