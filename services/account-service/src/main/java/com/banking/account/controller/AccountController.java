package com.banking.account.controller;

import com.banking.account.dto.request.CreateAccountRequest;
import com.banking.account.dto.response.AccountResponse;
import com.banking.account.dto.response.BalanceResponse;
import com.banking.account.service.AccountService;
import com.banking.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Tag(name = "Account", description = "Account creation and balance management")
public class AccountController {

    private final AccountService accountService;

    @PostMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Create a new account")
    public ResponseEntity<ApiResponse<AccountResponse>> createAccount(
            @Valid @RequestBody CreateAccountRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(accountService.createAccount(request)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get account by ID")
    public ResponseEntity<ApiResponse<AccountResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(accountService.getById(id)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get all accounts for a customer")
    public ResponseEntity<ApiResponse<List<AccountResponse>>> getByCustomerId(
            @RequestParam UUID customerId) {
        return ResponseEntity.ok(ApiResponse.success(accountService.getByCustomerId(customerId)));
    }

    @GetMapping("/{id}/balance")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get account balance (Redis cache-aside)")
    public ResponseEntity<ApiResponse<BalanceResponse>> getBalance(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success(accountService.getBalance(id)));
    }

    @PostMapping("/{id}/freeze")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Freeze an account (Admin only)")
    public ResponseEntity<ApiResponse<Void>> freeze(
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        accountService.freezeAccount(id, body.getOrDefault("reason", "Admin action"));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Close an account (zero balance required)")
    public ResponseEntity<ApiResponse<Void>> close(@PathVariable UUID id) {
        accountService.closeAccount(id);
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
