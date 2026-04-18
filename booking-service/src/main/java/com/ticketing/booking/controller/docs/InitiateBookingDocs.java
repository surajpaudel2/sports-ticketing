package com.ticketing.booking.controller.docs;

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
 * Swagger/OpenAPI documentation for {@code POST /api/v1/bookings/initiate}.
 * Extracted from the controller to keep {@code BookingController} focused on routing only.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Operation(
        summary = "Initiate a booking",
        description = """
                Checks seat availability in Event Service (Redis + DB optimistic lock),
                atomically reserves the seats, then delegates PaymentIntent creation to
                Payment Service. Returns a clientSecret that the frontend passes to
                stripe.confirmPayment() — card details never reach this server.
                After calling this endpoint, the frontend confirms payment with Stripe directly,
                then polls GET /api/v1/bookings/{bookingId}/status every 2 seconds until the
                status transitions out of PENDING.
                """
)
@ApiResponses({
        @ApiResponse(
                responseCode = "201",
                description = "Booking initiated — clientSecret returned for Stripe Elements",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = Object.class),
                        examples = @ExampleObject(
                                name = "Success",
                                value = """
                                        {
                                          "success": true,
                                          "message": "Booking initiated successfully",
                                          "data": {
                                            "bookingId": 12,
                                            "clientSecret": "pi_3OqX..._secret_...",
                                            "totalAmount": 99.98
                                          }
                                        }"""
                        )
                )
        ),
        @ApiResponse(
                responseCode = "201",
                description = "Booking failed at event check — no PaymentIntent created, no charge made",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = Object.class),
                        examples = @ExampleObject(
                                name = "Event check failure",
                                value = """
                                        {
                                          "success": false,
                                          "message": "Insufficient seats available",
                                          "data": null
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
                                          "message": "seatsBooked: must be greater than or equal to 1",
                                          "data": null
                                        }"""
                        )
                )
        ),
        @ApiResponse(
                responseCode = "503",
                description = "Event Service unavailable",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = Object.class),
                        examples = @ExampleObject(
                                name = "Service unavailable",
                                value = """
                                        {
                                          "success": false,
                                          "message": "A downstream service is currently unavailable. Please try again later.",
                                          "data": null
                                        }"""
                        )
                )
        ),
        @ApiResponse(
                responseCode = "500",
                description = "Unexpected error",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = Object.class),
                        examples = @ExampleObject(
                                name = "Internal server error",
                                value = """
                                        {
                                          "success": false,
                                          "message": "An unexpected error occurred. Please try again later.",
                                          "data": null
                                        }"""
                        )
                )
        )
})
public @interface InitiateBookingDocs {
}