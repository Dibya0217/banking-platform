package com.banking.upi.controller;

import com.banking.common.dto.ApiResponse;
import com.banking.upi.dto.request.ChangePinRequest;
import com.banking.upi.dto.request.CreateVpaRequest;
import com.banking.upi.dto.request.UpiTransferRequest;
import com.banking.upi.dto.response.UpiIdResponse;
import com.banking.upi.dto.response.UpiTransactionResponse;
import com.banking.upi.service.UpiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/upi")
@RequiredArgsConstructor
@Tag(name = "UPI", description = "VPA management, PIN, and UPI transfers")
public class UpiController {

    private final UpiService upiService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Register a new VPA (Virtual Payment Address)")
    public ResponseEntity<ApiResponse<UpiIdResponse>> createVpa(
            @Valid @RequestBody CreateVpaRequest request,
            Authentication auth) {
        UUID customerId = UUID.fromString(auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(upiService.createVpa(request, customerId)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List all VPAs for authenticated customer")
    public ResponseEntity<ApiResponse<List<UpiIdResponse>>> listVpas(Authentication auth) {
        UUID customerId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(upiService.listVpas(customerId)));
    }

    @PutMapping("/{id}/pin")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Change UPI PIN (protected by distributed lock)")
    public ResponseEntity<ApiResponse<Void>> changePin(
            @PathVariable UUID id,
            @Valid @RequestBody ChangePinRequest request,
            Authentication auth) {
        UUID customerId = UUID.fromString(auth.getName());
        upiService.changePin(id, request, customerId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/transfer")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Initiate a UPI transfer — PIN verified, daily limit enforced")
    public ResponseEntity<ApiResponse<UpiTransactionResponse>> transfer(
            @Valid @RequestBody UpiTransferRequest request,
            Authentication auth) {
        UUID initiatedBy = UUID.fromString(auth.getName());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(upiService.transfer(request, initiatedBy)));
    }

    @GetMapping("/{id}/transactions")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get paginated UPI transaction history for a VPA")
    public ResponseEntity<ApiResponse<Page<UpiTransactionResponse>>> getTransactions(
            @PathVariable UUID id,
            Authentication auth,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        UUID customerId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(upiService.getTransactions(id, customerId, pageable)));
    }
}
