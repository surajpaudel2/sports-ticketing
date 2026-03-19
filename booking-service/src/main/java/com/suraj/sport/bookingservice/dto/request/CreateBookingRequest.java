package com.suraj.sport.bookingservice.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateBookingRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Event ID is required")
    private Long eventId;

    @Min(value = 1, message = "Seats booked must be at least 1")
    private int seatsBooked;

    @NotNull(message = "Payment method is required")
    private String paymentMethod; // e.g. "CREDIT_CARD", "PAYPAL", etc.

    @Email
    private String recipientEmail; // For sending booking confirmation email
}