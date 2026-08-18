package com.banking.account.controller;

import com.banking.account.service.AccountService;
import com.banking.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/internal/accounts")
@RequiredArgsConstructor
@Hidden
public class InternalAccountController {

    @Value("${internal.secret:internal-secret-change-me}")
    private String internalSecret;

    private final AccountService accountService;

    @PostMapping("/{id}/debit")
    public ResponseEntity<ApiResponse<Void>> debit(
            @RequestHeader("X-Internal-Secret") String secret,
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        if (!internalSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "Invalid internal secret."));
        }
        accountService.debitAccount(id, new BigDecimal(body.get("amount")), body.get("transactionId"));
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/{id}/credit")
    public ResponseEntity<ApiResponse<Void>> credit(
            @RequestHeader("X-Internal-Secret") String secret,
            @PathVariable UUID id,
            @RequestBody Map<String, String> body) {
        if (!internalSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "Invalid internal secret."));
        }
        accountService.creditAccount(id, new BigDecimal(body.get("amount")), body.get("transactionId"));
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
