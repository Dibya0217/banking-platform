package com.banking.common.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import org.slf4j.MDC;

import java.time.Instant;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiResponse<T> {

    private final boolean success;
    private final T data;
    private final ErrorDetail error;
    private final String timestamp;
    private final String correlationId;

    public static <T> ApiResponse<T> success(T data) {
        return ApiResponse.<T>builder()
                .success(true)
                .data(data)
                .timestamp(Instant.now().toString())
                .correlationId(MDC.get("correlationId"))
                .build();
    }

    public static ApiResponse<Void> error(String code, String message) {
        return ApiResponse.<Void>builder()
                .success(false)
                .error(ErrorDetail.builder().code(code).message(message).build())
                .timestamp(Instant.now().toString())
                .correlationId(MDC.get("correlationId"))
                .build();
    }

    public static ApiResponse<Void> error(ErrorDetail errorDetail) {
        return ApiResponse.<Void>builder()
                .success(false)
                .error(errorDetail)
                .timestamp(Instant.now().toString())
                .correlationId(MDC.get("correlationId"))
                .build();
    }
}
