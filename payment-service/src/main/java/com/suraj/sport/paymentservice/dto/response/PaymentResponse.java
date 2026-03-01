package com.suraj.sport.paymentservice.dto.response;

import com.suraj.sport.paymentservice.entity.PaymentStatus;

import java.time.LocalDateTime;

public record PaymentResponse(
        Long id,
        Long bookingId,
        Long eventId,
        Long userId,
        double amount,
        PaymentStatus paymentStatus,
        String paymentMethod,
        String receiptUrl,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}