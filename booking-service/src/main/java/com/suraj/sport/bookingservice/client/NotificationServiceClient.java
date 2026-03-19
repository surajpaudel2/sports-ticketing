package com.suraj.sport.bookingservice.client;

import com.suraj.sport.bookingservice.dto.request.NotificationRequest;
import com.suraj.sport.bookingservice.dto.response.ApiResult;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(name = "notification-service")
public interface NotificationServiceClient {

    // =====================================================================
    // SEND NOTIFICATION
    // Called after key booking events — booking created, cancelled, confirmed.
    // Notification Service handles all template selection and email sending.
    // Booking Service just sends the type and template variables.
    // =====================================================================
    @PostMapping("/api/v1/notification")
    ApiResult<Void> sendNotification(@RequestBody NotificationRequest request);
}