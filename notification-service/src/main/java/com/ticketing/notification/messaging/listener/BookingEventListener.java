package com.ticketing.notification.messaging.listener;

import com.rabbitmq.client.Channel;
import com.ticketing.notification.config.RabbitMQConfig;
import com.ticketing.notification.dto.BookingResultEvent;
import com.ticketing.notification.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Consumes booking outcome events from {@code booking.notification.queue} and
 * delegates email sending to {@link EmailService}.
 *
 * <p>Uses manual acknowledgement — the message is acked only after the email
 * dispatch attempt completes. Email send failures are logged but still acked
 * to avoid infinite redelivery of an undeliverable notification.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventListener {

    private final EmailService emailService;

    @RabbitListener(queues = RabbitMQConfig.BOOKING_NOTIFICATION_QUEUE)
    public void handleBookingResult(
            BookingResultEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("Received booking result: bookingId={} status={} recipient={}",
                event.bookingId(), event.bookingStatus(), event.recipientEmail());

        try {
            switch (event.bookingStatus()) {
                case CONFIRMED -> emailService.sendBookingConfirmed(event);
                case FAILED -> emailService.sendBookingFailed(event);
                default -> log.warn("Unhandled booking status={} bookingId={}", event.bookingStatus(), event.bookingId());
            }
            channel.basicAck(deliveryTag, false);
        } catch (Exception e) {
            // Log and ack — retrying a notification failure won't fix a broken SMTP config
            log.error("Failed to process booking notification bookingId={}: {}", event.bookingId(), e.getMessage(), e);
            channel.basicAck(deliveryTag, false);
        }
    }
}
