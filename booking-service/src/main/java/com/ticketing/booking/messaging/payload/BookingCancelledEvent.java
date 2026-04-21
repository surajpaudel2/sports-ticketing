package com.ticketing.booking.messaging.payload;

import com.ticketing.booking.entity.RefundType;
import java.math.BigDecimal;

/**
 * Published by Booking Service to Notification Service when a booking
 * is fully cancelled and the outcome is known.
 *
 * <p>Fired in two scenarios:</p>
 * <ul>
 *   <li>PENDING booking cancelled immediately — refundType=NO_REFUND, refundAmount=0</li>
 *   <li>Admin approves CONFIRMED cancellation — refundType and refundAmount reflect admin decision</li>
 * </ul>
 *
 * <p>Notification Service uses refundType to determine email content:</p>
 * <ul>
 *   <li>FULL_REFUND    → "Cancellation approved, full refund of £X initiated"</li>
 *   <li>PARTIAL_REFUND → "Cancellation approved, partial refund of £X initiated"</li>
 *   <li>NO_REFUND      → "Booking cancelled — no refund per cancellation policy"</li>
 * </ul>
 */
public record BookingCancelledEvent(

        Long bookingId,
        Long userId,
        String recipientEmail,
        RefundType refundType,
        BigDecimal refundAmount,
        String refundReason
) {}