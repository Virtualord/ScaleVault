package com.aditya.scalevault.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

@Schema(description = "Update profile request payload")
public record UpdateProfileRequest(
    @Schema(description = "Updated user email address", example = "newemail@example.com")
    @Email(message = "Email must be a valid email address")
    @Size(max = 255, message = "Email cannot exceed 255 characters")
    String email
) {}
