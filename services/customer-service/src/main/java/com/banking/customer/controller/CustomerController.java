package com.banking.customer.controller;

import com.banking.common.dto.ApiResponse;
import com.banking.customer.dto.request.CustomerRegistrationRequest;
import com.banking.customer.dto.request.KycSubmissionRequest;
import com.banking.customer.dto.response.CustomerRegistrationResponse;
import com.banking.customer.dto.response.CustomerResponse;
import com.banking.customer.service.CustomerService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(customerService.register(request)));
    }

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "List all customers (paginated, Admin only)")
    public ResponseEntity<ApiResponse<Page<CustomerResponse>>> getAllCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String search,
            @RequestHeader("X-User-Role") String role) {
        Page<CustomerResponse> customers = customerService.findAll(page, size, search);
        return ResponseEntity.ok(ApiResponse.success(customers));
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

    @PostMapping("/{id}/kyc/{kycId}/approve")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Approve KYC (Admin only)")
    public ResponseEntity<ApiResponse<Void>> approveKyc(
            @PathVariable UUID id,
            @PathVariable UUID kycId,
            @AuthenticationPrincipal String adminId) {
        customerService.approveKyc(id, kycId, adminId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/kyc/{kycId}/reject")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Reject KYC (Admin only)")
    public ResponseEntity<ApiResponse<Void>> rejectKyc(
            @PathVariable UUID id,
            @PathVariable UUID kycId,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal String adminId) {
        customerService.rejectKyc(id, kycId, body.getOrDefault("reason", ""), adminId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/freeze")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Freeze a customer account (Admin only)")
    public ResponseEntity<ApiResponse<Void>> freeze(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal String adminId) {
        customerService.freeze(id, body.getOrDefault("reason", "Admin action"), adminId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/verify-mobile")
    @Operation(summary = "Verify mobile — update status to PENDING_KYC")
    public ResponseEntity<ApiResponse<Void>> verifyMobile(@PathVariable UUID id) {
        customerService.verifyMobile(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
