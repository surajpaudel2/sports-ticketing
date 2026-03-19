package com.suraj.sport.bookingservice.dto.response;

import java.time.LocalDateTime;

// =====================================================================
// EVENT RESPONSE — LOCAL DTO
// This is a local copy of Event Service's EventResponse.
// Used to deserialize the response from EventServiceClient.
// Only contains fields Booking Service actually needs.
// =====================================================================
public record EventClientResponse(
        Long id,
        String name,
        String sportType,
        String venue,
        LocalDateTime eventDate,
        int totalSeats,
        int availableSeats,
        double pricePerSeat,
        String status
) {}