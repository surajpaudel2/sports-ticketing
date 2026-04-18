package com.ticketing.booking.dto.response;

import lombok.Builder;

/**
 * Response returned from {@code POST /api/v1/bookings/initiate}.
 *
 * <p>Contains exactly what the frontend needs to proceed with Stripe Elements:</p>
 * <ol>
 *   <li>{@code clientSecret} — passed to {@code stripe.confirmPayment()} so the frontend
 *       can confirm the payment directly with Stripe. Card details never touch our server.</li>
 *   <li>{@code bookingId} — used to poll {@code GET /api/v1/bookings/{bookingId}/status}
 *       every 2 seconds after payment confirmation until status transitions out of PENDING.</li>
 *   <li>{@code totalAmount} — displayed in the payment UI (seatsBooked × pricePerSeat,
 *       calculated from the Event Service price snapshot).</li>
 * </ol>
 *
 * <p>The booking status at this point is always {@code PENDING} — the final outcome
 * (CONFIRMED or FAILED) arrives asynchronously via the Stripe webhook → RabbitMQ path.</p>
 */
@Builder
public record InitiateBookingResponse(

        // Used by frontend for status polling after stripe.confirmPayment() completes
        Long bookingId,

        // Stripe client secret for the created PaymentIntent (e.g. "pi_3OqX..._secret_...")
        // Must be treated as sensitive — do not log this value
        String clientSecret,

        // Total charge amount in the platform's display currency (e.g. £49.98)
        // Calculated as seatsBooked × pricePerSeat — not stored in DB, derived on the fly
        double totalAmount

) {}