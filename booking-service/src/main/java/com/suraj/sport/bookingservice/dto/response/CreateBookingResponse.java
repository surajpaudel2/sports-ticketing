package com.suraj.sport.bookingservice.dto.response;

import com.suraj.sport.bookingservice.entity.BookingStatus;
import java.time.LocalDateTime;

public record CreateBookingResponse(
        long id,
        long userId,
        long eventId,
        int seatsBooked,
        double pricePerSeat,
        double totalAmount,
        BookingStatus bookingStatus,
        LocalDateTime createdAt
) {}