package com.ticketing.event.repository;

import com.ticketing.event.entity.Event;
import com.ticketing.event.entity.EventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

/**
 * Repository for the {@link Event} entity.
 *
 * <p>Extends {@link JpaRepository} for standard CRUD. Concurrent seat-inventory
 * mutations are serialized at the database level using a pessimistic write lock
 * ({@code SELECT ... FOR UPDATE}) via {@link #findByIdWithLock}.</p>
 *
 * <p>The plain {@code findById(Long)} (inherited) is used only for read-only paths
 * and for {@code releaseSeats} (which also uses the lock to prevent concurrent
 * release/reserve races on the same event).</p>
 */
public interface EventRepository extends JpaRepository<Event, Long> {

    /**
     * Loads an event and immediately acquires an exclusive database row lock
     * ({@code SELECT ... FOR UPDATE}) on the matching row.
     *
     * <p>Called by both {@code EventServiceImpl#checkAndReserve} and
     * {@code EventServiceImpl#releaseSeats} inside their {@code @Transactional} methods.
     * The lock is held until the transaction commits or rolls back, preventing any other
     * concurrent transaction from acquiring the same lock (or even reading the row with
     * {@code FOR UPDATE}) until this one finishes.</p>
     *
     * <p>If two concurrent requests arrive simultaneously:</p>
     * <pre>
     *   Thread A (booking req 1)           Thread B (booking req 2)
     *   SELECT … FOR UPDATE → lock acquired  SELECT … FOR UPDATE → BLOCKS (waits)
     *   seats check ✓, deduct 2, commit      lock acquired (sees updated seats=8)
     *   lock released                        seats check ✓/✗, deduct or throw
     * </pre>
     *
     * <p>If the lock cannot be acquired within the database's default lock timeout,
     * Spring throws {@link org.springframework.dao.PessimisticLockingFailureException},
     * which {@code GlobalExceptionHandler} maps to HTTP 503.</p>
     *
     * @param id the event primary key
     * @return the locked event row, or empty if no event with that ID exists
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Event e WHERE e.id = :id")
    Optional<Event> findByIdWithLock(@Param("id") Long id);

    /**
     * Fetches all events with the given status.
     * Used at application startup to pre-populate the Redis seat-availability cache.
     *
     * @param status the lifecycle state to filter by (typically {@code ACTIVE})
     * @return all events in the requested state
     */
    List<Event> findByStatus(EventStatus status);

    Optional<Event> findByIdAndStatus(Long id, EventStatus status);
}
