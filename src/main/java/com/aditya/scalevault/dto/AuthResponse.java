package com.aditya.scalevault.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Authentication response with access tokens and user profile")
public record AuthResponse(
    @Schema(description = "JWT Access Token")
    String accessToken,

    @Schema(description = "Refresh Token", nullable = true)
    String refreshToken,

    @Schema(description = "Token type", example = "Bearer")
    String tokenType,

    @Schema(description = "Access token validity in milliseconds", example = "900000")
    long expiresIn,

    @Schema(description = "Authenticated user profile")
    UserResponse user
) {
    public static AuthResponse of(String accessToken, String refreshToken, long expiresIn, UserResponse user) {
        return new AuthResponse(accessToken, refreshToken, "Bearer", expiresIn, user);
    }

    public static AuthResponse of(String accessToken, long expiresIn, UserResponse user) {
        return new AuthResponse(accessToken, null, "Bearer", expiresIn, user);
    }
}
