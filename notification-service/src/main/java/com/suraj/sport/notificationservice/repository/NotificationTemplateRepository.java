package com.suraj.sport.notificationservice.repository;

import com.suraj.sport.notificationservice.entity.NotificationChannel;
import com.suraj.sport.notificationservice.entity.NotificationTemplate;
import com.suraj.sport.notificationservice.entity.NotificationType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface NotificationTemplateRepository extends JpaRepository<NotificationTemplate, Long> {

    // Used by handlers to fetch template for a specific notification type and channel
    Optional<NotificationTemplate> findByNotificationTypeAndChannel(
            NotificationType notificationType, NotificationChannel channel);
}