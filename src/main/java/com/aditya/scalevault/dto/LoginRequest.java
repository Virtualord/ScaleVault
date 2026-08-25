package com.aditya.scalevault.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "User authentication request payload")
public record LoginRequest(
    @Schema(description = "User email address", example = "user@example.com")
    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid email address")
    @Size(max = 255, message = "Email cannot exceed 255 characters")
    String email,

    @Schema(description = "Account password", example = "Password123!")
    @NotBlank(message = "Password is required")
    String password
) {}
