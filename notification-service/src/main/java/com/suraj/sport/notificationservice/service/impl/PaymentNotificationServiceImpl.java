package com.suraj.sport.notificationservice.service;

import com.suraj.sport.notificationservice.dto.request.NotificationRequest;
import com.suraj.sport.notificationservice.entity.*;
import com.suraj.sport.notificationservice.exception.NotificationTemplateNotFoundException;
import com.suraj.sport.notificationservice.repository.NotificationRepository;
import com.suraj.sport.notificationservice.repository.NotificationTemplateRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentNotificationServiceImpl implements PaymentNotificationService {

    private final NotificationTemplateRepository templateRepository;
    private final NotificationRepository notificationRepository;
    private final EmailService emailService;

    // =====================================================================
    // PAYMENT SUCCEEDED
    // =====================================================================

    /**
     * Handles PAYMENT_SUCCEEDED notification.
     * Sent when payment gateway confirms successful payment.
     *
     * Expected templateVariables:
     *   - paymentId, bookingId, eventName, amount, paymentMethod, receiptUrl
     */
    @Override
    public void handlePaymentSucceeded(NotificationRequest request, Notification notification) {
        sendNotificationEmail(request, notification, NotificationType.PAYMENT_SUCCEEDED);
    }

    // =====================================================================
    // PAYMENT FAILED
    // =====================================================================

    /**
     * Handles PAYMENT_FAILED notification.
     * Sent when payment gateway declines the payment.
     *
     * Expected templateVariables:
     *   - paymentId, bookingId, eventName, amount, failureReason
     */
   @Override
    public void handlePaymentFailed(NotificationRequest request, Notification notification) {
        sendNotificationEmail(request, notification, NotificationType.PAYMENT_FAILED);
    }

    // =====================================================================
    // REFUND SUCCEEDED
    // =====================================================================

    /**
     * Handles REFUND_SUCCEEDED notification.
     * Sent when refund is successfully processed by gateway.
     *
     * Expected templateVariables:
     *   - refundId, bookingId, eventName, refundAmount, refundedAt
     */
    @Override
    public void handleRefundSucceeded(NotificationRequest request, Notification notification) {
        sendNotificationEmail(request, notification, NotificationType.REFUND_SUCCEEDED);
    }

    // =====================================================================
    // REFUND FAILED
    // =====================================================================

    /**
     * Handles REFUND_FAILED notification.
     * Sent when refund processing fails at gateway.
     *
     * Expected templateVariables:
     *   - refundId, bookingId, eventName, refundAmount, failureReason
     */
    @Override
    public void handleRefundFailed(NotificationRequest request, Notification notification) {
        sendNotificationEmail(request, notification, NotificationType.REFUND_FAILED);
    }

    // =====================================================================
    // PRIVATE HELPER
    // =====================================================================

    private void sendNotificationEmail(NotificationRequest request, Notification notification,
                                       NotificationType type) {
        NotificationTemplate template = templateRepository
                .findByNotificationTypeAndChannel(type, request.getChannel())
                .orElseThrow(() -> new NotificationTemplateNotFoundException(
                        type.name(), request.getChannel().name()));

        notification.setSubject(template.getSubjectTemplate());
        notificationRepository.save(notification);

        try {
            emailService.sendEmail(
                    request.getRecipientEmail(),
                    template.getSubjectTemplate(),
                    template.getBodyTemplateName(),
                    request.getTemplateVariables()
            );

            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
            notificationRepository.save(notification);

        } catch (Exception ex) {
            notification.setStatus(NotificationStatus.FAILED);
            notification.setFailureReason(ex.getMessage());
            notificationRepository.save(notification);
            log.error("Failed to send {} notification to: {} | Error: {}",
                    type, request.getRecipientEmail(), ex.getMessage());
        }
    }
}