package com.ticketing.payment.controller;

import com.stripe.exception.SignatureVerificationException;
import com.ticketing.payment.schedular.OutboxEventSchedular;
import com.ticketing.payment.service.StripeWebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Receives Stripe webhook events and delegates all processing to {@link StripeWebhookService}.
 *
 * <p>Stripe calls this endpoint after every payment event (success, failure, etc.).
 * This controller's only responsibilities are:</p>
 * <ol>
 *   <li>Accept the raw HTTP request.</li>
 *   <li>Delegate to {@link StripeWebhookService#handleWebhook} for signature verification,
 *       event parsing, and outbox persistence.</li>
 *   <li>Return HTTP 400 if the signature is invalid; HTTP 200 for all other outcomes.</li>
 * </ol>
 *
 * <p>All business logic (PaymentIntent extraction, bookingId parsing, outbox writes)
 * lives in {@link StripeWebhookService} and its implementation. RabbitMQ publishing is
 * deferred to {@link OutboxEventSchedular}.</p>
 *
 * <p>All other event types are acknowledged with HTTP 200 and ignored — Stripe requires
 * a 200 response within 30 seconds to consider the event delivered. Non-200 responses
 * cause Stripe to retry the webhook up to 3 days.</p>
 *
 * <p><strong>Note on local development:</strong> use the Stripe CLI to forward webhooks
 * to your local server: {@code stripe listen --forward-to localhost:8084/api/v1/payments/webhook}</p>
 */
@RestController
@RequestMapping("/api/v1/payments")
@Slf4j
@Tag(name = "Stripe Webhook", description = "Stripe webhook receiver — called by Stripe, not by the frontend or other services")
public class StripeWebhookController {

    private final StripeWebhookService stripeWebhookService;

    /**
     * Constructs the controller with its single dependency injected via constructor.
     *
     * @param stripeWebhookService service that handles signature verification, event
     *                             routing, and outbox persistence
     */
    public StripeWebhookController(StripeWebhookService stripeWebhookService) {
        this.stripeWebhookService = stripeWebhookService;
    }

    /**
     * Receives a raw Stripe webhook request and delegates processing to the service layer.
     *
     * <p>The raw request body is used for signature verification — Spring must NOT parse
     * it as JSON before this method receives it. {@code @RequestBody String payload}
     * achieves this by reading the raw bytes as a string.</p>
     *
     * <p>All exceptions including {@code SignatureVerificationException} propagate to
     * {@code GlobalExceptionHandler} which returns the appropriate HTTP status.</p>
     *
     * @param payload   raw JSON body from Stripe (used verbatim for HMAC verification)
     * @param sigHeader the {@code Stripe-Signature} header containing the HMAC signature
     * @return HTTP 200 to acknowledge delivery; HTTP 400 if the signature is invalid
     */
    @Operation(
            summary = "Stripe webhook receiver",
            description = "Called by Stripe after payment events. Verifies the webhook signature " +
                          "and saves a PENDING OutboxEvent for async RabbitMQ delivery."
    )
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) throws SignatureVerificationException {

        stripeWebhookService.handleWebhook(payload, sigHeader);

        // HTTP 200 signals to Stripe that this webhook was received and processed.
        // Any non-2xx response causes Stripe to retry up to 3 days.
        return ResponseEntity.ok("Webhook processed");
    }
}
