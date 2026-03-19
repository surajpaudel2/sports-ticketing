package com.suraj.sport.bookingservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

// =====================================================================
// INITIATE PAYMENT REQUEST — LOCAL DTO
// Used to send payment initiation request to Payment Service via Feign.
// =====================================================================
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class InitiatePaymentRequest {
    private Long bookingId;
    private Long eventId;
    private Long userId;
    private double amount;
    private String paymentMethod;
}