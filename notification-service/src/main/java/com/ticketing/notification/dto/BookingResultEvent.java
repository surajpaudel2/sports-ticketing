package com.ticketing.notification.dto;

/**
 * Inbound RabbitMQ event consumed from {@code booking.notification.queue}.
 * Published by Booking Service on both confirmed and failed booking outcomes.
 *
 * <p>All fields needed for the receipt email are embedded — no inter-service
 * calls are required to render a complete notification.</p>
 */
public record BookingResultEvent(

        Long bookingId,
        Long userId,
        BookingStatus bookingStatus,
        String recipientEmail,

        Long eventId,
        String eventName,
        int seatsBooked,
        double pricePerSeat,
        double totalAmount,

        String reason

) {}
