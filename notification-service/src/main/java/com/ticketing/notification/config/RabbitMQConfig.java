package com.ticketing.notification.config;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ topology for Notification Service.
 *
 * <p>Declares and binds the single inbound queue that receives all booking outcome
 * events from Booking Service via the wildcard routing key
 * {@code sports.ticketing.booking.*}.</p>
 */
@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "sports.ticketing.exchange";
    public static final String BOOKING_NOTIFICATION_QUEUE = "booking.notification.queue";
    public static final String BOOKING_WILDCARD_ROUTING_KEY = "sports.ticketing.booking.*";

    @Bean
    public TopicExchange sportTicketingExchange() {
        return new TopicExchange(EXCHANGE);
    }

    @Bean
    public Queue bookingNotificationQueue() {
        return QueueBuilder.durable(BOOKING_NOTIFICATION_QUEUE).build();
    }

    @Bean
    public Binding bookingNotificationBinding(Queue bookingNotificationQueue, TopicExchange sportTicketingExchange) {
        return BindingBuilder.bind(bookingNotificationQueue)
                .to(sportTicketingExchange)
                .with(BOOKING_WILDCARD_ROUTING_KEY);
    }

    @Bean
    public JacksonJsonMessageConverter messageConverter() {
        return new JacksonJsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(messageConverter());
        return template;
    }

    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());
        factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        return factory;
    }
}
