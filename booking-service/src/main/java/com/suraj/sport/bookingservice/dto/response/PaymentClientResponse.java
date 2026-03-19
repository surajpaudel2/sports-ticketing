package com.suraj.sport.bookingservice.dto.response;

// =====================================================================
// PAYMENT CLIENT RESPONSE — LOCAL DTO
// Local copy of Payment Service's PaymentResponse.
// Only contains fields Booking Service actually needs.
// =====================================================================
public record PaymentClientResponse(
        Long id,
        Long bookingId,
        double amount,
        String paymentStatus,
        String paymentMethod,
        String receiptUrl
) {}