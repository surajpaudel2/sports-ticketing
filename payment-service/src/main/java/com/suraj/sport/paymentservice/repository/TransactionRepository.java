package com.suraj.sport.paymentservice.repository;

import com.suraj.sport.paymentservice.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    // Used for auditing — fetch all transaction attempts for a payment
    List<Transaction> findAllByPaymentId(Long paymentId);
}