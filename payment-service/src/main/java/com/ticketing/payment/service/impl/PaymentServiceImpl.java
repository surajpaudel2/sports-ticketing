package com.ticketing.payment.service.impl;

import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.PaymentIntent;
import com.stripe.param.PaymentIntentCreateParams;
import com.ticketing.payment.dto.request.InitiatePaymentIntentRequest;
import com.ticketing.payment.dto.response.InitiatePaymentIntentResponse;
import com.ticketing.payment.service.PaymentService;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Creates Stripe PaymentIntents on behalf of booking-service.
 *
 * <p>Sets the Stripe API key once at startup via {@link PostConstruct}.
 * Each call to {@link #initiatePaymentIntent} issues a single Stripe API request
 * and returns both the {@code paymentIntentId} (for future refunds) and
 * {@code clientSecret} (for the frontend to confirm payment).</p>
 *
 * <p>The booking ID is embedded in the PaymentIntent metadata — this is how
 * {@code StripeWebhookController} maps an incoming Stripe webhook event back to
 * the correct booking in the database when publishing the RabbitMQ event.</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentServiceImpl implements PaymentService {

    // Injected from application.yaml stripe.secret-key → STRIPE_SECRET_KEY env var
    @Value("${stripe.secret-key}")
    private String stripeSecretKey;

    /**
     * Initialises the Stripe SDK with the secret key once at application startup.
     * Setting {@code Stripe.apiKey} is a global operation — it applies to all
     * subsequent Stripe API calls made by this JVM.
     */
    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
        log.info("Stripe SDK initialised");
    }

    @Override
    public InitiatePaymentIntentResponse initiatePaymentIntent(InitiatePaymentIntentRequest request)
            throws StripeException {

        PaymentIntentCreateParams params = PaymentIntentCreateParams.builder()
                .setAmount(request.amountInSmallestUnit())
                .setCurrency(request.currency())
                // bookingId in metadata is the link between the Stripe webhook and our booking —
                // StripeWebhookController reads this to know which bookingId to publish on RabbitMQ
                .putMetadata("bookingId", String.valueOf(request.bookingId()))
                // AUTOMATIC capture: payment is captured immediately on confirmation.
                // MANUAL would allow pre-auth (capture later) — switch if pre-auth flows are needed.
                .setCaptureMethod(PaymentIntentCreateParams.CaptureMethod.AUTOMATIC)
                .build();

        log.info("Creating Stripe PaymentIntent for bookingId={} amount={} currency={}",
                request.bookingId(), request.amountInSmallestUnit(), request.currency());

        PaymentIntent intent = PaymentIntent.create(params);

        log.info("Stripe PaymentIntent created: intentId={} bookingId={}",
                intent.getId(), request.bookingId());

        return InitiatePaymentIntentResponse.builder()
                .paymentIntentId(intent.getId())
                // clientSecret is NOT logged — it is sensitive transient data
                .clientSecret(intent.getClientSecret())
                .build();
    }
}
