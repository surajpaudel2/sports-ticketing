package com.ticketing.booking.dto.response;

import com.ticketing.booking.entity.BookingStatus;
import com.ticketing.booking.entity.RefundType;

import java.math.BigDecimal;

/**
 * Response payload for cancel booking.
 *
 * <p>{@code recommendedRefundType} and {@code recommendedRefundAmount} are
 * only populated for CONFIRMED cancellations going to admin review.
 * Both are null for PENDING cancellations (no money was moved).</p>
 */
public record CancelBookingResponse(

        Long bookingId,
        BookingStatus status,
        String message,

        RefundType recommendedRefundType,
        // null for PENDING cancellations

        BigDecimal recommendedRefundAmount
        // null for PENDING cancellations
) {}