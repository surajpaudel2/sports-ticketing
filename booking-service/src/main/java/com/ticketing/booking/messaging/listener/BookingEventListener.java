package com.ticketing.booking.messaging.listener;

import com.ticketing.booking.client.EventServiceClient;
import com.ticketing.booking.entity.Booking;
import com.ticketing.booking.entity.BookingStatus;
import com.ticketing.booking.messaging.payload.CancellationApprovedEvent;
import com.ticketing.booking.messaging.payload.CancellationRejectedEvent;
import com.ticketing.booking.service.BookingPersistenceService;
import com.ticketing.booking.messaging.publisher.BookingEventPublisher;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import static com.ticketing.booking.config.RabbitMQConfig.*;

/**
 * Listens for admin decisions on cancellation requests.
 *
 * <p>Two listeners: approval and rejection. Both include idempotency
 * guards — if the booking is no longer in CANCELLATION_REQUESTED state
 * (e.g. duplicate event delivery), the event is silently ignored.</p>
 *
 * <p>On approval: seats restored first, then refund. This order is
 * intentional — lost seats cannot be recovered, money can be manually
 * refunded. See architecture decision doc for full reasoning.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class BookingEventListener {

    private final BookingPersistenceService bookingPersistenceService;
    private final EventServiceClient eventServiceClient;
    private final BookingEventPublisher bookingEventPublisher;

    // =============================================================================
    //                       CANCELLATION APPROVED
    // =============================================================================

    @RabbitListener(queues = CANCELLATION_APPROVED_QUEUE)
    public void onCancellationApproved(CancellationApprovedEvent event) {
        log.info("Cancellation approved received: bookingId={}", event.bookingId());
        Booking booking = bookingPersistenceService.findById(event.bookingId());

        if (isIdempotencyCheckFailed(booking)) return;

        if (!attemptSeatRestoration(booking, event.seatsToCancel())) {
            return; // Seat restoration failed, flow halts here.
        }

        // Step 2 — Stripe refund
        // TODO: stripe.refunds.create({ payment_intent: stripePaymentIntentId, amount: refundAmount })
        //   On failure:
        //     booking → CANCELLATION_FAILED
        //     publish cancellationFailed: "Refund failed, seats already restored — manual refund needed"
        //     return
        //   NOTE: seats already restored at this point — admin must manually process refund
        //   On success (or NO_REFUND — skip Stripe call entirely): continue to Step 3

        finalizeApprovedCancellation(booking, event);
        dispatchCancellationApprovedNotification(booking, event);

        log.info("Cancellation approved and processed: bookingId={} refundType={} newStatus={}",
                booking.getId(), event.refundType(), booking.getBookingStatus());
    }

    // =============================================================================
    //                       CANCELLATION APPROVED END
    // =============================================================================


    // =============================================================================
    //                       CANCELLATION REJECTED
    // =============================================================================

    @RabbitListener(queues = CANCELLATION_REJECTED_QUEUE)
    public void onCancellationRejected(CancellationRejectedEvent event) {
        log.info("Cancellation rejected received: bookingId={}", event.bookingId());
        Booking booking = bookingPersistenceService.findById(event.bookingId());

        if (isIdempotencyCheckFailed(booking)) return;

        revertBookingToConfirmed(booking);
        dispatchCancellationRejectedNotification(booking, event);

        log.info("Cancellation rejected, booking reverted to CONFIRMED: bookingId={}", booking.getId());
    }

    // =============================================================================
    //                       CANCELLATION REJECTED END
    // =============================================================================


    // -----------------------------------------------------------------------------
    //                         PRIVATE HELPER METHODS
    // -----------------------------------------------------------------------------

    /**
     * Idempotency guard — ignore duplicate or out-of-order events.
     * Returns true if the event should be ignored.
     */
    private boolean isIdempotencyCheckFailed(Booking booking) {
        if (booking.getBookingStatus() != BookingStatus.CANCELLATION_REQUESTED) {
            log.warn("Ignoring event — unexpected status: bookingId={} status={}",
                    booking.getId(), booking.getBookingStatus());
            return true;
        }
        return false;
    }

    private boolean attemptSeatRestoration(Booking booking, int seatsToCancel) {
        // Step 1 — Restore seats first (always before refund)
        // If this fails → CANCELLATION_FAILED + alert admin → DO NOT attempt refund
        try {
            eventServiceClient.restoreSeats(booking.getEventId(), seatsToCancel);
            log.info("Seats restored on cancellation approval: bookingId={}", booking.getId());
            return true;
        } catch (FeignException e) {
            log.error("CRITICAL: Seat restoration failed on cancellation approval: bookingId={}: {}",
                    booking.getId(), e.getMessage());

            booking.setBookingStatus(BookingStatus.CANCELLATION_FAILED);
            bookingPersistenceService.save(booking);
            bookingEventPublisher.publishCancellationFailed(booking.getId(),
                    "Seat restoration failed — manual intervention needed");
            return false;
        }
    }

    private void finalizeApprovedCancellation(Booking booking, CancellationApprovedEvent event) {
        // Step 3 — Update seat count and status
        int updatedSeatCount = booking.getActiveSeatCount() - event.seatsToCancel();
        booking.setActiveSeatCount(updatedSeatCount);
        booking.setBookingStatus(updatedSeatCount == 0
                ? BookingStatus.CANCELLED
                : BookingStatus.CONFIRMED);
        bookingPersistenceService.save(booking);
    }

    private void dispatchCancellationApprovedNotification(Booking booking, CancellationApprovedEvent event) {
        // Step 4 — Notify user
        bookingEventPublisher.publishBookingCancelled(new BookingCancelledEvent(
                booking.getId(),
                booking.getUserId(),
                booking.getRecipientEmail(),
                event.refundType(),
                event.refundAmount(),
                event.refundReason()
        ));
    }

    private void revertBookingToConfirmed(Booking booking) {
        // Revert to CONFIRMED — seats were never touched during admin review
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        bookingPersistenceService.save(booking);
    }

    private void dispatchCancellationRejectedNotification(Booking booking, CancellationRejectedEvent event) {
        bookingEventPublisher.publishCancellationRejected(new CancellationRejectedNotificationEvent(
                booking.getId(),
                booking.getUserId(),
                booking.getRecipientEmail(),
                event.rejectionReason()
        ));
    }
}