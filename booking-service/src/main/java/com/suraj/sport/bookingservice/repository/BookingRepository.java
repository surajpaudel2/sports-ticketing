package com.suraj.sport.bookingservice.repository;

import com.suraj.sport.bookingservice.entity.Booking;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BookingRepository extends JpaRepository<Booking, Long> {

    // Used in getAllBookingsByUserId — fetch all bookings for a specific user
    List<Booking> findAllByUserId(Long userId);

    // TODO: findAllByUserIdAndIsDeletedFalse(Long userId)
    // Once auth/roles are implemented, regular users should only see non-deleted bookings
    // Revisit in Section 12
}