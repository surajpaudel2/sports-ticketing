package com.ticketing.payment.service;

/**
 * Handles all outbound RabbitMQ event publishing for Payment Service.
 *
 * <p>Publishes payment outcome events to Booking Service after every Stripe
 * webhook is received and verified. Routing key differs by outcome so consumers
 * can subscribe selectively.</p>
 *
 * <p>Intentionally contains no business logic — each method is a pure publish
 * operation that builds the event payload and sends it to the exchange.</p>
 */
public interface PaymentEventPublisher {

    /**
     * Publishes a {@code PaymentSuccessEvent} on the success routing key
     * ({@code sports.ticketing.payment.success}) after Stripe confirms a payment.
     *
     * <p>Called from {@code StripeWebhookController} after verifying a
     * {@code payment_intent.succeeded} webhook event.</p>
     *
     * @param bookingId             the booking ID extracted from PaymentIntent metadata
     * @param stripePaymentIntentId the Stripe PaymentIntent ID
     * @param amount                the charged amount in the smallest currency unit
     */
    void publishPaymentSuccess(Long bookingId, String stripePaymentIntentId, long amount);

    /**
     * Publishes a {@code PaymentFailedEvent} on the failed routing key
     * ({@code sports.ticketing.payment.failed}) after Stripe reports a payment failure.
     *
     * <p>Called from {@code StripeWebhookController} after verifying a
     * {@code payment_intent.payment_failed} webhook event.</p>
     *
     * @param bookingId the booking ID extracted from PaymentIntent metadata
     * @param reason    the human-readable failure reason from Stripe
     */
    void publishPaymentFailed(Long bookingId, String reason);
}
