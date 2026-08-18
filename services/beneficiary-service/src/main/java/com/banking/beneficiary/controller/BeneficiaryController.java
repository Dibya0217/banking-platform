package com.banking.beneficiary.controller;

import com.banking.beneficiary.dto.request.AddBeneficiaryRequest;
import com.banking.beneficiary.dto.response.BeneficiaryResponse;
import com.banking.beneficiary.service.BeneficiaryService;
import com.banking.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/beneficiaries")
@RequiredArgsConstructor
@Tag(name = "Beneficiary", description = "Manage payment beneficiaries with penny-drop verification")
public class BeneficiaryController {

    private final BeneficiaryService beneficiaryService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Add a new beneficiary — triggers async penny-drop verification")
    public ResponseEntity<ApiResponse<BeneficiaryResponse>> add(
            @Valid @RequestBody AddBeneficiaryRequest request,
            Authentication auth) {
        UUID customerId = UUID.fromString(auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(beneficiaryService.add(request, customerId)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "List all active beneficiaries for the authenticated customer")
    public ResponseEntity<ApiResponse<List<BeneficiaryResponse>>> list(Authentication auth) {
        UUID customerId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(beneficiaryService.list(customerId)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get beneficiary by ID")
    public ResponseEntity<ApiResponse<BeneficiaryResponse>> getById(
            @PathVariable UUID id,
            Authentication auth) {
        UUID customerId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(beneficiaryService.getById(id, customerId)));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Remove a beneficiary")
    public ResponseEntity<ApiResponse<Void>> remove(
            @PathVariable UUID id,
            Authentication auth) {
        UUID customerId = UUID.fromString(auth.getName());
        beneficiaryService.remove(id, customerId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @GetMapping("/{id}/verify")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Check if transfer is allowed (cooldown + active status)")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> verify(
            @PathVariable UUID id,
            Authentication auth) {
        UUID customerId = UUID.fromString(auth.getName());
        boolean allowed = beneficiaryService.isTransferAllowed(id, customerId);
        return ResponseEntity.ok(ApiResponse.success(Map.of("transferAllowed", allowed)));
    }
}
