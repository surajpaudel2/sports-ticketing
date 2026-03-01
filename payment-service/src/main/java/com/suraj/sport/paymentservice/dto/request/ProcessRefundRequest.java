package com.suraj.sport.paymentservice.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class ProcessRefundRequest {

    @NotNull(message = "Payment ID is required")
    private Long paymentId;

    @Min(value = 1, message = "Refund amount must be greater than 0")
    private double refundAmount;

    @NotBlank(message = "Refund reason is required")
    private String refundReason;
}