package com.banking.customer.controller;

import com.banking.customer.dto.request.CustomerRegistrationRequest;
import com.banking.customer.dto.request.KycSubmissionRequest;
import com.banking.customer.dto.response.CustomerRegistrationResponse;
import com.banking.customer.dto.response.CustomerResponse;
import com.banking.customer.service.CustomerService;
import com.banking.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Tag(name = "Customer", description = "Customer registration and profile management")
public class CustomerController {

    private final CustomerService customerService;

    @PostMapping("/register")
    @Operation(summary = "Register a new customer")
    public ResponseEntity<ApiResponse<CustomerRegistrationResponse>> register(
            @Valid @RequestBody CustomerRegistrationRequest request) {
        CustomerRegistrationResponse response = customerService.register(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(response));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get customer by ID")
    public ResponseEntity<ApiResponse<CustomerResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(customerService.getById(id)));
    }

    @PostMapping("/{id}/kyc")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Submit KYC documents")
    public ResponseEntity<ApiResponse<Void>> submitKyc(
            @PathVariable UUID id,
            @Valid @RequestBody KycSubmissionRequest request) {
        customerService.submitKyc(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }

    @PostMapping("/{id}/freeze")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Freeze a customer account (Admin only)")
    public ResponseEntity<ApiResponse<Void>> freeze(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        customerService.freeze(id, body.getOrDefault("reason", "Admin action"));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/verify-mobile")
    @Operation(summary = "Verify mobile after OTP — update status to PENDING_KYC")
    public ResponseEntity<ApiResponse<Void>> verifyMobile(@PathVariable UUID id) {
        customerService.verifyMobile(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
