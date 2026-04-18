package com.ticketing.booking.dto.response;

/**
 * Deserialized response from Payment Service's
 * {@code POST /api/v1/payments/initiate-intent} Feign call.
 *
 * <p>Field names must match Payment Service's {@code InitiatePaymentIntentResponse}
 * exactly — Jackson uses these names for JSON deserialization.</p>
 */
public record InitiatePaymentIntentResponse(

        // Stripe PaymentIntent ID — stored on the Booking entity for future refund capability
        String paymentIntentId,

        // Stripe client secret — forwarded to the frontend for stripe.confirmPayment()
        // DO NOT log or persist this value — it is sensitive transient data
        String clientSecret

) {}
