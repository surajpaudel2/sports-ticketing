package com.suraj.sport.eventservice.dto.response;

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
        ApiResult<T> response = new ApiResult<>();
        response.setSuccess(success);
        response.setMessage(message);
        response.setData(data);
        return response;
    }
}