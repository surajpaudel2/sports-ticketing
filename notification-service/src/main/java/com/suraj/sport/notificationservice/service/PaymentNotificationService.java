package com.suraj.sport.notificationservice.service;

import com.suraj.sport.notificationservice.dto.request.NotificationRequest;
import com.suraj.sport.notificationservice.entity.Notification;

public interface PaymentNotificationService {
    void handlePaymentSucceeded(NotificationRequest request, Notification notification);

    void handlePaymentFailed(NotificationRequest request, Notification notification);

    void handleRefundSucceeded(NotificationRequest request, Notification notification);

    void handleRefundFailed(NotificationRequest request, Notification notification);
}