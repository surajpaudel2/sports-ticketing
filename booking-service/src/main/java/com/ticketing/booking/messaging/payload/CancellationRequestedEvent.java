package com.ticketing.booking.messaging.payload;

import com.ticketing.booking.entity.RefundType;
import java.math.BigDecimal;

/**
 * Published by Booking Service to Admin/User Service
 * when a CONFIRMED booking cancellation is requested.
 *
 * <p>Includes the system-calculated refund recommendation.
 * Admin sees this and can override before approving.</p>
 */
public record CancellationRequestedEvent(

        Long bookingId,
        Long userId,
        Long eventId,

        int seatsToCancel,
        int activeSeatCount,

        BigDecimal pricePerSeat,
        BigDecimal totalAmount,
        // seatsToCancel * pricePerSeat — for admin display

        String cancellationReason,
        // User-provided reason

        String stripePaymentIntentId,
        // Admin needs this to process manual refund if automation fails

        long hoursUntilEvent,
        // Pre-calculated at request time — admin sees context

        RefundType recommendedRefundType,
        BigDecimal recommendedRefundAmount
        // System recommendation — admin can override
) {}