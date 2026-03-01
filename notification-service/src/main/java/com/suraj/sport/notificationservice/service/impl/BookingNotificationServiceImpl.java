package com.suraj.sport.notificationservice.service.impl;

import com.suraj.sport.notificationservice.dto.request.NotificationRequest;
import com.suraj.sport.notificationservice.entity.*;
import com.suraj.sport.notificationservice.exception.NotificationTemplateNotFoundException;
import com.suraj.sport.notificationservice.repository.NotificationRepository;
import com.suraj.sport.notificationservice.repository.NotificationTemplateRepository;
import com.suraj.sport.notificationservice.service.BookingNotificationService;
import com.suraj.sport.notificationservice.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class BookingNotificationServiceImpl implements BookingNotificationService {

    private final NotificationTemplateRepository templateRepository;
    private final NotificationRepository notificationRepository;
    private final EmailService emailService;

    // =====================================================================
    // BOOKING CONFIRMED
    // =====================================================================

    /**
     * Handles BOOKING_CONFIRMED notification.
     * Sent when payment succeeds and booking is confirmed.
     *
     * Expected templateVariables:
     *   - bookingId, eventName, eventDate, venue, seatsBooked, totalAmount, receiptUrl
     */
    public void handleBookingConfirmed(NotificationRequest request, Notification notification) {
        sendNotificationEmail(request, notification, NotificationType.BOOKING_CONFIRMED);
    }

    // =====================================================================
    // BOOKING CANCELLED
    // =====================================================================

    /**
     * Handles BOOKING_CANCELLED notification.
     * Sent when a booking is cancelled by the user.
     *
     * Expected templateVariables:
     *   - bookingId, eventName, eventDate, cancellationReason, refundAmount (if applicable)
     */
    public void handleBookingCancelled(NotificationRequest request, Notification notification) {
        sendNotificationEmail(request, notification, NotificationType.BOOKING_CANCELLED);
    }

    // =====================================================================
    // PRIVATE HELPER
    // =====================================================================

    private void sendNotificationEmail(NotificationRequest request, Notification notification,
                                       NotificationType type) {
        // Fetch template for this notification type and channel
        NotificationTemplate template = templateRepository
                .findByNotificationTypeAndChannel(type, request.getChannel())
                .orElseThrow(() -> new NotificationTemplateNotFoundException(
                        type.name(), request.getChannel().name()));

        // Update notification with composed subject
        notification.setSubject(template.getSubjectTemplate());
        notificationRepository.save(notification);

        try {
            // Send email asynchronously via EmailService
            emailService.sendEmail(
                    request.getRecipientEmail(),
                    template.getSubjectTemplate(),
                    template.getBodyTemplateName(),
                    request.getTemplateVariables()
            );

            // Update notification to SENT
            notification.setStatus(NotificationStatus.SENT);
            notification.setSentAt(LocalDateTime.now());
            notificationRepository.save(notification);

        } catch (Exception ex) {
            // Update notification to FAILED — does not affect main flow
            notification.setStatus(NotificationStatus.FAILED);
            notification.setFailureReason(ex.getMessage());
            notificationRepository.save(notification);
            log.error("Failed to send {} notification to: {} | Error: {}",
                    type, request.getRecipientEmail(), ex.getMessage());
        }
    }
}