package com.ticketing.payment.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic API response wrapper used across all endpoints in Payment Service.
 *
 * <p>Mirrors the same wrapper used by booking-service so that Feign clients
 * can deserialize responses consistently.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ApiResult<T> {

    private boolean success;
    private String message;
    private T data;

    public static <T> ApiResult<T> of(boolean success, String message, T data) {
        return new ApiResult<>(success, message, data);
    }
}
