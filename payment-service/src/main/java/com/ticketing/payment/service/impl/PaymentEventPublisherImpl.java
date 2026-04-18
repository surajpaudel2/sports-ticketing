package com.ticketing.payment.service.impl;

import com.ticketing.payment.config.RabbitMQConfig;
import com.ticketing.payment.dto.event.PaymentFailedEvent;
import com.ticketing.payment.dto.event.PaymentSuccessEvent;
import com.ticketing.payment.service.PaymentEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes payment outcome events to Booking Service via RabbitMQ.
 *
 * <p>Two routing keys are used — {@code sports.ticketing.payment.success} and
 * {@code sports.ticketing.payment.failed} — so Booking Service can consume each
 * independently with separate {@code @RabbitListener} methods.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentEventPublisherImpl implements PaymentEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishPaymentSuccess(Long bookingId, String stripePaymentIntentId, long amount) {
        PaymentSuccessEvent event = PaymentSuccessEvent.builder()
                .bookingId(bookingId)
                .stripePaymentIntentId(stripePaymentIntentId)
                .amount(amount)
                .build();

        log.info("Publishing payment.success event: bookingId={} intentId={} amount={}",
                bookingId, stripePaymentIntentId, amount);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.PAYMENT_SUCCESS_ROUTING_KEY,
                event
        );
    }

    @Override
    public void publishPaymentFailed(Long bookingId, String reason) {
        PaymentFailedEvent event = PaymentFailedEvent.builder()
                .bookingId(bookingId)
                .reason(reason)
                .build();

        log.info("Publishing payment.failed event: bookingId={} reason={}", bookingId, reason);

        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.PAYMENT_FAILED_ROUTING_KEY,
                event
        );
    }
}
