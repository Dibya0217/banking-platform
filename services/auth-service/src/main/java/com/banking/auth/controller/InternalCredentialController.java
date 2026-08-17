package com.banking.auth.controller;

import com.banking.auth.dto.request.CreateCredentialRequest;
import com.banking.auth.service.AuthService;
import com.banking.common.dto.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal")
@RequiredArgsConstructor
public class InternalCredentialController {

    @Value("${internal.secret:internal-secret-change-me}")
    private String internalSecret;

    private final AuthService authService;

    @PostMapping("/credentials")
    public ResponseEntity<ApiResponse<Void>> createCredential(
            @RequestHeader("X-Internal-Secret") String secret,
            @Valid @RequestBody CreateCredentialRequest request) {

        if (!internalSecret.equals(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(ApiResponse.error("UNAUTHORIZED", "Invalid internal secret."));
        }
        authService.createCredential(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(null));
    }
}
