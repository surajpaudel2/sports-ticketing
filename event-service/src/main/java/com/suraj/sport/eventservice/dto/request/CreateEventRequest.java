package com.suraj.sport.eventservice.dto.request;

import com.suraj.sport.eventservice.entity.EventStatus;
import jakarta.validation.constraints.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateEventRequest {

    @NotBlank(message = "Event name is required")
    private String name;

    @NotBlank(message = "Sport type is required")
    private String sportType;

    @NotBlank(message = "Venue is required")
    private String venue;

    @Future(message = "Event date must be in the future")
    @NotNull(message = "Event date is required")
    private LocalDateTime eventDate;

    @Min(value = 1, message = "Total seats must be at least 1")
    private int totalSeats;

    @DecimalMin(value = "0.0", inclusive = false, message = "Price per seat must be greater than 0")
    private double pricePerSeat;

}