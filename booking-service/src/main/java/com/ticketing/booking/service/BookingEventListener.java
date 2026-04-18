package com.ticketing.booking.service;

import com.rabbitmq.client.Channel;
import com.ticketing.booking.client.EventServiceClient;
import com.ticketing.booking.config.RabbitMQConfig;
import com.ticketing.booking.dto.event.PaymentFailedEvent;
import com.ticketing.booking.dto.event.PaymentSuccessEvent;
import com.ticketing.booking.entity.Booking;
import com.ticketing.booking.entity.BookingStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Listens for inbound RabbitMQ payment events published by Payment Service
 * and drives the booking to its final state (CONFIRMED or FAILED).
 *
 * <p>All methods use <strong>manual acknowledgement</strong> — the message is acked
 * only after the business logic completes successfully, and nacked (with or without
 * requeue) on failure. This ensures no payment event is silently lost.</p>
 *
 * <p>FeignExceptions are intentionally caught here rather than delegated to
 * {@code GlobalExceptionHandler} because acknowledgement decisions must be made
 * inside the listener — a controller advice cannot nack a RabbitMQ message.</p>
 *
 * <p><strong>Seat management:</strong> seats are deducted by Event Service during
 * {@code checkAndReserve} in the {@code initiateBooking} flow — <em>before</em>
 * payment is attempted. Therefore:</p>
 * <ul>
 *   <li>{@code handlePaymentSuccess} only needs to confirm the booking and notify —
 *       no seat operations are needed.</li>
 *   <li>{@code handlePaymentFailed} must release the pre-reserved seats back to Event
 *       Service as a compensating transaction before failing the booking.</li>
 * </ul>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventListener {

    private final BookingPersistenceService bookingPersistenceService;
    private final BookingEventPublisher bookingEventPublisher;
    private final EventServiceClient eventServiceClient;

    /**
     * Processes a successful Stripe payment event.
     *
     * <p>Flow:</p>
     * <ol>
     *   <li>Find the booking by ID.</li>
     *   <li>Idempotency check — Payment Service retries; ignore if already past PENDING.</li>
     *   <li>Confirm the booking — seats were already deducted during {@code initiateBooking}.</li>
     *   <li>Publish booking confirmed event to Notification Service.</li>
     *   <li>Ack the message.</li>
     * </ol>
     *
     * <p>No seat operations are needed here — {@code checkAndReserve} in Event Service already
     * deducted seats atomically when the booking was initiated. This makes the payment success
     * path simple and lock-free.</p>
     */
    @RabbitListener(queues = RabbitMQConfig.PAYMENT_SUCCESS_QUEUE)
    public void handlePaymentSuccess(
            PaymentSuccessEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("Received payment.success: bookingId={} intentId={}",
                event.bookingId(), event.stripePaymentIntentId());

        try {
            // Step 1 — find booking
            Booking booking = bookingPersistenceService.findById(event.bookingId());

            // Step 2 — idempotency: Payment Service can retry a delivery multiple times
            if (booking.getBookingStatus() != BookingStatus.PENDING) {
                log.warn("Duplicate payment.success ignored: bookingId={} currentStatus={}",
                        booking.getId(), booking.getBookingStatus());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // Step 3 — confirm booking and notify
            // Seats were already deducted during checkAndReserve in initiateBooking —
            // no seat operations needed here
            booking = bookingPersistenceService.confirmBooking(booking);
            bookingEventPublisher.publishBookingConfirmed(booking);
            channel.basicAck(deliveryTag, false);
            log.info("Booking confirmed: bookingId={}", booking.getId());

        } catch (Exception e) {
            log.error("Unexpected error processing payment.success for bookingId={}: {}",
                    event.bookingId(), e.getMessage(), e);
            // Dead-letter — do not requeue to avoid infinite retry loops on hard failures
            // (e.g. booking not found). Ops team investigates via DLQ.
            channel.basicNack(deliveryTag, false, false);
        }
    }

    /**
     * Processes a failed Stripe payment event.
     *
     * <p>Flow:</p>
     * <ol>
     *   <li>Find the booking by ID.</li>
     *   <li>Idempotency check — ignore if already past PENDING.</li>
     *   <li>Release reserved seats back to Event Service — compensating transaction
     *       for the {@code checkAndReserve} deduction that happened during
     *       {@code initiateBooking}.</li>
     *   <li>Fail the booking with the reason from Stripe.</li>
     *   <li>Publish booking failed event to Notification Service.</li>
     *   <li>Ack the message.</li>
     * </ol>
     *
     * <p>Seat release failure is logged but does NOT prevent the message from being
     * acked — the booking is failed regardless. Seat release failures are flagged
     * for manual review (operational alert). Allowing a nack here would cause infinite
     * retries without fixing the underlying data inconsistency.</p>
     */
    @RabbitListener(queues = RabbitMQConfig.PAYMENT_FAILED_QUEUE)
    public void handlePaymentFailed(
            PaymentFailedEvent event,
            Channel channel,
            @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) throws IOException {

        log.info("Received payment.failed: bookingId={}", event.bookingId());

        try {
            // Step 1 — find booking
            Booking booking = bookingPersistenceService.findById(event.bookingId());

            // Step 2 — idempotency check
            if (booking.getBookingStatus() != BookingStatus.PENDING) {
                log.warn("Duplicate payment.failed ignored: bookingId={} currentStatus={}",
                        booking.getId(), booking.getBookingStatus());
                channel.basicAck(deliveryTag, false);
                return;
            }

            // Step 3 — release seats back to Event Service (compensating transaction)
            // Seats were deducted during checkAndReserve in initiateBooking — must be returned
            // so the inventory is accurate and other users can book them
            try {
                eventServiceClient.releaseSeats(booking.getEventId(), booking.getSeatsBooked());
                log.info("Seats released for failed payment: bookingId={} eventId={} seats={}",
                        booking.getId(), booking.getEventId(), booking.getSeatsBooked());
            } catch (Exception releaseEx) {
                // Log as CRITICAL — seat count is now inconsistent and requires manual intervention
                // Do not rethrow — we still want to fail the booking and notify the user
                log.error("CRITICAL: Failed to release seats for failed payment — " +
                        "bookingId={} eventId={} seats={} — manual intervention required: {}",
                        booking.getId(), booking.getEventId(), booking.getSeatsBooked(),
                        releaseEx.getMessage());
            }

            // Step 4 — fail booking and notify
            booking = bookingPersistenceService.failBooking(booking, event.reason());
            bookingEventPublisher.publishBookingFailed(booking);
            channel.basicAck(deliveryTag, false);
            log.info("Booking failed: bookingId={} reason={}", booking.getId(), event.reason());

        } catch (Exception e) {
            log.error("Unexpected error processing payment.failed for bookingId={}: {}",
                    event.bookingId(), e.getMessage(), e);
            channel.basicNack(deliveryTag, false, false); // dead-letter, do not requeue
        }
    }
}
