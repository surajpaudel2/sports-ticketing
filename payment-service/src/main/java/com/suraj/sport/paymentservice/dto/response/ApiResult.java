package com.suraj.sport.paymentservice.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class ApiResult<T> {
    private boolean success;
    private String message;
    private T data;

    public static <T> ApiResult<T> of(boolean success, String message, T data) {
        ApiResult<T> result = new ApiResult<>();
        result.setSuccess(success);
        result.setMessage(message);
        result.setData(data);
        return result;
    }
}