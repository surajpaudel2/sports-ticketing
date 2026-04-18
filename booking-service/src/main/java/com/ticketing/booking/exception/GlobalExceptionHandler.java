package com.ticketing.booking.exception;

import com.ticketing.booking.dto.response.ApiResult;
import feign.FeignException;
import feign.RetryableException;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralised exception handler for all exceptions that propagate out of the controller layer.
 *
 * <p><strong>What is handled here:</strong></p>
 * <ul>
 *   <li>Feign exceptions from {@code EventServiceClient} — these can reach the controller
 *       on the {@code checkAndReserve} call if the Event Service is down or returns 4xx/5xx.</li>
 *   <li>{@link EntityNotFoundException} from {@code BookingPersistenceService#findById} —
 *       thrown when the polling endpoint receives an unknown bookingId.</li>
 *   <li>{@link MethodArgumentNotValidException} — bean validation failures on request DTOs.</li>
 *   <li>Catch-all {@link Exception} handler for anything unexpected.</li>
 * </ul>
 *
 * <p><strong>What is NOT handled here:</strong></p>
 * <ul>
 *   <li>Exceptions inside {@code BookingEventListener} — RabbitMQ listeners handle their own
 *       exceptions so that message acknowledgement is controlled correctly. A controller advice
 *       cannot nack a RabbitMQ message.</li>
 *   <li>{@code FeignException} from {@code PaymentServiceClient} — caught directly in
 *       {@code BookingServiceImpl} because a PENDING booking and reserved seats already
 *       exist and must be compensated (booking failed, seats released) before returning.</li>
 * </ul>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles 404 from a downstream Feign call (e.g. event not found in Event Service).
     */
    @ExceptionHandler(FeignException.NotFound.class)
    public ResponseEntity<ApiResult<Void>> handleFeignNotFound(FeignException.NotFound ex) {
        log.warn("Downstream service returned 404: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResult.of(false, "Requested resource not found", null));
    }

    /**
     * Handles service unavailability from Feign (circuit open, connection refused, timeout).
     */
    @ExceptionHandler({FeignException.ServiceUnavailable.class, RetryableException.class})
    public ResponseEntity<ApiResult<Void>> handleServiceUnavailable(Exception ex) {
        log.error("Downstream service unavailable: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(ApiResult.of(false, "A downstream service is currently unavailable. Please try again later.", null));
    }

    /**
     * Catch-all for any other Feign error (4xx/5xx from a downstream service).
     */
    @ExceptionHandler(FeignException.class)
    public ResponseEntity<ApiResult<Void>> handleFeignException(FeignException ex) {
        log.error("Feign error: status={} message={}", ex.status(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResult.of(false, "An error occurred communicating with a downstream service.", null));
    }

    /**
     * Handles booking not found — thrown by {@code BookingPersistenceService#findById}
     * when the status polling endpoint receives an unknown bookingId.
     */
    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResult<Void>> handleEntityNotFound(EntityNotFoundException ex) {
        log.warn("Entity not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResult.of(false, ex.getMessage(), null));
    }

    /**
     * Handles bean validation failures on {@code @Valid} request bodies.
     * Returns the first field error message for a concise client-facing message.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResult<Void>> handleValidationException(MethodArgumentNotValidException ex) {
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
    public ResponseEntity<ApiResult<Void>> handleUnexpectedException(Exception ex) {
        log.error("Unexpected error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.of(false, "An unexpected error occurred. Please try again later.", null));
    }
}
