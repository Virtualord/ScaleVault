package com.aditya.scalevault.user;

import com.aditya.scalevault.BaseIntegrationTest;
import com.aditya.scalevault.dto.ChangePasswordRequest;
import com.aditya.scalevault.dto.LoginRequest;
import com.aditya.scalevault.dto.UpdateProfileRequest;
import com.aditya.scalevault.entity.RefreshToken;
import com.aditya.scalevault.entity.Role;
import com.aditya.scalevault.entity.User;
import com.aditya.scalevault.repository.RefreshTokenRepository;
import com.aditya.scalevault.repository.UserRepository;
import com.aditya.scalevault.security.JwtService;
import com.aditya.scalevault.service.RefreshTokenService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class UserProfileIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private User testUser;
    private String token;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(
            new User("profile.user@scalevault.com", passwordEncoder.encode("Password123!"), Role.USER, true)
        );
        token = jwtService.generateAccessToken(testUser);
    }

    @Test
    @DisplayName("GET /api/v1/users/me should return authenticated user profile")
    void shouldReturnUserProfile() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").value(testUser.getId().toString()))
            .andExpect(jsonPath("$.data.email").value("profile.user@scalevault.com"))
            .andExpect(jsonPath("$.data.role").value("USER"))
            .andExpect(jsonPath("$.data.enabled").value(true));
    }

    @Test
    @DisplayName("GET /api/v1/users/me without token should return 401 UNAUTHORIZED")
    void shouldRejectUnauthenticatedProfileAccess() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me should update allowed profile fields (email)")
    void shouldUpdateProfileEmail() throws Exception {
        UpdateProfileRequest request = new UpdateProfileRequest("updated.email@scalevault.com");

        mockMvc.perform(patch("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.email").value("updated.email@scalevault.com"));

        User updated = userRepository.findById(testUser.getId()).orElseThrow();
        assertThat(updated.getEmail()).isEqualTo("updated.email@scalevault.com");
    }

    @Test
    @DisplayName("PATCH /api/v1/users/me with existing email should return 409 EMAIL_ALREADY_EXISTS")
    void shouldRejectDuplicateEmailOnProfileUpdate() throws Exception {
        userRepository.save(new User("existing@scalevault.com", passwordEncoder.encode("Pass123!"), Role.USER, true));

        UpdateProfileRequest request = new UpdateProfileRequest("existing@scalevault.com");

        mockMvc.perform(patch("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("POST /api/v1/users/me/change-password should change password and invalidate active sessions")
    void shouldChangePasswordAndRevokeSessions() throws Exception {
        // Create an active refresh token session for the user
        refreshTokenService.createRefreshToken(testUser);
        List<RefreshToken> activeTokensBefore = refreshTokenRepository.findAllByUser(testUser);
        assertThat(activeTokensBefore).isNotEmpty();
        assertThat(activeTokensBefore.get(0).isActive()).isTrue();

        ChangePasswordRequest request = new ChangePasswordRequest("Password123!", "NewSecretPassword123!");

        mockMvc.perform(post("/api/v1/users/me/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.message").value("Password changed successfully"));

        // All active refresh tokens must be revoked
        List<RefreshToken> activeTokensAfter = refreshTokenRepository.findAllByUser(testUser);
        assertThat(activeTokensAfter).allMatch(RefreshToken::isRevoked);

        // Verify login works with new password
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(testUser.getEmail(), "NewSecretPassword123!"))))
            .andExpect(status().isOk());

        // Verify login fails with old password
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest(testUser.getEmail(), "Password123!"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/users/me/change-password with invalid current password should return 401 INVALID_CREDENTIALS")
    void shouldRejectInvalidCurrentPassword() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("WrongCurrentPass123!", "NewSecretPassword123!");

        mockMvc.perform(post("/api/v1/users/me/change-password")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }
}
