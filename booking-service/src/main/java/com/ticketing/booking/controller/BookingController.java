package com.ticketing.booking.controller;

import com.ticketing.booking.controller.docs.InitiateBookingDocs;
import com.ticketing.booking.dto.request.InitiateBookingRequest;
import com.ticketing.booking.dto.response.ApiResult;
import com.ticketing.booking.dto.response.BookingStatusResponse;
import com.ticketing.booking.dto.response.InitiateBookingResponse;
import com.ticketing.booking.service.BookingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for Booking Service.
 *
 * <p>Thin layer — handles HTTP concerns only (request mapping, response status codes,
 * parameter binding). All business logic lives in {@code BookingService}.</p>
 *
 * <p>Both endpoints return {@link ApiResult} wrappers. The {@code success} field in the
 * body is the authoritative signal — HTTP 201 does not guarantee a successful booking.</p>
 */
@RestController
@RequestMapping("/api/v1/bookings")
@RequiredArgsConstructor
@Tag(name = "Booking", description = "Booking management endpoints")
public class BookingController {

    private final BookingService bookingService;

    /**
     * Initiates a booking: checks seat availability, creates a Stripe PaymentIntent,
     * and returns the clientSecret for the frontend to confirm payment with Stripe Elements.
     *
     * <p>Always returns HTTP 201. Check {@code success} in the response body to determine
     * whether initiation succeeded — a failed event check also returns 201 with
     * {@code success=false} and no {@code data}.</p>
     */
    @InitiateBookingDocs
    @PostMapping("/initiate")
    public ResponseEntity<ApiResult<InitiateBookingResponse>> initiateBooking(
            @Valid @RequestBody InitiateBookingRequest request) {

        ApiResult<InitiateBookingResponse> result = bookingService.initiateBooking(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(result);
    }

    /**
     * Returns the current status of a booking for frontend polling.
     *
     * <p>The frontend calls this every 2 seconds after {@code stripe.confirmPayment()}
     * completes, until {@code bookingStatus} transitions out of {@code PENDING}
     * (max ~15 seconds / ~7 attempts).</p>
     *
     * <p><strong>Security note:</strong> the requesting user must own this bookingId.
     * TODO: enforce ownership check once JWT authentication is wired — extract userId
     * from the token and verify it matches {@code Booking.userId}.</p>
     *
     * @param bookingId the booking to query — 404 if not found
     */
    @Operation(
            summary = "Get booking status",
            description = """
                    Polling endpoint used by the frontend after stripe.confirmPayment() completes.
                    Poll every 2 seconds until bookingStatus is no longer PENDING.
                    CONFIRMED → show success. FAILED → show failure + failureReason.
                    Timeout after ~15 seconds → show "still processing, check your email".
                    """
    )
    @GetMapping("/{bookingId}/status")
    public ResponseEntity<ApiResult<BookingStatusResponse>> getBookingStatus(
            @PathVariable Long bookingId) {

        ApiResult<BookingStatusResponse> result = bookingService.getBookingStatus(bookingId);
        return ResponseEntity.ok(result);
    }
}
