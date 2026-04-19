package com.ticketing.booking.service.impl;

import com.ticketing.booking.config.RabbitMQConfig;
import com.ticketing.booking.dto.cache.BookingCacheDto;
import com.ticketing.booking.dto.event.BookingResultEvent;
import com.ticketing.booking.entity.Booking;
import com.ticketing.booking.entity.BookingStatus;
import com.ticketing.booking.service.BookingEventPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingEventPublisherImpl implements BookingEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    // ===== Slow path (DB entity) =====

    @Override
    public void publishBookingConfirmed(Booking booking) {
        BookingResultEvent event = fromEntity(booking);
        log.info("Publishing booking.confirmed bookingId={} userId={}", booking.getId(), booking.getUserId());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.BOOKING_CONFIRMED_ROUTING_KEY, event);
    }

    @Override
    public void publishBookingFailed(Booking booking) {
        BookingResultEvent event = fromEntity(booking);
        log.info("Publishing booking.failed bookingId={} userId={} reason={}",
                booking.getId(), booking.getUserId(), booking.getFailureReason());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.BOOKING_FAILED_ROUTING_KEY, event);
    }

    // ===== Fast path (Redis cache) =====

    @Override
    public void publishBookingConfirmed(BookingCacheDto cached) {
        BookingResultEvent event = fromCache(cached, BookingStatus.CONFIRMED, null);
        log.info("Publishing booking.confirmed (cache) bookingId={} userId={}", cached.bookingId(), cached.userId());
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.BOOKING_CONFIRMED_ROUTING_KEY, event);
    }

    @Override
    public void publishBookingFailed(BookingCacheDto cached, String reason) {
        BookingResultEvent event = fromCache(cached, BookingStatus.FAILED, reason);
        log.info("Publishing booking.failed (cache) bookingId={} userId={} reason={}",
                cached.bookingId(), cached.userId(), reason);
        rabbitTemplate.convertAndSend(RabbitMQConfig.EXCHANGE, RabbitMQConfig.BOOKING_FAILED_ROUTING_KEY, event);
    }

    // ===== Builders =====

    private BookingResultEvent fromEntity(Booking booking) {
        return BookingResultEvent.builder()
                .bookingId(booking.getId())
                .userId(booking.getUserId())
                .bookingStatus(booking.getBookingStatus())
                .recipientEmail(booking.getRecipientEmail())
                .eventId(booking.getEventId())
                .eventName(booking.getEventName())
                .seatsBooked(booking.getSeatsBooked())
                .pricePerSeat(booking.getPricePerSeat())
                .totalAmount((double) booking.getSeatsBooked() * booking.getPricePerSeat())
                .reason(booking.getFailureReason())
                .build();
    }

    private BookingResultEvent fromCache(BookingCacheDto cached, BookingStatus status, String reason) {
        return BookingResultEvent.builder()
                .bookingId(cached.bookingId())
                .userId(cached.userId())
                .bookingStatus(status)
                .recipientEmail(cached.recipientEmail())
                .eventId(cached.eventId())
                .eventName(cached.eventName())
                .seatsBooked(cached.seatsBooked())
                .pricePerSeat(cached.pricePerSeat())
                .totalAmount((double) cached.seatsBooked() * cached.pricePerSeat())
                .reason(reason)
                .build();
    }
}
