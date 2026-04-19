package com.ticketing.booking.dto.event;

import com.ticketing.booking.entity.BookingStatus;
import lombok.Builder;

/**
 * Outbound RabbitMQ event published by Booking Service after every booking attempt,
 * regardless of outcome (CONFIRMED or FAILED).
 *
 * <p>Published on routing key {@code sports.ticketing.booking.confirmed} or
 * {@code sports.ticketing.booking.failed} depending on the final status.
 * Consumed by Notification Service via the wildcard binding
 * {@code sports.ticketing.booking.*} to send email/SMS to the user.</p>
 *
 * <p>All booking and event details are embedded so the Notification Service can
 * render a complete receipt email without calling back into Booking Service.</p>
 *
 * <p><strong>Cross-service contract:</strong> renaming any field here requires a
 * matching update in the Notification Service consumer.</p>
 */
@Builder
public record BookingResultEvent(

        Long bookingId,
        Long userId,
        BookingStatus bookingStatus,
        String recipientEmail,

        // Event snapshot — embedded so Notification Service needs no inter-service call
        Long eventId,
        String eventName,
        int seatsBooked,
        double pricePerSeat,
        double totalAmount,

        // Populated when bookingStatus=FAILED
        String reason

) {}
