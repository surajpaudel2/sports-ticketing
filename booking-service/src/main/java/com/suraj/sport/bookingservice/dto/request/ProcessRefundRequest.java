package com.suraj.sport.bookingservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProcessRefundRequest {
    private Long paymentId;
    private double refundAmount;
    private String refundReason;
}