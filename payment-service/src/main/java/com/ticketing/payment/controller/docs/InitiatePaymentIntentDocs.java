package com.ticketing.payment.controller.docs;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Swagger/OpenAPI documentation for {@code POST /api/v1/payments/initiate-intent}.
 * Extracted from the controller to keep {@code PaymentController} focused on routing only.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Operation(
        summary = "Create a Stripe PaymentIntent",
        description = """
                Creates a Stripe PaymentIntent for the given booking.
                The bookingId is embedded in the PaymentIntent metadata so the Stripe webhook
                can identify which booking to update when payment succeeds or fails.

                Called internally by booking-service during initiateBooking — not intended
                for direct frontend use. The clientSecret returned here is forwarded to the
                frontend by booking-service so Stripe Elements can confirm the payment.

                On success: returns paymentIntentId (stored on the booking) and clientSecret
                (forwarded to the frontend — do NOT log or persist this value).
                On Stripe API failure: returns HTTP 502 so booking-service can fail the pending booking.
                """
)
@ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "PaymentIntent created successfully",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = Object.class),
                        examples = @ExampleObject(
                                name = "Success",
                                value = """
                                        {
                                          "success": true,
                                          "message": "PaymentIntent created successfully",
                                          "data": {
                                            "paymentIntentId": "pi_3OqX...",
                                            "clientSecret": "pi_3OqX..._secret_..."
                                          }
                                        }"""
                        )
                )
        ),
        @ApiResponse(
                responseCode = "400",
                description = "Validation failed",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = Object.class),
                        examples = @ExampleObject(
                                name = "Validation error",
                                value = """
                                        {
                                          "success": false,
                                          "message": "bookingId: must not be null",
                                          "data": null
                                        }"""
                        )
                )
        ),
        @ApiResponse(
                responseCode = "502",
                description = "Stripe API failure",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = Object.class),
                        examples = @ExampleObject(
                                name = "Stripe error",
                                value = """
                                        {
                                          "success": false,
                                          "message": "Payment provider error. Please try again.",
                                          "data": null
                                        }"""
                        )
                )
        )
})
public @interface InitiatePaymentIntentDocs {
}
