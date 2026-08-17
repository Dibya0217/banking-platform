package com.banking.auth.controller;

import com.banking.auth.dto.request.LoginRequest;
import com.banking.auth.dto.request.OtpSendRequest;
import com.banking.auth.dto.request.OtpVerifyRequest;
import com.banking.auth.dto.request.RefreshRequest;
import com.banking.auth.dto.response.LoginResponse;
import com.banking.auth.dto.response.TokenResponse;
import com.banking.auth.service.AuthService;
import com.banking.auth.service.OtpService;
import com.banking.common.dto.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Login, logout, token refresh, and OTP")
public class AuthController {

    private final AuthService authService;
    private final OtpService otpService;

    @PostMapping("/login")
    @Operation(summary = "Authenticate with email and password")
    public ResponseEntity<ApiResponse<LoginResponse>> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.login(request)));
    }

    @PostMapping("/logout")
    @Operation(summary = "Revoke current access token")
    public ResponseEntity<ApiResponse<Void>> logout(
            @RequestHeader("Authorization") String authorizationHeader) {
        authService.logout(authorizationHeader);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate access token using refresh token")
    public ResponseEntity<ApiResponse<TokenResponse>> refresh(@Valid @RequestBody RefreshRequest request) {
        return ResponseEntity.ok(ApiResponse.success(authService.refresh(request.refreshToken())));
    }

    @PostMapping("/otp/send")
    @Operation(summary = "Send OTP to mobile number")
    public ResponseEntity<ApiResponse<Void>> sendOtp(@Valid @RequestBody OtpSendRequest request) {
        // In prod the OTP is sent via SMS; here we generate and store it (MailHog/log for dev)
        otpService.generateAndStore(request.mobile(), request.purpose());
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    @PostMapping("/otp/verify")
    @Operation(summary = "Verify OTP submitted by customer")
    public ResponseEntity<ApiResponse<Void>> verifyOtp(@Valid @RequestBody OtpVerifyRequest request) {
        otpService.verify(request.mobile(), request.purpose(), request.otp());
        return ResponseEntity.ok(ApiResponse.success(null));
    }
}
