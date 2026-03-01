package com.suraj.sport.paymentservice.repository;

import com.suraj.sport.paymentservice.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {

    // Used in getPaymentByBookingId — fetch payment for a specific booking
    Optional<Payment> findByBookingId(Long bookingId);
}