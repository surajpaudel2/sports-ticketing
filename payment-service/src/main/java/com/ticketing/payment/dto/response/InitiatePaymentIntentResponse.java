package com.ticketing.payment.dto.response;

import lombok.Builder;

/**
 * Response returned from {@code POST /api/v1/payments/initiate-intent}.
 *
 * <p>Contains exactly what booking-service needs to hand off to the frontend:</p>
 * <ol>
 *   <li>{@code clientSecret} — passed to {@code stripe.confirmPayment()} so the frontend
 *       confirms the payment directly with Stripe. Card details never touch our servers.</li>
 *   <li>{@code paymentIntentId} — stored by booking-service on the {@code Booking} entity
 *       so that a refund can be issued in future (e.g. cancellation flow) via
 *       {@code stripe.refunds.create(paymentIntent: intentId)}.</li>
 * </ol>
 *
 * <p><strong>Security note:</strong> {@code clientSecret} must be treated as sensitive
 * transient data. It must NOT be logged and must NOT be stored in any persistent store.
 * It is only valid for a short window (typically 24 hours) and is single-use.</p>
 */
@Builder
public record InitiatePaymentIntentResponse(

        // Stripe PaymentIntent ID (e.g. "pi_3OqX...") — stored on the Booking entity by
        // booking-service for future refund capability. Never changes after creation.
        String paymentIntentId,

        // Stripe client secret (e.g. "pi_3OqX..._secret_...") — handed to the frontend
        // for stripe.confirmPayment(). DO NOT LOG THIS VALUE.
        String clientSecret

) {}
