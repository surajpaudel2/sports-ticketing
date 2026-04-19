package com.ticketing.booking.service.impl;

import com.ticketing.booking.client.EventServiceClient;
import com.ticketing.booking.client.PaymentServiceClient;
import com.ticketing.booking.dto.cache.BookingCacheDto;
import com.ticketing.booking.dto.request.InitiateBookingRequest;
import com.ticketing.booking.dto.request.InitiatePaymentIntentRequest;
import com.ticketing.booking.dto.response.*;
import com.ticketing.booking.entity.Booking;
import com.ticketing.booking.service.BookingCacheService;
import com.ticketing.booking.service.BookingPersistenceService;
import com.ticketing.booking.service.BookingService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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

    @Override
    public ApiResult<InitiateBookingResponse> initiateBooking(InitiateBookingRequest request) {

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

            // TODO - try to remove event name from the db. It is only used for email content, and we can fetch it from the event service when we consume the payment success event. This way we can avoid data duplication and potential inconsistencies if the event name changes after the booking is initiated but before the payment success event is consumed.
            // Step 5 — cache booking snapshot for fast access in BookingEventListener and email service
            bookingCacheService.save(new BookingCacheDto(
                    booking.getId(),
                    booking.getUserId(),
                    booking.getEventId(),
                    booking.getEventName(),
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
            return ApiResult.of(true, "Booking initiated successfully", response);

        } catch (FeignException e) {
            // Payment Service call failed — compensate: fail the booking and release the reserved seats
            log.error("Payment Service call failed for bookingId={}: {}", booking.getId(), e.getMessage());
            bookingPersistenceService.failBooking(booking, "Payment initialisation failed");

            // Release seats that were deducted in checkAndReserve — compensating transaction
            // Failure here is logged but not rethrown — the booking is already failed and the
            // seat release failure must be resolved operationally (alert/manual review)
            try {
                eventServiceClient.releaseSeats(booking.getEventId(), booking.getSeatsBooked());
                log.info("Seats released after payment init failure: bookingId={} eventId={}",
                        booking.getId(), booking.getEventId());
            } catch (FeignException releaseEx) {
                log.error("CRITICAL: Failed to release seats after payment init failure — " +
                        "bookingId={} eventId={} seats={} — manual intervention required: {}",
                        booking.getId(), booking.getEventId(), booking.getSeatsBooked(),
                        releaseEx.getMessage());
            }

            return ApiResult.of(false, "Unable to initialise payment. Please try again.", null);
        }
    }

    @Override
    public ApiResult<BookingStatusResponse> getBookingStatus(Long bookingId) {
        // EntityNotFoundException from findById propagates to GlobalExceptionHandler → 404 response
        Booking booking = bookingPersistenceService.findById(bookingId);
        return ApiResult.of(true, "Booking status retrieved",
                bookingPersistenceService.toStatusResponse(booking));
    }
}
