package com.gameapp.service;

import com.gameapp.model.User;
import com.gameapp.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for UserService — all dependencies mocked with Mockito.
 * Uses PasswordEncoder interface (not BCryptPasswordEncoder) to avoid
 * Mockito inline-mocking restrictions on newer JVMs.
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;   // mock the interface, not the concrete class

    @InjectMocks
    private UserService userService;

    private User sampleUser;

    @BeforeEach
    void setUp() {
        sampleUser = new User();
        sampleUser.setId(1L);
        sampleUser.setUsername("john");
        sampleUser.setEmail("john@example.com");
        sampleUser.setPassword("$2a$12$encodedPassword");
        sampleUser.setFirstName("John");
        sampleUser.setLastName("Doe");
    }

    // ===== loadUserByUsername =====

    @Test
    @DisplayName("loadUserByUsername returns UserDetails for existing user")
    void loadUserByUsername_Success() {
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(sampleUser));

        UserDetails details = userService.loadUserByUsername("john");

        assertThat(details.getUsername()).isEqualTo("john");
        assertThat(details.getPassword()).isEqualTo("$2a$12$encodedPassword");
        assertThat(details.getAuthorities()).hasSize(1);
        assertThat(details.getAuthorities().iterator().next().getAuthority()).isEqualTo("ROLE_USER");
    }

    @Test
    @DisplayName("loadUserByUsername throws UsernameNotFoundException for unknown user")
    void loadUserByUsername_UserNotFound_Throws() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> userService.loadUserByUsername("ghost"))
            .isInstanceOf(UsernameNotFoundException.class)
            .hasMessageContaining("ghost");
    }

    // ===== registerUser — happy path =====

    @Test
    @DisplayName("registerUser saves and returns new user successfully")
    void registerUser_Success() {
        when(userRepository.existsByUsername("john")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("plainPwd")).thenReturn("$2a$12$encoded");
        when(userRepository.save(any(User.class))).thenReturn(sampleUser);

        User result = userService.registerUser("john", "john@example.com", "plainPwd", "John", "Doe");

        assertThat(result).isNotNull();
        assertThat(result.getUsername()).isEqualTo("john");
        verify(userRepository).save(any(User.class));
        verify(passwordEncoder).encode("plainPwd");
    }

    @Test
    @DisplayName("registerUser stores encoded password, not plain text")
    void registerUser_PasswordIsEncoded() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode("secret")).thenReturn("$2a$12$hashed");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.registerUser("mary", "mary@e.com", "secret", null, null);

        assertThat(result.getPassword()).isEqualTo("$2a$12$hashed");
        assertThat(result.getPassword()).doesNotContain("secret");
    }

    @Test
    @DisplayName("registerUser normalises email to lowercase")
    void registerUser_EmailLowercased() {
        when(userRepository.existsByUsername(anyString())).thenReturn(false);
        when(userRepository.existsByEmail(anyString())).thenReturn(false);
        when(passwordEncoder.encode(any())).thenReturn("encoded");
        when(userRepository.save(any(User.class))).thenAnswer(inv -> inv.getArgument(0));

        User result = userService.registerUser("alice", "ALICE@EXAMPLE.COM", "pass", "Alice", null);

        assertThat(result.getEmail()).isEqualTo("alice@example.com");
    }

    // ===== registerUser — duplicate username =====

    @Test
    @DisplayName("registerUser throws IllegalArgumentException for duplicate username")
    void registerUser_DuplicateUsername_Throws() {
        when(userRepository.existsByUsername("john")).thenReturn(true);

        assertThatThrownBy(() ->
            userService.registerUser("john", "other@e.com", "pass", null, null)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("john");

        verify(userRepository, never()).save(any());
    }

    // ===== registerUser — duplicate email =====

    @Test
    @DisplayName("registerUser throws IllegalArgumentException for duplicate email")
    void registerUser_DuplicateEmail_Throws() {
        when(userRepository.existsByUsername("newuser")).thenReturn(false);
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() ->
            userService.registerUser("newuser", "john@example.com", "pass", null, null)
        ).isInstanceOf(IllegalArgumentException.class)
         .hasMessageContaining("john@example.com");

        verify(userRepository, never()).save(any());
    }

    // ===== findByUsername =====

    @Test
    @DisplayName("findByUsername returns Optional<User> when present")
    void findByUsername_Found() {
        when(userRepository.findByUsername("john")).thenReturn(Optional.of(sampleUser));
        assertThat(userService.findByUsername("john")).contains(sampleUser);
    }

    @Test
    @DisplayName("findByUsername returns empty Optional when absent")
    void findByUsername_Empty() {
        when(userRepository.findByUsername("ghost")).thenReturn(Optional.empty());
        assertThat(userService.findByUsername("ghost")).isEmpty();
    }

    // ===== findByEmail =====

    @Test
    @DisplayName("findByEmail returns Optional<User> when present")
    void findByEmail_Found() {
        when(userRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser));
        assertThat(userService.findByEmail("john@example.com")).contains(sampleUser);
    }

    // ===== usernameExists / emailExists =====

    @Test
    @DisplayName("usernameExists returns true for existing username")
    void usernameExists_True() {
        when(userRepository.existsByUsername("john")).thenReturn(true);
        assertThat(userService.usernameExists("john")).isTrue();
    }

    @Test
    @DisplayName("usernameExists returns false for non-existing username")
    void usernameExists_False() {
        when(userRepository.existsByUsername("nobody")).thenReturn(false);
        assertThat(userService.usernameExists("nobody")).isFalse();
    }

    @Test
    @DisplayName("emailExists returns true for existing email")
    void emailExists_True() {
        when(userRepository.existsByEmail("john@example.com")).thenReturn(true);
        assertThat(userService.emailExists("john@example.com")).isTrue();
    }

    // ===== getTotalUserCount =====

    @Test
    @DisplayName("getTotalUserCount delegates to repository count()")
    void getTotalUserCount() {
        when(userRepository.count()).thenReturn(42L);
        assertThat(userService.getTotalUserCount()).isEqualTo(42L);
    }

    // ===== recordLogin =====

    @Test
    @DisplayName("recordLogin calls updateLastLogin on repository")
    void recordLogin_CallsRepository() {
        userService.recordLogin("john");
        verify(userRepository).updateLastLogin(eq("john"), any());
    }
}
