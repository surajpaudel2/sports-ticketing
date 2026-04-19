package com.ticketing.booking.repository;

import com.ticketing.booking.entity.Booking;
import com.ticketing.booking.entity.BookingStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for the {@link Booking} entity.
 *
 * <p>Extends {@link JpaRepository} for standard CRUD. The inherited {@code findById(Long)}
 * is the primary read operation used across the service — both in the REST layer
 * (status polling) and in {@code BookingEventListener} (webhook processing).</p>
 *
 * <p><strong>Note on advisory locks:</strong> the previous implementation used
 * PostgreSQL advisory locks inside {@code BookingEventListener#handlePaymentSuccess}
 * to protect the final seat-deduction step. This is no longer needed because seat
 * deduction now happens atomically inside Event Service's {@code checkAndReserve}
 * (using JPA optimistic locking with {@code @Version}), and the payment success
 * handler only confirms the booking — no seat operations are performed there.</p>
 */
public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Modifying
    @Transactional
    @Query("UPDATE Booking b SET b.bookingStatus = :status WHERE b.id = :id")
    void updateStatus(@Param("id") Long id, @Param("status") BookingStatus status);

    @Modifying
    @Transactional
    @Query("UPDATE Booking b SET b.bookingStatus = :status, b.failureReason = :reason WHERE b.id = :id")
    void updateStatusAndReason(@Param("id") Long id, @Param("status") BookingStatus status, @Param("reason") String reason);
}
