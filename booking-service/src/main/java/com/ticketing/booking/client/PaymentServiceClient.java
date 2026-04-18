package com.ticketing.booking.client;

import com.ticketing.booking.dto.request.InitiatePaymentIntentRequest;
import com.ticketing.booking.dto.response.ApiResult;
import com.ticketing.booking.dto.response.InitiatePaymentIntentResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client for the Payment Service.
 *
 * <p>Booking Service delegates all Stripe interactions to Payment Service via this client.
 * Card details never touch Booking Service — this client only initiates the PaymentIntent
 * and receives the credentials needed by the frontend to complete the payment.</p>
 *
 * <p>FeignExceptions from {@link #initiatePaymentIntent} are caught in
 * {@code BookingServiceImpl#initiateBooking} because a PENDING booking and reserved seats
 * already exist at that point — both must be compensated before returning to the caller.</p>
 */
@FeignClient(name = "PAYMENT-SERVICE")
public interface PaymentServiceClient {

    /**
     * Creates a Stripe PaymentIntent via Payment Service.
     *
     * <p>Called from {@code BookingServiceImpl#initiateBooking} after the Event Service
     * confirms seat availability and a PENDING booking has been persisted.</p>
     *
     * <p>On success: returns the {@code paymentIntentId} (stored on the booking entity)
     * and {@code clientSecret} (forwarded to the frontend for {@code stripe.confirmPayment()}).</p>
     *
     * <p>On failure (Stripe API error or Payment Service unavailable): throws a
     * {@link feign.FeignException} which is caught in {@code BookingServiceImpl} to fail the
     * PENDING booking and release the reserved seats.</p>
     *
     * @param request bookingId (for Stripe metadata), amount (smallest unit), and currency
     * @return success result wrapping paymentIntentId and clientSecret
     */
    @PostMapping("/api/v1/payments/initiate-intent")
    ApiResult<InitiatePaymentIntentResponse> initiatePaymentIntent(@RequestBody InitiatePaymentIntentRequest request);
}
