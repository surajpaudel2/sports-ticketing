package com.suraj.sport.bookingservice.service;

import com.suraj.sport.bookingservice.dto.request.CancelBookingRequest;
import com.suraj.sport.bookingservice.dto.request.CreateBookingRequest;
import com.suraj.sport.bookingservice.dto.response.BookingResponse;
import com.suraj.sport.bookingservice.dto.response.CreateBookingResponse;

import java.util.List;

public interface BookingService {

    CreateBookingResponse createBooking(CreateBookingRequest request);

    BookingResponse cancelBooking(Long bookingId, CancelBookingRequest request);

    BookingResponse retryPayment(Long bookingId);

    BookingResponse reBook(Long bookingId);

    BookingResponse getBookingById(Long bookingId);

    List<BookingResponse> getAllBookingsByUserId(Long userId);
}