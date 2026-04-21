package com.ticketing.booking.client;

import com.ticketing.booking.dto.response.ApiResult;
import com.ticketing.booking.dto.response.EventBookingResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * Feign client for the Event Service.
 *
 * <p>Two operations matching the two points in the booking lifecycle where seat
 * inventory must be modified:</p>
 * <ol>
 *   <li>{@link #checkAndReserve} — called during {@code initiateBooking} to validate
 *       availability and atomically deduct the requested seats. If this call succeeds,
 *       seats are reserved and must be explicitly released if anything fails downstream.</li>
 *   <li>{@link #releaseSeats} — compensating operation; called when payment fails or
 *       payment initiation itself fails, to restore the seats reserved by
 *       {@code checkAndReserve}.</li>
 * </ol>
 *
 * <p>The Event Service owns all seat-availability and validation logic — this client
 * never interprets seat counts directly.</p>
 */
@FeignClient(name = "EVENT-SERVICE")
public interface EventServiceClient {

    /**
     * Validates seat availability in Redis, confirms under optimistic locking in the DB,
     * and atomically deducts the requested seats from the event's inventory.
     *
     * <p>Called from {@code BookingServiceImpl#initiateBooking} as the first step of
     * the booking flow. On success, seats are reserved and the event's {@code pricePerSeat}
     * is returned for snapshotting onto the {@code Booking} entity.</p>
     *
     * <p>On failure, Event Service throws an exception which maps to an HTTP error:
     * <ul>
     *   <li>404 — event not found in Redis → Feign throws {@code FeignException.NotFound}
     *       → {@code GlobalExceptionHandler} in booking-service returns 404 to the client.</li>
     *   <li>409 — insufficient seats or optimistic lock conflict → Feign throws
     *       {@code FeignException} → {@code GlobalExceptionHandler} returns 502 to the client.</li>
     * </ul>
     * No seats are modified on failure — no compensation needed.</p>
     *
     * @param eventId the event to reserve seats for
     * @param seats   the number of seats being requested (must be ≥ 1)
     * @return event details including price snapshot and confirmed seat count
     */
    @PostMapping("/api/v1/events/{eventId}/check-and-reserve")
    ApiResult<EventBookingResponse> checkAndReserve(
            @PathVariable Long eventId,
            @RequestParam int seats
    );

    /**
     * Restores the given number of seats to the event's available inventory.
     *
     * <p>This is the compensating transaction for {@link #checkAndReserve}. Called from:</p>
     * <ul>
     *   <li>{@code BookingServiceImpl#initiateBooking} — if the Payment Service Feign call
     *       fails after {@code checkAndReserve} already deducted seats.</li>
     *   <li>{@code BookingEventListener#handlePaymentFailed} — when Stripe reports a
     *       payment failure, so the pre-reserved seats are returned to the pool.</li>
     * </ul>
     *
     * <p>FeignExceptions from this call are caught at the call site with a best-effort
     * log — the booking is already failed at that point so the failure is flagged for
     * manual review rather than propagated.</p>
     *
     * @param eventId the event to release seats for
     * @param seats   the number of seats to return (must match the original reservation quantity)
     * @return empty success response
     */
    @PostMapping("/api/v1/events/{eventId}/release-seats")
    ApiResult<Void> releaseSeats(
            @PathVariable Long eventId,
            @RequestParam int seats
    );

    /**
     * Fetches live event details including current eventDate.
     *
     * <p>Used in cancelBooking() to get authoritative eventDate for refund
     * policy calculation. NOT the snapshot on Booking entity — that may
     * be stale if the organiser rescheduled after booking.</p>
     */
    @GetMapping("/api/v1/events/{eventId}")
    ApiResult<EventResponse> getEvent(@PathVariable Long eventId);
}
