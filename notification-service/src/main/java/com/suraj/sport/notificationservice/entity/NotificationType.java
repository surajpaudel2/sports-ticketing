package com.suraj.sport.notificationservice.entity;

public enum NotificationType {
    // Booking notifications
    BOOKING_CONFIRMED,
    BOOKING_CANCELLED,

    // Payment notifications
    PAYMENT_SUCCEEDED,
    PAYMENT_FAILED,

    // Refund notifications
    REFUND_SUCCEEDED,
    REFUND_FAILED,

    // Auth notifications (future — Section 12)
    // WELCOME_EMAIL,
    // PASSWORD_RESET,
    // EMAIL_VERIFICATION

    // Event notifications (future)
    // EVENT_CANCELLED,
    // EVENT_DATE_CHANGED
}