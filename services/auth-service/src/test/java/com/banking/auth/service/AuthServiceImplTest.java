package com.banking.auth.service;

import com.banking.auth.config.JwtProperties;
import com.banking.auth.dto.request.LoginRequest;
import com.banking.auth.dto.response.LoginResponse;
import com.banking.auth.entity.CredentialStatus;
import com.banking.auth.entity.UserCredential;
import com.banking.auth.exception.AccountLockedException;
import com.banking.auth.exception.InvalidCredentialsException;
import com.banking.auth.repository.UserCredentialRepository;
import com.banking.auth.service.impl.AuthServiceImpl;
import com.banking.auth.util.JwtUtil;
import com.banking.common.exception.BusinessRuleException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthServiceImplTest {

    @Mock UserCredentialRepository credentialRepository;
    @Mock TokenService tokenService;

    private PasswordEncoder passwordEncoder;
    private JwtUtil jwtUtil;
    private JwtProperties jwtProperties;
    private AuthServiceImpl authService;

    private static final UUID CUSTOMER_ID = UUID.randomUUID();
    private static final String EMAIL     = "priya@example.com";
    private static final String RAW_PASS  = "Secret@123";

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4); // low cost for tests
        jwtProperties = new JwtProperties();
        jwtProperties.setSecret("banking-super-secret-jwt-key-change-in-production-256bits");
        jwtProperties.setAccessTokenExpiry(900L);
        jwtProperties.setRefreshTokenExpiry(604800L);
        jwtUtil = new JwtUtil(jwtProperties);

        authService = new AuthServiceImpl(credentialRepository, passwordEncoder,
                tokenService, jwtUtil, jwtProperties);
    }

    private UserCredential activeCredential() {
        return UserCredential.builder()
                .id(UUID.randomUUID())
                .customerId(CUSTOMER_ID)
                .email(EMAIL)
                .mobile("9876543210")
                .passwordHash(passwordEncoder.encode(RAW_PASS))
                .status(CredentialStatus.ACTIVE)
                .build();
    }

    @Test
    void login_withValidCredentials_shouldReturnTokens() {
        UserCredential cred = activeCredential();
        when(credentialRepository.findByEmail(EMAIL)).thenReturn(Optional.of(cred));
        when(tokenService.generateAccessToken(any(), any(), any(), any())).thenReturn("access-token");
        when(tokenService.generateRefreshToken(any())).thenReturn("refresh-token");

        LoginResponse response = authService.login(new LoginRequest(EMAIL, RAW_PASS));

        assertThat(response.accessToken()).isEqualTo("access-token");
        assertThat(response.refreshToken()).isEqualTo("refresh-token");
        assertThat(response.userId()).isEqualTo(CUSTOMER_ID);
        assertThat(cred.getFailedAttempts()).isZero();
    }

    @Test
    void login_withWrongPassword_shouldIncrementFailedAttempts() {
        UserCredential cred = activeCredential();
        when(credentialRepository.findByEmail(EMAIL)).thenReturn(Optional.of(cred));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "WrongPass")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(cred.getFailedAttempts()).isEqualTo(1);
        verify(credentialRepository).save(cred);
    }

    @Test
    void login_afterFiveFailures_shouldLockAccount() {
        UserCredential cred = activeCredential();
        // Simulate 4 prior failures
        for (int i = 0; i < 4; i++) cred.incrementFailedAttempts();
        when(credentialRepository.findByEmail(EMAIL)).thenReturn(Optional.of(cred));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, "WrongPass")))
                .isInstanceOf(InvalidCredentialsException.class);

        assertThat(cred.getFailedAttempts()).isEqualTo(5);
        assertThat(cred.getLockedUntil()).isAfter(Instant.now());
    }

    @Test
    void login_whenAccountAlreadyLocked_shouldThrowAccountLocked() {
        UserCredential cred = activeCredential();
        for (int i = 0; i < 5; i++) cred.incrementFailedAttempts(); // triggers lock
        when(credentialRepository.findByEmail(EMAIL)).thenReturn(Optional.of(cred));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASS)))
                .isInstanceOf(AccountLockedException.class);
    }

    @Test
    void login_withUnknownEmail_shouldThrowInvalidCredentials() {
        when(credentialRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASS)))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    void login_withSuspendedAccount_shouldThrowBusinessRule() {
        UserCredential cred = activeCredential();
        cred.setStatus(CredentialStatus.SUSPENDED);
        when(credentialRepository.findByEmail(EMAIL)).thenReturn(Optional.of(cred));

        assertThatThrownBy(() -> authService.login(new LoginRequest(EMAIL, RAW_PASS)))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("not active");
    }

    @Test
    void login_success_shouldResetFailedAttempts() {
        UserCredential cred = activeCredential();
        cred.incrementFailedAttempts(); // 1 prior failure
        when(credentialRepository.findByEmail(EMAIL)).thenReturn(Optional.of(cred));
        when(tokenService.generateAccessToken(any(), any(), any(), any())).thenReturn("tok");
        when(tokenService.generateRefreshToken(any())).thenReturn("ref");

        authService.login(new LoginRequest(EMAIL, RAW_PASS));

        assertThat(cred.getFailedAttempts()).isZero();
        assertThat(cred.getLockedUntil()).isNull();
    }
}
