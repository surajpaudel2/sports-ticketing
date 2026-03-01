package com.suraj.sport.notificationservice.service.impl;

import com.suraj.sport.notificationservice.dto.request.NotificationRequest;
import com.suraj.sport.notificationservice.entity.Notification;
import com.suraj.sport.notificationservice.service.BookingNotificationService;
import com.suraj.sport.notificationservice.service.NotificationDispatcher;
import com.suraj.sport.notificationservice.service.PaymentNotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationDispatcherImpl implements NotificationDispatcher {

    private final BookingNotificationService bookingNotificationService;
    private final PaymentNotificationService paymentNotificationService;

    /**
     * Routes notification request to the correct handler based on NotificationType.
     *
     * ARCHITECTURAL DECISION — Centralized routing:
     *   All notification routing is handled here — controller and service layer
     *   never need to know which handler to call. Adding a new notification type
     *   only requires adding a new case here and a new handler method.
     *
     * TODO: Replace switch routing with Kafka topic subscriptions in Section 14.
     *   Each handler will subscribe to its own Kafka topic instead of being
     *   called directly via dispatcher.
     */
    public void dispatch(NotificationRequest request, Notification notification) {
        log.info("Dispatching notification type: {} for userId: {}",
                request.getNotificationType(), request.getUserId());

        switch (request.getNotificationType()) {

            // Booking notifications
            case BOOKING_CONFIRMED ->
                    bookingNotificationService.handleBookingConfirmed(request, notification);
            case BOOKING_CANCELLED ->
                    bookingNotificationService.handleBookingCancelled(request, notification);

            // Payment notifications
            case PAYMENT_SUCCEEDED ->
                    paymentNotificationService.handlePaymentSucceeded(request, notification);
            case PAYMENT_FAILED ->
                    paymentNotificationService.handlePaymentFailed(request, notification);

            // Refund notifications
            case REFUND_SUCCEEDED ->
                    paymentNotificationService.handleRefundSucceeded(request, notification);
            case REFUND_FAILED ->
                    paymentNotificationService.handleRefundFailed(request, notification);

            default -> log.warn("No handler found for notification type: {}",
                    request.getNotificationType());
        }
    }
}