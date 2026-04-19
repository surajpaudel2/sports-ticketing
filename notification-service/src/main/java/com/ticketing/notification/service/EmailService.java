package com.ticketing.notification.service;

import com.ticketing.notification.dto.BookingResultEvent;

public interface EmailService {

    void sendBookingConfirmed(BookingResultEvent event);

    void sendBookingFailed(BookingResultEvent event);
}
