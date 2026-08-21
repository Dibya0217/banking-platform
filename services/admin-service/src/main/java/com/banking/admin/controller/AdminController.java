package com.banking.admin.controller;

import com.banking.admin.service.AdminProxyService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Slf4j
@RestController
@RequestMapping("/api/v1/admin")
@PreAuthorize("hasAuthority('ROLE_ADMIN')")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Admin operations — proxied to downstream services")
public class AdminController {

    private final AdminProxyService adminProxyService;

    // ── Customers ──────────────────────────────────────────────────────────

    @GetMapping("/customers")
    @Operation(summary = "List all customers")
    public ResponseEntity<String> getCustomers(
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size,
            @RequestParam(required = false) String search) {
        String result = adminProxyService.getCustomers(page, size, search).block();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    @PostMapping("/customers/{customerId}/freeze")
    @Operation(summary = "Freeze a customer account")
    public ResponseEntity<String> freezeCustomer(
            @PathVariable String customerId,
            @RequestBody(required = false) String body) {
        String result = adminProxyService.freezeCustomer(customerId, body).block();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    @PostMapping("/customers/{customerId}/kyc/{kycId}/approve")
    @Operation(summary = "Approve KYC document")
    public ResponseEntity<String> approveKyc(
            @PathVariable String customerId,
            @PathVariable String kycId) {
        String result = adminProxyService.approveKyc(customerId, kycId).block();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    @PostMapping("/customers/{customerId}/kyc/{kycId}/reject")
    @Operation(summary = "Reject KYC document")
    public ResponseEntity<String> rejectKyc(
            @PathVariable String customerId,
            @PathVariable String kycId,
            @RequestBody(required = false) String body) {
        String result = adminProxyService.rejectKyc(customerId, kycId, body).block();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    // ── Accounts ───────────────────────────────────────────────────────────

    @GetMapping("/accounts")
    @Operation(summary = "List accounts, optionally filtered by customer")
    public ResponseEntity<String> getAccounts(
            @RequestParam(required = false) String customerId,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        String result = adminProxyService.getAccounts(customerId, page, size).block();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    @PostMapping("/accounts/{accountId}/freeze")
    @Operation(summary = "Freeze an account")
    public ResponseEntity<String> freezeAccount(
            @PathVariable String accountId,
            @RequestBody(required = false) String body) {
        String result = adminProxyService.freezeAccount(accountId, body).block();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    // ── Transactions ───────────────────────────────────────────────────────

    @GetMapping("/transactions")
    @Operation(summary = "List transactions with filters")
    public ResponseEntity<String> getTransactions(
            @RequestParam(required = false) String accountId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String dateFrom,
            @RequestParam(required = false) String dateTo,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        String result = adminProxyService.getTransactions(accountId, type, status, dateFrom, dateTo, page, size).block();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    @GetMapping("/transactions/{transactionId}")
    @Operation(summary = "Get a single transaction")
    public ResponseEntity<String> getTransaction(@PathVariable String transactionId) {
        String result = adminProxyService.getTransaction(transactionId).block();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    // ── Fraud ──────────────────────────────────────────────────────────────

    @GetMapping("/fraud/alerts")
    @Operation(summary = "List fraud alerts")
    public ResponseEntity<String> getFraudAlerts(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String size) {
        String result = adminProxyService.getFraudAlerts(status, page, size).block();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    @PatchMapping("/fraud/alerts/{alertId}/resolve")
    @Operation(summary = "Resolve a fraud alert")
    public ResponseEntity<String> resolveAlert(
            @PathVariable String alertId,
            @RequestBody(required = false) String body) {
        String result = adminProxyService.resolveAlert(alertId, body).block();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }

    @PostMapping("/fraud/blacklist")
    @Operation(summary = "Blacklist an account for fraud")
    public ResponseEntity<String> blacklistAccount(@RequestBody(required = false) String body) {
        String result = adminProxyService.blacklistAccount(body).block();
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_JSON).body(result);
    }
}
