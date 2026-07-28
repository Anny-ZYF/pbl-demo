package com.gdmu.common;

import org.slf4j.MDC;

public record ApiResponse<T>(int code, String message, T data, String requestId) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(200, "success", data, MDC.get("requestId"));
    }

    public static <T> ApiResponse<T> created(T data) {
        return new ApiResponse<>(201, "created", data, MDC.get("requestId"));
    }

    public static ApiResponse<Void> error(int code, String message) {
        return new ApiResponse<>(code, message, null, MDC.get("requestId"));
    }
}
