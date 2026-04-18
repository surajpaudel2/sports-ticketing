package com.ticketing.payment.service;

import com.stripe.exception.SignatureVerificationException;

/**
 * Encapsulates all business logic for processing incoming Stripe webhook events.
 *
 * <p>Responsibilities of the implementation:</p>
 * <ul>
 *   <li>Verify the {@code Stripe-Signature} HMAC header — throws
 *       {@link SignatureVerificationException} for invalid signatures so the controller
 *       can return HTTP 400 without any further processing.</li>
 *   <li>Route the verified event by type ({@code payment_intent.succeeded},
 *       {@code payment_intent.payment_failed}).</li>
 *   <li>Extract the {@link com.stripe.model.PaymentIntent} and metadata fields
 *       ({@code bookingId}, failure reason) from the event.</li>
 *   <li>Persist an {@link com.ticketing.payment.entity.OutboxEvent} with status
 *       {@code PENDING} — publishing to RabbitMQ is deferred to
 *       {@link com.ticketing.payment.outbox.OutboxEventPoller}.</li>
 * </ul>
 *
 * <p>The controller delegates entirely to this interface and never directly interacts
 * with Stripe models, repositories, or the event publisher.</p>
 */
public interface StripeWebhookService {

    /**
     * Processes a raw Stripe webhook request end-to-end.
     *
     * <p>Verifies the signature, parses the event, extracts the relevant PaymentIntent
     * data, and persists an outbox event for later RabbitMQ delivery. Unrecognised event
     * types are silently acknowledged (returning without any outbox write).</p>
     *
     * @param payload   the raw JSON request body from Stripe — must not be pre-parsed,
     *                  as it is used verbatim for HMAC signature verification
     * @param sigHeader the value of the {@code Stripe-Signature} HTTP header
     * @throws SignatureVerificationException if the signature is invalid or absent —
     *                                        callers should respond with HTTP 400
     */
    void handleWebhook(String payload, String sigHeader) throws SignatureVerificationException;
}
