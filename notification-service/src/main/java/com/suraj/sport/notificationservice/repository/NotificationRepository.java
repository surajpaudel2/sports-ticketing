package com.suraj.sport.notificationservice.repository;

import com.suraj.sport.notificationservice.entity.Notification;
import com.suraj.sport.notificationservice.entity.NotificationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, Long> {

    // Used in getAllNotificationsByUserId
    List<Notification> findAllByUserId(Long userId);

    // TODO: used by retry scheduler — fetch all failed notifications to retry
    List<Notification> findAllByStatus(NotificationStatus status);
}