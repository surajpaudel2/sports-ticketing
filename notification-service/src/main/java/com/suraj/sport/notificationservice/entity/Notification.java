package com.suraj.sport.notificationservice.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * Represents a notification record for a user.
 *
 * This entity tracks every notification sent or attempted to be sent to a user.
 * It serves as an audit trail — allowing us to check if a notification was sent,
 * when it was sent, and why it failed if it did.
 *
 * Responsibilities:
 *   - Track notification status (PENDING, SENT, FAILED)
 *   - Store composed subject for audit purposes
 *   - Reference which template was used via notificationType
 *   - Support retry of failed notifications in future
 *
 * Note: userId is a plain Long reference — no FK constraint across microservices.
 * recipientEmail is stored directly — avoids calling User Service on every retry.
 */
@Entity
@Table(name = "notifications")
@Setter
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Reference to User Service — no FK constraint, microservices own their data
    @Column(nullable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationChannel channel;

    // Stored directly to avoid calling User Service on retry
    @Column(nullable = false)
    private String recipientEmail;

    // Composed subject after template variables are replaced
    @Column(nullable = false)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus status;

    // Null if notification was sent successfully
    private String failureReason;

    // Null until notification is successfully sent
    private LocalDateTime sentAt;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    private LocalDateTime updatedAt;
}