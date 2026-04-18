package com.ticketing.payment.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology configuration for Payment Service.
 *
 * <p>Uses the single {@link TopicExchange} shared across the entire sports-ticketing platform.
 * Routing key convention: {@code sports.ticketing.<domain>.<event>}</p>
 *
 * <p><strong>Message flow:</strong></p>
 * <pre>
 *   Stripe webhook → StripeWebhookController → PaymentEventPublisher
 *       → payment.success.queue → BookingEventListener#handlePaymentSuccess
 *       → payment.failed.queue  → BookingEventListener#handlePaymentFailed
 * </pre>
 *
 * <p>Payment Service only <em>publishes</em> — it does not consume any queue.
 * The queues and bindings are declared here so this service can create them
 * on startup if they do not yet exist in the broker.</p>
 */
@Configuration
public class RabbitMQConfig {

    // ===== Exchange =====

    /** Single topic exchange for the entire sports-ticketing platform. */
    public static final String EXCHANGE = "sports.ticketing.exchange";

    // ===== Outbound routing keys (published here, consumed by Booking Service) =====

    /** Routing key published after Stripe confirms a successful payment. */
    public static final String PAYMENT_SUCCESS_ROUTING_KEY = "sports.ticketing.payment.success";

    /** Routing key published after Stripe reports a payment failure. */
    public static final String PAYMENT_FAILED_ROUTING_KEY = "sports.ticketing.payment.failed";

    // ===== Queue names =====

    /** Outbound queue — receives payment success events; consumed by Booking Service. */
    public static final String PAYMENT_SUCCESS_QUEUE = "payment.success.queue";

    /** Outbound queue — receives payment failure events; consumed by Booking Service. */
    public static final String PAYMENT_FAILED_QUEUE = "payment.failed.queue";

    // ===== Exchange bean =====

    /**
     * TopicExchange allows wildcard routing key matching.
     * Declared here to ensure it exists even if Booking Service starts after Payment Service.
     */
    @Bean
    public TopicExchange sportTicketingExchange() {
        return new TopicExchange(EXCHANGE);
    }

    // ===== Queue beans =====

    /** Durable — survives broker restart so no payment success events are lost. */
    @Bean
    public Queue paymentSuccessQueue() {
        return QueueBuilder.durable(PAYMENT_SUCCESS_QUEUE).build();
    }

    /** Durable — survives broker restart so no payment failure events are lost. */
    @Bean
    public Queue paymentFailedQueue() {
        return QueueBuilder.durable(PAYMENT_FAILED_QUEUE).build();
    }

    // ===== Binding beans =====

    /** Binds payment success queue to the payment success routing key. */
    @Bean
    public Binding paymentSuccessBinding(Queue paymentSuccessQueue, TopicExchange sportTicketingExchange) {
        return BindingBuilder.bind(paymentSuccessQueue).to(sportTicketingExchange).with(PAYMENT_SUCCESS_ROUTING_KEY);
    }

    /** Binds payment failed queue to the payment failed routing key. */
    @Bean
    public Binding paymentFailedBinding(Queue paymentFailedQueue, TopicExchange sportTicketingExchange) {
        return BindingBuilder.bind(paymentFailedQueue).to(sportTicketingExchange).with(PAYMENT_FAILED_ROUTING_KEY);
    }

    // ===== Message converter and template =====

    /**
     * Serialize/deserialize messages as JSON instead of Java serialization.
     * Booking Service deserializes {@code PaymentSuccessEvent} / {@code PaymentFailedEvent}
     * from these JSON messages — field names must match.
     */
    @Bean
    public JacksonJsonMessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * Overrides the default RabbitTemplate to use JSON serialization for all outbound
     * messages published via {@code PaymentEventPublisher}.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        return rabbitTemplate;
    }
}
