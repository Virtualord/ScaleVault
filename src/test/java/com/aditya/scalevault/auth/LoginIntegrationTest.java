package com.aditya.scalevault.auth;

import com.aditya.scalevault.BaseIntegrationTest;
import com.aditya.scalevault.dto.LoginRequest;
import com.aditya.scalevault.entity.Role;
import com.aditya.scalevault.entity.User;
import com.aditya.scalevault.repository.UserRepository;
import com.aditya.scalevault.security.JwtService;
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
class LoginIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with valid credentials should return 200 OK and JWT access token")
    void shouldLoginSuccessfully() throws Exception {
        String email = "login.test@scalevault.com";
        String password = "CorrectPassword123!";
        User user = userRepository.save(new User(email, passwordEncoder.encode(password), Role.USER, true));

        LoginRequest request = new LoginRequest(email, password);

        String responseContent = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accessToken").isString())
            .andExpect(jsonPath("$.data.refreshToken").isString())
            .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.data.expiresIn").isNumber())
            .andExpect(jsonPath("$.data.user.id").value(user.getId().toString()))
            .andExpect(jsonPath("$.data.user.email").value(email))
            .andExpect(jsonPath("$.data.user.role").value("USER"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        // Verify the generated JWT is valid and has correct claims
        String accessToken = objectMapper.readTree(responseContent).path("data").path("accessToken").asText();
        assertThat(jwtService.isTokenValid(accessToken)).isTrue();
        assertThat(jwtService.extractUserId(accessToken)).isEqualTo(user.getId());
        assertThat(jwtService.extractEmail(accessToken)).isEqualTo(email);
        assertThat(jwtService.extractRole(accessToken)).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with incorrect password should return 401 INVALID_CREDENTIALS")
    void shouldRejectInvalidPassword() throws Exception {
        String email = "wrong.password@scalevault.com";
        userRepository.save(new User(email, passwordEncoder.encode("CorrectPassword123!"), Role.USER, true));

        LoginRequest request = new LoginRequest(email, "WrongPassword999!");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with non-existent email should return 401 INVALID_CREDENTIALS")
    void shouldRejectNonExistentUser() throws Exception {
        LoginRequest request = new LoginRequest("doesnotexist@scalevault.com", "Password123!");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with disabled account should return 403 ACCOUNT_DISABLED")
    void shouldRejectDisabledAccount() throws Exception {
        String email = "disabled.user@scalevault.com";
        String password = "Password123!";
        userRepository.save(new User(email, passwordEncoder.encode(password), Role.USER, false));

        LoginRequest request = new LoginRequest(email, password);

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/login with invalid payload should return 400 VALIDATION_FAILED")
    void shouldRejectInvalidPayload() throws Exception {
        LoginRequest request = new LoginRequest("not-an-email", "");

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
