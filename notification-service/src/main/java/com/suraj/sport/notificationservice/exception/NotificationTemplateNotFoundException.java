package com.suraj.sport.notificationservice.exception;

public class NotificationTemplateNotFoundException extends RuntimeException {
    public NotificationTemplateNotFoundException(String type, String channel) {
        super("Notification template not found for type: " + type + " and channel: " + channel);
    }
}