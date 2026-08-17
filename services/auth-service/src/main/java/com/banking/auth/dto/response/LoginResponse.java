package com.banking.auth.dto.response;

import java.util.UUID;

public record LoginResponse(
        String accessToken,
        String refreshToken,
        String tokenType,
        long expiresIn,
        UUID userId
) {
    public static LoginResponse of(String accessToken, String refreshToken, long expiresIn, UUID userId) {
        return new LoginResponse(accessToken, refreshToken, "Bearer", expiresIn, userId);
    }
}
