package com.suraj.sport.bookingservice.dto.response;

public record RefundClientResponse(
        Long id,
        Long paymentId,
        double refundAmount,
        String refundStatus,
        String gatewayRefundId
) {}