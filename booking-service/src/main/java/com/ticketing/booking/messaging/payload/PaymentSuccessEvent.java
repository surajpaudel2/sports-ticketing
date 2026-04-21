package com.ticketing.booking.messaging.payload;

import lombok.Builder;

/**
 * Inbound RabbitMQ event consumed from the Payment Service when a Stripe payment succeeds.
 *
 * <p>Published by Payment Service on routing key
 * {@code sports.ticketing.payment.success} after verifying the Stripe webhook signature
 * for a {@code payment_intent.succeeded} event. Consumed by
 * {@code BookingEventListener#handlePaymentSuccess}.</p>
 *
 * <p>On receipt, the Booking Service acquires a distributed lock on the eventId,
 * re-checks seat availability (last line of defence against race conditions),
 * deducts seats via the Event Service, and transitions the booking to
 * {@code CONFIRMED} — or issues a Stripe refund and transitions to {@code FAILED}
 * if seats are no longer available.</p>
 */
@Builder
public record PaymentSuccessEvent(

        // Identifies which booking this payment belongs to — extracted from the
        // PaymentIntent metadata that was set when the intent was created
        Long bookingId,

        // Stripe PaymentIntent ID (e.g. "pi_3OqX...") — stored on the Booking entity
        // and used to issue a refund via stripe.refunds.create() if the final
        // seat-availability check fails after payment
        String stripePaymentIntentId,

        // Amount charged in the smallest currency unit (e.g. pence, cents) —
        // carried for audit/logging purposes; not used in booking logic
        long amount

) {}