package com.aditya.scalevault.dto;

import com.aditya.scalevault.entity.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Admin request to update user role")
public record UpdateUserRoleRequest(
    @Schema(description = "New user role", example = "ADMIN")
    @NotNull(message = "Role is required")
    Role role
) {}
