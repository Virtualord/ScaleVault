package com.aditya.scalevault.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Refresh token request payload")
public record RefreshTokenRequest(
    @Schema(description = "High-entropy refresh token", example = "d94b00ca-4cfa-40f4-b223-9fef4c084793")
    @NotBlank(message = "Refresh token is required")
    String refreshToken
) {}
