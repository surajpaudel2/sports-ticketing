package com.suraj.sport.notificationservice.controller;

import com.suraj.sport.notificationservice.dto.request.NotificationRequest;
import com.suraj.sport.notificationservice.dto.response.ApiResult;
import com.suraj.sport.notificationservice.dto.response.NotificationResponse;
import com.suraj.sport.notificationservice.service.NotificationService;
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

import java.util.List;

@Tag(name = "Notification API", description = "Manages sending and tracking of notifications")
@RestController
@RequestMapping("/api/v1/notification")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    // =====================================================================
    // POST NOTIFICATION - SEND
    // =====================================================================

    @Operation(
            summary = "Send a notification",
            description = "Sends an email notification to a user. Called internally by other services. Email is sent asynchronously — response returns immediately while email sends in background."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "201",
                    description = "Notification sent successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": true,
                                        "message": "Notification Sent Successfully",
                                        "data": {
                                            "id": 1,
                                            "userId": 1,
                                            "notificationType": "PAYMENT_SUCCEEDED",
                                            "channel": "EMAIL",
                                            "recipientEmail": "user@example.com",
                                            "subject": "Your payment was successful!",
                                            "status": "SENT",
                                            "failureReason": null,
                                            "sentAt": "2025-02-25T10:00:00",
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
                                        "message": "Recipient email is required",
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
    public ResponseEntity<ApiResult<NotificationResponse>> sendNotification(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "Notification details",
                    required = true,
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "userId": 1,
                                        "notificationType": "PAYMENT_SUCCEEDED",
                                        "channel": "EMAIL",
                                        "recipientEmail": "user@example.com",
                                        "templateVariables": {
                                            "paymentId": 1,
                                            "bookingId": 1,
                                            "eventName": "IPL 2025 Final",
                                            "amount": 5000.00,
                                            "paymentMethod": "CREDIT_CARD",
                                            "receiptUrl": "https://receipts.stub.com/1"
                                        }
                                    }
                                    """))
            )
            @Valid @RequestBody NotificationRequest request) {
        NotificationResponse response = notificationService.sendNotification(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResult.of(true, "Notification Sent Successfully", response));
    }

    // =====================================================================
    // GET NOTIFICATION BY ID
    // =====================================================================

    @Operation(
            summary = "Get notification by ID",
            description = "Retrieves full details of a notification by its unique ID."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Notification retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": true,
                                        "message": "Notification Retrieved Successfully",
                                        "data": {
                                            "id": 1,
                                            "userId": 1,
                                            "notificationType": "PAYMENT_SUCCEEDED",
                                            "channel": "EMAIL",
                                            "recipientEmail": "user@example.com",
                                            "subject": "Your payment was successful!",
                                            "status": "SENT",
                                            "failureReason": null,
                                            "sentAt": "2025-02-25T10:00:00",
                                            "createdAt": "2025-02-25T10:00:00",
                                            "updatedAt": "2025-02-25T10:00:00"
                                        }
                                    }
                                    """))
            ),
            @ApiResponse(
                    responseCode = "404",
                    description = "Notification not found",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": false,
                                        "message": "Notification not found with id: 1",
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
    @GetMapping("/{notificationId}")
    public ResponseEntity<ApiResult<NotificationResponse>> getNotificationById(
            @Parameter(description = "ID of the notification to retrieve", required = true, example = "1")
            @PathVariable Long notificationId) {
        NotificationResponse response = notificationService.getNotificationById(notificationId);
        return ResponseEntity.ok(ApiResult.of(true, "Notification Retrieved Successfully", response));
    }

    // =====================================================================
    // GET ALL NOTIFICATIONS BY USER ID
    // =====================================================================

    // TODO: pagination — return type will change to CursorPageResponse<NotificationResponse>
    //       once keyset pagination is implemented. Likely a breaking change — /api/v2/notification

    // TODO: filtering — method signature will accept @RequestParam filters like
    //       notificationType, status, channel, dateRange once filtering is implemented.

    @Operation(
            summary = "Get all notifications for a user",
            description = "Retrieves all notifications for a specific user. Note: Pagination and filtering will be added in the future."
    )
    @ApiResponses(value = {
            @ApiResponse(
                    responseCode = "200",
                    description = "Notifications retrieved successfully",
                    content = @Content(mediaType = "application/json",
                            examples = @ExampleObject(value = """
                                    {
                                        "success": true,
                                        "message": "Notifications Retrieved Successfully",
                                        "data": [
                                            {
                                                "id": 1,
                                                "userId": 1,
                                                "notificationType": "PAYMENT_SUCCEEDED",
                                                "channel": "EMAIL",
                                                "recipientEmail": "user@example.com",
                                                "subject": "Your payment was successful!",
                                                "status": "SENT",
                                                "failureReason": null,
                                                "sentAt": "2025-02-25T10:00:00",
                                                "createdAt": "2025-02-25T10:00:00",
                                                "updatedAt": "2025-02-25T10:00:00"
                                            }
                                        ]
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
    @GetMapping("/user/{userId}")
    public ResponseEntity<ApiResult<List<NotificationResponse>>> getAllNotificationsByUserId(
            @Parameter(description = "ID of the user to retrieve notifications for", required = true, example = "1")
            @PathVariable Long userId) {
        List<NotificationResponse> response = notificationService.getAllNotificationsByUserId(userId);
        return ResponseEntity.ok(ApiResult.of(true, "Notifications Retrieved Successfully", response));
    }
}