package com.ticketing.booking.service;

import com.ticketing.booking.entity.Booking;

/**
 * Handles all outbound RabbitMQ event publishing for Booking Service.
 *
 * <p>Publishes booking outcome events to the Notification Service after every
 * booking attempt, regardless of whether it succeeded or failed. Routing key
 * differs by outcome so consumers can subscribe selectively.</p>
 *
 * <p>Intentionally contains no business logic — each method is a pure publish
 * operation that builds the event payload and sends it to the exchange.</p>
 */
public interface BookingEventPublisher {

    /**
     * Publishes a {@code BookingResultEvent} on the confirmed routing key
     * ({@code sports.ticketing.booking.confirmed}) after a booking is fully confirmed.
     *
     * <p>Called from {@code BookingEventListener#handlePaymentSuccess} after seats
     * have been successfully deducted and the booking transitioned to CONFIRMED.</p>
     *
     * @param booking the CONFIRMED booking to notify about
     */
    void publishBookingConfirmed(Booking booking);

    /**
     * Publishes a {@code BookingResultEvent} on the failed routing key
     * ({@code sports.ticketing.booking.failed}) when a booking fails for any reason.
     *
     * <p>Called from two places:</p>
     * <ul>
     *   <li>{@code BookingEventListener#handlePaymentSuccess} — when the post-payment
     *       seat check fails (ultra-rare race condition) or seat deduction throws.</li>
     *   <li>{@code BookingEventListener#handlePaymentFailed} — when Stripe reports
     *       a payment failure.</li>
     * </ul>
     *
     * @param booking the FAILED booking to notify about
     */
    void publishBookingFailed(Booking booking);
}