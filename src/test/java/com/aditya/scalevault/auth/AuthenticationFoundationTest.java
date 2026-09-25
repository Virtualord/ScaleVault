package com.aditya.scalevault.auth;

import com.aditya.scalevault.BaseIntegrationTest;
import com.aditya.scalevault.entity.Role;
import com.aditya.scalevault.entity.User;
import com.aditya.scalevault.repository.UserRepository;
import com.aditya.scalevault.security.CustomUserDetailsService;
import com.aditya.scalevault.security.SecurityUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AuthenticationFoundationTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CustomUserDetailsService userDetailsService;

    @Autowired
    private AuthenticationManager authenticationManager;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("PasswordEncoder should encode passwords with salt and verify matches correctly")
    void shouldEncodeAndVerifyPasswords() {
        String rawPassword = "StrongPassword123!";
        String hash1 = passwordEncoder.encode(rawPassword);
        String hash2 = passwordEncoder.encode(rawPassword);

        // BCrypt uses random salts, so distinct hashes are generated for identical plaintext
        assertThat(hash1).isNotEqualTo(hash2);
        assertThat(passwordEncoder.matches(rawPassword, hash1)).isTrue();
        assertThat(passwordEncoder.matches(rawPassword, hash2)).isTrue();
        assertThat(passwordEncoder.matches("WrongPassword", hash1)).isFalse();
    }

    @Test
    @DisplayName("CustomUserDetailsService should load user by email case-insensitively and map authorities")
    void shouldLoadUserByEmail() {
        String email = "test.user@scalevault.com";
        String password = "Password123!";
        User user = new User(email, passwordEncoder.encode(password), Role.USER, true);
        userRepository.save(user);

        UserDetails userDetails = userDetailsService.loadUserByUsername("TEST.USER@SCALEVAULT.COM");
        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo(email);
        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.getAuthorities())
            .extracting("authority")
            .containsExactly("ROLE_USER");
        assertThat(userDetails).isInstanceOf(SecurityUser.class);
        assertThat(((SecurityUser) userDetails).getId()).isEqualTo(user.getId());
        assertThat(((SecurityUser) userDetails).getRole()).isEqualTo(Role.USER);
    }

    @Test
    @DisplayName("CustomUserDetailsService should throw UsernameNotFoundException for unknown email")
    void shouldThrowExceptionForUnknownUser() {
        assertThatThrownBy(() -> userDetailsService.loadUserByUsername("unknown@scalevault.com"))
            .isInstanceOf(UsernameNotFoundException.class)
            .hasMessageContaining("User not found with email");
    }

    @Test
    @DisplayName("AuthenticationManager should authenticate valid credentials successfully")
    void shouldAuthenticateValidCredentials() {
        String email = "auth.success@scalevault.com";
        String rawPassword = "ValidPassword123!";
        userRepository.save(new User(email, passwordEncoder.encode(rawPassword), Role.USER, true));

        Authentication authentication = authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(email, rawPassword)
        );

        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal()).isInstanceOf(SecurityUser.class);
        SecurityUser principal = (SecurityUser) authentication.getPrincipal();
        assertThat(principal.getUsername()).isEqualTo(email);
    }

    @Test
    @DisplayName("AuthenticationManager should throw BadCredentialsException on invalid password")
    void shouldFailOnInvalidPassword() {
        String email = "auth.fail@scalevault.com";
        userRepository.save(new User(email, passwordEncoder.encode("CorrectPassword123!"), Role.USER, true));

        assertThatThrownBy(() -> authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(email, "WrongPassword123!")
        )).isInstanceOf(BadCredentialsException.class);
    }

    @Test
    @DisplayName("AuthenticationManager should throw DisabledException when account is disabled")
    void shouldFailWhenAccountIsDisabled() {
        String email = "disabled@scalevault.com";
        String password = "ValidPassword123!";
        userRepository.save(new User(email, passwordEncoder.encode(password), Role.USER, false));

        assertThatThrownBy(() -> authenticationManager.authenticate(
            new UsernamePasswordAuthenticationToken(email, password)
        )).isInstanceOf(DisabledException.class);
    }
}
