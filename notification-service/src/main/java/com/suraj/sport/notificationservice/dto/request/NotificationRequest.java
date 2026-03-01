package com.suraj.sport.notificationservice.dto.request;

import com.suraj.sport.notificationservice.entity.NotificationChannel;
import com.suraj.sport.notificationservice.entity.NotificationType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NotificationRequest {

    @NotNull(message = "User ID is required")
    private Long userId;

    @NotNull(message = "Notification type is required")
    private NotificationType notificationType;

    @NotNull(message = "Channel is required")
    private NotificationChannel channel;

    @NotBlank(message = "Recipient email is required")
    @Email(message = "Invalid email format")
    private String recipientEmail;

    // Dynamic variables injected into Thymeleaf template
    // e.g. bookingId, eventName, amount, refundAmount etc.
    // Each handler knows which variables its template needs
    private Map<String, Object> templateVariables;
}