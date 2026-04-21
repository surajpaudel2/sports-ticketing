package com.ticketing.booking.scheduler;

import com.ticketing.booking.entity.Booking;
import com.ticketing.booking.entity.BookingStatus;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.booking.service.BookingPersistenceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Expires PENDING bookings where the user did not complete payment within the allowed window.
 *
 * <p>A booking is created as PENDING when {@code initiateBooking} succeeds and a Stripe
 * PaymentIntent is created. The frontend receives a {@code clientSecret} and the user is
 * expected to complete payment via {@code stripe.confirmPayment()}. If they abandon the
 * flow, this scheduler detects stale PENDING bookings and transitions them to FAILED.</p>
 *
 * <p>Expired bookings have {@code seatsReleased=false} by default —
 * {@link FailedBookingSeatReleaseScheduler} picks them up and restores seats.</p>
 *
 * <p>Stripe PaymentIntent cancellation is deferred — see TODO below.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PendingBookingExpiryScheduler {

    private final BookingRepository bookingRepository;
    private final BookingPersistenceService bookingPersistenceService;

    @Value("${booking.pending-expiry-minutes}")
    private int pendingExpiryMinutes;

    // =============================================================================
    //                       SCHEDULER EXECUTION
    // =============================================================================

    @Scheduled(fixedDelayString = "${booking.scheduler.expiry-interval-ms}")
    public void expireStaleBookings() {
        List<Booking> staleBookings = fetchStaleBookings();

        if (staleBookings.isEmpty()) return;

        log.info("PendingBookingExpiryScheduler: expiring {} booking(s)", staleBookings.size());

        staleBookings.forEach(this::expireBooking);
    }

    // -----------------------------------------------------------------------------
    //                         PRIVATE HELPER METHODS
    // -----------------------------------------------------------------------------

    private List<Booking> fetchStaleBookings() {
        LocalDateTime expiryThreshold = LocalDateTime.now().minusMinutes(pendingExpiryMinutes);
        return bookingRepository.findAllByBookingStatusAndCreatedAtBefore(BookingStatus.PENDING, expiryThreshold);
    }

    private void expireBooking(Booking booking) {
        // TODO: Cancel Stripe PaymentIntent before failing the booking
        //   stripe.paymentIntents.cancel(booking.getStripePaymentIntentId())
        //   Prevents accidental late payments completing after expiry
        // Release seats in event.

        String expiryReason = "Booking expired — payment not completed within " + pendingExpiryMinutes + " minutes";
        bookingPersistenceService.failBooking(booking, expiryReason);

        log.info("Booking expired: bookingId={} createdAt={}", booking.getId(), booking.getCreatedAt());
    }
}