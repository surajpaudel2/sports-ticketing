package com.suraj.sport.paymentservice.service.impl;

import com.suraj.sport.paymentservice.dto.request.InitiatePaymentRequest;
import com.suraj.sport.paymentservice.dto.request.ProcessRefundRequest;
import com.suraj.sport.paymentservice.dto.response.PaymentResponse;
import com.suraj.sport.paymentservice.dto.response.RefundResponse;
import com.suraj.sport.paymentservice.entity.*;
import com.suraj.sport.paymentservice.exception.*;
import com.suraj.sport.paymentservice.mapper.PaymentMapper;
import com.suraj.sport.paymentservice.mapper.TransactionMapper;
import com.suraj.sport.paymentservice.repository.PaymentRepository;
import com.suraj.sport.paymentservice.repository.RefundRepository;
import com.suraj.sport.paymentservice.repository.TransactionRepository;
import com.suraj.sport.paymentservice.service.PaymentService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;
    private final TransactionRepository transactionRepository;
    private final RefundRepository refundRepository;

// =====================================================================
// INITIATE PAYMENT
// =====================================================================

    /**
     * Initiates a payment for a sports event booking.
     *
     * ARCHITECTURAL DECISION — Why we trust Booking Service:
     *   This method is called internally by Booking Service only — not by end users.
     *   Booking Service is the orchestrator and has already handled:
     *   - Event exists and is bookable
     *   - Seats are available and deducted in Event Service
     *   Therefore Payment Service does NOT re-validate seats or event status.
     *   Single Responsibility Principle — Payment Service owns payment concerns only.
     *
     * ARCHITECTURAL DECISION — Why Payment Service does not notify Booking or Notification:
     *   Booking Service is the orchestrator — it calls Payment Service and handles
     *   all state updates and notifications based on the response returned here.
     *   Having Payment Service call back to Booking Service would create a circular
     *   dependency: Booking → Payment → Booking.
     *   In Section 14, Kafka will replace this synchronous orchestration entirely.
     *
     * Flow:
     *   1. Create Payment record with PENDING status
     *   2. Create Transaction record with PENDING status
     *   3. Call payment gateway (stubbed — always SUCCESS until real gateway is wired)
     *   4. SUCCESS → update Payment and Transaction to SUCCESS, set receiptUrl
     *   5. FAILED  → update Payment and Transaction to FAILED, set failureReason
     *
     * TODO: Wire real payment gateway (Stripe/Razorpay) when credentials are available
     *
     * FIXME: Distributed transaction — if gateway call succeeds but DB update fails,
     *   payment is processed but not recorded. Implement SAGA pattern in Section 14.
     */
    @Override
    public PaymentResponse initiatePayment(InitiatePaymentRequest request) {

        // Step 1: Create Payment record with PENDING status
        Payment payment = PaymentMapper.mapToPayment(request);
        Payment savedPayment = paymentRepository.save(payment);

        // Step 2: Create Transaction record with PENDING status
        // Each payment attempt is tracked as a separate transaction record
        Transaction transaction = TransactionMapper.mapToTransaction(savedPayment);
        Transaction savedTransaction = transactionRepository.save(transaction);

        // Step 3: Call payment gateway
        // TODO: replace with real Stripe/Razorpay gateway call
        // For now stubbed as always SUCCESS — real gateway wired when credentials available
        boolean paymentSucceeded = true; // STUB

        if (paymentSucceeded) {
            savedPayment = handleGatewaySuccess(savedPayment, savedTransaction);
        } else {
            savedPayment = handleGatewayFailure(savedPayment, savedTransaction);
        }

        return PaymentMapper.mapToPaymentResponse(savedPayment);
    }

    // =====================================================================
    // PROCESS REFUND
    // =====================================================================

    /**
     * Processes a refund for a payment.
     *
     * Supports both partial and full refunds:
     *   - Full refund: refundAmount equals payment.amount
     *   - Partial refund: refundAmount is less than payment.amount
     *
     * NOTE: refundAmount is ALWAYS calculated and provided by Booking Service.
     *   Booking Service owns pricing logic — pricePerSeat and seatsBooked.
     *   Payment Service simply processes whatever amount Booking Service sends.
     *   Single Responsibility Principle — Booking Service owns booking/pricing concerns,
     *   Payment Service owns payment/refund processing concerns.
     *
     * ARCHITECTURAL DECISION — Gateway refund approach:
     *   Gateway does not need to know about internal booking logic.
     *   We simply tell it "refund $X from transaction Y" and it processes it.
     *   This keeps Payment Service clean and gateway-agnostic.
     *
     * ARCHITECTURAL DECISION — Why Payment Service does not notify Notification Service:
     *   Booking Service handles all notifications based on refund response returned here.
     *   In Section 14, Kafka will replace this synchronous orchestration.
     *
     * Flow:
     *   1. Find payment or throw PaymentNotFoundException
     *   2. Validate payment is SUCCESS or PARTIALLY_REFUNDED
     *   3. Validate refundAmount <= remaining refundable amount
     *   4. Create Refund record with PENDING status
     *   5. Call gateway refund API (stubbed — always SUCCESS)
     *   6. SUCCESS → update Refund to SUCCESS, update Payment to REFUNDED/PARTIALLY_REFUNDED
     *   7. FAILED  → update Refund to FAILED
     *
     * TODO: Wire real gateway refund API when credentials are available
     *
     * FIXME: Implement idempotency — prevent duplicate refunds for same booking cancellation.
     */
    @Override
    public RefundResponse processRefund(ProcessRefundRequest request) {

        // Edge case: payment must exist
        Payment payment = findPaymentOrThrow(request.getPaymentId());

        // Edge case: only SUCCESS or PARTIALLY_REFUNDED payments can be refunded
        if (payment.getPaymentStatus() != PaymentStatus.SUCCESS &&
                payment.getPaymentStatus() != PaymentStatus.PARTIALLY_REFUNDED) {
            throw new PaymentNotRefundableException(
                    "Only SUCCESS or PARTIALLY_REFUNDED payments can be refunded. Current status: "
                            + payment.getPaymentStatus());
        }

        // Edge case: refund amount cannot exceed remaining refundable amount
        double totalAlreadyRefunded = calculateTotalRefunded(payment.getId());
        double remainingRefundable = payment.getAmount() - totalAlreadyRefunded;
        if (request.getRefundAmount() > remainingRefundable) {
            throw new InvalidRefundAmountException(
                    "Refund amount exceeds remaining refundable amount. Remaining: " + remainingRefundable);
        }

        // Create Refund record with PENDING status
        Refund refund = Refund.builder()
                .payment(payment)
                .refundAmount(request.getRefundAmount())
                .refundReason(request.getRefundReason())
                .refundStatus(RefundStatus.PENDING)
                .build();
        Refund savedRefund = refundRepository.save(refund);

        // Call gateway refund API
        // TODO: replace with real Stripe/Razorpay refund API call
        // Pass: gatewayTransactionId from original successful transaction, refundAmount
        // For now stubbed as always SUCCESS
        boolean refundSucceeded = true; // STUB

        if (refundSucceeded) {
            savedRefund = handleRefundSuccess(savedRefund, payment, totalAlreadyRefunded, request.getRefundAmount());
        } else {
            savedRefund = handleRefundFailure(savedRefund);
        }

        return PaymentMapper.mapToRefundResponse(savedRefund);
    }

// =====================================================================
// RETRY PAYMENT — INTERNAL ENDPOINT
// =====================================================================

    /**
     * Retries a failed or pending payment.
     *
     * INTERNAL ENDPOINT — not exposed to end users.
     * Should only be called by Booking Service via FeignClient.
     * In production, secured via API Gateway rules or service token.
     * Once Kafka is implemented in Section 14, this endpoint will be replaced
     * by event-driven communication — Booking Service publishes PAYMENT_RETRY_REQUESTED
     * and Payment Service reacts independently.
     *
     * ARCHITECTURAL DECISION — Why seat re-check is NOT done here:
     *   Retry always goes through Booking Service which already handles seat
     *   re-check and re-deduction before calling this endpoint.
     *   Payment Service trusts that Booking Service has handled seats — same
     *   principle as initiatePayment.
     *
     * ARCHITECTURAL DECISION — Why Payment Service does not notify Booking or Notification:
     *   Booking Service is the orchestrator — handles all state updates and
     *   notifications based on the response returned here.
     *
     * Flow:
     *   1. Find payment or throw PaymentNotFoundException
     *   2. Validate payment is FAILED or PENDING
     *   3. Create new Transaction record for this retry attempt
     *   4. Update Payment back to PENDING
     *   5. Call payment gateway (stubbed — always SUCCESS)
     *   6. SUCCESS → update Payment and Transaction to SUCCESS
     *   7. FAILED  → update Payment and Transaction to FAILED
     *
     * TODO: Wire real payment gateway when credentials are available
     */
    @Override
    public PaymentResponse retryPayment(Long paymentId) {

        // Edge case: payment must exist
        Payment payment = findPaymentOrThrow(paymentId);

        // Edge case: only FAILED or PENDING payments can be retried
        if (payment.getPaymentStatus() != PaymentStatus.FAILED &&
                payment.getPaymentStatus() != PaymentStatus.PENDING) {
            throw new PaymentNotRetryableException(
                    "Only FAILED or PENDING payments can be retried. Current status: "
                            + payment.getPaymentStatus());
        }

        // Create new Transaction record for this retry attempt
        // Each retry is tracked separately for audit purposes
        Transaction transaction = TransactionMapper.mapToTransaction(payment);
        Transaction savedTransaction = transactionRepository.save(transaction);

        // Reset Payment to PENDING for this retry
        payment.setPaymentStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);

        // Call payment gateway
        // TODO: replace with real Stripe/Razorpay gateway call
        // For now stubbed as always SUCCESS
        boolean paymentSucceeded = true; // STUB

        if (paymentSucceeded) {
            payment = handleGatewaySuccess(payment, savedTransaction);
        } else {
            payment = handleGatewayFailure(payment, savedTransaction);
        }

        return PaymentMapper.mapToPaymentResponse(payment);
    }

    // =====================================================================
    // GET PAYMENT BY ID
    // =====================================================================

    /**
     * Retrieves a payment by its unique ID.
     *
     * Restrictions:
     *   - Throws PaymentNotFoundException if no payment exists with the given ID
     *
     * TODO: implementCaching()
     * Consider caching frequently accessed payments using Redis.
     * Revisit when Redis is introduced.
     */
    @Override
    public PaymentResponse getPaymentById(Long paymentId) {

        // Edge case: payment must exist
        Payment payment = findPaymentOrThrow(paymentId);

        return PaymentMapper.mapToPaymentResponse(payment);
    }

    // =====================================================================
    // GET PAYMENT BY BOOKING ID
    // =====================================================================

    /**
     * Retrieves a payment by its associated booking ID.
     * Called by Booking Service to check payment status for a booking.
     *
     * Restrictions:
     *   - Throws PaymentNotFoundException if no payment exists for the given booking ID
     */
    @Override
    public PaymentResponse getPaymentByBookingId(Long bookingId) {

        // Edge case: payment must exist for this booking
        Payment payment = paymentRepository.findByBookingId(bookingId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found for booking id: " + bookingId));

        return PaymentMapper.mapToPaymentResponse(payment);
    }

    // =====================================================================
    // PRIVATE HELPER METHODS
    // =====================================================================

    /**
     * Handles successful gateway response — updates Payment and Transaction to SUCCESS.
     * Sets receiptUrl and gatewayTransactionId from gateway response.
     * Currently stubbed — real values come from Stripe/Razorpay when wired.
     */
    private Payment handleGatewaySuccess(Payment payment, Transaction transaction) {
        payment.setPaymentStatus(PaymentStatus.SUCCESS);
        payment.setReceiptUrl("https://receipts.stub.com/" + payment.getId()); // STUB
        Payment savedPayment = paymentRepository.save(payment);

        transaction.setTransactionStatus(TransactionStatus.SUCCESS);
        transaction.setGatewayTransactionId("GATEWAY_TX_STUB_" + transaction.getId()); // STUB
        transactionRepository.save(transaction);

        return savedPayment;
    }

    /**
     * Handles failed gateway response — updates Payment and Transaction to FAILED.
     * Sets failureReason from gateway response.
     * Currently stubbed — real reason comes from Stripe/Razorpay when wired.
     */
    private Payment handleGatewayFailure(Payment payment, Transaction transaction) {
        payment.setPaymentStatus(PaymentStatus.FAILED);
        Payment savedPayment = paymentRepository.save(payment);

        transaction.setTransactionStatus(TransactionStatus.FAILED);
        transaction.setFailureReason("Payment gateway declined the transaction"); // STUB
        transactionRepository.save(transaction);

        return savedPayment;
    }

    /**
     * Handles successful refund — updates Refund to SUCCESS.
     * Updates Payment to REFUNDED or PARTIALLY_REFUNDED based on total refunded amount.
     */
    private Refund handleRefundSuccess(Refund refund, Payment payment,
                                       double totalAlreadyRefunded, double refundAmount) {
        refund.setRefundStatus(RefundStatus.SUCCESS);
        refund.setGatewayRefundId("GATEWAY_REFUND_STUB_" + refund.getId()); // STUB
        refund.setRefundedAt(LocalDateTime.now());
        Refund savedRefund = refundRepository.save(refund);

        // Update Payment status — PARTIALLY_REFUNDED or fully REFUNDED
        double newTotalRefunded = totalAlreadyRefunded + refundAmount;
        if (newTotalRefunded >= payment.getAmount()) {
            payment.setPaymentStatus(PaymentStatus.REFUNDED);
        } else {
            payment.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
        }
        paymentRepository.save(payment);

        return savedRefund;
    }

    /**
     * Handles failed refund — updates Refund to FAILED.
     * Payment status remains unchanged — refund can be retried.
     */
    private Refund handleRefundFailure(Refund refund) {
        refund.setRefundStatus(RefundStatus.FAILED);
        refund.setFailureReason("Gateway declined the refund"); // STUB
        return refundRepository.save(refund);
    }

    /**
     * Fetches the payment by ID or throws PaymentNotFoundException if not found.
     */
    private Payment findPaymentOrThrow(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found with id: " + paymentId));
    }

    /**
     * Calculates the total amount already refunded for a payment.
     * Only counts SUCCESS refunds — PENDING and FAILED refunds are not counted.
     */
    private double calculateTotalRefunded(Long paymentId) {
        return refundRepository.findAllByPaymentId(paymentId)
                .stream()
                .filter(r -> r.getRefundStatus() == RefundStatus.SUCCESS)
                .mapToDouble(Refund::getRefundAmount)
                .sum();
    }
}