package com.aditya.scalevault.service;

import com.aditya.scalevault.dto.ChangePasswordRequest;
import com.aditya.scalevault.dto.UpdateProfileRequest;
import com.aditya.scalevault.dto.UserResponse;
import com.aditya.scalevault.entity.User;
import com.aditya.scalevault.exception.EmailAlreadyExistsException;
import com.aditya.scalevault.exception.ResourceNotFoundException;
import com.aditya.scalevault.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenService refreshTokenService;

    public UserService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            RefreshTokenService refreshTokenService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.refreshTokenService = refreshTokenService;
    }

    @Transactional(readOnly = true)
    public UserResponse getProfile(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));
        return UserResponse.fromEntity(user);
    }

    @Transactional
    public UserResponse updateProfile(UUID userId, UpdateProfileRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (request.email() != null && !request.email().isBlank()) {
            String normalizedEmail = request.email().trim().toLowerCase();
            if (!normalizedEmail.equalsIgnoreCase(user.getEmail())) {
                if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
                    log.warn("Profile update rejected: email {} is already in use", normalizedEmail);
                    throw new EmailAlreadyExistsException("Email is already registered: " + normalizedEmail);
                }
                user.setEmail(normalizedEmail);
            }
        }

        User updatedUser = userRepository.save(user);
        log.info("Updated profile for user ID: {}", userId);
        return UserResponse.fromEntity(updatedUser);
    }

    @Transactional
    public void changePassword(UUID userId, ChangePasswordRequest request) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + userId));

        if (!passwordEncoder.matches(request.currentPassword(), user.getPasswordHash())) {
            log.warn("Password change rejected: incorrect current password for user ID: {}", userId);
            throw new BadCredentialsException("Current password does not match");
        }

        String newPasswordHash = passwordEncoder.encode(request.newPassword());
        user.setPasswordHash(newPasswordHash);
        userRepository.saveAndFlush(user);

        // Security best practice: invalidate all active refresh tokens on password change
        refreshTokenService.revokeAllUserTokens(userId);
        log.info("Password changed and sessions invalidated for user ID: {}", userId);
    }
}
