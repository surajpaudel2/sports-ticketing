package com.ticketing.booking.entity;

/**
 * Refund eligibility tier calculated from hours remaining until the event.
 *
 * <p>Calculated automatically by Booking Service using a live Feign call
 * to Event Service at cancellation time — NOT from the eventDate snapshot
 * on the Booking entity (which may be stale if the event was rescheduled).</p>
 *
 * <p>Admin sees the recommendation but can override the amount.</p>
 */
public enum RefundType {

    FULL_REFUND,
    // > 48 hours until event — 100% of seatsToCancel * pricePerSeat

    PARTIAL_REFUND,
    // 24–48 hours until event — 50% of seatsToCancel * pricePerSeat

    NO_REFUND
    // < 24 hours until event — 0
}