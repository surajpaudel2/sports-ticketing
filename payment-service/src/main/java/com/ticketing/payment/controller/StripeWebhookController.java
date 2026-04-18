package com.ticketing.payment.controller;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.ApiResource;
import com.stripe.net.Webhook;
import com.ticketing.payment.service.PaymentEventPublisher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Receives and processes Stripe webhook events.
 *
 * <p>Stripe calls this endpoint after every payment event (success, failure, etc.).
 * Every request is signature-verified before any logic executes — unsigned or tampered
 * requests are rejected immediately with HTTP 400.</p>
 *
 * <p><strong>Handled event types:</strong></p>
 * <ul>
 *   <li>{@code payment_intent.succeeded} — payment confirmed; publishes
 *       {@code PaymentSuccessEvent} to RabbitMQ for booking-service to process.</li>
 *   <li>{@code payment_intent.payment_failed} — payment failed; publishes
 *       {@code PaymentFailedEvent} to RabbitMQ for booking-service to release seats
 *       and fail the booking.</li>
 * </ul>
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
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Stripe Webhook", description = "Stripe webhook receiver — called by Stripe, not by the frontend or other services")
public class StripeWebhookController {

    private final PaymentEventPublisher paymentEventPublisher;

    // Injected from application.yaml stripe.webhook-secret → STRIPE_WEBHOOK_SECRET env var
    @Value("${stripe.webhook-secret}")
    private String webhookSecret;

    /**
     * Receives a Stripe webhook event and routes it to the correct RabbitMQ publish call.
     *
     * <p>The raw request body is used for signature verification — Spring must NOT parse
     * it as JSON before this method receives it. {@code @RequestBody String payload}
     * achieves this by reading the raw bytes as a string.</p>
     *
     * @param payload   raw JSON body from Stripe (used for HMAC verification)
     * @param sigHeader the {@code Stripe-Signature} header containing the HMAC signature
     * @return HTTP 200 to acknowledge delivery; HTTP 400 if signature is invalid
     */
    @Operation(
            summary = "Stripe webhook receiver",
            description = "Called by Stripe after payment events. Verifies the webhook signature " +
                          "and publishes PaymentSuccessEvent or PaymentFailedEvent to RabbitMQ."
    )
    @PostMapping("/webhook")
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {

        Event stripeEvent;

        // ── Signature verification ────────────────────────────────────────────────────
        // Reject any request that is not genuinely from Stripe.
        // SignatureVerificationException is caught here (not in GlobalExceptionHandler)
        // because Stripe expects a plain text 400, not an ApiResult JSON body.
        try {
            stripeEvent = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Stripe webhook signature verification failed: {}", e.getMessage());
            return ResponseEntity.badRequest().body("Invalid signature");
        }

        log.info("Received Stripe webhook: type={} id={}", stripeEvent.getType(), stripeEvent.getId());

        // ── Event routing ─────────────────────────────────────────────────────────────
        switch (stripeEvent.getType()) {

            case "payment_intent.succeeded" -> {
                PaymentIntent intent = extractPaymentIntent(stripeEvent);
                if (intent == null) {
                    log.error("Could not deserialize PaymentIntent from payment_intent.succeeded webhook id={}",
                            stripeEvent.getId());
                    return ResponseEntity.ok("Deserialization failed — acknowledged to prevent Stripe retry");
                }

                Long bookingId = extractBookingId(intent, stripeEvent.getId());
                if (bookingId == null) {
                    return ResponseEntity.ok("Missing bookingId metadata — acknowledged");
                }

                paymentEventPublisher.publishPaymentSuccess(bookingId, intent.getId(), intent.getAmount());
                log.info("Processed payment_intent.succeeded: bookingId={} intentId={}",
                        bookingId, intent.getId());
            }

            case "payment_intent.payment_failed" -> {
                PaymentIntent intent = extractPaymentIntent(stripeEvent);
                if (intent == null) {
                    log.error("Could not deserialize PaymentIntent from payment_intent.payment_failed webhook id={}",
                            stripeEvent.getId());
                    return ResponseEntity.ok("Deserialization failed — acknowledged to prevent Stripe retry");
                }

                Long bookingId = extractBookingId(intent, stripeEvent.getId());
                if (bookingId == null) {
                    return ResponseEntity.ok("Missing bookingId metadata — acknowledged");
                }

                String reason = resolveFailureReason(intent);
                paymentEventPublisher.publishPaymentFailed(bookingId, reason);
                log.info("Processed payment_intent.payment_failed: bookingId={} reason={}", bookingId, reason);
            }

            default -> log.debug("Unhandled Stripe event type: {} — acknowledged and ignored",
                    stripeEvent.getType());
        }

        // HTTP 200 signals to Stripe that this webhook was received and processed.
        // Any non-2xx response causes Stripe to retry up to 3 days.
        return ResponseEntity.ok("Webhook processed");
    }

    /**
     * Deserializes the {@link PaymentIntent} object from the Stripe event data container.
     * Returns {@code null} (and logs an error) if deserialization fails — callers should
     * acknowledge the webhook to prevent Stripe from retrying indefinitely.
     */
    private PaymentIntent extractPaymentIntent(Event stripeEvent) {
        EventDataObjectDeserializer deserializer = stripeEvent.getDataObjectDeserializer();

        // 1. Happy path — Webhook API version matches your SDK version
        if (deserializer.getObject().isPresent()) {
            StripeObject obj = deserializer.getObject().get();
            if (obj instanceof PaymentIntent intent) {
                return intent;
            }
        }

        // 2. Fallback — API version mismatch, force deserialization safely
        log.warn("Stripe API version mismatch for webhook id={}. Falling back to unsafe deserialization.", stripeEvent.getId());

        try {
            StripeObject unsafeObj = deserializer.deserializeUnsafe();
            if (unsafeObj instanceof PaymentIntent intent) {
                return intent;
            } else {
                log.error("Deserialized object is not a PaymentIntent for webhook id={}", stripeEvent.getId());
            }
        } catch (Exception e) {
            log.error("Fallback PaymentIntent deserialization failed for webhook id={}: {}",
                    stripeEvent.getId(), e.getMessage());
        }

        return null;
    }

    /**
     * Reads the {@code bookingId} from the PaymentIntent metadata.
     * Returns {@code null} if the key is absent — this should never happen in production
     * (we always embed it during intent creation) but we guard defensively.
     */
    private Long extractBookingId(PaymentIntent intent, String webhookEventId) {
        String bookingIdStr = intent.getMetadata().get("bookingId");
        if (bookingIdStr == null) {
            log.error("PaymentIntent {} has no bookingId metadata — webhook id={} cannot be processed",
                    intent.getId(), webhookEventId);
            return null;
        }
        return Long.parseLong(bookingIdStr);
    }

    /**
     * Extracts a human-readable failure reason from the PaymentIntent.
     * Falls back to a generic message if Stripe does not provide a last-error description.
     */
    private String resolveFailureReason(PaymentIntent intent) {
        if (intent.getLastPaymentError() != null && intent.getLastPaymentError().getMessage() != null) {
            return intent.getLastPaymentError().getMessage();
        }
        return "Payment was declined";
    }
}
