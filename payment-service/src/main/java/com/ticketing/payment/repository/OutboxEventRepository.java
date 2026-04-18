package com.ticketing.payment.repository;

import com.ticketing.payment.entity.OutboxEvent;
import com.ticketing.payment.entity.OutboxStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link OutboxEvent} persistence.
 *
 * <p>Provides standard CRUD operations inherited from {@link JpaRepository} plus a
 * custom query method used by the {@link com.ticketing.payment.outbox.OutboxEventPoller}
 * to retrieve all events that are waiting to be delivered to RabbitMQ.</p>
 */
public interface OutboxEventRepository extends JpaRepository<OutboxEvent, UUID> {

    /**
     * Returns all {@link OutboxEvent} rows whose {@code status} matches the given value.
     *
     * <p>Called by the outbox poller with {@link OutboxStatus#PENDING} to find events
     * that still need to be published to RabbitMQ.</p>
     *
     * @param status the lifecycle state to filter by
     * @return list of matching outbox events; never {@code null}, may be empty
     */
    List<OutboxEvent> findByStatus(OutboxStatus status);
}
