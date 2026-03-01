package com.suraj.sport.paymentservice.dto.response;

import com.suraj.sport.paymentservice.entity.RefundStatus;

import java.time.LocalDateTime;

public record RefundResponse(
        Long id,
        Long paymentId,
        double refundAmount,
        String refundReason,
        RefundStatus refundStatus,
        String gatewayRefundId,
        String failureReason,
        LocalDateTime refundedAt,
        LocalDateTime createdAt
) {}