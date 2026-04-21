package com.ticketing.booking.messaging.payload;

/**
 * Published by Admin/User Service when a cancellation request is rejected.
 * Consumed by Booking Service — reverts booking to CONFIRMED + notifies user.
 *
 * <p>Seats are never touched during admin review —
 * reverting to CONFIRMED requires no compensation.</p>
 */
public record CancellationRejectedEvent(

        Long bookingId,

        String rejectionReason,
        // Shown to the user in notification email

        String adminNote
        // Internal only — not shown to user
) {}