package com.ticketing.booking.entity;

/**
 * Represents the lifecycle states of a booking.
 *
 * <ul>
 *   <li>{@link #PENDING} — The booking record has been created and a Stripe PaymentIntent has been
 *       issued. The frontend is in the process of confirming the payment directly with Stripe.
 *       This state is transient — it resolves to {@code CONFIRMED} or {@code FAILED} once the
 *       Stripe webhook fires and the RabbitMQ listener processes the result.</li>
 *   <li>{@link #CONFIRMED} — Stripe confirmed the payment, seats were deducted from the Event
 *       Service, and the booking is fully complete.</li>
 *   <li>{@link #FAILED} — Either event/seat validation failed before a PaymentIntent was created
 *       (e.g. event not found, insufficient seats), or Stripe reported a payment failure. A
 *       {@code failureReason} is recorded in all cases.</li>
 *   <li>{@link #CANCELLED} — The booking was cancelled by the user after it had been confirmed.
 *       A {@code cancellationReason} is recorded.</li>
 * </ul>
 */
public enum BookingStatus {
    PENDING,
    CONFIRMED,
    FAILED,
    CANCELLED,

    CANCELLATION_REQUESTED,
// CONFIRMED booking — cancellation submitted, awaiting admin review
// Prevents duplicate cancellation requests on same booking

    CANCELLATION_FAILED
// Seat restoration or Stripe refund failed during admin approval
// Requires manual intervention — admin is notified via event
}