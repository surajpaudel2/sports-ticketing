package com.ticketing.booking.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic API response wrapper used across all endpoints.
 * {@code success=false} with HTTP 200 indicates a business logic failure (e.g. payment failed)
 * vs HTTP 4xx/5xx for system-level failures.
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