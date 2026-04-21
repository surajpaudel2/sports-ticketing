package com.ticketing.booking.messaging.payload;

/**
 * Published by Booking Service to Notification Service
 * when admin rejects a cancellation request.
 *
 * <p>Separate from CancellationRejectedEvent (which comes FROM Admin Service)
 * — this goes TO Notification Service to email the user.</p>
 */
public record CancellationRejectedNotificationEvent(

        Long bookingId,
        Long userId,
        String recipientEmail,
        String rejectionReason
) {
}