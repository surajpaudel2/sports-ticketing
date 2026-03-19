package com.suraj.sport.bookingservice.client;

import com.suraj.sport.bookingservice.dto.response.ApiResult;
import com.suraj.sport.bookingservice.dto.response.EventClientResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;

@FeignClient(name = "event-service")
public interface EventServiceClient {

    // =====================================================================
    // GET EVENT BY ID
    // Used to fetch event details — price per seat, status, available seats
    // Called in createBooking and reBook to validate event is bookable
    // =====================================================================

    @GetMapping("/api/v1/event/{eventId}")
    ApiResult<EventClientResponse> getEventById(@PathVariable Long eventId);

    // =====================================================================
    // CHECK SEATS AVAILABILITY
    // Called before deducting seats — verify enough seats are available
    // Called in createBooking, retryPayment, reBook
    // =====================================================================

    @GetMapping("/api/v1/event/{eventId}/check-seats")
    ApiResult<Boolean> checkSeatsAvailability(
            @PathVariable Long eventId,
            @RequestParam int seats);

    // =====================================================================
    // REDUCE AVAILABLE SEATS
    // Called after confirming seats are available
    // Called in createBooking, retryPayment, reBook
    // =====================================================================

    @PatchMapping("/api/v1/event/{eventId}/reduce-seats")
    ApiResult<Void> reduceSeats(
            @PathVariable Long eventId,
            @RequestParam int seats);

    // =====================================================================
    // RESTORE AVAILABLE SEATS
    // Called when payment fails — give seats back
    // Called in createBooking and retryPayment on payment failure
    // NOTE: Not called on cancel for PENDING bookings — seats were already
    //       restored when payment failed during creation
    // =====================================================================

    @PatchMapping("/api/v1/event/{eventId}/restore-seats")
    ApiResult<Void> restoreSeats(
            @PathVariable Long eventId,
            @RequestParam int seats);
}