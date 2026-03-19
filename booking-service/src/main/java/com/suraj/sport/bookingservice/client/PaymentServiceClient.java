package com.suraj.sport.bookingservice.client;

import com.suraj.sport.bookingservice.dto.request.InitiatePaymentRequest;
import com.suraj.sport.bookingservice.dto.response.ApiResult;
import com.suraj.sport.bookingservice.dto.response.PaymentClientResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "payment-service")
public interface PaymentServiceClient {

    // =====================================================================
    // INITIATE PAYMENT
    // Called after seats are successfully deducted in Event Service.
    // Booking Service trusts that seats are already handled before calling this.
    // =====================================================================
    @PostMapping("/api/v1/payment")
    ApiResult<PaymentClientResponse> initiatePayment(@RequestBody InitiatePaymentRequest request);
}