package com.ticketing.event.controller.docs;

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
 * Swagger/OpenAPI documentation for {@code POST /api/v1/events/{eventId}/release-seats}.
 * Extracted from the controller to keep {@code EventController} focused on routing only.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Operation(
        summary = "Release previously reserved seats",
        description = """
                Compensating operation for checkAndReserve — restores seats to the event's
                available inventory.

                Called by booking-service in two scenarios:
                1. Payment initiation (Feign call to payment-service) failed after checkAndReserve
                   already deducted seats.
                2. Stripe reported a payment failure (PaymentFailedEvent received by booking-service).

                On success: availableSeats is incremented by the requested quantity in both
                the DB and Redis.
                """
)
@ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Seats released successfully",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = Object.class),
                        examples = @ExampleObject(
                                name = "Success",
                                value = """
                                        {
                                          "success": true,
                                          "message": "Seats released successfully",
                                          "data": null
                                        }"""
                        )
                )
        ),
        @ApiResponse(
                responseCode = "404",
                description = "Event not found",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = Object.class),
                        examples = @ExampleObject(
                                name = "Not found",
                                value = """
                                        {
                                          "success": false,
                                          "message": "Event not found: 42",
                                          "data": null
                                        }"""
                        )
                )
        )
})
public @interface ReleaseSeatsDoc {
}
