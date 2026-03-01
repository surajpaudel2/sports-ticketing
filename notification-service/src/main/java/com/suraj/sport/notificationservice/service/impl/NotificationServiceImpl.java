package com.suraj.sport.notificationservice.service.impl;

import com.suraj.sport.notificationservice.dto.request.NotificationRequest;
import com.suraj.sport.notificationservice.dto.response.NotificationResponse;
import com.suraj.sport.notificationservice.entity.Notification;
import com.suraj.sport.notificationservice.exception.NotificationNotFoundException;
import com.suraj.sport.notificationservice.mapper.NotificationMapper;
import com.suraj.sport.notificationservice.repository.NotificationRepository;
import com.suraj.sport.notificationservice.service.NotificationDispatcher;
import com.suraj.sport.notificationservice.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationRepository notificationRepository;
    private final NotificationDispatcher notificationDispatcher;

    // =====================================================================
    // SEND NOTIFICATION
    // =====================================================================

    /**
     * Entry point for all notification requests.
     *
     * Flow:
     *   1. Save Notification record as PENDING
     *   2. Delegate to NotificationDispatcher
     *   3. Dispatcher routes to correct handler
     *   4. Handler fetches template, sends email, updates status to SENT or FAILED
     *
     * ARCHITECTURAL DECISION — Save before sending:
     *   Notification record is saved as PENDING before sending.
     *   This ensures we always have an audit trail even if sending fails.
     *   Status is updated to SENT or FAILED by the handler after email attempt.
     *
     * TODO: implementRetryScheduler()
     *   Add a scheduler to retry FAILED notifications automatically.
     *   Revisit when scheduler is implemented.
     */
    @Override
    public NotificationResponse sendNotification(NotificationRequest request) {

        // Save notification record as PENDING before attempting to send
        Notification notification = NotificationMapper.mapToNotification(request);
        Notification savedNotification = notificationRepository.save(notification);

        // Delegate to dispatcher — routes to correct handler based on notificationType
        notificationDispatcher.dispatch(request, savedNotification);

        return NotificationMapper.mapToNotificationResponse(savedNotification);
    }

    // =====================================================================
    // GET NOTIFICATION BY ID
    // =====================================================================

    /**
     * Retrieves a notification by its unique ID.
     *
     * Restrictions:
     *   - Throws NotificationNotFoundException if no notification exists with the given ID
     */
    @Override
    public NotificationResponse getNotificationById(Long notificationId) {

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new NotificationNotFoundException(notificationId));

        return NotificationMapper.mapToNotificationResponse(notification);
    }

    // =====================================================================
    // GET ALL NOTIFICATIONS BY USER ID
    // =====================================================================

    /**
     * Retrieves all notifications for a specific user.
     *
     * TODO: implementPagination()
     * Current implementation returns all notifications at once which is not scalable.
     * Implement cursor/keyset pagination — revisit when pagination is added.
     *
     * TODO: implementFiltering()
     * Add filtering by notificationType, status, channel, date range.
     */
    @Override
    public List<NotificationResponse> getAllNotificationsByUserId(Long userId) {

        // TODO: Replace with paginated and filtered query once implemented
        return notificationRepository.findAllByUserId(userId)
                .stream()
                .map(NotificationMapper::mapToNotificationResponse)
                .collect(Collectors.toList());
    }
}