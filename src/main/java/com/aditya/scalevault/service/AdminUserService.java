package com.aditya.scalevault.service;

import com.aditya.scalevault.dto.PageResponse;
import com.aditya.scalevault.dto.UserResponse;
import com.aditya.scalevault.entity.Role;
import com.aditya.scalevault.entity.User;
import com.aditya.scalevault.exception.ResourceNotFoundException;
import com.aditya.scalevault.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class AdminUserService {

    private static final Logger log = LoggerFactory.getLogger(AdminUserService.class);

    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;

    public AdminUserService(UserRepository userRepository, RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public PageResponse<UserResponse> getUsers(Pageable pageable, String search) {
        Page<User> page;
        if (search != null && !search.isBlank()) {
            page = userRepository.findByEmailContainingIgnoreCase(search.trim(), pageable);
        } else {
            page = userRepository.findAll(pageable);
        }

        Page<UserResponse> dtoPage = page.map(UserResponse::fromEntity);
        return PageResponse.fromPage(dtoPage);
    }

    @Transactional(readOnly = true)
    public UserResponse getUserById(UUID id) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse updateRole(UUID id, Role newRole) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        user.setRole(newRole);
        User updated = userRepository.save(user);
        log.info("Admin updated role for user ID {} to {}", id, newRole);
        return UserResponse.fromEntity(updated);
    }

    @Transactional
    public UserResponse updateStatus(UUID id, boolean enabled) {
        User user = userRepository.findById(id)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + id));

        user.setEnabled(enabled);
        User updated = userRepository.saveAndFlush(user);

        // If disabling the user, terminate all their active sessions immediately
        if (!enabled) {
            refreshTokenService.revokeAllUserTokens(id);
            log.info("Admin disabled user ID {} and revoked all active sessions", id);
        } else {
            log.info("Admin enabled user ID {}", id);
        }

        return UserResponse.fromEntity(updated);
    }
}
