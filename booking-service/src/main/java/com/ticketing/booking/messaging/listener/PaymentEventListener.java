package com.ticketing.booking.messaging.listener;

import com.rabbitmq.client.Channel;
import com.ticketing.booking.client.EventServiceClient;
import com.ticketing.booking.config.RabbitMQConfig;
import com.ticketing.booking.dto.cache.BookingCacheDto;
import com.ticketing.booking.messaging.payload.PaymentFailedEvent;
import com.ticketing.booking.messaging.payload.PaymentSuccessEvent;
import com.ticketing.booking.entity.Booking;
import com.ticketing.booking.entity.BookingStatus;
import com.ticketing.booking.messaging.publisher.BookingEventPublisher;
import com.ticketing.booking.service.BookingCacheService;
import com.ticketing.booking.service.BookingPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Optional;

/**
 * Listens for inbound RabbitMQ payment events published by Payment Service
 * and drives the booking to its final state (CONFIRMED or FAILED).
 *
 * <p><strong>Fast path (Redis hit):</strong> when the booking snapshot is still in Redis
 * (written during {@code initiateBooking}), the handler skips the {@code findById} DB read,
 * issues a targeted UPDATE, and builds the notification event from the cached data.
 * The cache entry is evicted after processing.</p>
 *
 * <p><strong>Slow path (Redis miss):</strong> falls back to {@code findById} — covers
 * duplicate deliveries (cache already evicted) and any Redis outage scenario.</p>
 *
 * <p>All methods use <strong>manual acknowledgement</strong> — the message is acked
 * only after business logic completes, and nacked to DLQ on hard failure.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventListener {

    private final BookingPersistenceService bookingPersistenceService;
    private final BookingEventPublisher bookingEventPublisher;
    private final BookingCacheService bookingCacheService;
    private final EventServiceClient eventServiceClient;

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_SUCCESS_QUEUE)
    public void handlePaymentSuccess(
            PaymentSuccessEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("Received payment.success: bookingId={} intentId={}",
                event.bookingId(), event.stripePaymentIntentId());

        try {
            Optional<BookingCacheDto> cached = bookingCacheService.get(event.bookingId());

            if (cached.isPresent()) {
                // Fast path — Redis hit: targeted UPDATE, no SELECT
                bookingPersistenceService.confirmBookingById(event.bookingId());
                // TODO : You are storing in cache when booking is confirmed about booking where you have stored the event name from the event-service as well which is not in the booking entity, so for the publishBookingConfirmed() method you also have to provide logic, not to rely on cache mechanism only, where you might have to check the booking related information from the BookingRepository and to get the "event name" you might have to use the feign client again to get the event name, so you might have to fix the else block as well.
                bookingEventPublisher.publishBookingConfirmed(cached.get());
                bookingCacheService.evict(event.bookingId());
                log.info("Booking confirmed (fast path): bookingId={}", event.bookingId());
            } else {
                // Slow path — cache miss: load from DB (covers duplicate deliveries)
                Booking booking = bookingPersistenceService.findById(event.bookingId());
                if (booking.getBookingStatus() != BookingStatus.PENDING) {
                    log.warn("Duplicate payment.success ignored: bookingId={} currentStatus={}",
                            booking.getId(), booking.getBookingStatus());
                    channel.basicAck(deliveryTag, false);
                    return;
                }
                bookingPersistenceService.confirmBooking(booking);
                bookingEventPublisher.publishBookingConfirmed(booking);
                log.info("Booking confirmed (slow path): bookingId={}", event.bookingId());
            }

            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("Unexpected error processing payment.success for bookingId={}: {}",
                    event.bookingId(), e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    @RabbitListener(queues = RabbitMQConfig.PAYMENT_FAILED_QUEUE)
    public void handlePaymentFailed(
            PaymentFailedEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("Received payment.failed: bookingId={}", event.bookingId());

        try {
            Optional<BookingCacheDto> cached = bookingCacheService.get(event.bookingId());

            if (cached.isPresent()) {
                // Fast path — Redis hit: release seats, targeted UPDATE, no SELECT
                releaseSeatsSafely(cached.get().eventId(), cached.get().seatsBooked(), event.bookingId());
                bookingPersistenceService.failBookingById(event.bookingId(), event.reason());
                bookingEventPublisher.publishBookingFailed(cached.get(), event.reason());
                bookingCacheService.evict(event.bookingId());
                log.info("Booking failed (fast path): bookingId={} reason={}", event.bookingId(), event.reason());
            } else {
                // Slow path — cache miss: load from DB
                Booking booking = bookingPersistenceService.findById(event.bookingId());
                if (booking.getBookingStatus() != BookingStatus.PENDING) {
                    log.warn("Duplicate payment.failed ignored: bookingId={} currentStatus={}",
                            booking.getId(), booking.getBookingStatus());
                    channel.basicAck(deliveryTag, false);
                    return;
                }
                releaseSeatsSafely(booking.getEventId(), booking.getSeatsBooked(), event.bookingId());
                bookingPersistenceService.failBooking(booking, event.reason());
                bookingEventPublisher.publishBookingFailed(booking);
                log.info("Booking failed (slow path): bookingId={} reason={}", event.bookingId(), event.reason());
            }

            channel.basicAck(deliveryTag, false);

        } catch (Exception e) {
            log.error("Unexpected error processing payment.failed for bookingId={}: {}",
                    event.bookingId(), e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false);
        }
    }

    private void releaseSeatsSafely(Long eventId, int seats, Long bookingId) {
        try {
            eventServiceClient.releaseSeats(eventId, seats);
            log.info("Seats released: bookingId={} eventId={} seats={}", bookingId, eventId, seats);
        } catch (Exception e) {
            log.error("CRITICAL: Failed to release seats — bookingId={} eventId={} seats={} — manual intervention required: {}",
                    bookingId, eventId, seats, e.getMessage());
        }
    }
}
