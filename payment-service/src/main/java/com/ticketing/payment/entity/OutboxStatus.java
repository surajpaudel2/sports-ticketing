package com.ticketing.payment.entity;

/**
 * Lifecycle states for an {@link OutboxEvent}.
 *
 * <p>An event starts as {@code PENDING}, transitions to {@code PUBLISHED} once the
 * {@link com.ticketing.payment.outbox.OutboxEventPoller} successfully delivers it to
 * RabbitMQ, and is marked {@code FAILED} after the maximum number of retry attempts
 * has been exhausted without a successful publish.</p>
 */
public enum OutboxStatus {

    /** Event has been persisted but not yet delivered to RabbitMQ. */
    PENDING,

    /** Event was successfully published to the RabbitMQ exchange. */
    PUBLISHED,

    /** Event exceeded the maximum retry limit and will not be retried further. */
    FAILED
}
