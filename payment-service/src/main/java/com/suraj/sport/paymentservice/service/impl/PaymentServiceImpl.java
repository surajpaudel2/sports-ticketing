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
     *   Booking Service has already handled all seat-related concerns:
     *   - Event exists and is bookable
     *   - Seats are available
     *   - Seats have been deducted in Event Service
     *   Therefore Payment Service does NOT re-validate seats or event status.
     *   This follows the Single Responsibility Principle — Payment Service owns
     *   payment concerns only, not booking or seat concerns.
     *
     * Flow:
     *   1. Create Payment record with PENDING status
     *   2. Create Transaction record with PENDING status
     *   3. Call payment gateway (stubbed)
     *   4. SUCCESS → update Payment and Transaction to SUCCESS
     *   5. FAILED → update Payment and Transaction to FAILED
     *              → notify Booking Service to restore seats
     *
     * FIXME: Replace synchronous gateway call with Kafka event publishing in Section 14.
     *   Payment Service publishes PAYMENT_SUCCEEDED or PAYMENT_FAILED event.
     *   Booking Service and Notification Service subscribe and react independently.
     *
     * FIXME: Distributed transaction — if gateway call succeeds but DB update fails,
     *   payment is processed but not recorded. Implement SAGA pattern in Section 14.
     */
    @Override
    public PaymentResponse initiatePayment(InitiatePaymentRequest request) {

        //will create payment with pending status as 'PENDING' before saving to db.
        Payment payment = PaymentMapper.mapToPayment(request);
        Payment savedPayment = paymentRepository.save(payment);

        // will create transaction status as 'PENDING' before saving to db.
        Transaction transaction = TransactionMapper.mapToTransaction(savedPayment);
        Transaction savedTransaction = transactionRepository.save(transaction);

        // TODO: callPaymentGateway(request)
        // Integrate with Stripe/Razorpay to process the payment
        // Pass: amount, paymentMethod, userId
        // Returns: gatewayTransactionId, receiptUrl, success/failure status
        boolean paymentSucceeded = true; // STUB — replace with actual gateway call

        if (paymentSucceeded) {

            // Update Payment to SUCCESS
            savedPayment.setPaymentStatus(PaymentStatus.SUCCESS);
            savedPayment.setReceiptUrl("https://receipts.stub.com/" + savedPayment.getId()); // STUB
            paymentRepository.save(savedPayment);

            // Update Transaction to SUCCESS
            savedTransaction.setTransactionStatus(TransactionStatus.SUCCESS);
            savedTransaction.setGatewayTransactionId("GATEWAY_TX_STUB_" + savedTransaction.getId()); // STUB
            transactionRepository.save(savedTransaction);

            // TODO: notifyBookingService(savedPayment)
            // Call Booking Service to update booking status to CONFIRMED
            // Set paymentId on the booking record
            // Revisit in Section 8 — replace with Kafka event in Section 14

            // TODO: notifyNotificationService(savedPayment)
            // Publish PAYMENT_SUCCEEDED event
            // Notification Service sends confirmation email/SMS to user
            // Revisit in Section 8 — replace with Kafka event in Section 14

        } else {

            // Update Payment to FAILED
            savedPayment.setPaymentStatus(PaymentStatus.FAILED);
            paymentRepository.save(savedPayment);

            // Update Transaction to FAILED
            savedTransaction.setTransactionStatus(TransactionStatus.FAILED);
            savedTransaction.setFailureReason("Payment gateway declined the transaction"); // STUB
            transactionRepository.save(savedTransaction);

            // TODO: notifyBookingService(savedPayment)
            // Call Booking Service to keep booking as PENDING and restore seats in Event Service
            // Revisit in Section 8 — replace with Kafka event in Section 14

            // TODO: notifyNotificationService(savedPayment)
            // Publish PAYMENT_FAILED event
            // Notification Service sends payment failure email/SMS to user
            // Revisit in Section 8 — replace with Kafka event in Section 14
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
     *     e.g. user booked 3 seats, cancels 1 → refundAmount = pricePerSeat
     *
     * ARCHITECTURAL DECISION — Gateway refund approach:
     *   The gateway does not need to know about internal booking logic.
     *   We simply tell it "refund $X from transaction Y" and it processes it.
     *   This keeps Payment Service clean and gateway-agnostic.
     *
     * Flow:
     *   1. Find payment or throw PaymentNotFoundException
     *   2. Validate payment is SUCCESS → else throw PaymentNotRefundableException
     *   3. Validate refundAmount <= remaining refundable amount
     *   4. Create Refund record with PENDING status
     *   5. Call gateway refund API (stubbed)
     *   6. SUCCESS → update Refund to SUCCESS, update Payment to REFUNDED/PARTIALLY_REFUNDED
     *   7. FAILED → update Refund to FAILED
     *
     * FIXME: Replace synchronous gateway call with Kafka event in Section 14.
     * FIXME: Implement idempotency — prevent duplicate refunds for same booking cancellation.
     */
    @Override
    public RefundResponse processRefund(ProcessRefundRequest request) {

        // Edge case: payment must exist
        Payment payment = findPaymentOrThrow(request.getPaymentId());

        // Edge case: only SUCCESS payments can be refunded
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

        // TODO: callGatewayRefundAPI(payment, refund)
        // Call Stripe/Razorpay refund API with:
        // - gatewayTransactionId from original successful transaction
        // - refundAmount
        // Returns: gatewayRefundId, success/failure status
        boolean refundSucceeded = true; // STUB — replace with actual gateway call

        if (refundSucceeded) {

            // Update Refund to SUCCESS
            savedRefund.setRefundStatus(RefundStatus.SUCCESS);
            savedRefund.setGatewayRefundId("GATEWAY_REFUND_STUB_" + savedRefund.getId()); // STUB
            savedRefund.setRefundedAt(LocalDateTime.now());
            refundRepository.save(savedRefund);

            // Update Payment status — PARTIALLY_REFUNDED or REFUNDED
            double newTotalRefunded = totalAlreadyRefunded + request.getRefundAmount();
            if (newTotalRefunded >= payment.getAmount()) {
                payment.setPaymentStatus(PaymentStatus.REFUNDED);
            } else {
                payment.setPaymentStatus(PaymentStatus.PARTIALLY_REFUNDED);
            }
            paymentRepository.save(payment);

            // TODO: notifyNotificationService(savedRefund)
            // Publish REFUND_SUCCEEDED event
            // Notification Service sends refund confirmation email/SMS to user
            // Revisit in Section 8 — replace with Kafka event in Section 14

        } else {

            // Update Refund to FAILED
            savedRefund.setRefundStatus(RefundStatus.FAILED);
            savedRefund.setFailureReason("Gateway declined the refund"); // STUB
            refundRepository.save(savedRefund);

            // TODO: notifyNotificationService(savedRefund)
            // Publish REFUND_FAILED event
            // Notification Service sends refund failure email/SMS to user
            // Revisit in Section 8 — replace with Kafka event in Section 14
        }

        return PaymentMapper.mapToRefundResponse(savedRefund);
    }

    // =====================================================================
    // RETRY PAYMENT
    // =====================================================================

    /**
     * Retries a failed or pending payment.
     *
     * ARCHITECTURAL DECISION — Why we re-check seats here but not in initiatePayment:
     *   - initiatePayment: called by Booking Service which already deducted seats → trust it
     *   - retryPayment: payment previously failed → seats were restored → other users
     *     may have booked those seats in between → MUST re-check and re-deduct
     *   This is a conscious decision to prevent the trap where:
     *   user retries payment → payment succeeds → but seats no longer available
     *
     * Flow:
     *   1. Find payment or throw PaymentNotFoundException
     *   2. Validate payment is FAILED or PENDING → else throw PaymentNotRetryableException
     *   3. Re-check seats in Event Service — someone may have booked in between
     *   4. Re-deduct seats in Event Service
     *   5. Create new Transaction record for this retry attempt
     *   6. Call payment gateway
     *   7. SUCCESS → update Payment and Transaction to SUCCESS
     *   8. FAILED → update Payment and Transaction to FAILED, restore seats
     *
     * FIXME: Replace synchronous calls with Kafka events in Section 14.
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

        // TODO: checkSeatsAvailability(payment.getEventId(), seatsBooked)
        // IMPORTANT: Must re-check seats — payment failed previously, seats were restored
        // Other users may have booked those seats in between
        // Call Event Service to verify seats are still available
        // Throw InsufficientSeatsException if seats no longer available
        // Revisit in Section 8

        // TODO: deductSeats(payment.getEventId(), seatsBooked)
        // Re-deduct seats in Event Service before attempting payment
        // Only proceed if deduction is successful
        // Revisit in Section 8

        // Create new Transaction record for this retry attempt
        Transaction transaction = Transaction.builder()
                .payment(payment)
                .amount(payment.getAmount())
                .transactionStatus(TransactionStatus.PENDING)
                .build();
        Transaction savedTransaction = transactionRepository.save(transaction);

        // Update Payment back to PENDING for this retry
        payment.setPaymentStatus(PaymentStatus.PENDING);
        paymentRepository.save(payment);

        // TODO: callPaymentGateway(payment)
        // Retry payment with same amount and payment method
        // Returns: gatewayTransactionId, success/failure status
        boolean paymentSucceeded = true; // STUB — replace with actual gateway call

        if (paymentSucceeded) {

            // Update Payment to SUCCESS
            payment.setPaymentStatus(PaymentStatus.SUCCESS);
            payment.setReceiptUrl("https://receipts.stub.com/" + payment.getId()); // STUB
            paymentRepository.save(payment);

            // Update Transaction to SUCCESS
            savedTransaction.setTransactionStatus(TransactionStatus.SUCCESS);
            savedTransaction.setGatewayTransactionId("GATEWAY_TX_STUB_" + savedTransaction.getId()); // STUB
            transactionRepository.save(savedTransaction);

            // TODO: notifyBookingService(payment)
            // Call Booking Service to update booking status to CONFIRMED
            // Revisit in Section 8 — replace with Kafka event in Section 14

            // TODO: notifyNotificationService(payment)
            // Publish PAYMENT_SUCCEEDED event
            // Notification Service sends confirmation email/SMS to user
            // Revisit in Section 8 — replace with Kafka event in Section 14

        } else {

            // Update Payment to FAILED
            payment.setPaymentStatus(PaymentStatus.FAILED);
            paymentRepository.save(payment);

            // Update Transaction to FAILED
            savedTransaction.setTransactionStatus(TransactionStatus.FAILED);
            savedTransaction.setFailureReason("Payment gateway declined the transaction"); // STUB
            transactionRepository.save(savedTransaction);

            // TODO: notifyBookingService(payment)
            // Notify Booking Service to restore seats in Event Service
            // Revisit in Section 8 — replace with Kafka event in Section 14

            // TODO: notifyNotificationService(payment)
            // Publish PAYMENT_FAILED event
            // Notification Service sends payment failure email/SMS to user
            // Revisit in Section 8 — replace with Kafka event in Section 14
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
     * Fetches the payment by ID or throws PaymentNotFoundException if not found.
     */
    private Payment findPaymentOrThrow(Long paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new PaymentNotFoundException(
                        "Payment not found with id: " + paymentId));
    }

    /**
     * Calculates the total amount already refunded for a payment.
     * Used to validate new refund requests do not exceed refundable amount.
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