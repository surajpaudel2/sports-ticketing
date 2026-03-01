package com.suraj.sport.notificationservice.mapper;

import com.suraj.sport.notificationservice.dto.request.NotificationRequest;
import com.suraj.sport.notificationservice.dto.response.NotificationResponse;
import com.suraj.sport.notificationservice.entity.Notification;
import com.suraj.sport.notificationservice.entity.NotificationStatus;

public class NotificationMapper {

    private NotificationMapper() {}

    /**
     * Maps NotificationRequest to Notification entity.
     * Status is always PENDING on creation — updated after email is sent.
     * Subject is initially empty — populated by handler after template is fetched.
     */
    public static Notification mapToNotification(NotificationRequest request) {
        return Notification.builder()
                .userId(request.getUserId())
                .notificationType(request.getNotificationType())
                .channel(request.getChannel())
                .recipientEmail(request.getRecipientEmail())
                .subject("")
                .status(NotificationStatus.PENDING)
                .build();
    }

    /**
     * Maps Notification entity to NotificationResponse.
     */
    public static NotificationResponse mapToNotificationResponse(Notification notification) {
        return new NotificationResponse(
                notification.getId(),
                notification.getUserId(),
                notification.getNotificationType(),
                notification.getChannel(),
                notification.getRecipientEmail(),
                notification.getSubject(),
                notification.getStatus(),
                notification.getFailureReason(),
                notification.getSentAt(),
                notification.getCreatedAt(),
                notification.getUpdatedAt()
        );
    }
}