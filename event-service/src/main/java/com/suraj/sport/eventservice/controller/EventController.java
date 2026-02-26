package com.suraj.sport.eventservice.controller;

import com.suraj.sport.eventservice.dto.request.CreateEventRequest;
import com.suraj.sport.eventservice.dto.request.UpdateEventRequest;
import com.suraj.sport.eventservice.dto.response.ApiResult;
import com.suraj.sport.eventservice.dto.response.CreateEventResponse;
import com.suraj.sport.eventservice.dto.response.UpdateEventResponse;
import com.suraj.sport.eventservice.service.EventService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Event API", description = "Manages sports events")
@RestController
@RequestMapping("/api/v1/event")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    // =====================================================================
    // POST EVENT
    // =====================================================================

    @Operation(
            summary = "Create a new sports event",
            description = "Creates a sports event with venue, date, available seats and pricing. Status is automatically set to UPCOMING."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Event created successfully",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": true,
                                        "message": "Event Created Successfully",
                                        "data": {
                                            "id": 1,
                                            "name": "IPL 2025 Final",
                                            "createdAt": "2025-02-25T10:00:00"
                                        }
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed or available seats exceed total seats",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "Available seats cannot be greater than total seats",
                                        "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "409",
                    description = "Duplicate event already exists",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "An event with the same name, venue and date already exists",
                                        "data": null
                                    }
                                    """)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected internal server error",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "An unexpected error occurred",
                                        "data": null
                                    }
                                    """)
                    )
            )
    })
    @PostMapping
    public ResponseEntity<ApiResult<CreateEventResponse>> createEvent(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Sports event details",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                {
                                    "name": "IPL 2025 Final",
                                    "sportType": "Cricket",
                                    "venue": "Wankhede Stadium, Mumbai",
                                    "eventDate": "2025-05-25T18:00:00",
                                    "totalSeats": 1000,
                                    "availableSeats": 1000,
                                    "pricePerSeat": 2500.00
                                }
                                """)
                    )
            )
            @Valid @RequestBody CreateEventRequest createEventRequest) {
        CreateEventResponse createEventResponse = eventService.createEvent(createEventRequest);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of(true, "Event Created Successfully", createEventResponse));
    }

    // =====================================================================
    // PUT EVENT
    // =====================================================================

    @Operation(
            summary = "Update an existing sports event",
            description = "Updates a sports event. UPCOMING events can transition to ONGOING. ONGOING events can transition to COMPLETED or CANCELLED. Event date cannot be changed when ONGOING."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Event updated successfully",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                {
                                    "success": true,
                                    "message": "Event Updated Successfully",
                                    "data": {
                                        "id": 1,
                                        "name": "IPL 2025 Final",
                                        "sportType": "Cricket",
                                        "venue": "Eden Gardens, Kolkata",
                                        "eventDate": "2025-05-25T18:00:00",
                                        "totalSeats": 1000,
                                        "availableSeats": 800,
                                        "pricePerSeat": 3000.00,
                                        "status": "ONGOING",
                                        "createdAt": "2025-02-25T10:00:00"
                                    }
                                }
                                """)
                    )
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Invalid update request, invalid status transition or event date change not allowed",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                {
                                    "success": false,
                                    "message": "UPCOMING event can only transition to UPCOMING or ONGOING",
                                    "data": null
                                }
                                """)
                    )
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Event not found",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                {
                                    "success": false,
                                    "message": "Event not found with id: 1",
                                    "data": null
                                }
                                """)
                    )
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected internal server error",
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                {
                                    "success": false,
                                    "message": "An unexpected error occurred",
                                    "data": null
                                }
                                """)
                    )
            )
    })
    @PutMapping("/{eventId}")
    public ResponseEntity<ApiResult<UpdateEventResponse>> updateEvent(
            @Parameter(description = "ID of the event to update", required = true, example = "1")
            @PathVariable Long eventId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Updated event details",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                {
                                    "name": "IPL 2025 Final",
                                    "sportType": "Cricket",
                                    "venue": "Eden Gardens, Kolkata",
                                    "eventDate": "2025-05-25T18:00:00",
                                    "totalSeats": 1000,
                                    "pricePerSeat": 3000.00,
                                    "status": "ONGOING"
                                }
                                """)
                    )
            )
            @Valid @RequestBody UpdateEventRequest updateEventRequest) {
        UpdateEventResponse updateEventResponse = eventService.updateEvent(eventId, updateEventRequest);
        return ResponseEntity.ok(ApiResult.of(true, "Event Updated Successfully", updateEventResponse));
    }

}