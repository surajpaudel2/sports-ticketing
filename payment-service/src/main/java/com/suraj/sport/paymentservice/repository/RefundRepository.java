package com.suraj.sport.paymentservice.repository;

import com.suraj.sport.paymentservice.entity.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RefundRepository extends JpaRepository<Refund, Long> {

    // Used in calculateTotalRefunded — fetch all refunds for a payment
    List<Refund> findAllByPaymentId(Long paymentId);
}