package com.ticketing.booking.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

/**
 * Request payload for initiating a booking via {@code POST /api/v1/bookings/initiate}.
 *
 * <p>This request triggers an optimistic seat-availability check against the Event Service,
 * then creates a Stripe PaymentIntent. The returned {@code clientSecret} is handed to the
 * frontend so Stripe Elements can confirm the payment directly with Stripe — card details
 * never reach this server.</p>
 *
 * <p>{@code recipientEmail} is optional — falls back to the user's account email if omitted.</p>
 */
@Schema(description = "Request payload for initiating a new booking")
@Builder
public record InitiateBookingRequest(

        @Schema(description = "ID of the user making the booking", example = "1")
        @NotNull
        Long userId,

        @Schema(description = "ID of the event to book seats for", example = "42")
        @NotNull
        Long eventId,

        @Schema(description = "Number of seats to book (must be at least 1)", example = "2")
        @Min(1)
        int seatsBooked,

        @Schema(description = "Payment method identifier (e.g. CREDIT_CARD)", example = "CREDIT_CARD")
        @NotNull
        String paymentMethod,

        @Schema(
                description = "Email to send booking confirmation to — optional, falls back to user account email",
                example = "user@example.com",
                nullable = true
        )
        @Email
        String recipientEmail

) {}