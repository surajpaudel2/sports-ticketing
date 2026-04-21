package com.ticketing.booking.scheduler;

import com.ticketing.booking.client.EventServiceClient;
import com.ticketing.booking.entity.Booking;
import com.ticketing.booking.entity.BookingStatus;
import com.ticketing.booking.repository.BookingRepository;
import com.ticketing.booking.service.BookingPersistenceService;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Compensating scheduler for FAILED bookings where seat release has not yet completed.
 *
 * <p>Seats are deducted atomically in Event Service during {@code checkAndReserve}.
 * If the subsequent payment initiation fails, the booking is marked FAILED but seat
 * release may not have occurred (e.g. Event Service was unreachable at compensation time).
 * This scheduler retries seat release until {@code seatsReleased=true}.</p>
 *
 * <p>Also handles bookings expired by {@link PendingBookingExpiryScheduler} —
 * those are transitioned to FAILED with {@code seatsReleased=false},
 * making them eligible for pickup here.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FailedBookingSeatReleaseScheduler {

    private final BookingRepository bookingRepository;
    private final EventServiceClient eventServiceClient;
    private final BookingPersistenceService bookingPersistenceService;

    // =============================================================================
    //                       SCHEDULER EXECUTION
    // =============================================================================

    @Scheduled(fixedDelayString = "${booking.scheduler.seat-release-interval-ms}")
    public void releaseSeatsForFailedBookings() {
        List<Booking> bookings = fetchPendingSeatReleases();

        if (bookings.isEmpty()) return;

        log.info("FailedBookingSeatReleaseScheduler: processing {} booking(s)", bookings.size());

        bookings.forEach(this::processSeatRelease);
    }

    // -----------------------------------------------------------------------------
    //                         PRIVATE HELPER METHODS
    // -----------------------------------------------------------------------------

    private List<Booking> fetchPendingSeatReleases() {
        return bookingRepository.findAllByBookingStatusAndSeatsReleasedFalse(BookingStatus.FAILED);
    }

    private void processSeatRelease(Booking booking) {
        try {
            eventServiceClient.releaseSeats(booking.getEventId(), booking.getSeatsBooked());
            bookingPersistenceService.markSeatsReleased(booking);
            log.info("Seats released: bookingId={} eventId={} seats={}",
                    booking.getId(), booking.getEventId(), booking.getSeatsBooked());
        } catch (FeignException e) {
            log.error("Failed to release seats — will retry next tick: " +
                            "bookingId={} eventId={} seats={}: {}",
                    booking.getId(), booking.getEventId(), booking.getSeatsBooked(),
                    e.getMessage());
        }
    }
}