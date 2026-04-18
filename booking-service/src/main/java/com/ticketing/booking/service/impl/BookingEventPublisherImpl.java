package com.ticketing.booking.service.impl;

import com.ticketing.booking.config.RabbitMQConfig;
import com.ticketing.booking.dto.event.BookingResultEvent;
import com.ticketing.booking.entity.Booking;
import com.ticketing.booking.service.BookingEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/**
 * Publishes booking outcome events to the Notification Service via RabbitMQ.
 *
 * <p>Two routing keys are used — {@code sports.ticketing.booking.confirmed} and
 * {@code sports.ticketing.booking.failed} — so the Notification Service can bind
 * selectively. Both are caught by the wildcard binding on
 * {@code booking.notification.queue} ({@code sports.ticketing.booking.*}).</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingEventPublisherImpl implements BookingEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publishBookingConfirmed(Booking booking) {
        BookingResultEvent event = buildResultEvent(booking);
        log.info("Publishing booking.confirmed event bookingId={} userId={}",
                booking.getId(), booking.getUserId());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.BOOKING_CONFIRMED_ROUTING_KEY,
                event
        );
    }

    @Override
    public void publishBookingFailed(Booking booking) {
        BookingResultEvent event = buildResultEvent(booking);
        log.info("Publishing booking.failed event bookingId={} userId={} reason={}",
                booking.getId(), booking.getUserId(), booking.getFailureReason());
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE,
                RabbitMQConfig.BOOKING_FAILED_ROUTING_KEY,
                event
        );
    }

    /**
     * Builds the {@link BookingResultEvent} payload shared by both publish methods.
     * Extracted to avoid duplication — the only difference between confirmed and failed
     * events is the routing key, not the payload shape.
     */
    private BookingResultEvent buildResultEvent(Booking booking) {
        return BookingResultEvent.builder()
                .bookingId(booking.getId())
                .userId(booking.getUserId())
                .bookingStatus(booking.getBookingStatus())
                .recipientEmail(booking.getRecipientEmail())
                // reason is null for CONFIRMED — Notification Service handles null gracefully
                .reason(booking.getFailureReason())
                .build();
    }
}