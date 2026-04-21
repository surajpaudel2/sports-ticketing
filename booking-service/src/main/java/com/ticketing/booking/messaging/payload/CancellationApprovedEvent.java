package com.ticketing.booking.messaging.payload;

import com.ticketing.booking.entity.RefundType;
import java.math.BigDecimal;

/**
 * Published by Admin/User Service when a cancellation request is approved.
 * Consumed by Booking Service — triggers seat restoration + refund + status update.
 *
 * <p>refundType and refundAmount reflect the admin's final decision —
 * may differ from the recommendedRefundType/Amount sent in CancellationRequestedEvent
 * if the admin chose to override.</p>
 *
 * <p>If refundAmount == 0 (NO_REFUND) — skip Stripe call entirely.</p>
 */
public record CancellationApprovedEvent(

        Long bookingId,

        int seatsToCancel,
        // How many seats the admin approved for cancellation
        // May be less than originally requested (partial approval)

        RefundType refundType,
        // Admin's final decision — FULL_REFUND / PARTIAL_REFUND / NO_REFUND

        BigDecimal refundAmount,
        // Exact amount to refund in pounds (not smallest unit — Payment Service handles conversion)
        // 0 if refundType = NO_REFUND

        String refundReason,
        // Shown to the user in notification email

        String adminNote
        // Internal only — not shown to user
) {}
