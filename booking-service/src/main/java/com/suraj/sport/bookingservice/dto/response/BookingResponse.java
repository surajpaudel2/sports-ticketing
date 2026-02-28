package com.suraj.sport.bookingservice.dto.response;

import com.suraj.sport.bookingservice.entity.BookingStatus;
import java.time.LocalDateTime;

public record BookingResponse(
        long id,
        long userId,
        long eventId,
        Long paymentId,
        int seatsBooked,
        double pricePerSeat,
        double totalAmount,
        BookingStatus bookingStatus,
        String cancellationReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}