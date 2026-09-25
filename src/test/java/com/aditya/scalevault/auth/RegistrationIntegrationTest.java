package com.aditya.scalevault.auth;

import com.aditya.scalevault.BaseIntegrationTest;
import com.aditya.scalevault.dto.RegisterRequest;
import com.aditya.scalevault.entity.Role;
import com.aditya.scalevault.repository.UserRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class RegistrationIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("POST /api/v1/auth/register should create a USER account and return 201")
    void shouldRegisterUserSuccessfully() throws Exception {
        RegisterRequest request = new RegisterRequest("newuser@scalevault.com", "SecurePassword123!");

        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.id").isNotEmpty())
            .andExpect(jsonPath("$.data.email").value("newuser@scalevault.com"))
            .andExpect(jsonPath("$.data.role").value(Role.USER.name()))
            .andExpect(jsonPath("$.data.enabled").value(true))
            .andExpect(jsonPath("$.data.password").doesNotExist())
            .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        assertThat(userRepository.findByEmail("newuser@scalevault.com")).isPresent();
    }

    @Test
    @DisplayName("POST /api/v1/auth/register with duplicate email should return 409 Conflict")
    void shouldRejectDuplicateEmailRegistration() throws Exception {
        RegisterRequest request1 = new RegisterRequest("duplicate@scalevault.com", "SecurePassword123!");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request1)))
            .andExpect(status().isCreated());

        RegisterRequest request2 = new RegisterRequest("DUPLICATE@scalevault.com", "AnotherPassword123!");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    @DisplayName("POST /api/v1/auth/register with invalid data should return 400 Bad Request")
    void shouldRejectInvalidRegistrationData() throws Exception {
        RegisterRequest invalidEmail = new RegisterRequest("invalid-email", "SecurePassword123!");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidEmail)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));

        RegisterRequest shortPassword = new RegisterRequest("valid@scalevault.com", "short");
        mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(shortPassword)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }
}
