package com.suraj.sport.paymentservice.service;

import com.suraj.sport.paymentservice.dto.request.InitiatePaymentRequest;
import com.suraj.sport.paymentservice.dto.request.ProcessRefundRequest;
import com.suraj.sport.paymentservice.dto.response.PaymentResponse;
import com.suraj.sport.paymentservice.dto.response.RefundResponse;

public interface PaymentService {

    PaymentResponse initiatePayment(InitiatePaymentRequest request);

    RefundResponse processRefund(ProcessRefundRequest request);

    PaymentResponse retryPayment(Long paymentId);

    PaymentResponse getPaymentById(Long paymentId);

    PaymentResponse getPaymentByBookingId(Long bookingId);
}