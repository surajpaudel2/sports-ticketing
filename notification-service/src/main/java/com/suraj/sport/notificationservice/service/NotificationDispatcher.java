package com.suraj.sport.notificationservice.service;

import com.suraj.sport.notificationservice.dto.request.NotificationRequest;
import com.suraj.sport.notificationservice.entity.Notification;

public interface NotificationDispatcher {
    void dispatch(NotificationRequest request, Notification notification);
}