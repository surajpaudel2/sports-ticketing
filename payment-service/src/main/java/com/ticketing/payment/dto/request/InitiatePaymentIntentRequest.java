package com.ticketing.payment.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

/**
 * Request payload for creating a Stripe PaymentIntent via
 * {@code POST /api/v1/payments/initiate-intent}.
 *
 * <p>The amount is expected in the <em>smallest currency unit</em> (e.g. pence for GBP,
 * cents for USD) — the same unit Stripe requires. booking-service calculates this as
 * {@code Math.round(seatsBooked * pricePerSeat * 100)} before calling this endpoint.</p>
 *
 * <p>{@code bookingId} is embedded in the Stripe PaymentIntent metadata so that the
 * Stripe webhook can identify which booking a given payment event belongs to.</p>
 */
@Schema(description = "Request payload for initiating a Stripe PaymentIntent")
@Builder
public record InitiatePaymentIntentRequest(

        @Schema(description = "Booking ID to embed in PaymentIntent metadata — used by the Stripe webhook to correlate payments", example = "12")
        @NotNull
        Long bookingId,

        @Schema(description = "Total charge amount in the smallest currency unit (pence/cents)", example = "9998")
        @Min(1)
        long amountInSmallestUnit,

        @Schema(description = "ISO 4217 currency code (lowercase)", example = "gbp")
        @NotBlank
        String currency

) {}
