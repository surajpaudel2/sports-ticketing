package com.suraj.sport.paymentservice.controller;

import com.suraj.sport.paymentservice.dto.request.InitiatePaymentRequest;
import com.suraj.sport.paymentservice.dto.request.ProcessRefundRequest;
import com.suraj.sport.paymentservice.dto.response.ApiResult;
import com.suraj.sport.paymentservice.dto.response.PaymentResponse;
import com.suraj.sport.paymentservice.dto.response.RefundResponse;
import com.suraj.sport.paymentservice.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "Payment API", description = "Manages payment processing, transaction tracking and refunds")
@RestController
@RequestMapping("/api/v1/payment")
@RequiredArgsConstructor
public class PaymentController {

    private final PaymentService paymentService;

    // =====================================================================
    // POST PAYMENT - INITIATE
    // =====================================================================

    @Operation(
            summary = "Initiate a payment",
            description = "Initiates a payment for a booking. Called internally by Booking Service only — not by end users. Booking Service has already validated seats and event availability. Gateway integration will be wired in future."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Payment initiated successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": true,
                                        "message": "Payment Initiated Successfully",
                                        "data": {
                                            "id": 1,
                                            "bookingId": 1,
                                            "eventId": 1,
                                            "userId": 1,
                                            "amount": 5000.00,
                                            "paymentStatus": "SUCCESS",
                                            "paymentMethod": "CREDIT_CARD",
                                            "receiptUrl": "https://receipts.stub.com/1",
                                            "createdAt": "2025-02-25T10:00:00",
                                            "updatedAt": "2025-02-25T10:00:00"
                                        }
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Validation failed",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "Amount must be greater than 0",
                                        "data": null
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected internal server error",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "An unexpected error occurred",
                                        "data": null
                                    }
                                    """))
            )
    })
    @PostMapping
    public ResponseEntity<ApiResult<PaymentResponse>> initiatePayment(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Payment details",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "bookingId": 1,
                                        "eventId": 1,
                                        "userId": 1,
                                        "amount": 5000.00,
                                        "paymentMethod": "CREDIT_CARD"
                                    }
                                    """))
            )
            @Valid @RequestBody InitiatePaymentRequest request) {
        PaymentResponse response = paymentService.initiatePayment(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of(true, "Payment Initiated Successfully", response));
    }

    // =====================================================================
    // POST PAYMENT - PROCESS REFUND
    // =====================================================================

    @Operation(
            summary = "Process a refund",
            description = "Processes a full or partial refund for a payment. Called internally by Booking Service on cancellation. Supports multiple partial refunds against same payment. Gateway integration will be wired in future."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Refund processed successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": true,
                                        "message": "Refund Processed Successfully",
                                        "data": {
                                            "id": 1,
                                            "paymentId": 1,
                                            "refundAmount": 2500.00,
                                            "refundReason": "Booking cancelled by user",
                                            "refundStatus": "SUCCESS",
                                            "gatewayRefundId": "GATEWAY_REFUND_STUB_1",
                                            "failureReason": null,
                                            "refundedAt": "2025-02-26T10:00:00",
                                            "createdAt": "2025-02-26T10:00:00"
                                        }
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Payment cannot be refunded or refund amount exceeds remaining refundable amount",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "Refund amount exceeds remaining refundable amount. Remaining: 2500.0",
                                        "data": null
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Payment not found",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "Payment not found with id: 1",
                                        "data": null
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected internal server error",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "An unexpected error occurred",
                                        "data": null
                                    }
                                    """))
            )
    })
    @PostMapping("/refund")
    public ResponseEntity<ApiResult<RefundResponse>> processRefund(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Refund details",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "paymentId": 1,
                                        "refundAmount": 2500.00,
                                        "refundReason": "Booking cancelled by user"
                                    }
                                    """))
            )
            @Valid @RequestBody ProcessRefundRequest request) {
        RefundResponse response = paymentService.processRefund(request);
        return ResponseEntity.ok(ApiResult.of(true, "Refund Processed Successfully", response));
    }

    // =====================================================================
    // PATCH PAYMENT - RETRY
    // =====================================================================

    @Operation(
            summary = "Retry a failed or pending payment",
            description = "Retries a FAILED or PENDING payment. Unlike initiatePayment, this MUST re-check seat availability in Event Service because seats were restored when payment failed. Gateway integration will be wired in future."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Payment retry initiated successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": true,
                                        "message": "Payment Retry Initiated Successfully",
                                        "data": {
                                            "id": 1,
                                            "bookingId": 1,
                                            "eventId": 1,
                                            "userId": 1,
                                            "amount": 5000.00,
                                            "paymentStatus": "PENDING",
                                            "paymentMethod": "CREDIT_CARD",
                                            "receiptUrl": null,
                                            "createdAt": "2025-02-25T10:00:00",
                                            "updatedAt": "2025-02-25T10:00:00"
                                        }
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "400",
                    description = "Payment is not in FAILED or PENDING status",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "Only FAILED or PENDING payments can be retried. Current status: SUCCESS",
                                        "data": null
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Payment not found",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "Payment not found with id: 1",
                                        "data": null
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected internal server error",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "An unexpected error occurred",
                                        "data": null
                                    }
                                    """))
            )
    })
    @PatchMapping("/{paymentId}/retry")
    public ResponseEntity<ApiResult<PaymentResponse>> retryPayment(
            @Parameter(description = "ID of the payment to retry", required = true, example = "1")
            @PathVariable Long paymentId) {
        PaymentResponse response = paymentService.retryPayment(paymentId);
        return ResponseEntity.ok(ApiResult.of(true, "Payment Retry Initiated Successfully", response));
    }

    // =====================================================================
    // GET PAYMENT BY ID
    // =====================================================================

    @Operation(
            summary = "Get payment by ID",
            description = "Retrieves full details of a payment by its unique ID."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Payment retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": true,
                                        "message": "Payment Retrieved Successfully",
                                        "data": {
                                            "id": 1,
                                            "bookingId": 1,
                                            "eventId": 1,
                                            "userId": 1,
                                            "amount": 5000.00,
                                            "paymentStatus": "SUCCESS",
                                            "paymentMethod": "CREDIT_CARD",
                                            "receiptUrl": "https://receipts.stub.com/1",
                                            "createdAt": "2025-02-25T10:00:00",
                                            "updatedAt": "2025-02-25T10:00:00"
                                        }
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Payment not found",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "Payment not found with id: 1",
                                        "data": null
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected internal server error",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "An unexpected error occurred",
                                        "data": null
                                    }
                                    """))
            )
    })
    @GetMapping("/{paymentId}")
    public ResponseEntity<ApiResult<PaymentResponse>> getPaymentById(
            @Parameter(description = "ID of the payment to retrieve", required = true, example = "1")
            @PathVariable Long paymentId) {
        PaymentResponse response = paymentService.getPaymentById(paymentId);
        return ResponseEntity.ok(ApiResult.of(true, "Payment Retrieved Successfully", response));
    }

    // =====================================================================
    // GET PAYMENT BY BOOKING ID
    // =====================================================================

    @Operation(
            summary = "Get payment by booking ID",
            description = "Retrieves payment details for a specific booking. Used by Booking Service to check payment status."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Payment retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": true,
                                        "message": "Payment Retrieved Successfully",
                                        "data": {
                                            "id": 1,
                                            "bookingId": 1,
                                            "eventId": 1,
                                            "userId": 1,
                                            "amount": 5000.00,
                                            "paymentStatus": "SUCCESS",
                                            "paymentMethod": "CREDIT_CARD",
                                            "receiptUrl": "https://receipts.stub.com/1",
                                            "createdAt": "2025-02-25T10:00:00",
                                            "updatedAt": "2025-02-25T10:00:00"
                                        }
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Payment not found for booking",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "Payment not found for booking id: 1",
                                        "data": null
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "500",
                    description = "Unexpected internal server error",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "An unexpected error occurred",
                                        "data": null
                                    }
                                    """))
            )
    })
    @GetMapping("/booking/{bookingId}")
    public ResponseEntity<ApiResult<PaymentResponse>> getPaymentByBookingId(
            @Parameter(description = "ID of the booking to retrieve payment for", required = true, example = "1")
            @PathVariable Long bookingId) {
        PaymentResponse response = paymentService.getPaymentByBookingId(bookingId);
        return ResponseEntity.ok(ApiResult.of(true, "Payment Retrieved Successfully", response));
    }
}