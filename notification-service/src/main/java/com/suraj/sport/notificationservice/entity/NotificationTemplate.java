package com.suraj.sport.notificationservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Represents an email/SMS template for a notification type.
 *
 * Each NotificationType has exactly one template per channel.
 * Templates are managed here — not scattered across services.
 * This is the single source of truth for all notification content.
 *
 * Thymeleaf integration:
 *   - bodyTemplateName references a Thymeleaf HTML file in resources/templates/email/
 *   - e.g. "booking-confirmed" maps to resources/templates/email/booking-confirmed.html
 *   - Template variables (bookingId, eventName etc) are injected at send time
 *   - Keeping templates as files (not DB strings) allows rich HTML formatting
 *
 * Example templates:
 *   - booking-confirmed.html
 *   - booking-cancelled.html
 *   - payment-succeeded.html
 *   - payment-failed.html
 *   - refund-succeeded.html
 *   - refund-failed.html
 */
@Entity
@Table(name = "notification_templates",
        uniqueConstraints = @UniqueConstraint(columnNames = {"notificationType", "channel"}))
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class NotificationTemplate {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Each notificationType + channel combination has exactly one template
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    // Subject template — supports simple variables e.g. "Your booking {{bookingId}} is confirmed!"
    // TODO: Consider using Thymeleaf for subject as well for consistency
    @Column(nullable = false)
    private String subjectTemplate;

    // Thymeleaf template filename — maps to resources/templates/email/<name>.html
    // e.g. "booking-confirmed" → resources/templates/email/booking-confirmed.html
    @Column(nullable = false)
    private String bodyTemplateName;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}