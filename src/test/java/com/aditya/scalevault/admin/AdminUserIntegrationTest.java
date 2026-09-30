package com.aditya.scalevault.admin;

import com.aditya.scalevault.BaseIntegrationTest;
import com.aditya.scalevault.dto.LoginRequest;
import com.aditya.scalevault.dto.UpdateUserRoleRequest;
import com.aditya.scalevault.dto.UpdateUserStatusRequest;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class AdminUserIntegrationTest extends BaseIntegrationTest {

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

    private User adminUser;
    private User regularUser;
    private String adminToken;
    private String userToken;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        userRepository.deleteAll();

        adminUser = userRepository.save(
            new User("admin@scalevault.com", passwordEncoder.encode("AdminPass123!"), Role.ADMIN, true)
        );
        regularUser = userRepository.save(
            new User("target.user@scalevault.com", passwordEncoder.encode("UserPass123!"), Role.USER, true)
        );

        adminToken = jwtService.generateAccessToken(adminUser);
        userToken = jwtService.generateAccessToken(regularUser);
    }

    @Test
    @DisplayName("GET /api/v1/admin/users should return paginated list of users")
    void shouldReturnPaginatedUsersForAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users?page=0&size=10")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.content").isArray())
            .andExpect(jsonPath("$.data.page").value(0))
            .andExpect(jsonPath("$.data.size").value(10))
            .andExpect(jsonPath("$.data.totalElements").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/admin/users?search=target should filter users by email query")
    void shouldFilterUsersBySearchQuery() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users?search=target")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.content.length()").value(1))
            .andExpect(jsonPath("$.data.content[0].email").value("target.user@scalevault.com"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/users/{id} should return user by ID")
    void shouldGetUserById() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/" + regularUser.getId())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.id").value(regularUser.getId().toString()))
            .andExpect(jsonPath("$.data.email").value("target.user@scalevault.com"))
            .andExpect(jsonPath("$.data.role").value("USER"));
    }

    @Test
    @DisplayName("GET /api/v1/admin/users/{id} with unknown ID should return 404 RESOURCE_NOT_FOUND")
    void shouldReturn404ForUnknownUserId() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users/" + UUID.randomUUID())
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/users/{id}/role should update role to ADMIN")
    void shouldUpdateUserRole() throws Exception {
        UpdateUserRoleRequest request = new UpdateUserRoleRequest(Role.ADMIN);

        mockMvc.perform(patch("/api/v1/admin/users/" + regularUser.getId() + "/role")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.role").value("ADMIN"));

        User updated = userRepository.findById(regularUser.getId()).orElseThrow();
        assertThat(updated.getRole()).isEqualTo(Role.ADMIN);
    }

    @Test
    @DisplayName("PATCH /api/v1/admin/users/{id}/status should disable user and revoke active sessions")
    void shouldDisableUserAndRevokeSessions() throws Exception {
        // Create an active session for the regular user
        refreshTokenService.createRefreshToken(regularUser);
        List<RefreshToken> activeBefore = refreshTokenRepository.findAllByUser(regularUser);
        assertThat(activeBefore).isNotEmpty();
        assertThat(activeBefore.get(0).isActive()).isTrue();

        UpdateUserStatusRequest request = new UpdateUserStatusRequest(false);

        mockMvc.perform(patch("/api/v1/admin/users/" + regularUser.getId() + "/status")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + adminToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.enabled").value(false));

        // Active tokens must be revoked
        List<RefreshToken> activeAfter = refreshTokenRepository.findAllByUser(regularUser);
        assertThat(activeAfter).allMatch(RefreshToken::isRevoked);

        // Subsequent login must be rejected with 403 ACCOUNT_DISABLED
        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new LoginRequest("target.user@scalevault.com", "UserPass123!"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCOUNT_DISABLED"));
    }

    @Test
    @DisplayName("Non-admin user accessing admin endpoints should return 403 ACCESS_DENIED")
    void nonAdminCannotAccessAdminEndpoints() throws Exception {
        mockMvc.perform(get("/api/v1/admin/users")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + userToken)
                .contentType(MediaType.APPLICATION_JSON))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }
}
