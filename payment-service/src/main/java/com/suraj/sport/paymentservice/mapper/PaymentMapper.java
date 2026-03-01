package com.suraj.sport.paymentservice.mapper;

import com.suraj.sport.paymentservice.dto.request.InitiatePaymentRequest;
import com.suraj.sport.paymentservice.dto.response.PaymentResponse;
import com.suraj.sport.paymentservice.dto.response.RefundResponse;
import com.suraj.sport.paymentservice.entity.Payment;
import com.suraj.sport.paymentservice.entity.PaymentStatus;
import com.suraj.sport.paymentservice.entity.Refund;

public class PaymentMapper {

    private PaymentMapper() {}

    /**
     * Maps InitiatePaymentRequest to Payment entity.
     * Status is always set to PENDING on creation —
     * updated to SUCCESS or FAILED after gateway response.
     */
    public static Payment mapToPayment(InitiatePaymentRequest request) {
        return Payment.builder()
                .bookingId(request.getBookingId())
                .eventId(request.getEventId())
                .userId(request.getUserId())
                .amount(request.getAmount())
                .paymentStatus(PaymentStatus.PENDING)
                .paymentMethod(request.getPaymentMethod())
                .build();
    }

    /**
     * Maps Payment entity to PaymentResponse.
     * Used for all payment-related responses.
     */
    public static PaymentResponse mapToPaymentResponse(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getBookingId(),
                payment.getEventId(),
                payment.getUserId(),
                payment.getAmount(),
                payment.getPaymentStatus(),
                payment.getPaymentMethod(),
                payment.getReceiptUrl(),
                payment.getCreatedAt(),
                payment.getUpdatedAt()
        );
    }

    /**
     * Maps Refund entity to RefundResponse.
     * Used for all refund-related responses.
     */
    public static RefundResponse mapToRefundResponse(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getPayment().getId(),
                refund.getRefundAmount(),
                refund.getRefundReason(),
                refund.getRefundStatus(),
                refund.getGatewayRefundId(),
                refund.getFailureReason(),
                refund.getRefundedAt(),
                refund.getCreatedAt()
        );
    }
}