package com.ticketing.booking.service;

import com.ticketing.booking.dto.request.InitiateBookingRequest;
import com.ticketing.booking.dto.response.BookingStatusResponse;
import com.ticketing.booking.dto.response.EventBookingResponse;
import com.ticketing.booking.dto.response.InitiateBookingResponse;
import com.ticketing.booking.entity.Booking;

/**
 * Handles all database read and write operations for the {@link Booking} entity.
 *
 * <p>Intentionally contains no business logic — every method is a pure persistence
 * or mapping operation. This keeps {@code BookingService} and {@code BookingEventListener}
 * free of JPA concerns and makes each class independently testable.</p>
 */
public interface BookingPersistenceService {

    /**
     * Creates and persists a {@code PENDING} booking after the Event Service confirms
     * seat availability but before the Stripe PaymentIntent ID is known.
     *
     * <p>{@code pricePerSeat} is snapshotted from the Event Service response so the
     * booking total is locked in at initiation time, even if the event price changes
     * before the payment webhook fires.</p>
     *
     * @param request               the incoming initiate-booking request
     * @param eventBookingResponse  price and event details from the Event Service
     * @return the persisted PENDING booking
     */
    Booking createPendingBooking(InitiateBookingRequest request, EventBookingResponse eventBookingResponse);

    /**
     * Creates and persists a {@code FAILED} booking when the Event Service rejects the
     * availability check — before any Stripe interaction occurs.
     *
     * <p>{@code pricePerSeat} is stored as {@code 0.0} because we never reached the Event
     * Service successfully. The record still serves as an audit trail.</p>
     *
     * @param request the incoming initiate-booking request
     * @param reason  the failure message returned by the Event Service (or Feign error message)
     * @return the persisted FAILED booking
     */
    Booking createFailedBooking(InitiateBookingRequest request, String reason);

    /**
     * Stores the Stripe PaymentIntent ID on the booking and persists it.
     *
     * <p>Called immediately after {@code PaymentIntent.create()} succeeds, before returning
     * the {@code clientSecret} to the frontend. Storing the intent ID here ensures we can
     * issue a refund later even if the service restarts between initiation and webhook receipt.</p>
     *
     * @param booking             the PENDING booking to update
     * @param stripePaymentIntentId the Stripe PaymentIntent ID (e.g. {@code "pi_3OqX..."})
     * @return the updated and persisted booking
     */
    Booking storePaymentIntentId(Booking booking, String stripePaymentIntentId);

    /**
     * Transitions the booking to {@code CONFIRMED} and persists it.
     *
     * <p>Called inside {@code BookingEventListener#handlePaymentSuccess} after seats have
     * been successfully deducted from the Event Service.</p>
     *
     * @param booking the PENDING booking to confirm
     * @return the updated and persisted CONFIRMED booking
     */
    Booking confirmBooking(Booking booking);

    /**
     * Transitions the booking to {@code FAILED} with a reason, and persists it.
     *
     * <p>Called from two places:</p>
     * <ul>
     *   <li>{@code BookingEventListener#handlePaymentSuccess} — when the post-payment
     *       seat check reveals seats are no longer available (ultra-rare race condition).</li>
     *   <li>{@code BookingEventListener#handlePaymentFailed} — when Stripe reports a
     *       payment failure.</li>
     * </ul>
     *
     * @param booking the PENDING booking to fail
     * @param reason  the human-readable failure reason stored in {@code Booking.failureReason}
     * @return the updated and persisted FAILED booking
     */
    Booking failBooking(Booking booking, String reason);

    /**
     * Fetches a booking by its primary key.
     *
     * <p>Throws {@link jakarta.persistence.EntityNotFoundException} if no booking exists
     * with the given ID — callers do not need to handle an empty Optional.</p>
     *
     * @param bookingId the booking primary key
     * @return the booking entity
     */
    Booking findById(Long bookingId);

    /**
     * Maps a booking to the {@link InitiateBookingResponse} returned from
     * {@code POST /api/v1/bookings/initiate}.
     *
     * <p>{@code clientSecret} is passed in separately because it is returned by the Stripe
     * SDK and is never stored on the entity (treating it as transient sensitive data).</p>
     *
     * @param booking      the freshly created PENDING booking
     * @param clientSecret the Stripe PaymentIntent client secret
     * @return the initiate response containing bookingId, clientSecret, and totalAmount
     */
    InitiateBookingResponse toInitiateResponse(Booking booking, String clientSecret);

    /**
     * Maps a booking to the {@link BookingStatusResponse} returned from
     * {@code GET /api/v1/bookings/{bookingId}/status}.
     *
     * @param booking the booking to map
     * @return the status response containing bookingId, bookingStatus, and failureReason
     */
    BookingStatusResponse toStatusResponse(Booking booking);
}