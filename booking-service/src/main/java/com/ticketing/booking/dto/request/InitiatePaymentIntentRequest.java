package com.ticketing.booking.dto.request;

/**
 * Request payload sent to Payment Service via Feign to create a Stripe PaymentIntent.
 *
 * <p>Constructed in {@code BookingServiceImpl#initiateBooking} after the PENDING booking
 * is persisted. The booking ID is forwarded to Payment Service which embeds it in the
 * Stripe PaymentIntent metadata — this is how the Stripe webhook maps a payment event
 * back to the correct booking.</p>
 *
 * <p>The amount is pre-calculated by booking-service as
 * {@code Math.round(seatsBooked * pricePerSeat * 100)} (smallest currency unit)
 * because booking-service owns the seat and price snapshot from the Event Service.</p>
 */
public record InitiatePaymentIntentRequest(

        // Booking ID to embed in Stripe PaymentIntent metadata
        Long bookingId,

        // Total charge in smallest currency unit (pence for GBP, cents for USD)
        long amountInSmallestUnit,

        // ISO 4217 currency code (e.g. "gbp", "usd")
        String currency

) {}
