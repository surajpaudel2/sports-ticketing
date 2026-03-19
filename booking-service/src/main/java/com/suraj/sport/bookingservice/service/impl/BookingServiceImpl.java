package com.suraj.sport.bookingservice.service.impl;

import com.suraj.sport.bookingservice.client.EventServiceClient;
import com.suraj.sport.bookingservice.client.NotificationServiceClient;
import com.suraj.sport.bookingservice.client.PaymentServiceClient;
import com.suraj.sport.bookingservice.dto.request.*;
import com.suraj.sport.bookingservice.dto.response.*;
import com.suraj.sport.bookingservice.entity.Booking;
import com.suraj.sport.bookingservice.entity.BookingStatus;
import com.suraj.sport.bookingservice.exception.*;
import com.suraj.sport.bookingservice.mapper.BookingMapper;
import com.suraj.sport.bookingservice.repository.BookingRepository;
import com.suraj.sport.bookingservice.service.BookingService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BookingServiceImpl implements BookingService {

    private final BookingRepository bookingRepository;
    private final EventServiceClient eventServiceClient;
    private final PaymentServiceClient paymentServiceClient;
    private final NotificationServiceClient notificationServiceClient;

    // =====================================================================
    // CREATE BOOKING
    // =====================================================================

    /**
     * Creates a new booking for a sports event.
     * <p>
     * Flow:
     * 1. Fetch event details and validate — event must exist, be bookable, and have enough seats
     * 2. Deduct seats in Event Service immediately
     * 3. Create booking with status PENDING
     * 4. Initiate payment via Payment Service
     * 5. Payment success → booking CONFIRMED, notify user
     * 6. Payment failed → restore seats in Event Service, notify user, booking stays PENDING
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

        // Step 1: Fetch and validate event — checks existence, status, and seat availability
        EventClientResponse event = fetchAndValidateEvent(request);

        // Step 2: Deduct seats in Event Service immediately before creating booking
        // FIXME: Race condition — see Javadoc above
        eventServiceClient.reduceSeats(request.getEventId(), request.getSeatsBooked());

        // Step 3: Create booking with PENDING status — price fetched from Event Service above
        // FIXME: Distributed transaction — see Javadoc above
        Booking savedBooking = createPendingBooking(request, event);

        // Step 4: Initiate payment via Payment Service
        // Payment Service trusts that seats are already handled — no re-check needed here
        PaymentClientResponse payment = processPayment(request, savedBooking);

        // Step 5: Handle payment result
        // Success → confirm booking, notify user
        // Failure → restore seats, notify user, booking stays PENDING for retry
        if (isPaymentSuccessful(payment)) {
            savedBooking = handlePaymentSuccess(savedBooking, event, request, payment);
        } else {
            handlePaymentFailure(savedBooking, event, request);
        }

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
        // Requires cancellationDeadline field to be added to Event entity first
        // Revisit when Event Service is updated with cancellationDeadline

        // Capture previous status before cancelling — needed for seat restoration and refund
        // CONFIRMED → restore seats + trigger refund
        // PENDING   → seats already restored when payment failed, no refund needed
        BookingStatus previousStatus = booking.getBookingStatus();

        // Step 1: Restore seats FIRST — before saving cancellation to DB
        // ARCHITECTURAL DECISION: Seats must be restored before cancelling booking.
        // If we cancel booking first and seat restoration fails — booking is cancelled
        // but seats are never restored — permanent data corruption.
        // By restoring seats first — if it fails, booking stays CONFIRMED and
        // user can retry cancellation safely.
        // FIXME: Distributed transaction — if seat restoration succeeds but booking
        //   save fails — seats are restored but booking is still CONFIRMED.
        //   Implement SAGA pattern in Section 14.
        if (previousStatus == BookingStatus.CONFIRMED) {
            restoreSeatsOnCancellation(booking);
        }

        // Step 2: Cancel booking in DB — only after seats are safely restored
        booking.setBookingStatus(BookingStatus.CANCELLED);
        booking.setCancellationReason(request.getCancellationReason());
        Booking savedBooking = bookingRepository.save(booking);

        // Step 3: Trigger refund and notify — CONFIRMED bookings only
        if (previousStatus == BookingStatus.CONFIRMED) {
            triggerRefundAndNotify(savedBooking, request);
        } else {
            // PENDING booking — no refund, just notify cancellation
            sendCancellationNotification(savedBooking, request, null);
        }

        return BookingMapper.mapToBookingResponse(savedBooking);
    }

    // =====================================================================
    // RETRY PAYMENT (PENDING -> CONFIRMED)
    // =====================================================================

    /**
     * Retries payment for a PENDING booking.
     *
     * Flow:
     *   1. Booking must exist → else BookingNotFoundException
     *   2. Booking must be PENDING → else BookingNotRetryableException
     *   3. Re-validate event — fetch event details and check availability
     *   4. Re-deduct seats — seats were restored when previous payment failed
     *   5. Retry payment via Payment Service
     *   6. Payment success → booking CONFIRMED, notify user
     *   7. Payment failed → restore seats, notify user, booking stays PENDING
     *
     * ARCHITECTURAL DECISION — Why we re-check and re-deduct seats on retry:
     *   When previous payment failed, seats were restored to Event Service.
     *   Between the original PENDING state and this retry, another user may have
     *   booked those seats. We cannot assume seats are still available.
     *   Therefore we treat every retry like a fresh seat deduction attempt.
     *   This is different from initiatePayment where Booking Service already
     *   handled seats before calling Payment Service.
     *
     * TODO: implementPendingBookingScheduler()
     *   Scheduler to auto-cancel and soft delete PENDING bookings after event ends.
     *   Revisit when eventEndDate is added to Event Service.
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

        // Step 3: Re-validate event and re-check seat availability
        // Seats were restored when previous payment failed — must re-check
        EventClientResponse event = fetchAndValidateEventForRetry(booking);

        // Step 4: Re-deduct seats before retrying payment
        // FIXME: Race condition — same risk as createBooking. Revisit with Redis.
        eventServiceClient.reduceSeats(booking.getEventId(), booking.getSeatsBooked());

        // Step 5: Retry payment via Payment Service
        // Build payment request from existing booking data — no new request needed
        PaymentClientResponse payment = retryPaymentWithGateway(booking);

        // Step 6 + 7: Handle payment result
        // Success → confirm booking, notify user
        // Failure → restore seats, notify user, booking stays PENDING
        if (isPaymentSuccessful(payment)) {
            booking = handleRetryPaymentSuccess(booking, event, payment);
        } else {
            handleRetryPaymentFailure(booking, event);
        }

        return BookingMapper.mapToBookingResponse(booking);
    }

    // =====================================================================
    // REBOOK (CANCELLED -> PENDING -> CONFIRMED)
    // =====================================================================

    /**
     * Re-books a previously cancelled booking.
     * Treated as a completely fresh booking — checks availability and redoes payment.
     *
     * Flow:
     *   1. Booking must exist → else BookingNotFoundException
     *   2. Booking must be CANCELLED → else BookingNotRebookableException
     *   3. Fetch and validate event — must exist, be bookable, and have enough seats
     *   4. Deduct seats in Event Service — treated as fresh booking
     *   5. Reset booking to PENDING, clear cancellationReason and paymentId
     *   6. Initiate new payment via Payment Service
     *   7. Payment success → booking CONFIRMED, notify user
     *   8. Payment failed → restore seats, notify user, booking stays PENDING
     *
     * ARCHITECTURAL DECISION — Why reBook is treated as a fresh booking:
     *   When booking was cancelled, seats were restored (if CONFIRMED) or were
     *   already available (if PENDING). We cannot assume seats are still available.
     *   Therefore reBook follows the exact same flow as createBooking for seat
     *   management and payment processing.
     *
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

        // Step 3: Fetch and validate event — same validation as createBooking
        // Event may have changed since original booking — must re-validate
        EventClientResponse event = fetchAndValidateEventForRetry(booking);

        // Step 4: Deduct seats — treated as fresh booking
        // FIXME: Race condition — same risk as createBooking. Revisit with Redis.
        eventServiceClient.reduceSeats(booking.getEventId(), booking.getSeatsBooked());

        // Step 5: Reset booking to PENDING — clear previous cancellation data
        // FIXME: Distributed transaction — if seat deduction succeeds but booking save fails,
        //   seats are deducted but booking stays CANCELLED. Implement SAGA pattern in Section 14.
        booking.setBookingStatus(BookingStatus.PENDING);
        booking.setCancellationReason(null);
        booking.setPaymentId(null);
        Booking savedBooking = bookingRepository.save(booking);

        // Step 6: Initiate new payment via Payment Service
        // Payment Service trusts that seats are already handled here
        PaymentClientResponse payment = retryPaymentWithGateway(savedBooking);

        // Step 7 + 8: Handle payment result
        // Success → confirm booking, notify user
        // Failure → restore seats, notify user, booking stays PENDING for retry
        if (isPaymentSuccessful(payment)) {
            savedBooking = handleRetryPaymentSuccess(savedBooking, event, payment);
        } else {
            handleRetryPaymentFailure(savedBooking, event);
        }

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

    /**
     * Fetches event details from Event Service and validates it is bookable.
     *
     * Validations:
     *   - Event must exist → else EventNotFoundException
     *   - Event must be UPCOMING or ONGOING → else EventNotBookableException
     *   - Available seats must be >= requested seats → else InsufficientSeatsException
     *
     * Note: Uses Yoda conditions ("CONSTANT".equals(variable)) to prevent
     * NullPointerExceptions when comparing status strings.
     */
    private EventClientResponse fetchAndValidateEvent(CreateBookingRequest request) {
        ApiResult<EventClientResponse> eventResult = eventServiceClient.getEventById(request.getEventId());
        EventClientResponse event = eventResult.getData();

        if (event == null) {
            throw new EventNotFoundException("Event not found with id: " + request.getEventId());
        }

        // Yoda conditions ("CONSTANT".equals(variable)) prevent NullPointerExceptions
        if ("CANCELLED".equals(event.status()) || "COMPLETED".equals(event.status())) {
            throw new EventNotBookableException("Event is not bookable. Current status: " + event.status());
        }

        if (event.availableSeats() < request.getSeatsBooked()) {
            throw new InsufficientSeatsException(
                    String.format("Not enough seats available. Requested: %d, Available: %d",
                            request.getSeatsBooked(), event.availableSeats()));
        }

        return event;
    }

    /**
     * Creates a booking record with PENDING status and saves it to the database.
     * Price per seat is fetched from Event Service — not from request — to ensure
     * we always use the current event price at booking time.
     */
    private Booking createPendingBooking(CreateBookingRequest request, EventClientResponse event) {
        Booking booking = BookingMapper.mapToBooking(request, event.pricePerSeat());
        return bookingRepository.save(booking);
    }

    /**
     * Initiates payment via Payment Service.
     * Builds the payment request from the saved booking and calls Payment Service.
     *
     * Note: Payment Service trusts that seats are already deducted at this point.
     * No seat validation is done in Payment Service for initial payment creation.
     */
    private PaymentClientResponse processPayment(CreateBookingRequest request, Booking savedBooking) {
        InitiatePaymentRequest paymentRequest = InitiatePaymentRequest.builder()
                .bookingId(savedBooking.getId())
                .eventId(savedBooking.getEventId())
                .userId(savedBooking.getUserId())
                .amount(savedBooking.getTotalAmount())
                .paymentMethod(request.getPaymentMethod())
                .build();

        ApiResult<PaymentClientResponse> paymentResult = paymentServiceClient.initiatePayment(paymentRequest);
        return paymentResult.getData();
    }

    /**
     * Checks if the payment was successful.
     * Returns false if payment is null (gateway failure or timeout) or status is not SUCCESS.
     */
    private boolean isPaymentSuccessful(PaymentClientResponse payment) {
        return payment != null && "SUCCESS".equals(payment.paymentStatus());
    }

    /**
     * Handles successful payment — confirms booking and notifies user.
     *
     * Flow:
     *   1. Update booking status to CONFIRMED
     *   2. Set paymentId on booking — links booking to payment record
     *   3. Send BOOKING_CONFIRMED notification to user
     */
    private Booking handlePaymentSuccess(Booking booking, EventClientResponse event,
                                         CreateBookingRequest request, PaymentClientResponse payment) {
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setPaymentId(payment.id());
        Booking updatedBooking = bookingRepository.save(booking);

        sendConfirmationNotification(updatedBooking, event, request, payment);
        return updatedBooking;
    }

    /**
     * Handles failed payment — restores seats and notifies user.
     *
     * Flow:
     *   1. Restore seats in Event Service — seats were deducted before payment attempt
     *   2. Send PAYMENT_FAILED notification to user — booking stays PENDING for retry
     *
     * Note: Booking status stays PENDING — user can retry payment.
     * Seats are restored so other users can book while this user retries.
     *
     * FIXME: Seat locking window — restoring seats creates a window where seats
     *   are available to other users while this user is retrying.
     *   Proper solution: Redis TTL-based seat locking. Revisit when Redis is introduced.
     */
    private void handlePaymentFailure(Booking booking, EventClientResponse event, CreateBookingRequest request) {
        eventServiceClient.restoreSeats(request.getEventId(), request.getSeatsBooked());
        sendFailureNotification(booking, event, request);
    }

    /**
     * Sends BOOKING_CONFIRMED notification to user via Notification Service.
     * Includes all relevant booking details for the confirmation email template.
     *
     * Template variables provided:
     *   - bookingId, eventName, eventDate, venue, seatsBooked, totalAmount, receiptUrl
     */
    private void sendConfirmationNotification(Booking booking, EventClientResponse event,
                                              CreateBookingRequest request, PaymentClientResponse payment) {
        notificationServiceClient.sendNotification(NotificationRequest.builder()
                .userId(booking.getUserId())
                .notificationType("BOOKING_CONFIRMED")
                .channel("EMAIL")
                .recipientEmail(request.getRecipientEmail())
                .templateVariables(Map.of(
                        "bookingId", booking.getId(),
                        "eventName", event.name(),
                        "eventDate", event.eventDate(),
                        "venue", event.venue(),
                        "seatsBooked", booking.getSeatsBooked(),
                        "totalAmount", booking.getTotalAmount(),
                        "receiptUrl", payment.receiptUrl() != null ? payment.receiptUrl() : ""
                ))
                .build());
    }

    /**
     * Sends PAYMENT_FAILED notification to user via Notification Service.
     * Informs user that payment failed and they can retry.
     *
     * Template variables provided:
     *   - bookingId, eventName, amount
     */
    private void sendFailureNotification(Booking booking, EventClientResponse event, CreateBookingRequest request) {
        notificationServiceClient.sendNotification(NotificationRequest.builder()
                .userId(booking.getUserId())
                .notificationType("PAYMENT_FAILED")
                .channel("EMAIL")
                .recipientEmail(request.getRecipientEmail())
                .templateVariables(Map.of(
                        "bookingId", booking.getId(),
                        "eventName", event.name(),
                        "amount", booking.getTotalAmount()
                ))
                .build());
    }

    /**
     * Restores seats in Event Service when a CONFIRMED booking is cancelled.
     * Only called for CONFIRMED bookings — PENDING bookings already had
     * their seats restored when payment failed during creation.
     */
    private void restoreSeatsOnCancellation(Booking booking) {
        eventServiceClient.restoreSeats(booking.getEventId(), booking.getSeatsBooked());
    }

    /**
     * Triggers refund via Payment Service and notifies user.
     * Only called for CONFIRMED bookings — PENDING bookings have no payment to refund.
     *
     * NOTE: refundAmount is calculated here as full refund for now.
     * Partial refund logic (based on cancellation policy) will be added
     * when cancellationDeadline and refund policy are implemented in Event Service.
     *
     * FIXME: Partial refund — if cancelled after deadline, refund only partial amount.
     *   Business decision needed on percentage. Revisit when policy is defined.
     */
    private void triggerRefundAndNotify(Booking booking, CancelBookingRequest request) {

        // Full refund for now — partial refund logic deferred
        // TODO: calculate partial refund based on cancellationDeadline policy
        ProcessRefundRequest refundRequest = ProcessRefundRequest.builder()
                .paymentId(booking.getPaymentId())
                .refundAmount(booking.getTotalAmount())
                .refundReason(request.getCancellationReason())
                .build();

        ApiResult<RefundClientResponse> refundResult = paymentServiceClient.processRefund(refundRequest);
        RefundClientResponse refund = refundResult.getData();

        sendCancellationNotification(booking, request, refund);
    }

    /**
     * Sends BOOKING_CANCELLED notification to user via Notification Service.
     * Includes refund details if booking was CONFIRMED and refund was processed.
     *
     * Template variables provided:
     *   - bookingId, cancellationReason
     *   - refundAmount, refundStatus (if refund was processed)
     */
    private void sendCancellationNotification(Booking booking, CancelBookingRequest request,
                                              RefundClientResponse refund) {
        Map<String, Object> variables = new HashMap<>();
        variables.put("bookingId", booking.getId());
        variables.put("cancellationReason", booking.getCancellationReason());

        if (refund != null) {
            variables.put("refundAmount", refund.refundAmount());
            variables.put("refundStatus", refund.refundStatus());
        }

        notificationServiceClient.sendNotification(NotificationRequest.builder()
                .userId(booking.getUserId())
                .notificationType("BOOKING_CANCELLED")
                .channel("EMAIL")
                .recipientEmail(request.getRecipientEmail())
                .templateVariables(variables)
                .build());
    }

    /**
     * Fetches and validates event for a payment retry.
     * Same validation as createBooking — event must exist, be bookable, and have seats.
     *
     * Note: We reuse the same validation logic as createBooking because
     * the event state may have changed since the original booking was made.
     * e.g. event may have been CANCELLED, or seats may no longer be available.
     */
    private EventClientResponse fetchAndValidateEventForRetry(Booking booking) {
        ApiResult<EventClientResponse> eventResult = eventServiceClient.getEventById(booking.getEventId());
        EventClientResponse event = eventResult.getData();

        if (event == null) {
            throw new EventNotFoundException("Event not found with id: " + booking.getEventId());
        }

        if ("CANCELLED".equals(event.status()) || "COMPLETED".equals(event.status())) {
            throw new EventNotBookableException(
                    "Event is no longer bookable. Current status: " + event.status());
        }

        if (event.availableSeats() < booking.getSeatsBooked()) {
            throw new InsufficientSeatsException(
                    String.format("Seats no longer available for retry. Requested: %d, Available: %d",
                            booking.getSeatsBooked(), event.availableSeats()));
        }

        return event;
    }

    /**
     * Retries payment via Payment Service using existing booking data.
     * Builds payment request from the existing booking — no new user input needed.
     */
    private PaymentClientResponse retryPaymentWithGateway(Booking booking) {
        InitiatePaymentRequest paymentRequest = InitiatePaymentRequest.builder()
                .bookingId(booking.getId())
                .eventId(booking.getEventId())
                .userId(booking.getUserId())
                .amount(booking.getTotalAmount())
                .paymentMethod(booking.getPaymentMethod())
                .build();

        ApiResult<PaymentClientResponse> paymentResult = paymentServiceClient.initiatePayment(paymentRequest);
        return paymentResult.getData();
    }

    /**
     * Handles successful payment retry — confirms booking and notifies user.
     */
    private Booking handleRetryPaymentSuccess(Booking booking, EventClientResponse event,
                                              PaymentClientResponse payment) {
        booking.setBookingStatus(BookingStatus.CONFIRMED);
        booking.setPaymentId(payment.id());
        Booking updatedBooking = bookingRepository.save(booking);

        notificationServiceClient.sendNotification(NotificationRequest.builder()
                .userId(updatedBooking.getUserId())
                .notificationType("BOOKING_CONFIRMED")
                .channel("EMAIL")
                .recipientEmail(updatedBooking.getRecipientEmail())
                .templateVariables(Map.of(
                        "bookingId", updatedBooking.getId(),
                        "eventName", event.name(),
                        "eventDate", event.eventDate(),
                        "venue", event.venue(),
                        "seatsBooked", updatedBooking.getSeatsBooked(),
                        "totalAmount", updatedBooking.getTotalAmount(),
                        "receiptUrl", payment.receiptUrl() != null ? payment.receiptUrl() : ""
                ))
                .build());

        return updatedBooking;
    }

    /**
     * Handles failed payment retry — restores seats and notifies user.
     * Booking stays PENDING — user can retry again.
     */
    private void handleRetryPaymentFailure(Booking booking, EventClientResponse event) {
        eventServiceClient.restoreSeats(booking.getEventId(), booking.getSeatsBooked());

        notificationServiceClient.sendNotification(NotificationRequest.builder()
                .userId(booking.getUserId())
                .notificationType("PAYMENT_FAILED")
                .channel("EMAIL")
                .recipientEmail(booking.getRecipientEmail())
                .templateVariables(Map.of(
                        "bookingId", booking.getId(),
                        "eventName", event.name(),
                        "amount", booking.getTotalAmount()
                ))
                .build());
    }
}