package com.ticketing.booking.messaging.payload;

/**
 * Published by Booking Service to Notification Service
 * immediately after a CONFIRMED cancellation request is submitted.
 *
 * <p>Acknowledgement to the user — "we received your request,
 * our team will review and process your refund shortly."</p>
 */
public record CancellationReceivedEvent(

        Long bookingId,
        Long userId,
        String recipientEmail,
        String cancellationReason
) {}