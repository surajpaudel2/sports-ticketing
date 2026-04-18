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
 * <p><strong>Cross-service contract:</strong> renaming any field here requires a
 * matching update in the Notification Service consumer.</p>
 */
@Builder
public record BookingResultEvent(

        // Primary identifier — Notification Service uses this for idempotency checks
        Long bookingId,

        // Used by Notification Service to look up the user's contact preferences
        Long userId,

        // Final outcome — CONFIRMED or FAILED (CANCELLED uses a separate flow)
        BookingStatus bookingStatus,

        // Destination for the confirmation/failure email — may be the user's account
        // email or an override supplied in the original booking request
        String recipientEmail,

        // Populated when bookingStatus=FAILED — forwarded to the user in the notification
        // so they know why the booking did not go through (e.g. "Your card was declined.")
        String reason

) {}