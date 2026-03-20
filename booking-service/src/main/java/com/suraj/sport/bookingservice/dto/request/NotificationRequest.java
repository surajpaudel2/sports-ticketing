package com.suraj.sport.bookingservice.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

// =====================================================================
// NOTIFICATION REQUEST — LOCAL DTO
// Used to send notification request to Notification Service via Feign.
// Booking Service provides type, channel, recipient and template variables.
// Notification Service handles template selection and email composition.
// =====================================================================
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class NotificationRequest {
    private Long userId;

    private String notificationType;

    private String channel;

    private String recipientEmail;

    private Map<String, Object> templateVariables;
}