package com.ticketing.event.exception;

/**
 * Thrown when the requested event is not found in the Redis seat-availability cache.
 *
 * <p>Redis is the first line of defence in {@code checkAndReserve} — if the event key
 * is absent from Redis, either the event does not exist or the cache has not been
 * populated for it. Both cases are treated as "not found" and surface as HTTP 404.</p>
 *
 * <p>Handled by {@code GlobalExceptionHandler#handleEventNotFound} which maps it to
 * a structured {@code ApiResult} with HTTP 404. This propagates to booking-service as a
 * {@code FeignException.NotFound}, which booking-service's own {@code GlobalExceptionHandler}
 * maps to a 404 response for the client.</p>
 */
public class EventNotFoundException extends RuntimeException {

    public EventNotFoundException(String message) {
        super(message);
    }
}
