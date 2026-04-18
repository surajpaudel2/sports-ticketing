package com.ticketing.event.exception;

import com.ticketing.event.dto.response.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralised exception handler for all exceptions that propagate out of the controller layer
 * in Event Service.
 *
 * <p><strong>What is handled here:</strong></p>
 * <ul>
 *   <li>{@link EventNotFoundException} — event not found in Redis cache → 404.</li>
 *   <li>{@link InsufficientSeatsException} — not enough seats available → 409.</li>
 *   <li>{@link PessimisticLockingFailureException} — the {@code SELECT … FOR UPDATE} could not
 *       acquire the row lock within the database timeout (e.g. deadlock or extreme contention) → 503.
 *       The caller (booking-service) should treat this as a transient failure and retry.</li>
 *   <li>{@link MethodArgumentNotValidException} — bean validation failures on request params → 400.</li>
 *   <li>Catch-all {@link Exception} for anything unexpected → 500.</li>
 * </ul>
 *
 * <p>All responses use the same {@link ApiResult} envelope used by every other service
 * in the platform so that Feign clients receive a consistent error shape.</p>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles events not found in the Redis cache.
     * Maps to HTTP 404 so booking-service Feign throws {@code FeignException.NotFound}.
     */
    @ExceptionHandler(EventNotFoundException.class)
    public ResponseEntity<ApiResult<Void>> handleEventNotFound(EventNotFoundException ex) {
        log.warn("Event not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResult.of(false, ex.getMessage(), null));
    }

    /**
     * Handles insufficient seat availability detected at either the Redis or DB check stage.
     * Maps to HTTP 409 (Conflict) — the resource exists but the requested operation cannot
     * be fulfilled with the current inventory state.
     */
    @ExceptionHandler(InsufficientSeatsException.class)
    public ResponseEntity<ApiResult<Void>> handleInsufficientSeats(InsufficientSeatsException ex) {
        log.warn("Insufficient seats: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResult.of(false, ex.getMessage(), null));
    }

    /**
     * Handles pessimistic lock acquisition failures.
     *
     * <p>Thrown when {@code SELECT … FOR UPDATE} cannot acquire the exclusive row lock within
     * the database timeout — typically caused by extreme concurrent load or a deadlock.
     * Under normal load this should never fire: the Redis pre-check ensures only requests
     * with a plausible chance of success reach the DB lock, keeping contention low.</p>
     *
     * <p>Maps to HTTP 503 (Service Unavailable) — signals the caller to retry after a
     * brief back-off. The retry will succeed once the competing transaction releases the lock.</p>
     */
    @ExceptionHandler(PessimisticLockingFailureException.class)
    public ResponseEntity<ApiResult<Void>> handlePessimisticLockFailure(PessimisticLockingFailureException ex) {
        log.warn("Pessimistic lock acquisition failed — high contention on event row: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResult.of(false, "Service temporarily busy — please retry your request", null));
    }

    /**
     * Handles bean validation failures on {@code @Valid} request parameters.
     * Returns the first field error message for a concise client-facing message.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidation(MethodArgumentNotValidException ex) {
        log.warn("Validation failed: {}", ex.getMessage());
        String firstError = ex.getBindingResult().getFieldErrors().stream()
                .findFirst()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .orElse("Validation failed");
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResult.of(false, firstError, null));
    }

    /**
     * Catch-all for any unhandled exception. Prevents stack traces from leaking to the client.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResult<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.of(false, "An unexpected error occurred. Please try again later.", null));
    }
}
