package com.suraj.sport.bookingservice.exception;

public class BookingNotFoundException extends RuntimeException {
    public BookingNotFoundException(Long id) {
        super("Booking not found with id: " + id);
    }
}
