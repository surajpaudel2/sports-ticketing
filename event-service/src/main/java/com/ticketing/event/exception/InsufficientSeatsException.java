package com.ticketing.event.exception;

/**
 * Thrown when the requested number of seats exceeds the available inventory.
 *
 * <p>Can be thrown at two points in {@code checkAndReserve}:</p>
 * <ol>
 *   <li><strong>Redis pre-check</strong> — fast path; thrown before any DB access if the
 *       cached available-seat count is less than the requested quantity.</li>
 *   <li><strong>DB confirmation</strong> — thrown after acquiring an optimistic lock if the
 *       live seat count in the database is insufficient (ultra-rare race where cache was
 *       slightly stale between the Redis read and the DB read).</li>
 * </ol>
 *
 * <p>Handled by {@code GlobalExceptionHandler#handleInsufficientSeats} which maps it to
 * HTTP 409 (Conflict). This propagates to booking-service as a {@code FeignException}
 * which booking-service's own {@code GlobalExceptionHandler} maps to a clean error response.</p>
 */
public class InsufficientSeatsException extends RuntimeException {

    public InsufficientSeatsException(String message) {
        super(message);
    }
}
