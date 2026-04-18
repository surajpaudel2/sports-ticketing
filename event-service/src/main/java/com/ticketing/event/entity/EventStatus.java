package com.ticketing.event.entity;

/**
 * Represents the lifecycle state of a sporting event.
 *
 * <ul>
 *   <li>{@link #ACTIVE} — The event is open for booking. {@code availableSeats > 0}.</li>
 *   <li>{@link #SOLD_OUT} — All seats have been booked. {@code availableSeats == 0}.
 *       Automatically transitioned when the last seat is reserved via
 *       {@code checkAndReserve}.</li>
 *   <li>{@link #CANCELLED} — The event has been cancelled by the organiser.
 *       No new bookings accepted. Existing bookings should be refunded.</li>
 * </ul>
 */
public enum EventStatus {
    ACTIVE,
    SOLD_OUT,
    CANCELLED
}
