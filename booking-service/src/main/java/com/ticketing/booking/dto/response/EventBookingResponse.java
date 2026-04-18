package com.ticketing.booking.dto.response;

/**
 * Response from the Event Service {@code POST /api/v1/events/{eventId}/check-and-reserve} call.
 *
 * <p>The Event Service validates that the event exists and that sufficient seats are available,
 * then returns this payload. Seat deduction does NOT happen at this point — it is deferred
 * until payment fails (see {@code EventServiceClient#releaseSeats}).</p>
 *
 * <p>{@code pricePerSeat} is snapshotted here and stored on the {@code Booking} entity so that
 * the booking total is locked in at the moment of initiation, even if the event price changes
 * before the webhook fires.</p>
 */
public record EventBookingResponse(

        // Event identifier — echoed back for correlation and used as the lock key
        // when deducting seats inside BookingEventListener
        Long eventId,

        // Human-readable event name — included for display in notifications
        String eventName,

        // Price per seat at the time of the check — snapshotted onto the Booking entity
        double pricePerSeat,

        // Number of seats being booked — echoed from the request for confirmation
        int seatsBooked

) {}