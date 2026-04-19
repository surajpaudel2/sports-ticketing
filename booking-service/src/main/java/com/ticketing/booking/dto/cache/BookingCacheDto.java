package com.ticketing.booking.dto.cache;

/**
 * Snapshot of a booking stored in Redis after a Stripe PaymentIntent is successfully created.
 *
 * <p>Written by {@code BookingServiceImpl} at the end of {@code initiateBooking}, with a
 * 30-minute TTL to cover the Stripe payment window. Consumed by:</p>
 * <ul>
 *   <li>{@code BookingEventListener} — to skip the DB {@code findById} on payment success/failure.</li>
 *   <li>{@code BookingEventPublisherImpl} — to build {@code BookingResultEvent} for Notification Service.</li>
 * </ul>
 *
 * <p>All fields are a point-in-time snapshot — they reflect the booking at initiation and
 * are not updated if the DB record changes (which it won't for these fields).</p>
 */
public record BookingCacheDto(

        Long bookingId,
        Long userId,
        Long eventId,
        String eventName,
        int seatsBooked,
        double pricePerSeat,
        String recipientEmail,
        String stripePaymentIntentId

) {}
