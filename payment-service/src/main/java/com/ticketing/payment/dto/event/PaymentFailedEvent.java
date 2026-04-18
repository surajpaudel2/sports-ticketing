package com.ticketing.payment.dto.event;

import lombok.Builder;

/**
 * Outbound RabbitMQ event published by Payment Service after Stripe reports a payment failure.
 *
 * <p>Published on routing key {@code sports.ticketing.payment.failed} and consumed by
 * {@code BookingEventListener#handlePaymentFailed} in Booking Service.</p>
 *
 * <p><strong>Cross-service contract:</strong> the field names here must match the
 * {@code PaymentFailedEvent} record in booking-service, which deserializes this payload
 * via RabbitMQ. Any rename requires a matching update in booking-service.</p>
 */
@Builder
public record PaymentFailedEvent(

        // Identifies which booking this failure belongs to — used by booking-service
        // to look up the Booking entity and release reserved seats
        Long bookingId,

        // Human-readable failure reason from Stripe (e.g. "Your card was declined.")
        // Stored in Booking.failureReason and forwarded to Notification Service
        String reason

) {}
