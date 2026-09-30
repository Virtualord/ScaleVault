package com.aditya.scalevault.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Admin request to update user active status")
public record UpdateUserStatusRequest(
    @Schema(description = "Whether user account is enabled", example = "false")
    @NotNull(message = "Enabled status is required")
    Boolean enabled
) {}
