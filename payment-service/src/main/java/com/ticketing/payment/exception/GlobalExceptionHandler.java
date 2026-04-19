package com.ticketing.payment.exception;

import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.ticketing.payment.dto.response.ApiResult;
import com.ticketing.payment.schedular.OutboxEventSchedular;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.AmqpException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.NoSuchElementException;

/**
 * Centralised exception handler for all exceptions that propagate out of the controller layer
 * in Payment Service.
 *
 * <p><strong>What is handled here:</strong></p>
 * <ul>
 *   <li>{@link SignatureVerificationException} — invalid or absent Stripe webhook signature → 400.</li>
 *   <li>{@link StripeException} — Stripe API failures during PaymentIntent creation.
 *       Returns HTTP 502 so booking-service Feign can distinguish a Stripe failure from
 *       a Payment Service bug and fail the PENDING booking appropriately.</li>
 *   <li>{@link MethodArgumentNotValidException} — bean validation failures on request bodies → 400.</li>
 *   <li>{@link IllegalArgumentException} — invalid caller-supplied argument → 400.</li>
 *   <li>{@link NoSuchElementException} — requested resource not found → 404.</li>
 *   <li>{@link AmqpException} — RabbitMQ messaging failure → 500.</li>
 *   <li>Catch-all {@link Exception} for anything unexpected → 500.</li>
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
     * Handles invalid or tampered Stripe webhook signatures.
     *
     * <p>Returns HTTP 400 — Stripe treats any non-2xx response as a delivery failure and
     * will retry the webhook. Returning 400 here signals that this specific delivery is
     * permanently rejected (bad signature) rather than a transient server error (which
     * would warrant a 5xx).</p>
     *
     * <p>This handler takes precedence over {@link #handleStripeException} for
     * {@link SignatureVerificationException} because Spring selects the most specific
     * matching handler.</p>
     *
     * @param ex the exception thrown by {@code Webhook.constructEvent} on signature mismatch
     * @return HTTP 400 with a fixed rejection message
     */
    @ExceptionHandler(SignatureVerificationException.class)
    public ResponseEntity<ApiResult<Void>> handleSignatureVerification(SignatureVerificationException ex) {
        log.warn("Stripe webhook signature verification failed: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResult.of(false, "Invalid Stripe webhook signature", null));
    }

    /**
     * Handles invalid caller-supplied arguments detected inside the service layer.
     *
     * <p>Returns HTTP 400 — the request itself is malformed from the caller's perspective
     * and re-sending with corrected data is the expected fix.</p>
     *
     * @param ex the exception carrying the human-readable validation message
     * @return HTTP 400 with the exception message as the response body
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ApiResult<Void>> handleIllegalArgument(IllegalArgumentException ex) {
        log.warn("Illegal argument: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResult.of(false, ex.getMessage(), null));
    }

    /**
     * Handles lookups for resources that do not exist (e.g. a booking ID not found in the DB).
     *
     * <p>Returns HTTP 404 — the resource the caller referenced is absent, and no amount of
     * retrying will change that.</p>
     *
     * @param ex the exception carrying the not-found description
     * @return HTTP 404 with the exception message as the response body
     */
    @ExceptionHandler(NoSuchElementException.class)
    public ResponseEntity<ApiResult<Void>> handleNoSuchElement(NoSuchElementException ex) {
        log.warn("Resource not found: {}", ex.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResult.of(false, ex.getMessage(), null));
    }

    /**
     * Handles RabbitMQ messaging failures propagated from {@link AmqpException} subtypes.
     *
     * <p>Returns HTTP 500 — broker unavailability is a server-side infrastructure problem,
     * not a client error. The {@link OutboxEventSchedular} handles
     * retries for deferred outbox delivery; this handler covers any synchronous publish paths.</p>
     *
     * @param ex the AMQP exception thrown when the broker is unreachable or rejects a message
     * @return HTTP 500 with a generic messaging error message
     */
    @ExceptionHandler(AmqpException.class)
    public ResponseEntity<ApiResult<Void>> handleAmqpException(AmqpException ex) {
        log.error("AMQP messaging error: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResult.of(false, "Messaging error — please try again", null));
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
