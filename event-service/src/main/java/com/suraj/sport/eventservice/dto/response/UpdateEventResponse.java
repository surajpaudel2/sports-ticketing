package com.suraj.sport.eventservice.dto.response;

import com.suraj.sport.eventservice.entity.EventStatus;

import java.time.LocalDateTime;

public record UpdateEventResponse(
        long id,
        String name,
        String sportType,
        String venue,
        LocalDateTime eventDate,
        int totalSeats,
        int availableSeats,
        double pricePerSeat,
        EventStatus status,
        LocalDateTime createdAt
) {}