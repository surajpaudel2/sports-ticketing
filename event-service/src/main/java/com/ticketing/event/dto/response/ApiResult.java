package com.ticketing.event.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Generic API response wrapper used across all endpoints in Event Service.
 *
 * <p>Mirrors the same wrapper used by booking-service so that cross-service
 * Feign clients can deserialize responses consistently.</p>
 *
 * <p>{@code success=false} with HTTP 200 is intentionally avoided here — business
 * failures are surfaced via exceptions that map to 4xx HTTP responses. This wrapper
 * is only used for successful responses ({@code success=true}).</p>
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
