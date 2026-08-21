package com.banking.gateway.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GatewayExceptionHandler {

    @ExceptionHandler(WebClientResponseException.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleWebClientResponseException(
            WebClientResponseException ex) {
        log.warn("Downstream service error: {} {}", ex.getStatusCode(), ex.getMessage());
        return Mono.just(ResponseEntity.status(ex.getStatusCode())
                .body(errorBody(ex.getStatusCode().value(), ex.getMessage())));
    }

    @ExceptionHandler(Exception.class)
    public Mono<ResponseEntity<Map<String, Object>>> handleGenericException(Exception ex) {
        log.error("Unhandled gateway exception: {}", ex.getMessage(), ex);
        return Mono.just(ResponseEntity.status(HttpStatus.BAD_GATEWAY)
                .body(errorBody(HttpStatus.BAD_GATEWAY.value(), "Service unavailable")));
    }

    private Map<String, Object> errorBody(int status, String message) {
        return Map.of(
                "timestamp", Instant.now().toString(),
                "status", status,
                "error", message
        );
    }
}
