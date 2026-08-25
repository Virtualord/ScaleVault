package com.aditya.scalevault.service;

import com.aditya.scalevault.dto.AuthResponse;
import com.aditya.scalevault.dto.LoginRequest;
import com.aditya.scalevault.dto.RegisterRequest;
import com.aditya.scalevault.dto.UserResponse;
import com.aditya.scalevault.entity.Role;
import com.aditya.scalevault.entity.User;
import com.aditya.scalevault.exception.EmailAlreadyExistsException;
import com.aditya.scalevault.exception.ResourceNotFoundException;
import com.aditya.scalevault.repository.UserRepository;
import com.aditya.scalevault.security.JwtService;
import com.aditya.scalevault.security.SecurityUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;

    public AuthService(
            UserRepository userRepository,
            PasswordEncoder passwordEncoder,
            AuthenticationManager authenticationManager,
            JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.authenticationManager = authenticationManager;
        this.jwtService = jwtService;
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

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        String normalizedEmail = request.email().trim().toLowerCase();

        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(normalizedEmail, request.password())
        );

        SecurityUser securityUser = (SecurityUser) authentication.getPrincipal();
        User user = userRepository.findById(securityUser.getId())
            .orElseThrow(() -> new ResourceNotFoundException("User not found with id: " + securityUser.getId()));

        String accessToken = jwtService.generateAccessToken(user);
        long expiresIn = jwtService.getAccessTokenExpirationMs();

        log.info("User logged in successfully with ID: {}", user.getId());
        return AuthResponse.of(accessToken, expiresIn, UserResponse.fromEntity(user));
    }
}
