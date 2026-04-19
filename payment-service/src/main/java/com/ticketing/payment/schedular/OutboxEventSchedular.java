package com.ticketing.payment.schedular;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.ticketing.payment.dto.event.PaymentFailedEvent;
import com.ticketing.payment.dto.event.PaymentSuccessEvent;
import com.ticketing.payment.entity.OutboxEvent;
import com.ticketing.payment.entity.OutboxStatus;
import com.ticketing.payment.messaging.publisher.PaymentEventPublisher;
import com.ticketing.payment.repository.OutboxEventRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Background poller that delivers {@link OutboxEvent} rows to RabbitMQ.
 *
 * <p>Part of the <em>Transactional Outbox Pattern</em>: this component reads all
 * {@link OutboxStatus#PENDING} events from the database on a fixed schedule and
 * publishes them to RabbitMQ via {@link PaymentEventPublisher}. Separating the write
 * (webhook handler) from the publish (this poller) guarantees that no event is lost
 * even if the broker is temporarily unavailable when the Stripe webhook arrives.</p>
 *
 * <p><strong>Retry behaviour:</strong> if a publish attempt throws an exception, the
 * event's {@code retryCount} is incremented. Once {@code retryCount} reaches
 * {@link #MAX_RETRY_COUNT}, the event is marked {@link OutboxStatus#FAILED} and will
 * not be retried further. A dead-letter investigation process should handle FAILED events.</p>
 *
 * <p><strong>Scheduling:</strong> the poller fires every {@value #POLL_INTERVAL_MS} ms
 * (measured from the end of the previous execution, i.e. {@code fixedDelay}), so a slow
 * or blocked publish will never cause concurrent executions to overlap.</p>
 */
@Component
@Slf4j
public class OutboxEventSchedular {

    /** Interval between the end of one poll and the start of the next, in milliseconds. */
    private static final long POLL_INTERVAL_MS = 3000L;

    /** Maximum number of consecutive failed publish attempts before an event is abandoned. */
    private static final int MAX_RETRY_COUNT = 3;

    private final OutboxEventRepository outboxEventRepository;
    private final PaymentEventPublisher paymentEventPublisher;
    private final ObjectMapper objectMapper;

    /**
     * Constructs the poller with all required dependencies injected via constructor.
     *
     * @param outboxEventRepository  JPA repository used to read and update outbox events
     * @param paymentEventPublisher  publisher that delivers events to the RabbitMQ exchange
     * @param objectMapper           Jackson mapper used to deserialize stored JSON payloads
     */
    public OutboxEventSchedular(
            OutboxEventRepository outboxEventRepository,
            PaymentEventPublisher paymentEventPublisher,
            ObjectMapper objectMapper) {
        this.outboxEventRepository = outboxEventRepository;
        this.paymentEventPublisher = paymentEventPublisher;
        this.objectMapper = objectMapper;
    }

    /**
     * Fetches all {@link OutboxStatus#PENDING} outbox events and attempts to publish each
     * one to RabbitMQ.
     *
     * <p>Runs every {@value #POLL_INTERVAL_MS} ms (fixed delay). Each event is processed
     * independently inside a try-catch block so that a failure on one event does not
     * prevent the remaining events from being processed in the same poll cycle.</p>
     *
     * <p>The entire method is wrapped in {@link Transactional} so that all status updates
     * (PUBLISHED / incremented retryCount / FAILED) are committed atomically at the end
     * of the poll cycle.</p>
     */
    @Scheduled(fixedDelay = POLL_INTERVAL_MS)
    @Transactional
    public void pollAndPublish() {
        List<OutboxEvent> pendingEvents = outboxEventRepository.findByStatus(OutboxStatus.PENDING);

        if (pendingEvents.isEmpty()) {
            return;
        }

        log.debug("Outbox poller found {} PENDING event(s)", pendingEvents.size());

        for (OutboxEvent event : pendingEvents) {
            try {
                publishEvent(event);
                markPublished(event);
            } catch (Exception e) {
                log.error("Failed to publish outbox event id={} eventType={}: {}",
                        event.getId(), event.getEventType(), e.getMessage());
                handleRetryFailure(event);
            }
        }
    }

    /**
     * Routes the outbox event to the correct {@link PaymentEventPublisher} method based
     * on the stored {@code eventType} string, deserializing the JSON payload first.
     *
     * @param event the outbox event to publish; must have a non-null, parseable payload
     * @throws Exception if the payload cannot be deserialized, or if the RabbitMQ publish fails
     */
    private void publishEvent(OutboxEvent event) throws Exception {
        switch (event.getEventType()) {

            case "payment_intent.succeeded" -> {
                PaymentSuccessEvent successEvent =
                        objectMapper.readValue(event.getPayload(), PaymentSuccessEvent.class);
                paymentEventPublisher.publishPaymentSuccess(
                        successEvent.bookingId(),
                        successEvent.stripePaymentIntentId(),
                        successEvent.amount());
            }

            case "payment_intent.payment_failed" -> {
                PaymentFailedEvent failedEvent =
                        objectMapper.readValue(event.getPayload(), PaymentFailedEvent.class);
                paymentEventPublisher.publishPaymentFailed(
                        failedEvent.bookingId(),
                        failedEvent.reason());
            }

            default -> log.warn("Outbox poller encountered unknown eventType={} for id={} — skipping",
                    event.getEventType(), event.getId());
        }
    }

    /**
     * Transitions the given event to {@link OutboxStatus#PUBLISHED} and records the
     * delivery timestamp.
     *
     * @param event the event that was successfully delivered to RabbitMQ
     */
    private void markPublished(OutboxEvent event) {
        event.setStatus(OutboxStatus.PUBLISHED);
        event.setPublishedAt(Instant.now());
        outboxEventRepository.save(event);
        log.info("Outbox event published successfully: id={} eventType={}", event.getId(), event.getEventType());
    }

    /**
     * Increments the {@code retryCount} of the given event. If the count reaches
     * {@link #MAX_RETRY_COUNT}, the event is transitioned to {@link OutboxStatus#FAILED}
     * and will not be retried in future poll cycles.
     *
     * @param event the event whose publish attempt just failed
     */
    private void handleRetryFailure(OutboxEvent event) {
        event.setRetryCount(event.getRetryCount() + 1);

        if (event.getRetryCount() >= MAX_RETRY_COUNT) {
            event.setStatus(OutboxStatus.FAILED);
            log.error("Outbox event id={} eventType={} permanently FAILED after {} attempts — manual intervention required",
                    event.getId(), event.getEventType(), event.getRetryCount());
        } else {
            log.warn("Outbox event id={} eventType={} will be retried (attempt {}/{})",
                    event.getId(), event.getEventType(), event.getRetryCount(), MAX_RETRY_COUNT);
        }

        outboxEventRepository.save(event);
    }
}
