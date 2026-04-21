package com.ticketing.booking.service.impl;

import com.ticketing.booking.dto.request.InitiateBookingRequest;
import com.ticketing.booking.dto.response.BookingStatusResponse;
import com.ticketing.booking.dto.response.EventBookingResponse;
import com.ticketing.booking.dto.response.InitiateBookingResponse;
import com.ticketing.booking.entity.Booking;
import com.ticketing.booking.entity.BookingStatus;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.booking.service.BookingPersistenceService;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Implements all database read and write operations for the {@link Booking} entity.
 *
 * <p>Every method is a pure persistence or mapping operation — no business logic lives here.
 * Entity construction, status transitions, and response mapping are all handled in this class
 * so that {@code BookingService} and {@code BookingEventListener} remain orchestrators only.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class BookingPersistenceServiceImpl implements BookingPersistenceService {

    private final BookingRepository bookingRepository;

    @Override
    public Booking createPendingBooking(InitiateBookingRequest request, EventBookingResponse eventBookingResponse) {
        Booking booking = Booking.builder()
                .userId(request.userId())
                .eventId(request.eventId())
                .seatsBooked(request.seatsBooked())
                // Snapshot price and event name from Event Service — locked in at initiation time
                .pricePerSeat(eventBookingResponse.pricePerSeat())
                .paymentMethod(request.paymentMethod())
                .recipientEmail(resolveEmail(request))
                .bookingStatus(BookingStatus.PENDING)
                // stripePaymentIntentId is null here — stored separately after PaymentIntent creation
                .build();

        Booking saved = bookingRepository.save(booking);
        log.debug("Created PENDING booking id={} for userId={} eventId={}",
                saved.getId(), saved.getUserId(), saved.getEventId());
        return saved;
    }

    @Override
    public Booking createFailedBooking(InitiateBookingRequest request, String reason) {
        Booking booking = Booking.builder()
                .userId(request.userId())
                .eventId(request.eventId())
                .seatsBooked(request.seatsBooked())
                // pricePerSeat is 0.0 — Event Service check failed before we got a price snapshot
                .pricePerSeat(0.0)
                .paymentMethod(request.paymentMethod())
                .recipientEmail(resolveEmail(request))
                .bookingStatus(BookingStatus.FAILED)
                .failureReason(reason)
                .build();

        Booking saved = bookingRepository.save(booking);
        log.debug("Created FAILED booking id={} for userId={} eventId={} reason={}",
                saved.getId(), saved.getUserId(), saved.getEventId(), reason);
        return saved;
    }

    @Override
    public Booking storePaymentIntentId(Booking booking, String stripePaymentIntentId) {
        booking.setStripePaymentIntentId(stripePaymentIntentId);
        Booking saved = bookingRepository.save(booking);
        log.debug("Stored PaymentIntent id={} on bookingId={}", stripePaymentIntentId, saved.getId());
        return saved;
    }

    @Override
    public Booking confirmBooking(Booking booking) {
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        Booking saved = bookingRepository.save(booking);
        log.debug("Confirmed bookingId={}", saved.getId());
        return saved;
    }

    @Override
    public Booking failBooking(Booking booking, String reason) {
        booking.setBookingStatus(BookingStatus.FAILED);
        booking.setFailureReason(reason);
        Booking saved = bookingRepository.save(booking);
        log.debug("Failed bookingId={} reason={}", saved.getId(), reason);
        return saved;
    }

    @Override
    public void confirmBookingById(Long bookingId) {
        bookingRepository.updateStatus(bookingId, BookingStatus.CONFIRMED);
        log.debug("Confirmed (targeted update) bookingId={}", bookingId);
    }

    @Override
    public void failBookingById(Long bookingId, String reason) {
        bookingRepository.updateStatusAndReason(bookingId, BookingStatus.FAILED, reason);
        log.debug("Failed (targeted update) bookingId={} reason={}", bookingId, reason);
    }

    @Override
    public Booking findById(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> {
                    log.error("Booking not found for id={}", bookingId);
                    return new EntityNotFoundException("Booking not found: " + bookingId);
                });
    }

    @Override
    public InitiateBookingResponse toInitiateResponse(Booking booking, String clientSecret) {
        return InitiateBookingResponse.builder()
                .bookingId(booking.getId())
                // clientSecret is NOT stored on the entity — passed through here only
                .clientSecret(clientSecret)
                // totalAmount is derived, not stored — seatsBooked * pricePerSeat
                .totalAmount((double) booking.getSeatsBooked() * booking.getPricePerSeat())
                .build();
    }

    @Override
    public BookingStatusResponse toStatusResponse(Booking booking) {
        return BookingStatusResponse.builder()
                .bookingId(booking.getId())
                .bookingStatus(booking.getBookingStatus())
                // failureReason is null for PENDING and CONFIRMED — that is intentional
                .failureReason(booking.getFailureReason())
                .build();
    }

    /**
     * Marks seats as released after successful compensation by the scheduler.
     * Called only by FailedBookingSeatReleaseScheduler.
     */
    @Override
    public void markSeatsReleased(Booking booking) {
        booking.setSeatsReleased(true);
        bookingRepository.save(booking);
        log.info("Seats marked as released: bookingId={}", booking.getId());
    }

    /**
     * Marks reminder as sent to prevent duplicate emails on scheduler retry.
     * Called only by PendingBookingReminderScheduler.
     */
    @Override
    public void markReminderSent(Booking booking) {
        booking.setReminderSent(true);
        bookingRepository.save(booking);
        log.info("Reminder marked as sent: bookingId={}", booking.getId());
    }

    /**
     * Resolves the recipient email for notifications.
     * Uses the email provided in the request if present; otherwise falls back to a placeholder.
     * TODO: fetch from User Service when inter-service user lookup is wired.
     */
    private String resolveEmail(InitiateBookingRequest request) {
        return request.recipientEmail() != null
                ? request.recipientEmail()
                : "noreply@sportticketing.com";
    }
}