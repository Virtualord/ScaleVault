package com.aditya.scalevault.dto;

import com.aditya.scalevault.entity.Role;
import com.aditya.scalevault.entity.User;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(
    UUID id,
    String email,
    Role role,
    boolean enabled,
    Instant createdAt,
    Instant updatedAt
) {
    public static UserResponse fromEntity(User user) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getRole(),
            user.isEnabled(),
            user.getCreatedAt(),
            user.getUpdatedAt()
        );
    }
}
