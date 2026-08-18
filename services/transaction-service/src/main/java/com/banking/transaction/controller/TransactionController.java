package com.banking.transaction.controller;

import com.banking.common.dto.ApiResponse;
import com.banking.transaction.dto.request.DepositRequest;
import com.banking.transaction.dto.request.TransferRequest;
import com.banking.transaction.dto.request.WithdrawRequest;
import com.banking.transaction.dto.response.TransactionResponse;
import com.banking.transaction.service.TransactionService;
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

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Tag(name = "Transaction", description = "Deposit, withdrawal, transfer, and history")
public class TransactionController {

    private final TransactionService transactionService;

    @PostMapping("/deposit")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Deposit funds into an account (synchronous)")
    public ResponseEntity<ApiResponse<TransactionResponse>> deposit(
            @Valid @RequestBody DepositRequest request,
            Authentication auth) {
        UUID initiatedBy = UUID.fromString(auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(transactionService.deposit(request, initiatedBy)));
    }

    @PostMapping("/withdraw")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Withdraw funds from an account (synchronous)")
    public ResponseEntity<ApiResponse<TransactionResponse>> withdraw(
            @Valid @RequestBody WithdrawRequest request,
            Authentication auth) {
        UUID initiatedBy = UUID.fromString(auth.getName());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(transactionService.withdraw(request, initiatedBy)));
    }

    @PostMapping("/transfer")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Initiate a transfer (async saga — returns 202 ACCEPTED)")
    public ResponseEntity<ApiResponse<TransactionResponse>> transfer(
            @Valid @RequestBody TransferRequest request,
            Authentication auth) {
        UUID initiatedBy = UUID.fromString(auth.getName());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(transactionService.transfer(request, initiatedBy)));
    }

    @GetMapping("/{id}")
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get transaction by ID")
    public ResponseEntity<ApiResponse<TransactionResponse>> getById(
            @PathVariable UUID id,
            Authentication auth) {
        UUID requesterId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(transactionService.getById(id, requesterId)));
    }

    @GetMapping
    @PreAuthorize("isAuthenticated()")
    @Operation(summary = "Get paginated transaction history for an account")
    public ResponseEntity<ApiResponse<Page<TransactionResponse>>> getHistory(
            @RequestParam UUID accountId,
            @PageableDefault(size = 20, sort = "createdAt") Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success(transactionService.getHistory(accountId, pageable)));
    }

    @PostMapping("/{id}/reverse")
    @PreAuthorize("hasAuthority('ROLE_ADMIN')")
    @Operation(summary = "Reverse a completed transaction (Admin only)")
    public ResponseEntity<ApiResponse<TransactionResponse>> reverse(
            @PathVariable UUID id,
            Authentication auth) {
        UUID requesterId = UUID.fromString(auth.getName());
        return ResponseEntity.ok(ApiResponse.success(transactionService.reverse(id, requesterId)));
    }
}
