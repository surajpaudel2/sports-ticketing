package com.ticketing.payment.service;

import com.ticketing.payment.dto.request.InitiatePaymentIntentRequest;
import com.ticketing.payment.dto.response.InitiatePaymentIntentResponse;

/**
 * Handles Stripe PaymentIntent lifecycle operations.
 *
 * <p>This service is the single Stripe integration point in the platform.
 * No other service touches Stripe directly — all Stripe API calls route through here.</p>
 *
 * <p>Stripe exceptions are allowed to propagate from implementations — they are handled
 * by {@code GlobalExceptionHandler} which maps them to HTTP 502 responses, signalling
 * to booking-service that payment initiation failed.</p>
 */
public interface PaymentService {

    /**
     * Creates a Stripe PaymentIntent for the given booking.
     *
     * <p>The {@code bookingId} is embedded in the PaymentIntent metadata so that the
     * Stripe webhook handler ({@code StripeWebhookController}) can map an incoming
     * webhook event back to the correct booking when publishing the RabbitMQ event.</p>
     *
     * <p>Card details never reach this service — Stripe Elements handles them entirely
     * on the client side. This service only creates the intent and returns the credentials
     * the frontend needs to complete the payment.</p>
     *
     * @param request contains bookingId (for metadata), amount (smallest currency unit), and currency
     * @return the Stripe PaymentIntent ID and client secret
     * @throws com.stripe.exception.StripeException if the Stripe API call fails
     */
    InitiatePaymentIntentResponse initiatePaymentIntent(InitiatePaymentIntentRequest request)
            throws com.stripe.exception.StripeException;
}
