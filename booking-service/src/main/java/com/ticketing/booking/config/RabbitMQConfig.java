package com.ticketing.booking.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology configuration for Booking Service.
 *
 * <p>Uses a single {@link TopicExchange} shared across the entire sports-ticketing platform.
 * Routing key convention: {@code sports.ticketing.<domain>.<event>}</p>
 *
 * <p><strong>Message flow:</strong></p>
 * <pre>
 *   Payment Service  →  payment.success.queue  →  BookingEventListener#handlePaymentSuccess
 *   Payment Service  →  payment.failed.queue   →  BookingEventListener#handlePaymentFailed
 *   BookingService   →  booking.notification.queue  →  Notification Service
 * </pre>
 */
@Configuration
public class RabbitMQConfig {

    // ===== Exchange =====

    /** Single topic exchange for the entire sports-ticketing platform. */
    public static final String EXCHANGE = "sports.ticketing.exchange";

    // ===== Inbound routing keys (published by Payment Service, consumed here) =====

    /** Routing key for successful Stripe payments — Booking Service listens on this. */
    public static final String PAYMENT_SUCCESS_ROUTING_KEY = "sports.ticketing.payment.success";

    /** Routing key for failed Stripe payments — Booking Service listens on this. */
    public static final String PAYMENT_FAILED_ROUTING_KEY = "sports.ticketing.payment.failed";

    // ===== Outbound routing keys (published here, consumed by Notification Service) =====

    /** Routing key published when a booking is confirmed — Notification Service listens on this. */
    public static final String BOOKING_CONFIRMED_ROUTING_KEY = "sports.ticketing.booking.confirmed";

    /** Routing key published when a booking fails — Notification Service listens on this. */
    public static final String BOOKING_FAILED_ROUTING_KEY = "sports.ticketing.booking.failed";

    // ===== Queue names (referenced by @RabbitListener in BookingEventListener) =====

    /** Inbound queue — receives payment success events from Payment Service. */
    public static final String PAYMENT_SUCCESS_QUEUE = "payment.success.queue";

    /** Inbound queue — receives payment failure events from Payment Service. */
    public static final String PAYMENT_FAILED_QUEUE = "payment.failed.queue";

    /** Outbound queue — receives all booking outcome events; consumed by Notification Service. */
    public static final String BOOKING_NOTIFICATION_QUEUE = "booking.notification.queue";

    // ===== Exchange bean =====

    /**
     * TopicExchange allows wildcard routing key matching — Notification Service subscribes
     * to {@code sports.ticketing.booking.*} to receive both confirmed and failed events
     * without needing two separate bindings.
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

    /**
     * Single queue for all booking outcomes — Notification Service uses the wildcard
     * binding {@code sports.ticketing.booking.*} to receive both confirmed and failed events.
     */
    @Bean
    public Queue bookingNotificationQueue() {
        return QueueBuilder.durable(BOOKING_NOTIFICATION_QUEUE).build();
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

    /**
     * Wildcard binding — catches both {@code booking.confirmed} and {@code booking.failed}
     * in one queue, so Notification Service has a single consumption point.
     */
    @Bean
    public Binding bookingNotificationBinding(Queue bookingNotificationQueue, TopicExchange sportTicketingExchange) {
        return BindingBuilder.bind(bookingNotificationQueue).to(sportTicketingExchange).with("sports.ticketing.booking.*");
    }

    // ===== Message converter and template =====

    /**
     * Serialize/deserialize messages as JSON instead of Java serialization.
     * Shared between the RabbitTemplate (outbound) and the listener container factory (inbound).
     */
    @Bean
    public JacksonJsonMessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    /**
     * Overrides the default RabbitTemplate to use JSON serialization for all outbound messages
     * published via {@code BookingEventPublisher}.
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        return rabbitTemplate;
    }

    /**
     * Configures the listener container factory used by all {@code @RabbitListener} methods
     * in {@code BookingEventListener}.
     *
     * <p>Two critical settings:</p>
     * <ul>
     *   <li>Message converter — ensures incoming JSON payloads are deserialized into
     *       {@code PaymentSuccessEvent} / {@code PaymentFailedEvent} records automatically.</li>
     *   <li>Acknowledge mode MANUAL — the listener explicitly calls {@code channel.basicAck()}
     *       or {@code channel.basicNack()} so RabbitMQ acknowledgement is tied to the outcome
     *       of the business logic, not just method return.</li>
     * </ul>
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        // MANUAL ack — listener controls exactly when the message is acknowledged
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }
}