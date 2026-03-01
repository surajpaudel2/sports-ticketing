package com.suraj.sport.notificationservice.dto.response;

import com.suraj.sport.notificationservice.entity.NotificationChannel;
import com.suraj.sport.notificationservice.entity.NotificationStatus;
import com.suraj.sport.notificationservice.entity.NotificationType;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long id,
        Long userId,
        NotificationType notificationType,
        NotificationChannel channel,
        String recipientEmail,
        String subject,
        NotificationStatus status,
        String failureReason,
        LocalDateTime sentAt,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {}