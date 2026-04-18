package com.ticketing.event.service;

import com.ticketing.event.dto.response.CheckAndReserveResponse;

/**
 * Core operations for event seat inventory management.
 *
 * <p>Contains no business logic itself — all logic lives in
 * {@code EventServiceImpl}. Each method represents one booking-related
 * inventory operation exposed to other services via the REST API.</p>
 *
 * <p>Both methods are intentionally scoped to the booking flow only.
 * General event CRUD (create, update, cancel) is a separate concern not
 * covered here.</p>
 */
public interface EventService {

    /**
     * Validates seat availability and atomically reserves the requested seats.
     *
     * <p>Two-phase check:</p>
     * <ol>
     *   <li><strong>Redis pre-check</strong> — fast; rejects requests before any DB
     *       access if the cached available-seat count is insufficient or the event key
     *       is absent from the cache.</li>
     *   <li><strong>DB confirmation under pessimistic lock</strong> — authoritative;
     *       acquires a {@code SELECT … FOR UPDATE} row lock, re-reads {@code availableSeats},
     *       and deducts the seats. Concurrent transactions block at the DB level until this
     *       transaction commits — no lost updates possible.</li>
     * </ol>
     *
     * <p>On success, {@code availableSeats} in both the DB and Redis is decremented by
     * {@code seats}. The deduction is permanent until {@link #releaseSeats} is called
     * (i.e. on payment failure).</p>
     *
     * <p>On failure, one of the following is thrown (never returns {@code null}):</p>
     * <ul>
     *   <li>{@code EventNotFoundException} — event key absent from Redis.</li>
     *   <li>{@code InsufficientSeatsException} — seat count insufficient in Redis or DB.</li>
     *   <li>{@code ObjectOptimisticLockingFailureException} — concurrent modification
     *       detected; caller should retry.</li>
     * </ul>
     *
     * @param eventId the event to reserve seats for
     * @param seats   the number of seats to reserve (must be ≥ 1)
     * @return event details including the snapshotted price and confirmed seat count
     */
    CheckAndReserveResponse checkAndReserve(Long eventId, int seats);

    /**
     * Restores the given number of seats to the event's available inventory.
     *
     * <p>This is the <em>compensating transaction</em> for {@link #checkAndReserve}. It is
     * called by booking-service in two scenarios:</p>
     * <ol>
     *   <li>{@code BookingEventListener#handlePaymentFailed} — Stripe reported a payment
     *       failure, so the pre-reserved seats must be returned to the pool.</li>
     *   <li>{@code BookingServiceImpl#initiateBooking} — Payment Service Feign call failed
     *       after {@code checkAndReserve} already deducted seats.</li>
     * </ol>
     *
     * <p>Updates both the DB and Redis atomically (within a single transaction for the DB;
     * Redis is updated after the DB commit).</p>
     *
     * @param eventId the event to restore seats for
     * @param seats   the number of seats to return (must match the original reservation quantity)
     */
    void releaseSeats(Long eventId, int seats);
}
