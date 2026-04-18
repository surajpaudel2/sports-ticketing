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
 * Swagger/OpenAPI documentation for {@code POST /api/v1/events/{eventId}/check-and-reserve}.
 * Extracted from the controller to keep {@code EventController} focused on routing only.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Operation(
        summary = "Check seat availability and reserve seats",
        description = """
                Two-phase seat reservation:
                1. Redis pre-check — rejects immediately if the event is not cached or
                   the cached seat count is less than requested.
                2. DB confirmation under pessimistic locking — acquires SELECT … FOR UPDATE,
                   re-reads availableSeats (authoritative), and atomically deducts the seats.
                   Concurrent transactions block at the DB level until this one commits.

                On success: seats are deducted from inventory. The caller (booking-service)
                must call releaseSeats if the subsequent payment initiation fails.

                On failure: no seats are modified. Throws:
                - 404 if the event is not found in Redis
                - 409 if seats are insufficient
                - 503 if the DB lock could not be acquired (extreme contention) — retry
                """
)
@ApiResponses({
        @ApiResponse(
                responseCode = "200",
                description = "Seats reserved successfully — price snapshot returned",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = Object.class),
                        examples = @ExampleObject(
                                name = "Success",
                                value = """
                                        {
                                          "success": true,
                                          "message": "Seats reserved successfully",
                                          "data": {
                                            "eventId": 42,
                                            "eventName": "FA Cup Final 2025",
                                            "pricePerSeat": 49.99,
                                            "seatsBooked": 2
                                          }
                                        }"""
                        )
                )
        ),
        @ApiResponse(
                responseCode = "404",
                description = "Event not found in Redis cache",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = Object.class),
                        examples = @ExampleObject(
                                name = "Event not found",
                                value = """
                                        {
                                          "success": false,
                                          "message": "Event not found: 42",
                                          "data": null
                                        }"""
                        )
                )
        ),
        @ApiResponse(
                responseCode = "409",
                description = "Insufficient seats or concurrent booking conflict",
                content = @Content(
                        mediaType = "application/json",
                        schema = @Schema(implementation = Object.class),
                        examples = @ExampleObject(
                                name = "Insufficient seats",
                                value = """
                                        {
                                          "success": false,
                                          "message": "Insufficient seats available — requested 4 but only 2 remain",
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
                                name = "Internal error",
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
public @interface CheckAndReserveDocs {
}
