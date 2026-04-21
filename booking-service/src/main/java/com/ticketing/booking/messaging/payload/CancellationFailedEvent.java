package com.ticketing.booking.messaging.payload;

/**
 * Published by Booking Service to Admin/User Service when seat restoration
 * or Stripe refund fails during cancellation approval processing.
 *
 * <p>Signals that manual intervention is required. Admin is notified
 * with the specific failure reason so they know what step failed.</p>
 *
 * <p>Two possible failure reasons:</p>
 * <ul>
 *   <li>"Seat restoration failed — manual intervention needed"</li>
 *   <li>"Refund failed, seats already restored — manual refund needed"</li>
 * </ul>
 */
public record CancellationFailedEvent(
        Long bookingId,
        String failureReason
) {}