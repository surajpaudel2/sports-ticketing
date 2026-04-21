package com.ticketing.booking.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request payload for cancelling an existing booking.
 *
 * <p>{@code seatsToCancel} supports partial cancellation — must be >= 1
 * and <= current {@code activeSeatCount} on the booking.</p>
 */
public record CancelBookingRequest(

        @NotNull(message = "seatsToCancel is required")
        @Min(value = 1, message = "seatsToCancel must be at least 1")
        Integer seatsToCancel,

        @NotBlank(message = "cancellationReason is required")
        String cancellationReason
) {}