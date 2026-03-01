package com.suraj.sport.paymentservice.mapper;

import com.suraj.sport.paymentservice.entity.Payment;
import com.suraj.sport.paymentservice.entity.Transaction;
import com.suraj.sport.paymentservice.entity.TransactionStatus;

public class TransactionMapper {

    private TransactionMapper() {}

    /**
     * Maps Payment entity and amount to Transaction entity.
     * Status is always set to PENDING on creation —
     * updated to SUCCESS or FAILED after gateway response.
     */
    public static Transaction mapToTransaction(Payment payment) {
        return Transaction.builder()
                .payment(payment)
                .amount(payment.getAmount())
                .transactionStatus(TransactionStatus.PENDING)
                .build();
    }

}
