package com.ticketing.payment.dto.event;

import lombok.Builder;

/**
 * Outbound RabbitMQ event published by Payment Service after Stripe confirms a payment.
 *
 * <p>Published on routing key {@code sports.ticketing.payment.success} after verifying
 * the Stripe webhook signature for a {@code payment_intent.succeeded} event.
 * Consumed by {@code BookingEventListener#handlePaymentSuccess} in Booking Service.</p>
 *
 * <p><strong>Cross-service contract:</strong> the field names here must match the
 * {@code PaymentSuccessEvent} record in booking-service, which deserializes this payload
 * via RabbitMQ. Any rename requires a matching update in booking-service.</p>
 */
@Builder
public record PaymentSuccessEvent(

        // Identifies which booking this payment belongs to — extracted from the
        // PaymentIntent metadata that was set when the intent was created in PaymentServiceImpl
        Long bookingId,

        // Stripe PaymentIntent ID (e.g. "pi_3OqX...") — stored on the Booking entity
        // by booking-service for future refund capability (e.g. cancellation flow)
        String stripePaymentIntentId,

        // Amount charged in the smallest currency unit (e.g. pence, cents) —
        // carried for audit/logging purposes; not used in booking logic
        long amount

) {}
