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
 * Sends payment reminder emails to users with PENDING bookings approaching expiry.
 *
 * <p>Fires when a booking has been PENDING for longer than the reminder threshold
 * (default: 10 minutes) but has not yet reached the expiry threshold (15 minutes).
 * This gives the user a 5-minute warning before their seats are released.</p>
 *
 * <p>{@code reminderSent=true} is set after publishing the notification event
 * to prevent duplicate emails on subsequent scheduler ticks.</p>
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PendingBookingReminderScheduler {

    private final BookingRepository bookingRepository;
    private final BookingPersistenceService bookingPersistenceService;

    // TODO: inject BookingEventPublisher when notification event is wired
    // private final BookingEventPublisher bookingEventPublisher;

    @Value("${booking.pending-expiry-minutes}")
    private int pendingExpiryMinutes;

    @Value("${booking.pending-reminder-minutes}")
    private int pendingReminderMinutes;

    // =============================================================================
    //                       SCHEDULER EXECUTION
    // =============================================================================

    @Scheduled(fixedDelayString = "${booking.scheduler.reminder-interval-ms}")
    public void sendPaymentReminders() {
        List<Booking> bookings = fetchBookingsDueForReminder();

        if (bookings.isEmpty()) return;

        log.info("PendingBookingReminderScheduler: sending reminders for {} booking(s)", bookings.size());

        bookings.forEach(this::processReminder);
    }

    // -----------------------------------------------------------------------------
    //                         PRIVATE HELPER METHODS
    // -----------------------------------------------------------------------------

    private List<Booking> fetchBookingsDueForReminder() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime reminderThreshold = now.minusMinutes(pendingReminderMinutes);
        LocalDateTime expiryThreshold = now.minusMinutes(pendingExpiryMinutes);

        // Find bookings: PENDING + past reminder window + not yet expired + reminder not sent
        return bookingRepository.findAllByBookingStatusAndCreatedAtBetweenAndReminderSentFalse(
                BookingStatus.PENDING,
                expiryThreshold,    // start — not yet expired
                reminderThreshold   // end — past reminder window
        );
    }

    private void processReminder(Booking booking) {
        // TODO: publish notification event when BookingEventPublisher is wired
        //   bookingEventPublisher.publishPaymentReminder(booking, expiresAt)
        //   payload: { bookingId, userId, recipientEmail, expiresAt }
        //   email: "You have X minutes left to complete your booking. Your seats will be released at {expiresAt}."

        log.info("Payment reminder due: bookingId={} recipientEmail={} expiresAt={}",
                booking.getId(), booking.getRecipientEmail(),
                booking.getCreatedAt().plusMinutes(pendingExpiryMinutes));

        bookingPersistenceService.markReminderSent(booking);
    }
}