package com.ticketing.booking.service;

import com.ticketing.booking.dto.request.InitiateBookingRequest;
import com.ticketing.booking.dto.response.ApiResult;
import com.ticketing.booking.dto.response.BookingStatusResponse;
import com.ticketing.booking.dto.response.InitiateBookingResponse;

/**
 * Orchestrator for the booking flow.
 *
 * <p>Contains no business logic itself — delegates entirely to focused service classes:
 * {@code BookingPersistenceService}, {@code PaymentServiceClient}, and
 * {@code EventServiceClient}. Each method represents one user-facing operation.</p>
 *
 * <p>Returns {@link ApiResult} wrappers directly so the controller can pass them through
 * without additional wrapping, keeping the controller layer purely HTTP-concerned.</p>
 */
public interface BookingService {

    /**
     * Orchestrates the booking initiation flow:
     * <ol>
     *   <li>Check and reserve seats via Event Service — validates in Redis then deducts under
     *       DB optimistic lock. On failure, a FeignException propagates to GlobalExceptionHandler.</li>
     *   <li>Create PENDING booking as audit trail; price snapshotted from Event Service response.</li>
     *   <li>Create Stripe PaymentIntent via Payment Service — returns {@code clientSecret}
     *       for the frontend to call {@code stripe.confirmPayment()}.</li>
     *   <li>Store {@code paymentIntentId} on the booking for future refund capability.</li>
     *   <li>Return {@code clientSecret} and {@code bookingId} to the frontend.</li>
     * </ol>
     *
     * <p>If Payment Service call fails, the PENDING booking is transitioned to FAILED and
     * the reserved seats are released back to Event Service (compensating transaction).</p>
     *
     * <p>Event Service failures (event not found, insufficient seats) propagate as
     * FeignExceptions to GlobalExceptionHandler — no booking is saved at that point.</p>
     *
     * @param request the booking initiation request from the client
     * @return success result with {@link InitiateBookingResponse}, or failure result with reason
     */
    InitiateBookingResponse initiateBooking(InitiateBookingRequest request);

    /**
     * Retrieves the current status of a booking for frontend status polling.
     *
     * <p>The frontend calls this every 2 seconds after {@code stripe.confirmPayment()}
     * completes until the status transitions out of PENDING (max ~15 seconds).</p>
     *
     * @param bookingId the booking to query
     * @return success result with {@link BookingStatusResponse}
     */
    ApiResult<BookingStatusResponse> getBookingStatus(Long bookingId);
}