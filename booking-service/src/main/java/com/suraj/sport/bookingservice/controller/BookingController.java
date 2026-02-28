package com.suraj.sport.bookingservice.controller;

import com.suraj.sport.bookingservice.dto.request.CancelBookingRequest;
import com.suraj.sport.bookingservice.dto.request.CreateBookingRequest;
import com.suraj.sport.bookingservice.dto.response.ApiResult;
import com.suraj.sport.bookingservice.dto.response.BookingResponse;
import com.suraj.sport.bookingservice.dto.response.CreateBookingResponse;
import com.suraj.sport.bookingservice.service.BookingService;
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

import java.util.List;

@Tag(name = "Booking API", description = "Manages sports event ticket bookings")
@RestController
@RequestMapping("/api/v1/booking")
@RequiredArgsConstructor
public class BookingController {

    private final BookingService bookingService;

    // =====================================================================
    // POST BOOKING - CREATE
    // =====================================================================

    @Operation(
            summary = "Create a new booking",
            description = "Creates a new ticket booking for a sports event. Booking starts as PENDING until payment is confirmed. Inter-service calls to Event Service and Payment Service will be wired in Section 8."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Booking created successfully with PENDING status",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": true,
                                        "message": "Booking Created Successfully",
                                        "data": {
                                            "id": 1,
                                            "userId": 1,
                                            "eventId": 1,
                                            "seatsBooked": 2,
                                            "pricePerSeat": 2500.00,
                                            "totalAmount": 5000.00,
                                            "bookingStatus": "PENDING",
                                            "createdAt": "2025-02-25T10:00:00"
                                        }
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "Seats booked must be at least 1",
                                        "data": null
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected internal server error",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "An unexpected error occurred",
                                        "data": null
                                    }
                                    """))
            )
    })
    @PostMapping
    public ResponseEntity<ApiResult<CreateBookingResponse>> createBooking(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Booking details",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "userId": 1,
                                        "eventId": 1,
                                        "seatsBooked": 2
                                    }
                                    """))
            )
            @Valid @RequestBody CreateBookingRequest request) {
        CreateBookingResponse response = bookingService.createBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of(true, "Booking Created Successfully", response));
    }

    // =====================================================================
    // PATCH BOOKING - CANCEL
    // =====================================================================

    @Operation(
            summary = "Cancel an existing booking",
            description = "Cancels a PENDING or CONFIRMED booking. Seat restoration and refund processing will be wired in Section 8."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Booking cancelled successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": true,
                                        "message": "Booking Cancelled Successfully",
                                        "data": {
                                            "id": 1,
                                            "userId": 1,
                                            "eventId": 1,
                                            "paymentId": null,
                                            "seatsBooked": 2,
                                            "pricePerSeat": 2500.00,
                                            "totalAmount": 5000.00,
                                            "bookingStatus": "CANCELLED",
                                            "cancellationReason": "Change of plans",
                                            "createdAt": "2025-02-25T10:00:00",
                                            "updatedAt": "2025-02-26T10:00:00"
                                        }
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Booking cannot be cancelled",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "Booking is already cancelled",
                                        "data": null
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Booking not found",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "Booking not found with id: 1",
                                        "data": null
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected internal server error",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "An unexpected error occurred",
                                        "data": null
                                    }
                                    """))
            )
    })
    @PatchMapping("/{bookingId}/cancel")
    public ResponseEntity<ApiResult<BookingResponse>> cancelBooking(
            @Parameter(description = "ID of the booking to cancel", required = true, example = "1")
            @PathVariable Long bookingId,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Cancellation details",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "cancellationReason": "Change of plans"
                                    }
                                    """))
            )
            @Valid @RequestBody CancelBookingRequest request) {
        BookingResponse response = bookingService.cancelBooking(bookingId, request);
        return ResponseEntity.ok(ApiResult.of(true, "Booking Cancelled Successfully", response));
    }

    // =====================================================================
    // PATCH BOOKING - RETRY PAYMENT
    // =====================================================================

    @Operation(
            summary = "Retry payment for a PENDING booking",
            description = "Retries payment for a PENDING booking. Seats are re-checked and re-deducted on every retry. Payment Service integration will be wired in Section 8."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Payment retry initiated successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": true,
                                        "message": "Payment Retry Initiated Successfully",
                                        "data": {
                                            "id": 1,
                                            "userId": 1,
                                            "eventId": 1,
                                            "paymentId": null,
                                            "seatsBooked": 2,
                                            "pricePerSeat": 2500.00,
                                            "totalAmount": 5000.00,
                                            "bookingStatus": "PENDING",
                                            "cancellationReason": null,
                                            "createdAt": "2025-02-25T10:00:00",
                                            "updatedAt": "2025-02-26T10:00:00"
                                        }
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Booking is not in PENDING status",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "Only PENDING bookings can retry payment. Current status: CONFIRMED",
                                        "data": null
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Booking not found",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "Booking not found with id: 1",
                                        "data": null
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected internal server error",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "An unexpected error occurred",
                                        "data": null
                                    }
                                    """))
            )
    })
    @PatchMapping("/{bookingId}/retry-payment")
    public ResponseEntity<ApiResult<BookingResponse>> retryPayment(
            @Parameter(description = "ID of the booking to retry payment for", required = true, example = "1")
            @PathVariable Long bookingId) {
        BookingResponse response = bookingService.retryPayment(bookingId);
        return ResponseEntity.ok(ApiResult.of(true, "Payment Retry Initiated Successfully", response));
    }

    // =====================================================================
    // PATCH BOOKING - REBOOK
    // =====================================================================

    @Operation(
            summary = "Re-book a cancelled booking",
            description = "Re-books a previously CANCELLED booking. Treated as a fresh booking — checks event availability and redoes payment. Inter-service calls will be wired in Section 8."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Re-booking initiated successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": true,
                                        "message": "Re-booking Initiated Successfully",
                                        "data": {
                                            "id": 1,
                                            "userId": 1,
                                            "eventId": 1,
                                            "paymentId": null,
                                            "seatsBooked": 2,
                                            "pricePerSeat": 2500.00,
                                            "totalAmount": 5000.00,
                                            "bookingStatus": "PENDING",
                                            "cancellationReason": null,
                                            "createdAt": "2025-02-25T10:00:00",
                                            "updatedAt": "2025-02-26T10:00:00"
                                        }
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Booking is not in CANCELLED status",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "Only CANCELLED bookings can be re-booked. Current status: PENDING",
                                        "data": null
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Booking not found",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "Booking not found with id: 1",
                                        "data": null
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected internal server error",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "An unexpected error occurred",
                                        "data": null
                                    }
                                    """))
            )
    })
    @PatchMapping("/{bookingId}/rebook")
    public ResponseEntity<ApiResult<BookingResponse>> reBook(
            @Parameter(description = "ID of the cancelled booking to re-book", required = true, example = "1")
            @PathVariable Long bookingId) {
        BookingResponse response = bookingService.reBook(bookingId);
        return ResponseEntity.ok(ApiResult.of(true, "Re-booking Initiated Successfully", response));
    }

    // =====================================================================
    // GET BOOKING BY ID
    // =====================================================================

    @Operation(
            summary = "Get a booking by ID",
            description = "Retrieves full details of a booking by its unique ID."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Booking retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": true,
                                        "message": "Booking Retrieved Successfully",
                                        "data": {
                                            "id": 1,
                                            "userId": 1,
                                            "eventId": 1,
                                            "paymentId": 1,
                                            "seatsBooked": 2,
                                            "pricePerSeat": 2500.00,
                                            "totalAmount": 5000.00,
                                            "bookingStatus": "CONFIRMED",
                                            "cancellationReason": null,
                                            "createdAt": "2025-02-25T10:00:00",
                                            "updatedAt": "2025-02-26T10:00:00"
                                        }
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Booking not found",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "Booking not found with id: 1",
                                        "data": null
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected internal server error",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "An unexpected error occurred",
                                        "data": null
                                    }
                                    """))
            )
    })
    @GetMapping("/{bookingId}")
    public ResponseEntity<ApiResult<BookingResponse>> getBookingById(
            @Parameter(description = "ID of the booking to retrieve", required = true, example = "1")
            @PathVariable Long bookingId) {
        BookingResponse response = bookingService.getBookingById(bookingId);
        return ResponseEntity.ok(ApiResult.of(true, "Booking Retrieved Successfully", response));
    }

    // =====================================================================
    // GET ALL BOOKINGS BY USER ID
    // =====================================================================

    // TODO: pagination — return type will change to Page<BookingResponse> or
    //       CursorPageResponse<BookingResponse> once keyset pagination is implemented.
    //       Likely a breaking change — will require /api/v2/booking endpoint.

    // TODO: filtering — method signature will accept @RequestParam filters like
    //       bookingStatus once filtering is implemented.

    @Operation(
            summary = "Get all bookings for a user",
            description = "Retrieves all bookings for a specific user. Note: Pagination and filtering will be added in the future."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Bookings retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": true,
                                        "message": "Bookings Retrieved Successfully",
                                        "data": [
                                            {
                                                "id": 1,
                                                "userId": 1,
                                                "eventId": 1,
                                                "paymentId": 1,
                                                "seatsBooked": 2,
                                                "pricePerSeat": 2500.00,
                                                "totalAmount": 5000.00,
                                                "bookingStatus": "CONFIRMED",
                                                "cancellationReason": null,
                                                "createdAt": "2025-02-25T10:00:00",
                                                "updatedAt": "2025-02-26T10:00:00"
                                            }
                                        ]
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected internal server error",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "An unexpected error occurred",
                                        "data": null
                                    }
                                    """))
            )
    })
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResult<List<BookingResponse>>> getAllBookingsByUserId(
            @Parameter(description = "ID of the user to retrieve bookings for", required = true, example = "1")
            @PathVariable Long userId) {
        List<BookingResponse> response = bookingService.getAllBookingsByUserId(userId);
        return ResponseEntity.ok(ApiResult.of(true, "Bookings Retrieved Successfully", response));
    }
}