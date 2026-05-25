package com.gameapp.repository;

import com.gameapp.model.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;

/**
 * Repository layer tests using @DataJpaTest (H2 in-memory DB).
 * Tests JPA queries and custom repository methods.
 */
@DataJpaTest
@ActiveProfiles("test")
class UserRepositoryTest {

    @Autowired
    private UserRepository userRepository;

    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();
        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("encoded_password");
        testUser.setFirstName("Test");
        testUser.setLastName("User");
        testUser = userRepository.save(testUser);
    }

    // ===== Save / Persist =====

    @Test
    @DisplayName("Should save user and auto-generate ID")
    void testSaveUser_GeneratesId() {
        User user = new User();
        user.setUsername("newuser");
        user.setEmail("new@example.com");
        user.setPassword("password123");

        User saved = userRepository.save(user);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getUsername()).isEqualTo("newuser");
    }

    @Test
    @DisplayName("Should populate createdAt automatically via @PrePersist")
    void testSaveUser_SetsCreatedAt() {
        assertThat(testUser.getCreatedAt()).isNotNull();
    }

    // ===== Find by Username =====

    @Test
    @DisplayName("Should find user by username (case-sensitive)")
    void testFindByUsername_Found() {
        Optional<User> result = userRepository.findByUsername("testuser");
        assertThat(result).isPresent();
        assertThat(result.get().getEmail()).isEqualTo("test@example.com");
    }

    @Test
    @DisplayName("Should return empty when username does not exist")
    void testFindByUsername_NotFound() {
        Optional<User> result = userRepository.findByUsername("nonexistent");
        assertThat(result).isEmpty();
    }

    // ===== Find by Email =====

    @Test
    @DisplayName("Should find user by email")
    void testFindByEmail_Found() {
        Optional<User> result = userRepository.findByEmail("test@example.com");
        assertThat(result).isPresent();
        assertThat(result.get().getUsername()).isEqualTo("testuser");
    }

    @Test
    @DisplayName("Should return empty when email does not exist")
    void testFindByEmail_NotFound() {
        Optional<User> result = userRepository.findByEmail("missing@example.com");
        assertThat(result).isEmpty();
    }

    // ===== Exists checks =====

    @Test
    @DisplayName("existsByUsername returns true for existing username")
    void testExistsByUsername_True() {
        assertThat(userRepository.existsByUsername("testuser")).isTrue();
    }

    @Test
    @DisplayName("existsByUsername returns false for missing username")
    void testExistsByUsername_False() {
        assertThat(userRepository.existsByUsername("ghost")).isFalse();
    }

    @Test
    @DisplayName("existsByEmail returns true for existing email")
    void testExistsByEmail_True() {
        assertThat(userRepository.existsByEmail("test@example.com")).isTrue();
    }

    @Test
    @DisplayName("existsByEmail returns false for missing email")
    void testExistsByEmail_False() {
        assertThat(userRepository.existsByEmail("ghost@example.com")).isFalse();
    }

    // ===== Uniqueness constraints =====

    @Test
    @DisplayName("Should throw when saving user with duplicate username")
    void testSaveUser_DuplicateUsername_ThrowsException() {
        User duplicate = new User();
        duplicate.setUsername("testuser");          // same username
        duplicate.setEmail("different@example.com");
        duplicate.setPassword("pass");

        assertThatThrownBy(() -> {
            userRepository.saveAndFlush(duplicate);
        }).isInstanceOf(Exception.class);
    }

    @Test
    @DisplayName("Should throw when saving user with duplicate email")
    void testSaveUser_DuplicateEmail_ThrowsException() {
        User duplicate = new User();
        duplicate.setUsername("differentuser");
        duplicate.setEmail("test@example.com");    // same email
        duplicate.setPassword("pass");

        assertThatThrownBy(() -> {
            userRepository.saveAndFlush(duplicate);
        }).isInstanceOf(Exception.class);
    }

    // ===== Count =====

    @Test
    @DisplayName("count() should return number of persisted users")
    void testCount() {
        long count = userRepository.count();
        assertThat(count).isEqualTo(1L);
    }

    // ===== Delete =====

    @Test
    @DisplayName("Should delete user by ID")
    void testDeleteById() {
        userRepository.deleteById(testUser.getId());
        assertThat(userRepository.findById(testUser.getId())).isEmpty();
    }

    // ===== findAll =====

    @Test
    @DisplayName("findAll should return all saved users")
    void testFindAll() {
        User u2 = new User("user2", "u2@e.com", "pass");
        User u3 = new User("user3", "u3@e.com", "pass");
        userRepository.save(u2);
        userRepository.save(u3);

        assertThat(userRepository.findAll()).hasSize(3);
    }

    // ===== User model helpers =====

    @Test
    @DisplayName("getFullName returns firstName + lastName when both set")
    void testGetFullName_BothNames() {
        assertThat(testUser.getFullName()).isEqualTo("Test User");
    }

    @Test
    @DisplayName("getFullName returns username when names are null")
    void testGetFullName_NullNames() {
        User u = new User("onlyuser", "o@e.com", "pass");
        assertThat(u.getFullName()).isEqualTo("onlyuser");
    }
}
