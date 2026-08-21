package com.banking.statement.controller;

import com.banking.common.dto.ApiResponse;
import com.banking.statement.dto.response.StatementResponse;
import com.banking.statement.service.StatementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/statements")
@PreAuthorize("isAuthenticated()")
@RequiredArgsConstructor
@Slf4j
public class StatementController {

    private final StatementService statementService;

    @GetMapping("/{accountId}/monthly")
    public ResponseEntity<ApiResponse<StatementResponse>> getStatement(
            @PathVariable UUID accountId,
            @RequestParam int month,
            @RequestParam int year,
            Authentication auth) {

        log.info("Statement request for account {} {}/{} by user {}", accountId, month, year,
                auth != null ? auth.getName() : "anonymous");

        StatementResponse resp = statementService.getOrGenerate(accountId, month, year);
        return ResponseEntity.ok(ApiResponse.success(resp));
    }

    @GetMapping("/{accountId}/download")
    public ResponseEntity<byte[]> downloadStatement(
            @PathVariable UUID accountId,
            @RequestParam int month,
            @RequestParam int year) {

        log.info("Download statement for account {} {}/{}", accountId, month, year);

        byte[] pdf = statementService.downloadStatement(accountId, month, year);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, "application/pdf")
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=statement-" + year + "-" + month + ".pdf")
                .body(pdf);
    }
}
