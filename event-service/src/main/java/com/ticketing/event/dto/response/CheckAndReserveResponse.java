package com.ticketing.event.dto.response;

/**
 * Response payload for {@code POST /api/v1/events/{eventId}/check-and-reserve}.
 *
 * <p>Returned when Redis confirms sufficient seat availability AND the DB confirms
 * availability under optimistic locking. At this point seats have been atomically
 * deducted from the event's inventory.</p>
 *
 * <p><strong>Cross-service contract:</strong> the field names here must match the
 * {@code EventBookingResponse} record in booking-service, which deserializes this
 * payload via Feign. Any rename requires a matching update in booking-service.</p>
 */
public record CheckAndReserveResponse(

        // Event identifier — echoed back for correlation and used as the key
        // when booking-service needs to release seats on payment failure
        Long eventId,

        // Human-readable event name — forwarded to booking-service for use in notifications
        String eventName,

        // Price per seat at the time of reservation — snapshotted by booking-service onto
        // the Booking entity so the booking total is locked in even if the event price changes later
        double pricePerSeat,

        // Number of seats being reserved — echoed from the request for confirmation
        int seatsBooked

) {}
