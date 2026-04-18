package com.ticketing.payment.exception;

import com.stripe.exception.StripeException;
import com.ticketing.payment.dto.response.ApiResult;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Centralised exception handler for all exceptions that propagate out of the controller layer
 * in Payment Service.
 *
 * <p><strong>What is handled here:</strong></p>
 * <ul>
 *   <li>{@link StripeException} — Stripe API failures during PaymentIntent creation.
 *       Returns HTTP 502 so booking-service Feign can distinguish a Stripe failure from
 *       a Payment Service bug and fail the PENDING booking appropriately.</li>
 *   <li>{@link MethodArgumentNotValidException} — bean validation failures on request bodies → 400.</li>
 *   <li>Catch-all {@link Exception} for anything unexpected → 500.</li>
 * </ul>
 *
 * <p><strong>What is NOT handled here:</strong></p>
 * <ul>
 *   <li>Stripe webhook signature failures — handled directly in {@code StripeWebhookController}
 *       because the response format differs (Stripe expects a plain 400, not an {@code ApiResult}).</li>
 * </ul>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Handles Stripe API failures during PaymentIntent creation.
     * Returns HTTP 502 (Bad Gateway) — booking-service treats this as a Feign error
     * and fails the PENDING booking that was created before calling this service.
     */
    @ExceptionHandler(StripeException.class)
    public ResponseEntity<ApiResult<Void>> handleStripeException(StripeException ex) {
        log.error("Stripe API error: code={} message={}", ex.getCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(ApiResult.of(false, "Payment provider error. Please try again.", null));
    }

    /**
     * Handles bean validation failures on {@code @Valid} request bodies.
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
