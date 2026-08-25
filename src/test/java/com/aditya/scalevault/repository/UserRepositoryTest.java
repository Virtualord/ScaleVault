package com.aditya.scalevault.repository;

import com.aditya.scalevault.BaseIntegrationTest;
import com.aditya.scalevault.entity.Role;
import com.aditya.scalevault.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserRepositoryTest extends BaseIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
    }

    @Test
    @DisplayName("Should persist user and populate UUID and timestamps")
    void shouldPersistUserSuccessfully() {
        User user = new User("dev@scalevault.com", "hashed_password", Role.USER, true);
        User saved = userRepository.saveAndFlush(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getEmail()).isEqualTo("dev@scalevault.com");
        assertThat(saved.getRole()).isEqualTo(Role.USER);
        assertThat(saved.isEnabled()).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Should find user by email and case-insensitive email")
    void shouldFindUserByEmailCaseInsensitive() {
        User user = new User("alice@scalevault.com", "hashed_password", Role.USER, true);
        userRepository.saveAndFlush(user);

        Optional<User> found = userRepository.findByEmailIgnoreCase("ALICE@scalevault.com");
        assertThat(found).isPresent();
        assertThat(found.get().getEmail()).isEqualTo("alice@scalevault.com");

        boolean exists = userRepository.existsByEmailIgnoreCase("ALICE@scalevault.com");
        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("Should enforce unique email constraint at the database layer")
    void shouldRejectDuplicateEmail() {
        User user1 = new User("duplicate@scalevault.com", "hash1", Role.USER, true);
        userRepository.saveAndFlush(user1);

        User user2 = new User("duplicate@scalevault.com", "hash2", Role.USER, true);
        assertThatThrownBy(() -> userRepository.saveAndFlush(user2))
            .isInstanceOf(DataIntegrityViolationException.class);
    }
}
