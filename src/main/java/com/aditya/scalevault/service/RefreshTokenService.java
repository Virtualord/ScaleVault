package com.aditya.scalevault.service;

import com.aditya.scalevault.dto.AuthResponse;
import com.aditya.scalevault.dto.UserResponse;
import com.aditya.scalevault.entity.RefreshToken;
import com.aditya.scalevault.entity.User;
import com.aditya.scalevault.exception.InvalidTokenException;
import com.aditya.scalevault.repository.RefreshTokenRepository;
import com.aditya.scalevault.security.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

@Service
public class RefreshTokenService {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtService jwtService;
    private final long refreshTokenExpirationMs;

    public RefreshTokenService(
            RefreshTokenRepository refreshTokenRepository,
            JwtService jwtService,
            @Value("${scalevault.jwt.refresh-token-expiration-ms:604800000}") long refreshTokenExpirationMs) {
        this.refreshTokenRepository = refreshTokenRepository;
        this.jwtService = jwtService;
        this.refreshTokenExpirationMs = refreshTokenExpirationMs;
    }

    @Transactional
    public String createRefreshToken(User user) {
        String rawToken = generateRawToken();
        String tokenHash = hashToken(rawToken);

        Instant expiresAt = Instant.now().plusMillis(refreshTokenExpirationMs);
        RefreshToken refreshToken = new RefreshToken(user, tokenHash, expiresAt);
        refreshTokenRepository.save(refreshToken);

        log.debug("Created refresh token for user ID: {}", user.getId());
        return rawToken;
    }

    @Transactional(noRollbackFor = InvalidTokenException.class)
    public AuthResponse rotateRefreshToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw new InvalidTokenException("Refresh token cannot be blank");
        }

        String tokenHash = hashToken(rawRefreshToken.trim());
        RefreshToken currentToken = refreshTokenRepository.findByTokenHash(tokenHash)
            .orElseThrow(() -> new InvalidTokenException("Invalid refresh token"));

        User user = currentToken.getUser();

        // 1. Detect Token Reuse
        if (currentToken.isRevoked()) {
            refreshTokenRepository.revokeAllActiveByUserId(user.getId(), Instant.now());
            log.warn("SECURITY ALERT: Revoked refresh token reuse detected for user ID: {}. Revoked all active user sessions.", user.getId());
            throw new InvalidTokenException("Invalid refresh token: token has already been revoked. All user sessions have been terminated.");
        }

        // 2. Validate Expiration
        if (currentToken.isExpired()) {
            log.warn("Refresh token expired for user ID: {}", user.getId());
            throw new InvalidTokenException("Refresh token has expired");
        }

        // 3. Validate Account State
        if (!user.isEnabled()) {
            log.warn("Refresh rejected: user account is disabled for user ID: {}", user.getId());
            throw new InvalidTokenException("User account is disabled");
        }

        // 4. Rotate: Revoke Current and Issue Replacement
        String newRawRefreshToken = generateRawToken();
        String newTokenHash = hashToken(newRawRefreshToken);

        currentToken.revoke(newTokenHash);
        refreshTokenRepository.save(currentToken);

        Instant newExpiresAt = Instant.now().plusMillis(refreshTokenExpirationMs);
        RefreshToken newRefreshToken = new RefreshToken(user, newTokenHash, newExpiresAt);
        refreshTokenRepository.save(newRefreshToken);

        String newAccessToken = jwtService.generateAccessToken(user);
        long expiresIn = jwtService.getAccessTokenExpirationMs();

        log.info("Successfully rotated refresh token for user ID: {}", user.getId());
        return AuthResponse.of(newAccessToken, newRawRefreshToken, expiresIn, UserResponse.fromEntity(user));
    }

    @Transactional
    public void revokeToken(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        String tokenHash = hashToken(rawRefreshToken.trim());
        refreshTokenRepository.findByTokenHash(tokenHash).ifPresent(token -> {
            if (!token.isRevoked()) {
                token.revoke();
                refreshTokenRepository.save(token);
                log.info("Revoked refresh token for user ID: {}", token.getUser().getId());
            }
        });
    }

    @Transactional
    public void revokeAllUserTokens(UUID userId) {
        int count = refreshTokenRepository.revokeAllActiveByUserId(userId, Instant.now());
        log.info("Revoked {} active refresh tokens for user ID: {}", count, userId);
    }

    public static String hashToken(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 algorithm not available", e);
        }
    }

    private String generateRawToken() {
        return UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
    }
}
