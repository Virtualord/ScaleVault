package com.aditya.scalevault.auth;

import com.aditya.scalevault.BaseIntegrationTest;
import com.aditya.scalevault.dto.LoginRequest;
import com.aditya.scalevault.dto.RefreshTokenRequest;
import com.aditya.scalevault.entity.RefreshToken;
import com.aditya.scalevault.entity.Role;
import com.aditya.scalevault.entity.User;
import com.aditya.scalevault.repository.RefreshTokenRepository;
import com.aditya.scalevault.repository.UserRepository;
import com.aditya.scalevault.service.RefreshTokenService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RefreshTokenIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh should rotate refresh token and issue new access token")
    void shouldRotateRefreshTokenSuccessfully() throws Exception {
        String email = "refresh.user@scalevault.com";
        String password = "Password123!";
        User user = userRepository.save(new User(email, passwordEncoder.encode(password), Role.USER, true));

        // 1. Initial Login
        LoginRequest loginRequest = new LoginRequest(email, password);
        String loginResponseStr = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode loginJson = objectMapper.readTree(loginResponseStr).path("data");
        String initialAccessToken = loginJson.path("accessToken").asText();
        String initialRefreshToken = loginJson.path("refreshToken").asText();

        assertThat(initialAccessToken).isNotBlank();
        assertThat(initialRefreshToken).isNotBlank();

        // 2. Perform Token Refresh
        RefreshTokenRequest refreshRequest = new RefreshTokenRequest(initialRefreshToken);
        String refreshResponseStr = mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refreshRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").isString())
            .andExpect(jsonPath("$.data.refreshToken").isString())
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode refreshJson = objectMapper.readTree(refreshResponseStr).path("data");
        String newAccessToken = refreshJson.path("accessToken").asText();
        String newRefreshToken = refreshJson.path("refreshToken").asText();

        // Ensure newly issued tokens are different from initial ones (rotation verified)
        assertThat(newRefreshToken).isNotEqualTo(initialRefreshToken);

        // Verify in database: initial token should be revoked and replacedBy the new token's hash
        String initialTokenHash = RefreshTokenService.hashToken(initialRefreshToken);
        RefreshToken oldTokenEntity = refreshTokenRepository.findByTokenHash(initialTokenHash).orElseThrow();
        assertThat(oldTokenEntity.isRevoked()).isTrue();
        assertThat(oldTokenEntity.getReplacedBy()).isEqualTo(RefreshTokenService.hashToken(newRefreshToken));

        // Verify new token entity is active
        String newTokenHash = RefreshTokenService.hashToken(newRefreshToken);
        RefreshToken newTokenEntity = refreshTokenRepository.findByTokenHash(newTokenHash).orElseThrow();
        assertThat(newTokenEntity.isActive()).isTrue();
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh on revoked token should trigger reuse detection and revoke all user sessions")
    void shouldDetectTokenReuseAndRevokeAllSessions() throws Exception {
        String email = "reuse.detection@scalevault.com";
        String password = "Password123!";
        User user = userRepository.save(new User(email, passwordEncoder.encode(password), Role.USER, true));

        // 1. Initial Login
        LoginRequest loginRequest = new LoginRequest(email, password);
        String loginResponseStr = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String token1 = objectMapper.readTree(loginResponseStr).path("data").path("refreshToken").asText();

        // 2. Legitimate rotation: token1 -> token2
        String refresh1Response = mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest(token1))))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String token2 = objectMapper.readTree(refresh1Response).path("data").path("refreshToken").asText();

        // 3. Attack scenario: Malicious actor (or stale client) attempts to reuse the already-revoked token1!
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest(token1))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));

        // 4. Verification: All active tokens for the user (including token2) MUST be revoked!
        List<RefreshToken> userTokens = refreshTokenRepository.findAllByUser(user);
        assertThat(userTokens).isNotEmpty();
        assertThat(userTokens).allMatch(RefreshToken::isRevoked);

        // 5. Consequently, even the legitimate token2 is now rejected
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest(token2))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh with expired token should return 401 INVALID_TOKEN")
    void shouldRejectExpiredRefreshToken() throws Exception {
        User user = userRepository.save(new User("expired.token@scalevault.com", passwordEncoder.encode("Pass123!"), Role.USER, true));

        String rawToken = "expired-token-uuid-12345";
        String tokenHash = RefreshTokenService.hashToken(rawToken);
        RefreshToken expiredToken = new RefreshToken(user, tokenHash, Instant.now().minus(2, ChronoUnit.DAYS));
        refreshTokenRepository.save(expiredToken);

        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest(rawToken))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/refresh with invalid token should return 401 INVALID_TOKEN")
    void shouldRejectNonExistentRefreshToken() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest("non-existent-token"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }
}
