package com.ticketing.event.controller;

import com.ticketing.event.controller.docs.CheckAndReserveDocs;
import com.ticketing.event.controller.docs.ReleaseSeatsDoc;
import com.ticketing.event.dto.response.ApiResult;
import com.ticketing.event.dto.response.CheckAndReserveResponse;
import com.ticketing.event.service.EventService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Min;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

/**
 * REST controller for Event Service.
 *
 * <p>Thin layer — handles HTTP concerns only (request mapping, response status codes,
 * parameter binding). All business logic lives in {@link EventService}.</p>
 *
 * <p>Both endpoints are designed for internal inter-service use (called by booking-service
 * via Feign). They are not intended to be called directly by the frontend.</p>
 */
@RestController
@RequestMapping("/api/v1/events")
@RequiredArgsConstructor
@Validated
@Tag(name = "Event", description = "Event seat inventory management — internal inter-service endpoints")
public class EventController {

    private final EventService eventService;

    /**
     * Validates seat availability in Redis, confirms under optimistic locking in the DB,
     * and atomically deducts the reserved seats.
     *
     * <p>Called by booking-service as the first step of {@code initiateBooking}.
     * If this call succeeds, seats are reserved and must be explicitly released via
     * {@code /release-seats} if the subsequent payment initiation fails.</p>
     *
     * <p>Exceptions thrown by {@link EventService#checkAndReserve} propagate to
     * {@code GlobalExceptionHandler} which maps them to structured HTTP error responses.
     * No exception propagates from this method to the caller as a raw stack trace.</p>
     *
     * @param eventId the event to reserve seats for
     * @param seats   the number of seats to reserve (must be ≥ 1)
     * @return HTTP 200 with the reserved event details (price, name, seat count)
     */
    @CheckAndReserveDocs
    @PostMapping("/{eventId}/check-and-reserve")
    public ResponseEntity<ApiResult<CheckAndReserveResponse>> checkAndReserve(
            @PathVariable Long eventId,
            @RequestParam @Min(1) int seats) {

        CheckAndReserveResponse response = eventService.checkAndReserve(eventId, seats);
        return ResponseEntity.ok(ApiResult.of(true, "Seats reserved successfully", response));
    }

    /**
     * Restores the given number of seats to the event's available inventory.
     *
     * <p>Compensating operation for {@code checkAndReserve} — called by booking-service
     * when payment initiation fails or Stripe reports a payment failure.</p>
     *
     * @param eventId the event to release seats for
     * @param seats   the number of seats to return (must be ≥ 1)
     * @return HTTP 200 with a success confirmation
     */
    @ReleaseSeatsDoc
    @PostMapping("/{eventId}/release-seats")
    public ResponseEntity<ApiResult<Void>> releaseSeats(
            @PathVariable Long eventId,
            @RequestParam @Min(1) int seats) {

        eventService.releaseSeats(eventId, seats);
        return ResponseEntity.ok(ApiResult.of(true, "Seats released successfully", null));
    }
}
