package com.banking.customer.exception;

import com.banking.common.dto.ApiResponse;
import com.banking.common.dto.ErrorDetail;
import com.banking.common.exception.BankingException;
import com.banking.common.exception.EntityNotFoundException;
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateCustomerException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicateCustomer(DuplicateCustomerException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleCustomerNotFound(CustomerNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(UnderAgeException.class)
    public ResponseEntity<ApiResponse<Void>> handleUnderAge(UnderAgeException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleEntityNotFound(EntityNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        List<ErrorDetail.FieldError> fieldErrors = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> ErrorDetail.FieldError.builder()
                        .field(fe.getField())
                        .message(fe.getDefaultMessage())
                        .build())
                .toList();
        ErrorDetail error = ErrorDetail.builder()
                .code("VALIDATION_ERROR")
                .message("Request validation failed")
                .fieldErrors(fieldErrors)
                .build();
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.<Void>builder().success(false).error(error).build());
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.error("VALIDATION_ERROR", ex.getMessage()));
    }

    @ExceptionHandler(BankingException.class)
    public ResponseEntity<ApiResponse<Void>> handleBankingException(BankingException ex) {
        log.warn("BankingException: code={}, msg={}", ex.getErrorCode(), ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.error(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception ex) {
        log.error("Unexpected error", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.error("INTERNAL_ERROR", "An unexpected error occurred."));
    }
}
