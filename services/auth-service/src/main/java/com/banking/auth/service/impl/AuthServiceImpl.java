package com.banking.auth.service.impl;

import com.banking.auth.config.JwtProperties;
import com.banking.auth.dto.request.CreateCredentialRequest;
import com.banking.auth.dto.request.LoginRequest;
import com.banking.auth.dto.response.LoginResponse;
import com.banking.auth.dto.response.TokenResponse;
import com.banking.auth.entity.CredentialStatus;
import com.banking.auth.entity.UserCredential;
import com.banking.auth.exception.AccountLockedException;
import com.banking.auth.exception.InvalidCredentialsException;
import com.banking.auth.exception.TokenException;
import com.banking.auth.repository.UserCredentialRepository;
import com.banking.auth.service.AuthService;
import com.banking.auth.service.TokenService;
import com.banking.auth.util.JwtUtil;
import com.banking.common.exception.BusinessRuleException;
import com.banking.common.exception.EntityNotFoundException;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final UserCredentialRepository credentialRepository;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokenService;
    private final JwtUtil jwtUtil;
    private final JwtProperties jwtProperties;

    @Override
    @Transactional
    public LoginResponse login(LoginRequest request) {
        UserCredential credential = credentialRepository.findByEmail(request.email())
                .orElseThrow(InvalidCredentialsException::new);

        if (credential.getStatus() != CredentialStatus.ACTIVE) {
            throw new BusinessRuleException("ACCOUNT_INACTIVE", "Account is not active.");
        }
        if (credential.isLocked()) {
            throw new AccountLockedException(credential.getLockedUntil());
        }
        if (!passwordEncoder.matches(request.password(), credential.getPasswordHash())) {
            credential.incrementFailedAttempts();
            credentialRepository.save(credential);
            throw new InvalidCredentialsException();
        }

        credential.resetFailedAttempts();
        credentialRepository.save(credential);

        String accessToken  = tokenService.generateAccessToken(
                credential.getCustomerId(), credential.getEmail(),
                Collections.singletonList("ROLE_CUSTOMER"), Collections.emptyList()
        );
        String refreshToken = tokenService.generateRefreshToken(credential.getCustomerId());

        log.info("Login successful for customerId={}", credential.getCustomerId());
        return LoginResponse.of(accessToken, refreshToken, jwtProperties.getAccessTokenExpiry(), credential.getCustomerId());
    }

    @Override
    @Transactional
    public void logout(String authorizationHeader) {
        String token = extractBearerToken(authorizationHeader);
        try {
            Claims claims = jwtUtil.validateAndExtract(token);
            long remaining = jwtUtil.remainingSeconds(claims);
            tokenService.revokeToken(claims.getId(), remaining);

            UUID userId = UUID.fromString(claims.getSubject());
            tokenService.deleteRefreshToken(userId);
            log.info("Logout successful for userId={}", userId);
        } catch (JwtException e) {
            throw new TokenException("Invalid token.");
        }
    }

    @Override
    @Transactional(readOnly = true)
    public TokenResponse refresh(String refreshToken) {
        try {
            Claims claims = jwtUtil.validateAndExtract(refreshToken);
            String type = claims.get("type", String.class);
            if (!"refresh".equals(type)) {
                throw new TokenException("Not a refresh token.");
            }
            if (tokenService.isBlacklisted(claims.getId())) {
                throw new TokenException("Token has been revoked.");
            }

            UUID userId = UUID.fromString(claims.getSubject());
            if (!tokenService.verifyRefreshToken(userId, claims.getId())) {
                throw new TokenException("Refresh token mismatch.");
            }

            UserCredential credential = credentialRepository.findByCustomerId(userId)
                    .orElseThrow(() -> new EntityNotFoundException("UserCredential", userId));

            String newAccessToken = tokenService.generateAccessToken(
                    userId, credential.getEmail(),
                    Collections.singletonList("ROLE_CUSTOMER"), Collections.emptyList()
            );
            return TokenResponse.of(newAccessToken, jwtProperties.getAccessTokenExpiry());
        } catch (JwtException e) {
            throw new TokenException("Invalid or expired refresh token.");
        }
    }

    @Override
    @Transactional
    public void createCredential(CreateCredentialRequest request) {
        if (credentialRepository.existsByEmail(request.email())) {
            throw new BusinessRuleException("DUPLICATE_EMAIL", "Email already registered.");
        }
        if (credentialRepository.existsByMobile(request.mobile())) {
            throw new BusinessRuleException("DUPLICATE_MOBILE", "Mobile already registered.");
        }
        UserCredential credential = UserCredential.builder()
                .customerId(request.customerId())
                .email(request.email())
                .mobile(request.mobile())
                .passwordHash(passwordEncoder.encode(request.password()))
                .build();
        credentialRepository.save(credential);
        log.info("Credential created for customerId={}", request.customerId());
    }

    private String extractBearerToken(String header) {
        if (header == null || !header.startsWith("Bearer ")) {
            throw new TokenException("Missing or malformed Authorization header.");
        }
        return header.substring(7);
    }
}
