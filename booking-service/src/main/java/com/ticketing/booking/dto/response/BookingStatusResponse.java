package com.ticketing.booking.dto.response;

import com.ticketing.booking.entity.BookingStatus;
import lombok.Builder;

/**
 * Response returned from {@code GET /api/v1/bookings/{bookingId}/status}.
 *
 * <p>Used by the frontend to poll for the final booking outcome after
 * {@code stripe.confirmPayment()} completes on the client side. The frontend
 * polls every 2 seconds until {@code bookingStatus} transitions out of
 * {@code PENDING} (max ~15 seconds / ~7 attempts before showing a timeout message).</p>
 *
 * <p>Intentionally minimal — exposes only what the frontend needs to render the
 * result screen. Full booking details are not included to avoid over-fetching.</p>
 *
 * <p><strong>Security note:</strong> the controller must verify that the requesting
 * user owns this bookingId before returning a response.</p>
 */
@Builder
public record BookingStatusResponse(

        // Echo of the requested bookingId — lets the frontend correlate responses
        // when polling multiple bookings concurrently (uncommon but possible)
        Long bookingId,

        // Current lifecycle state — frontend branches on CONFIRMED vs FAILED vs PENDING
        // PENDING  → keep polling
        // CONFIRMED → show success screen
        // FAILED    → show failure screen with failureReason
        BookingStatus bookingStatus,

        // Populated only when bookingStatus=FAILED — shown to the user so they
        // understand why the booking did not go through (e.g. "Your card was declined.")
        // Null for PENDING and CONFIRMED states
        String failureReason

) {}