package com.aditya.scalevault.auth;

import com.aditya.scalevault.BaseIntegrationTest;
import com.aditya.scalevault.dto.LoginRequest;
import com.aditya.scalevault.dto.LogoutRequest;
import com.aditya.scalevault.dto.RefreshTokenRequest;
import com.aditya.scalevault.entity.RefreshToken;
import com.aditya.scalevault.entity.Role;
import com.aditya.scalevault.entity.User;
import com.aditya.scalevault.repository.RefreshTokenRepository;
import com.aditya.scalevault.repository.UserRepository;
import com.aditya.scalevault.service.RefreshTokenService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class LogoutIntegrationTest extends BaseIntegrationTest {

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
    @DisplayName("POST /api/v1/auth/logout should revoke refresh token and terminate session")
    void shouldLogoutAndRevokeTokenSuccessfully() throws Exception {
        String email = "logout.test@scalevault.com";
        String password = "Password123!";
        User user = userRepository.save(new User(email, passwordEncoder.encode(password), Role.USER, true));

        // 1. Login to obtain tokens
        LoginRequest loginRequest = new LoginRequest(email, password);
        String loginResponseStr = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        String refreshToken = objectMapper.readTree(loginResponseStr).path("data").path("refreshToken").asText();
        assertThat(refreshToken).isNotBlank();

        // 2. Perform Logout
        LogoutRequest logoutRequest = new LogoutRequest(refreshToken);
        mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logoutRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Logged out successfully"));

        // 3. Verify in database that the token is marked revoked
        String tokenHash = RefreshTokenService.hashToken(refreshToken);
        RefreshToken tokenEntity = refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow();
        assertThat(tokenEntity.isRevoked()).isTrue();

        // 4. Verify that attempting to use this refresh token now fails
        mockMvc.perform(post("/api/v1/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout with blank token should return 400 Bad Request")
    void shouldRejectBlankTokenOnLogout() throws Exception {
        LogoutRequest logoutRequest = new LogoutRequest("");
        mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logoutRequest)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/logout with unknown token should complete safely")
    void shouldHandleUnknownTokenGracefully() throws Exception {
        LogoutRequest logoutRequest = new LogoutRequest("non-existent-token-value");
        mockMvc.perform(post("/api/v1/auth/logout")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(logoutRequest)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true));
    }
}
