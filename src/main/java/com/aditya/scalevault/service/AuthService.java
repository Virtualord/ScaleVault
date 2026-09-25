package com.aditya.scalevault.service;

import com.aditya.scalevault.dto.RegisterRequest;
import com.aditya.scalevault.dto.UserResponse;
import com.aditya.scalevault.entity.Role;
import com.aditya.scalevault.entity.User;
import com.aditya.scalevault.exception.EmailAlreadyExistsException;
import com.aditya.scalevault.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCase(normalizedEmail)) {
            log.warn("Registration rejected: email already exists for {}", normalizedEmail);
            throw new EmailAlreadyExistsException("Email is already registered: " + normalizedEmail);
        }

        String encodedPassword = passwordEncoder.encode(request.password());
        User user = new User(normalizedEmail, encodedPassword, Role.USER, true);
        User savedUser = userRepository.save(user);

        log.info("User registered successfully with ID: {}", savedUser.getId());
        return UserResponse.fromEntity(savedUser);
    }
}
