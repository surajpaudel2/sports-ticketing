package com.ticketing.payment.controller;

import com.ticketing.payment.controller.docs.InitiatePaymentIntentDocs;
import com.ticketing.payment.dto.request.InitiatePaymentIntentRequest;
import com.ticketing.payment.dto.response.ApiResult;
import com.ticketing.payment.dto.response.InitiatePaymentIntentResponse;
import com.ticketing.payment.service.PaymentService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for Payment Service — internal inter-service endpoints.
 *
 * <p>Thin layer — handles HTTP concerns only (request mapping, response status codes,
 * parameter binding). All business logic lives in {@link PaymentService}.</p>
 *
 * <p>These endpoints are called by booking-service via Feign and are not intended
 * for direct frontend use. The frontend interacts with Stripe directly via
 * Stripe Elements using the {@code clientSecret} forwarded by booking-service.</p>
 *
 * <p>{@link com.stripe.exception.StripeException} from {@link PaymentService} propagates to
 * {@code GlobalExceptionHandler} which returns HTTP 502 — booking-service Feign catches
 * this and fails the PENDING booking.</p>
 */
@RestController
@RequestMapping("/api/v1/payments")
@RequiredArgsConstructor
@Tag(name = "Payment", description = "Payment initiation — internal inter-service endpoints")
public class PaymentController {

    private final PaymentService paymentService;

    /**
     * Creates a Stripe PaymentIntent for the given booking.
     *
     * <p>Returns HTTP 201 on success. {@link com.stripe.exception.StripeException}
     * propagates to {@code GlobalExceptionHandler} → HTTP 502 on Stripe API failure.</p>
     *
     * @param request bookingId, amountInSmallestUnit, and currency
     * @return the Stripe PaymentIntent ID and client secret
     * @throws com.stripe.exception.StripeException propagates to GlobalExceptionHandler
     */
    @InitiatePaymentIntentDocs
    @PostMapping("/initiate-intent")
    public ResponseEntity<ApiResult<InitiatePaymentIntentResponse>> initiatePaymentIntent(
            @Valid @RequestBody InitiatePaymentIntentRequest request)
            throws com.stripe.exception.StripeException {

        InitiatePaymentIntentResponse response = paymentService.initiatePaymentIntent(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of(true, "PaymentIntent created successfully", response));
    }
}
