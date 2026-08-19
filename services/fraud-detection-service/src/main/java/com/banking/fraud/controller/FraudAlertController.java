package com.banking.fraud.controller;

import com.banking.common.dto.ApiResponse;
import com.banking.fraud.dto.request.BlacklistRequest;
import com.banking.fraud.dto.request.ResolveAlertRequest;
import com.banking.fraud.dto.response.FraudAlertResponse;
import com.banking.fraud.entity.FraudAlertStatus;
import com.banking.fraud.service.FraudDetectionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fraud")
@RequiredArgsConstructor
public class FraudAlertController {

    private final FraudDetectionService fraudDetectionService;

    @GetMapping("/alerts")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Page<FraudAlertResponse>>> getAlerts(
            @RequestParam(required = false) FraudAlertStatus status,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        Page<FraudAlertResponse> alerts = fraudDetectionService.getAlerts(status, pageable);
        return ResponseEntity.ok(ApiResponse.success(alerts));
    }

    @PatchMapping("/alerts/{alertId}/resolve")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<FraudAlertResponse>> resolveAlert(
            @PathVariable UUID alertId,
            @RequestBody @Valid ResolveAlertRequest request,
            Authentication auth) {
        UUID resolvedBy = UUID.fromString(auth.getName());
        FraudAlertResponse response = fraudDetectionService.resolveAlert(alertId, resolvedBy, request.getNote());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/blacklist")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    public ResponseEntity<ApiResponse<Void>> blacklistAccount(
            @RequestBody @Valid BlacklistRequest request,
            Authentication auth) {
        UUID adminId = UUID.fromString(auth.getName());
        fraudDetectionService.blacklistAccount(request.getAccountId(), request.getReason(), adminId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
