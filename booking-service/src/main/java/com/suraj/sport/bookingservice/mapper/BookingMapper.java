package com.suraj.sport.bookingservice.mapper;

import com.suraj.sport.bookingservice.dto.request.CreateBookingRequest;
import com.suraj.sport.bookingservice.dto.response.BookingResponse;
import com.suraj.sport.bookingservice.dto.response.CreateBookingResponse;
import com.suraj.sport.bookingservice.entity.Booking;
import com.suraj.sport.bookingservice.entity.BookingStatus;

public class BookingMapper {

    private BookingMapper() {}

    /**
     * Maps CreateBookingRequest to Booking entity.
     * pricePerSeat and totalAmount must be provided separately
     * as they are fetched from Event Service.
     */
    public static Booking mapToBooking(CreateBookingRequest request, double pricePerSeat) {
        return Booking.builder()
                .userId(request.getUserId())
                .eventId(request.getEventId())
                .seatsBooked(request.getSeatsBooked())
                .pricePerSeat(pricePerSeat)
                .totalAmount(pricePerSeat * request.getSeatsBooked())
                .bookingStatus(BookingStatus.PENDING)
                .build();
    }

    /**
     * Maps Booking entity to CreateBookingResponse.
     * Used immediately after booking creation.
     */
    public static CreateBookingResponse mapToCreateBookingResponse(Booking booking) {
        return new CreateBookingResponse(
                booking.getId(),
                booking.getUserId(),
                booking.getEventId(),
                booking.getSeatsBooked(),
                booking.getPricePerSeat(),
                booking.getTotalAmount(),
                booking.getBookingStatus(),
                booking.getCreatedAt()
        );
    }

    /**
     * Maps Booking entity to full BookingResponse.
     * Used for cancel, retry payment, rebook operations.
     */
    public static BookingResponse mapToBookingResponse(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getUserId(),
                booking.getEventId(),
                booking.getPaymentId(),
                booking.getSeatsBooked(),
                booking.getPricePerSeat(),
                booking.getTotalAmount(),
                booking.getBookingStatus(),
                booking.getCancellationReason(),
                booking.getCreatedAt(),
                booking.getUpdatedAt()
        );
    }
}