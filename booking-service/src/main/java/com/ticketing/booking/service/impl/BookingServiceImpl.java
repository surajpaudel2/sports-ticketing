package com.ticketing.booking.service.impl;

import com.ticketing.booking.client.EventServiceClient;
import com.ticketing.booking.client.PaymentServiceClient;
import com.ticketing.booking.dto.cache.BookingCacheDto;
import com.ticketing.booking.dto.request.CancelBookingRequest;
import com.ticketing.booking.dto.request.InitiateBookingRequest;
import com.ticketing.booking.dto.request.InitiatePaymentIntentRequest;
import com.ticketing.booking.dto.response.*;
import com.ticketing.booking.entity.Booking;
import com.ticketing.booking.entity.BookingStatus;
import com.ticketing.booking.messaging.publisher.BookingEventPublisher;
import com.ticketing.booking.service.BookingCacheService;
import com.ticketing.booking.service.BookingPersistenceService;
import com.ticketing.booking.service.BookingService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Orchestrates the booking initiation flow and status retrieval.
 *
 * <p>This class contains no business logic — it sequences calls to focused service classes
 * and handles the two failure modes that require compensation before returning to the caller:</p>
 * <ol>
 *   <li><strong>Event Service rejection</strong> — thrown as a {@link FeignException}
 *       before any booking or seat reservation has occurred. Propagates to
 *       {@code GlobalExceptionHandler} which returns a structured error; no compensation needed.</li>
 *   <li><strong>Payment Service failure</strong> — caught here because a PENDING booking
 *       exists and seats have already been deducted by {@code checkAndReserve}.
 *       Both must be compensated: booking is failed and seats are released.</li>
 * </ol>
 *
 * <p><strong>Flow:</strong></p>
 * <pre>
 *   checkAndReserve (Event Service)  ─── deducts seats atomically
 *           ↓ success
 *   createPendingBooking (DB)
 *           ↓ success
 *   initiatePaymentIntent (Payment Service)  ─── creates Stripe PaymentIntent
 *           ↓ success
 *   storePaymentIntentId (DB)
 *           ↓ success
 *   cacheBookingSnapshot (Redis, TTL 30 min)
 *           ↓
 *   return {bookingId, clientSecret, totalAmount} to frontend
 * </pre>
 *
 * <p>If {@code initiatePaymentIntent} fails, the compensating path runs:
 * fail the booking in the DB → release reserved seats in Event Service → return error.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingServiceImpl implements BookingService {

    private final EventServiceClient eventServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final BookingPersistenceService bookingPersistenceService;
    private final BookingCacheService bookingCacheService;
    private final BookingEventPublisher bookingEventPublisher;

    @Value("${cancellation.full-refund-hours}")
    private long fullRefundHours;

    @Value("${cancellation.partial-refund-hours}")
    private long partialRefundHours;

    @Value("${cancellation.partial-refund-percentage}")
    private double partialRefundPercentage;

    // ====================================================================================================
    //                         INITIATE BOOKING
    // ======================================================================================================

    @Override
    public InitiateBookingResponse initiateBooking(InitiateBookingRequest request) {

        // Step 1 — check and reserve seats in Event Service
        // Deducts seats atomically using Redis pre-check + DB pessimistic lock (SELECT … FOR UPDATE).
        // On failure: EventNotFoundException (404) or InsufficientSeatsException (409) → FeignException
        // → GlobalExceptionHandler in this service returns the error to the client.
        // No booking or seats have been modified at this point — no compensation needed.
        ApiResult<EventBookingResponse> eventResult =
                eventServiceClient.checkAndReserve(request.eventId(), request.seatsBooked());

        // Step 2 — create PENDING booking; pricePerSeat snapshotted from Event Service response
        Booking booking = bookingPersistenceService.createPendingBooking(request, eventResult.getData());

        // Step 3 — create Stripe PaymentIntent via Payment Service
        // FeignException is caught here because seats were deducted and a PENDING booking exists —
        // both must be compensated (booking failed, seats released) before returning to the caller
        try {
            //TODO: - use bigdecimal or something like that to avoid floating point issues — this is just a demo
            long amountInSmallestUnit =
                    Math.round((double) booking.getSeatsBooked() * booking.getPricePerSeat() * 100);

            ApiResult<InitiatePaymentIntentResponse> paymentResult =
                    paymentServiceClient.initiatePaymentIntent(new InitiatePaymentIntentRequest(
                            booking.getId(),
                            amountInSmallestUnit,
                            "gbp"   // GBP hardcoded — externalise to event/config when multi-currency is needed
                    ));

            // Step 4 — persist the PaymentIntent ID before returning clientSecret
            // Stored here so we can issue a refund even if the service restarts
            // before the Stripe webhook fires (future cancellation/refund flow)
            booking = bookingPersistenceService.storePaymentIntentId(
                    booking, paymentResult.getData().paymentIntentId());

            // Step 5 — cache booking snapshot for fast access in BookingEventListener and email service
            bookingCacheService.save(new BookingCacheDto(
                    booking.getId(),
                    booking.getUserId(),
                    booking.getEventId(),
                    eventResult.getData().eventName(),
                    booking.getSeatsBooked(),
                    booking.getPricePerSeat(),
                    booking.getRecipientEmail(),
                    booking.getStripePaymentIntentId()
            ));

            // Step 6 — return clientSecret to frontend for stripe.confirmPayment()
            // clientSecret is NOT stored on the entity — it is transient sensitive data
            InitiateBookingResponse response = bookingPersistenceService.toInitiateResponse(
                    booking, paymentResult.getData().clientSecret());

            log.info("Booking initiated: bookingId={} intentId={}",
                    booking.getId(), paymentResult.getData().paymentIntentId());
            return response;

        } catch (FeignException e) {
            // Payment Service call failed — fail the booking immediately
            // Seat release is NOT attempted here — delegated to FailedBookingSeatReleaseScheduler
            // which retries until seatsReleased=true (see scheduler/FailedBookingSeatReleaseScheduler.java)
            log.error("Payment Service call failed for bookingId={}: {}", booking.getId(), e.getMessage());
            bookingPersistenceService.failBooking(booking, "Payment initialisation failed");

            // TODO [Architecture — Seat Release]: Currently handled synchronously via Feign in
            //   FailedBookingSeatReleaseScheduler. Two alternatives worth revisiting at scale:
            //
            //   Option A — Async event (1-way):
            //     Booking Service publishes seats.release.requested
            //     Event Service listens → releases seats internally
            //     Simpler but no confirmation — seatsReleased flag cannot be set reliably
            //
            //   Option B — Async event (2-way):
            //     Booking Service publishes seats.release.requested
            //     Event Service releases seats → publishes seats.release.confirmed
            //     Booking Service listens → sets seatsReleased=true
            //     Architecturally clean but requires: new RELEASE_REQUESTED status to prevent
            //     duplicate events on scheduler retry + confirmation listener + timeout/DLQ handling
            //     Revisit when scale demands it or Event Service is owned by a separate team

            throw new Exception("");
        }

        }
    }
    // =====================================================================================================
    //                         INITIATE BOOKING END
    // ======================================================================================================


// =============================================================================
//                           CANCEL BOOKING
// =============================================================================

    @Override
    public ApiResult<CancelBookingResponse> cancelBooking(Long bookingId, CancelBookingRequest request) {
        Booking booking = bookingPersistenceService.findById(bookingId);
        // EntityNotFoundException propagates → GlobalExceptionHandler → 404

        validateStatusGuards(booking);
        validateCancellationSeats(booking, request);

        if (booking.getBookingStatus() == BookingStatus.PENDING) {
            return processPendingCancellation(booking, request);
        }

        return processConfirmedCancellation(booking, request);
    }

// -----------------------------------------------------------------------------
//                         PRIVATE HELPER METHODS
// -----------------------------------------------------------------------------

    private void validateStatusGuards(Booking booking) {
        // -----------------------------------------------------------------------------
        //                           STATUS GUARDS
        // -----------------------------------------------------------------------------
        String errorMessage = switch (booking.getBookingStatus()) {
            case FAILED -> "Cannot cancel a failed booking";
            case CANCELLED -> "Booking already cancelled";
            case CANCELLATION_REQUESTED -> "Cancellation already in progress, pending admin review";
            case CANCELLATION_FAILED -> "Previous cancellation attempt failed, please contact support";
            case PENDING, CONFIRMED -> null; // Valid states return no error
        };

        if (errorMessage != null) {
            throw new BookingNotCancellableException(errorMessage);
        }
    }

    private void validateCancellationSeats(Booking booking, CancelBookingRequest request) {
        // -----------------------------------------------------------------------------
        //                           VALIDATION
        // -----------------------------------------------------------------------------
        if (request.seatsToCancel() < 1 || request.seatsToCancel() > booking.getActiveSeatCount()) {
            throw new InvalidCancellationRequestException("Invalid number of seats to cancel");
        }
    }

// =============================================================================
//                       PENDING CANCELLATION FLOW
// =============================================================================

    private ApiResult<CancelBookingResponse> processPendingCancellation(Booking booking, CancelBookingRequest request) {
        // -----------------------------------------------------------------------------
        //                      PENDING CANCELLATION FLOW
        // -----------------------------------------------------------------------------

        // Step 1 — Cancel Stripe PaymentIntent via Payment Service
        // Payment Service owns all Stripe interactions — Booking Service never calls Stripe directly
        // Must succeed before restoring seats — if PaymentIntent stays active, user could
        // complete payment after cancellation (paid for a booking with no seats held)
        // FeignException propagates → 503 → caller retries — seats still held, safe state
        paymentServiceClient.cancelPaymentIntent(booking.getStripePaymentIntentId());

        updateBookingForPendingCancellation(booking, request);
        dispatchPendingCancellationEvent(booking, request);

        log.info("PENDING booking cancelled immediately: bookingId={}", booking.getId());

        return ApiResult.of(true, "Booking cancelled",
                new CancelBookingResponse(
                        booking.getId(),
                        booking.getBookingStatus(),
                        "Booking cancelled successfully",
                        null,
                        null
                ));
    }

    private void updateBookingForPendingCancellation(Booking booking, CancelBookingRequest request) {
        int updatedSeatCount = booking.getActiveSeatCount() - request.seatsToCancel();
        booking.setActiveSeatCount(updatedSeatCount);
        booking.setBookingStatus(updatedSeatCount == 0
                ? BookingStatus.CANCELLED
                : BookingStatus.CONFIRMED);
        booking.setCancellationReason(request.cancellationReason());
        bookingPersistenceService.save(booking);
    }

    private void dispatchPendingCancellationEvent(Booking booking, CancelBookingRequest request) {
        bookingEventPublisher.publishBookingCancelled(new BookingCancelledEvent(
                booking.getId(),
                booking.getUserId(),
                booking.getRecipientEmail(),
                RefundType.NO_REFUND,
                BigDecimal.ZERO,
                request.cancellationReason()
        ));
    }

// =============================================================================
//                      CONFIRMED CANCELLATION FLOW
// =============================================================================

    private ApiResult<CancelBookingResponse> processConfirmedCancellation(Booking booking, CancelBookingRequest request) {
        // -----------------------------------------------------------------------------
        //                     CONFIRMED CANCELLATION FLOW
        // -----------------------------------------------------------------------------

        // Live Feign call — NOT using eventDate snapshot on entity
        // Snapshot may be stale if organiser rescheduled — refund policy requires authoritative date
        // If Event Service is down → FeignException propagates → 503 → caller retries
        EventResponse event = eventServiceClient.getEvent(booking.getEventId()).getData();
        long hoursUntilEvent = ChronoUnit.HOURS.between(LocalDateTime.now(), event.getEventDate());

        RefundEstimate estimate = calculateRefundEstimate(booking, request, hoursUntilEvent);

        booking.setBookingStatus(BookingStatus.CANCELLATION_REQUESTED);
        booking.setCancellationReason(request.cancellationReason());
        bookingPersistenceService.save(booking);

        dispatchConfirmedCancellationEvents(booking, request, hoursUntilEvent, estimate);

        log.info("CONFIRMED booking cancellation requested: bookingId={} refundType={} refundAmount={}",
                booking.getId(), estimate.type(), estimate.amount());

        return ApiResult.of(true, "Cancellation request submitted",
                new CancelBookingResponse(
                        booking.getId(),
                        BookingStatus.CANCELLATION_REQUESTED,
                        "Cancellation request submitted for admin review",
                        estimate.type(),
                        estimate.amount()
                ));
    }

    /** Lightweight record to hold the results of our refund math */
    private record RefundEstimate(RefundType type, BigDecimal amount) {}

    private RefundEstimate calculateRefundEstimate(Booking booking, CancelBookingRequest request, long hoursUntilEvent) {
        BigDecimal pricePerSeat = BigDecimal.valueOf(booking.getPricePerSeat());
        BigDecimal seatsToCancel = BigDecimal.valueOf(request.seatsToCancel());

        if (hoursUntilEvent > fullRefundHours) {
            return new RefundEstimate(RefundType.FULL_REFUND, pricePerSeat.multiply(seatsToCancel));
        }

        if (hoursUntilEvent > partialRefundHours) {
            BigDecimal partialAmount = pricePerSeat
                    .multiply(seatsToCancel)
                    .multiply(BigDecimal.valueOf(partialRefundPercentage / 100));
            return new RefundEstimate(RefundType.PARTIAL_REFUND, partialAmount);
        }

        return new RefundEstimate(RefundType.NO_REFUND, BigDecimal.ZERO);
    }

    private void dispatchConfirmedCancellationEvents(Booking booking, CancelBookingRequest request,
                                                     long hoursUntilEvent, RefundEstimate estimate) {
        BigDecimal pricePerSeat = BigDecimal.valueOf(booking.getPricePerSeat());
        BigDecimal seatsToCancel = BigDecimal.valueOf(request.seatsToCancel());

        // Notify admin for review
        bookingEventPublisher.publishCancellationRequested(new CancellationRequestedEvent(
                booking.getId(),
                booking.getUserId(),
                booking.getEventId(),
                request.seatsToCancel(),
                booking.getActiveSeatCount(),
                pricePerSeat,
                pricePerSeat.multiply(seatsToCancel),
                request.cancellationReason(),
                booking.getStripePaymentIntentId(),
                hoursUntilEvent,
                estimate.type(),
                estimate.amount()
        ));

        // Acknowledge user immediately
        bookingEventPublisher.publishCancellationReceived(new CancellationReceivedEvent(
                booking.getId(),
                booking.getUserId(),
                booking.getRecipientEmail(),
                request.cancellationReason()
        ));
    }

// =============================================================================
//                         CANCEL BOOKING END
// =============================================================================

    // =====================================================================================================
    //                         GET BOOKING STATUS
    // ======================================================================================================

    @Override
    public ApiResult<BookingStatusResponse> getBookingStatus(Long bookingId) {
        // EntityNotFoundException from findById propagates to GlobalExceptionHandler → 404 response
        Booking booking = bookingPersistenceService.findById(bookingId);
        return ApiResult.of(true, "Booking status retrieved",
                bookingPersistenceService.toStatusResponse(booking));
    }

    // =====================================================================================================
    //                         GET BOOKING STATUS END
    // ======================================================================================================
}
