package com.banking.auth.service;

import com.banking.auth.dto.request.CreateCredentialRequest;
import com.banking.auth.dto.request.LoginRequest;
import com.banking.auth.dto.response.LoginResponse;
import com.banking.auth.dto.response.TokenResponse;

public interface AuthService {

    LoginResponse login(LoginRequest request);

    void logout(String authorizationHeader);

    TokenResponse refresh(String refreshToken);

    void createCredential(CreateCredentialRequest request);
}
