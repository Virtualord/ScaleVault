package com.aditya.scalevault.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@Schema(description = "Change password request payload")
public record ChangePasswordRequest(
    @Schema(description = "Current account password", example = "OldPassword123!")
    @NotBlank(message = "Current password is required")
    String currentPassword,

    @Schema(description = "New account password", example = "NewStrongPassword123!")
    @NotBlank(message = "New password is required")
    @Size(min = 8, max = 128, message = "New password must be between 8 and 128 characters")
    String newPassword
) {}
