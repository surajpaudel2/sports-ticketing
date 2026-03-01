package com.suraj.sport.notificationservice.service;

import com.suraj.sport.notificationservice.dto.request.NotificationRequest;
import com.suraj.sport.notificationservice.entity.Notification;

public interface BookingNotificationService {
    void handleBookingConfirmed(NotificationRequest request, Notification notification);

    void handleBookingCancelled(NotificationRequest request, Notification notification);
}