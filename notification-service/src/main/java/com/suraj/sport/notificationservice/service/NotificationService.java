package com.suraj.sport.notificationservice.service;

import com.suraj.sport.notificationservice.dto.request.NotificationRequest;
import com.suraj.sport.notificationservice.dto.response.NotificationResponse;

import java.util.List;

public interface NotificationService {

    NotificationResponse sendNotification(NotificationRequest request);

    NotificationResponse getNotificationById(Long notificationId);

    List<NotificationResponse> getAllNotificationsByUserId(Long userId);
}