package com.ticketing.payment.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.EventDataObjectDeserializer;
import com.stripe.model.PaymentIntent;
import com.stripe.model.StripeObject;
import com.stripe.net.Webhook;
import com.ticketing.payment.dto.event.PaymentFailedEvent;
import com.ticketing.payment.dto.event.PaymentSuccessEvent;
import com.ticketing.payment.entity.OutboxEvent;
import com.ticketing.payment.entity.OutboxStatus;
import com.ticketing.payment.repository.OutboxEventRepository;
import com.ticketing.payment.service.StripeWebhookService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Default implementation of {@link StripeWebhookService}.
 *
 * <p>Handles the full lifecycle of a Stripe webhook event inside a single
 * {@link Transactional} boundary:</p>
 * <ol>
 *   <li>Verify the HMAC signature via the Stripe SDK.</li>
 *   <li>Deserialize the embedded {@link PaymentIntent} from the event data.</li>
 *   <li>Extract {@code bookingId} from PaymentIntent metadata and, for failed payments,
 *       the human-readable error message.</li>
 *   <li>Serialize the appropriate event DTO ({@link PaymentSuccessEvent} or
 *       {@link PaymentFailedEvent}) to JSON and persist an {@link OutboxEvent} row
 *       with {@link OutboxStatus#PENDING}.</li>
 * </ol>
 *
 * <p>RabbitMQ delivery is intentionally <em>not</em> performed here — it is deferred to
 * {@link com.ticketing.payment.outbox.OutboxEventPoller}, which polls the outbox table
 * on a fixed schedule and retries on broker failures.</p>
 */
@Service
@Slf4j
public class StripeWebhookServiceImpl implements StripeWebhookService {

    private final String webhookSecret;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the service with all required dependencies injected via constructor.
     *
     * @param webhookSecret        the Stripe webhook signing secret, injected from
     *                             {@code stripe.webhook-secret} in {@code application.yaml}
     * @param outboxEventRepository JPA repository used to persist outbox events
     * @param objectMapper         Jackson mapper used to serialize event DTOs to JSON
     */
    public StripeWebhookServiceImpl(
            @Value("${stripe.webhook-secret}") String webhookSecret,
            OutboxEventRepository outboxEventRepository,
            ObjectMapper objectMapper) {
        this.webhookSecret = webhookSecret;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * {@inheritDoc}
     *
     * <p>The entire method runs inside a single database transaction. If an error
     * occurs after signature verification but before the outbox row is committed
     * (e.g. a serialization error), no partial state is persisted. Stripe will retry
     * the webhook on any non-2xx response, so the caller should return HTTP 200
     * as long as this method completes without throwing.</p>
     *
     * @param payload   raw JSON body from Stripe — used verbatim for HMAC verification
     * @param sigHeader the {@code Stripe-Signature} header value
     * @throws SignatureVerificationException if the request was not signed by Stripe
     */
    @Override
    @Transactional
    public void handleWebhook(String payload, String sigHeader) throws SignatureVerificationException {

        // ── Signature verification ────────────────────────────────────────────────────
        // Webhook.constructEvent throws SignatureVerificationException for tampered or
        // unsigned requests. The exception propagates to the controller, which returns 400.
        Event stripeEvent = Webhook.constructEvent(payload, sigHeader, webhookSecret);

        log.info("Received Stripe webhook: type={} id={}", stripeEvent.getType(), stripeEvent.getId());

        // ── Event routing ─────────────────────────────────────────────────────────────
        switch (stripeEvent.getType()) {

            case "payment_intent.succeeded" -> handlePaymentSucceeded(stripeEvent);

            case "payment_intent.payment_failed" -> handlePaymentFailed(stripeEvent);

            default -> log.debug("Unhandled Stripe event type: {} — acknowledged and ignored",
                    stripeEvent.getType());
        }
    }

    /**
     * Processes a {@code payment_intent.succeeded} event by extracting the PaymentIntent
     * and persisting a {@link PaymentSuccessEvent} payload in the outbox table.
     *
     * <p>If the PaymentIntent cannot be deserialized, or if the {@code bookingId} metadata
     * key is absent, the event is logged and acknowledged without writing an outbox row.
     * This prevents Stripe from retrying indefinitely for events we cannot process.</p>
     *
     * @param stripeEvent the verified Stripe event of type {@code payment_intent.succeeded}
     */
    private void handlePaymentSucceeded(Event stripeEvent) {
        PaymentIntent intent = extractPaymentIntent(stripeEvent);
        if (intent == null) {
            log.error("Could not deserialize PaymentIntent from payment_intent.succeeded webhook id={}",
                    stripeEvent.getId());
            return;
        }

        Long bookingId = extractBookingId(intent, stripeEvent.getId());
        if (bookingId == null) {
            return;
        }

        PaymentSuccessEvent event = PaymentSuccessEvent.builder()
                .bookingId(bookingId)
                .stripePaymentIntentId(intent.getId())
                .amount(intent.getAmount())
                .build();

        saveOutboxEvent(stripeEvent.getType(), event);
        log.info("Outbox event saved for payment_intent.succeeded: bookingId={} intentId={}",
                bookingId, intent.getId());
    }

    /**
     * Processes a {@code payment_intent.payment_failed} event by extracting the PaymentIntent,
     * resolving the failure reason, and persisting a {@link PaymentFailedEvent} in the outbox.
     *
     * <p>If the PaymentIntent cannot be deserialized, or if the {@code bookingId} metadata
     * key is absent, the event is acknowledged without writing an outbox row.</p>
     *
     * @param stripeEvent the verified Stripe event of type {@code payment_intent.payment_failed}
     */
    private void handlePaymentFailed(Event stripeEvent) {
        PaymentIntent intent = extractPaymentIntent(stripeEvent);
        if (intent == null) {
            log.error("Could not deserialize PaymentIntent from payment_intent.payment_failed webhook id={}",
                    stripeEvent.getId());
            return;
        }

        Long bookingId = extractBookingId(intent, stripeEvent.getId());
        if (bookingId == null) {
            return;
        }

        String reason = resolveFailureReason(intent);
        PaymentFailedEvent event = PaymentFailedEvent.builder()
                .bookingId(bookingId)
                .reason(reason)
                .build();

        saveOutboxEvent(stripeEvent.getType(), event);
        log.info("Outbox event saved for payment_intent.payment_failed: bookingId={} reason={}",
                bookingId, reason);
    }

    /**
     * Serializes the given event DTO to JSON and persists an {@link OutboxEvent} row
     * with {@link OutboxStatus#PENDING} status.
     *
     * <p>If Jackson serialization fails, the error is logged and no outbox row is written.
     * The webhook is still acknowledged to prevent Stripe retry loops.</p>
     *
     * @param eventType the Stripe event type string (e.g. {@code payment_intent.succeeded})
     * @param eventDto  the event DTO to serialize — must be a Jackson-serializable object
     */
    private void saveOutboxEvent(String eventType, Object eventDto) {
        String payload;
        try {
            payload = objectMapper.writeValueAsString(eventDto);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize event DTO for eventType={}: {}", eventType, e.getMessage());
            return;
        }

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .eventType(eventType)
                .payload(payload)
                .status(OutboxStatus.PENDING)
                .retryCount(0)
                .createdAt(Instant.now())
                .build();

        outboxEventRepository.save(outboxEvent);
    }

    /**
     * Deserializes the {@link PaymentIntent} object from the Stripe event data container.
     *
     * <p>Attempts the safe path first (SDK and webhook API versions match). Falls back to
     * unsafe deserialization when there is an API version mismatch. Returns {@code null}
     * if both paths fail — callers should acknowledge the webhook without processing.</p>
     *
     * @param stripeEvent the Stripe event whose embedded data object should be extracted
     * @return the deserialized {@link PaymentIntent}, or {@code null} on failure
     */
    private PaymentIntent extractPaymentIntent(Event stripeEvent) {
        EventDataObjectDeserializer deserializer = stripeEvent.getDataObjectDeserializer();

        // 1. Happy path — webhook API version matches your SDK version
        if (deserializer.getObject().isPresent()) {
            StripeObject obj = deserializer.getObject().get();
            if (obj instanceof PaymentIntent intent) {
                return intent;
            }
        }

        // 2. Fallback — API version mismatch, force deserialization safely
        log.warn("Stripe API version mismatch for webhook id={}. Falling back to unsafe deserialization.",
                stripeEvent.getId());

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
     * Reads the {@code bookingId} from the PaymentIntent metadata map.
     *
     * <p>The {@code bookingId} key is always set during PaymentIntent creation in
     * {@code PaymentServiceImpl}. Its absence indicates a data integrity issue.</p>
     *
     * @param intent         the PaymentIntent whose metadata should contain the booking ID
     * @param webhookEventId the Stripe event ID, used only for log context
     * @return the parsed booking ID, or {@code null} if the metadata key is absent
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
     * Extracts a human-readable failure reason from the PaymentIntent's last payment error.
     *
     * <p>Falls back to a generic message if Stripe does not provide an error description,
     * which can happen for certain decline codes.</p>
     *
     * @param intent the failed PaymentIntent to inspect
     * @return the failure reason string; never {@code null}
     */
    private String resolveFailureReason(PaymentIntent intent) {
        if (intent.getLastPaymentError() != null && intent.getLastPaymentError().getMessage() != null) {
            return intent.getLastPaymentError().getMessage();
        }
        return "Payment was declined";
    }
}
