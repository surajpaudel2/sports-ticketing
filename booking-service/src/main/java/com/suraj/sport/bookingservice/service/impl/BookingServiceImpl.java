package com.suraj.sport.bookingservice.service.impl;

import com.suraj.sport.bookingservice.dto.request.CancelBookingRequest;
import com.suraj.sport.bookingservice.dto.request.CreateBookingRequest;
import com.suraj.sport.bookingservice.dto.response.BookingResponse;
import com.suraj.sport.bookingservice.dto.response.CreateBookingResponse;
import com.suraj.sport.bookingservice.entity.Booking;
import com.suraj.sport.bookingservice.entity.BookingStatus;
import com.suraj.sport.bookingservice.exception.*;
import com.suraj.sport.bookingservice.mapper.BookingMapper;
import com.suraj.sport.bookingservice.repository.BookingRepository;
import com.suraj.sport.bookingservice.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;

    // =====================================================================
    // CREATE BOOKING
    // =====================================================================

    /**
     * Creates a new booking for a sports event.
     * <p>
     * Flow:
     * 1. Check seats available in Event Service
     * 2. Deduct seats in Event Service immediately
     * 3. Create booking with status PENDING
     * 4. Initiate payment with status PENDING
     * 5. Payment success → booking CONFIRMED
     * 6. Payment failed → restore seats in Event Service → booking stays PENDING
     * <p>
     * Restrictions:
     * - Multiple bookings per user per event are allowed — each is a separate record
     * - Booking stays PENDING until payment is confirmed
     * <p>
     * FIXME: Race condition — two users may check seats simultaneously and both succeed.
     *   Option A: Optimistic locking (@Version on Event entity)
     *   Option B: Pessimistic locking
     *   Option C: Redis distributed lock — most scalable
     *   Revisit when Redis is introduced.
     * <p>
     * FIXME: Distributed transaction — if seat deduction succeeds but booking save fails,
     *   seats are deducted but no booking exists. Implement SAGA pattern in Section 14.
     * <p>
     * FIXME: Seat locking window — seats are deducted then restored on payment failure.
     *   This creates a temporary window where seats are unavailable to other users.
     *   Proper solution: Redis TTL-based seat locking. Revisit when Redis is introduced.
     */
    @Override
    public CreateBookingResponse createBooking(CreateBookingRequest request) {

        // TODO: checkSeatsAvailability(request.getEventId(), request.getSeatsBooked())
        // Call Event Service to verify requested seats are available
        // Throw InsufficientSeatsException if not enough seats available
        // Throw EventNotFoundException if event does not exist
        // Throw EventNotBookableException if event is COMPLETED or CANCELLED

        // TODO: deductSeats(request.getEventId(), request.getSeatsBooked())
        // Call Event Service to deduct requested seats immediately
        // Only proceed if deduction is successful

        // TODO: fetchPricePerSeat(request.getEventId())
        // Call Event Service to get current price per seat for total amount calculation
        double pricePerSeat = 0.0; // STUB — replace with actual price from Event Service

        // Create booking with PENDING status
        Booking booking = BookingMapper.mapToBooking(request, pricePerSeat);
        Booking savedBooking = bookingRepository.save(booking);

        // TODO: initiatePayment(savedBooking)
        // Call Payment Service to create a PENDING payment record
        // On success → update booking status to CONFIRMED, set paymentId
        // On failure → restoreSeats(request.getEventId(), request.getSeatsBooked())
        //              keep booking as PENDING, user can retry payment later

        // TODO: notifyUser(savedBooking)
        // Notify user via Notification Service that booking is created
        // Include event details, seats booked, total amount and payment instructions

        return BookingMapper.mapToCreateBookingResponse(savedBooking);
    }

    // =====================================================================
    // CANCEL BOOKING
    // =====================================================================

    /**
     * Cancels an existing PENDING or CONFIRMED booking.
     * <p>
     * Flow:
     * 1. Booking must exist → else BookingNotFoundException
     * 2. Booking must be PENDING or CONFIRMED → else BookingNotCancellableException
     * 3. Check cancellationDeadline from Event Service — if past deadline → throw exception
     * 4. Update booking status to CANCELLED with cancellation reason
     * 5. Restore seats in Event Service, if the booking status was CONFIRMED
     * 6. Trigger refund in Payment Service if booking was CONFIRMED
     * <p>
     * FIXME: Refund logic — refund amount may vary based on cancellation policy.
     *   e.g. full refund before deadline, partial refund after.
     *   Business decision needed — revisit when Payment Service is wired.
     * <p>
     * FIXME: Distributed transaction — if booking cancellation succeeds but seat
     *   restoration fails, booking is cancelled but seats not restored.
     *   Implement SAGA pattern in Section 14.
     */
    @Override
    public BookingResponse cancelBooking(Long bookingId, CancelBookingRequest request) {

        // Edge case: booking must exist
        Booking booking = findBookingOrThrow(bookingId);

        // Edge case: only PENDING or CONFIRMED bookings can be cancelled
        validateBookingIsCancellable(booking);

        // TODO: checkCancellationDeadline(booking.getEventId())
        // Call Event Service to get cancellationDeadline for the event
        // If current date is past the deadline → throw BookingNotCancellableException
        // with message informing the user of the deadline

        // Update booking status to CANCELLED
        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(request.getCancellationReason());
        Booking savedBooking = bookingRepository.save(booking);

        // TODO: restoreSeats(booking.getEventId(), booking.getSeatsBooked())
        // Only restore seats if booking was CONFIRMED — PENDING bookings already
        // had their seats restored when payment failed during creation
//        if (previousStatus == BookingStatus.CONFIRMED) {
//            // Call Event Service to restore seats
//        }

        // TODO: processRefund(booking)
        // Call Payment Service to trigger refund if booking was CONFIRMED
        // Refund amount depends on cancellation policy — full or partial

        // TODO: notifyUser(savedBooking)
        // Notify user via Notification Service that booking has been cancelled
        // Include refund details if applicable

        return BookingMapper.mapToBookingResponse(savedBooking);
    }

    // =====================================================================
    // RETRY PAYMENT (PENDING -> CONFIRMED)
    // =====================================================================

    /**
     * Retries payment for a PENDING booking.
     * <p>
     * Flow:
     * 1. Booking must be PENDING → else BookingNotRetryableException
     * 2. Re-check seats availability — someone may have booked in between
     * 3. Re-deduct seats in Event Service
     * 4. Retry payment via Payment Service
     * 5. Payment success → booking CONFIRMED
     * 6. Payment failed → restore seats → stays PENDING
     * <p>
     * Note: Seats are re-checked and re-deducted on every retry because between
     * the original PENDING state and the retry, seats may have been restored
     * (due to payment failure) and taken by another user.
     * <p>
     * TODO: implementPendingBookingScheduler()
     * Scheduler to auto-cancel and soft delete PENDING bookings after event ends.
     * Revisit when eventEndDate is added to Event Service.
     */
    @Override
    public BookingResponse retryPayment(Long bookingId) {

        // Edge case: booking must exist
        Booking booking = findBookingOrThrow(bookingId);

        // Edge case: only PENDING bookings can retry payment
        if (booking.getBookingStatus() != BookingStatus.PENDING) {
            throw new BookingNotRetryableException(
                    "Only PENDING bookings can retry payment. Current status: " + booking.getBookingStatus());
        }

        // TODO: checkSeatsAvailability(booking.getEventId(), booking.getSeatsBooked())
        // Re-check seats — someone else may have booked in between
        // Throw InsufficientSeatsException if seats no longer available

        // TODO: deductSeats(booking.getEventId(), booking.getSeatsBooked())
        // Re-deduct seats in Event Service before retrying payment

        // TODO: retryPayment(booking)
        // Call Payment Service to retry payment for this booking
        // On success → set bookingStatus = CONFIRMED, set paymentId
        // On failure → restoreSeats(booking.getEventId(), booking.getSeatsBooked())
        //              keep bookingStatus = PENDING, user can retry again

        // TODO: notifyUser(booking)
        // Notify user of payment retry result via Notification Service

        return BookingMapper.mapToBookingResponse(booking);
    }

    // =====================================================================
    // REBOOK (CANCELLED -> PENDING -> CONFIRMED)
    // =====================================================================

    /**
     * Re-books a previously cancelled booking.
     * Treated as a completely fresh booking — checks availability and redoes payment.
     * <p>
     * Flow:
     * 1. Booking must be CANCELLED → else BookingNotRebookableException
     * 2. Check event is still available — call Event Service
     * 3. Check seats available — call Event Service
     * 4. Deduct seats — call Event Service
     * 5. Reset booking to PENDING, clear cancellationReason and paymentId
     * 6. Initiate new payment — call Payment Service
     * <p>
     * FIXME: Distributed transaction — same risks as createBooking.
     *   Implement SAGA pattern in Section 14.
     */
    @Override
    public BookingResponse reBook(Long bookingId) {

        // Edge case: booking must exist
        Booking booking = findBookingOrThrow(bookingId);

        // Edge case: only CANCELLED bookings can be re-booked
        if (booking.getBookingStatus() != BookingStatus.CANCELLED) {
            throw new BookingNotRebookableException(
                    "Only CANCELLED bookings can be re-booked. Current status: " + booking.getBookingStatus());
        }

        // TODO: checkEventAvailability(booking.getEventId())
        // Call Event Service to verify event is still UPCOMING or ONGOING
        // Throw EventNotBookableException if event is COMPLETED or CANCELLED

        // TODO: checkSeatsAvailability(booking.getEventId(), booking.getSeatsBooked())
        // Call Event Service to verify requested seats are still available
        // Throw InsufficientSeatsException if not enough seats available

        // TODO: deductSeats(booking.getEventId(), booking.getSeatsBooked())
        // Call Event Service to deduct seats again for this re-booking

        // Reset booking to PENDING — treated as fresh booking
        booking.setBookingStatus(BookingStatus.PENDING);
        booking.setCancellationReason(null);
        booking.setPaymentId(null);
        Booking savedBooking = bookingRepository.save(booking);

        // TODO: initiatePayment(savedBooking)
        // Call Payment Service to create a new PENDING payment record
        // On success → set bookingStatus = CONFIRMED, set paymentId
        // On failure → restoreSeats(booking.getEventId(), booking.getSeatsBooked())
        //              keep bookingStatus = PENDING, user can retry payment later

        // TODO: notifyUser(savedBooking)
        // Notify user that re-booking is initiated and payment is pending

        return BookingMapper.mapToBookingResponse(savedBooking);
    }

    // =====================================================================
    // GET BOOKING BY ID
    // =====================================================================

    /**
     * Retrieves a booking by its unique ID.
     * <p>
     * Restrictions:
     * - Throws BookingNotFoundException if no booking exists with the given ID
     * - Soft deleted bookings are currently returned — once auth/roles are implemented
     * in Section 12, regular users should not see soft deleted bookings.
     * <p>
     * TODO: implementCaching()
     * Consider caching frequently accessed bookings using Redis.
     * Revisit when Redis is introduced.
     */
    @Override
    public BookingResponse getBookingById(Long bookingId) {

        // Edge case: booking must exist
        Booking booking = findBookingOrThrow(bookingId);

        return BookingMapper.mapToBookingResponse(booking);
    }

    // =====================================================================
    // GET ALL BOOKINGS BY USER ID
    // =====================================================================

    /**
     * Retrieves all bookings for a specific user.
     * <p>
     * TODO: implementPagination()
     * Current implementation returns all bookings at once which is not scalable.
     * Implement cursor/keyset pagination — revisit when pagination is added to Event Service.
     * <p>
     * TODO: implementFiltering()
     * Add filtering by bookingStatus (PENDING, CONFIRMED, CANCELLED) so users
     * can filter their booking history effectively.
     * <p>
     * TODO: softDeleteVisibility()
     * Once auth/roles are implemented in Section 12, exclude soft deleted bookings
     * for regular users. Repository query will change to findAllByUserIdAndIsDeletedFalse().
     */
    @Override
    public List<BookingResponse> getAllBookingsByUserId(Long userId) {

        // TODO: Replace with paginated and filtered query once implemented
        // TODO: Replace findAllByUserId with findAllByUserIdAndIsDeletedFalse once auth/roles added
        return bookingRepository.findAllByUserId(userId)
                .stream()
                .map(BookingMapper::mapToBookingResponse)
                .collect(Collectors.toList());
    }

    // =====================================================================
    // PRIVATE HELPER METHODS
    // =====================================================================

    /**
     * Fetches the booking by ID or throws BookingNotFoundException if not found.
     */
    private Booking findBookingOrThrow(Long bookingId) {
        return bookingRepository.findById(bookingId)
                .orElseThrow(() -> new BookingNotFoundException(bookingId));
    }

    /**
     * Validates that the booking can be cancelled.
     * Only PENDING or CONFIRMED bookings are cancellable.
     * CANCELLED bookings cannot be cancelled again.
     */
    private void validateBookingIsCancellable(Booking booking) {
        if (booking.getBookingStatus() == BookingStatus.CANCELLED) {
            throw new BookingNotCancellableException("Booking is already cancelled");
        }
    }
}