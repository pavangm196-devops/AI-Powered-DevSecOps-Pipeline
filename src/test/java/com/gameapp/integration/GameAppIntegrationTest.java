package com.gameapp.integration;

import com.gameapp.model.User;
import com.gameapp.repository.UserRepository;
import com.gameapp.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full-stack integration tests using @SpringBootTest with H2 in-memory DB.
 * Tests the complete request/response cycle including Spring Security filters.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GameAppIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserService userService;
    @Autowired private UserRepository userRepository;
    @Autowired private BCryptPasswordEncoder passwordEncoder;

    @BeforeEach
    void cleanDb() {
        userRepository.deleteAll();
    }

    // ===== Registration flow =====

    @Test
    @DisplayName("Full registration: POST /register saves user to DB and redirects to login")
    void integrationTest_Register_SavesUserAndRedirects() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                .param("username",        "integuser")
                .param("email",           "integ@test.com")
                .param("password",        "IntegPass1!")
                .param("confirmPassword", "IntegPass1!")
                .param("firstName",       "Integ")
                .param("lastName",        "User"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?registered=true"));

        // Verify persisted in database
        assertThat(userRepository.existsByUsername("integuser")).isTrue();
        User saved = userRepository.findByUsername("integuser").orElseThrow();
        assertThat(saved.getEmail()).isEqualTo("integ@test.com");
        assertThat(saved.getFirstName()).isEqualTo("Integ");
        assertThat(passwordEncoder.matches("IntegPass1!", saved.getPassword())).isTrue();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("Registration with duplicate username returns error and does NOT persist")
    void integrationTest_Register_DuplicateUsername_NoSave() throws Exception {
        // Pre-create user
        userService.registerUser("existing", "exist@test.com", "pass123", "E", "U");
        long countBefore = userRepository.count();

        mockMvc.perform(post("/register").with(csrf())
                .param("username",        "existing")
                .param("email",           "new@test.com")
                .param("password",        "pass123")
                .param("confirmPassword", "pass123"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeExists("error"));

        assertThat(userRepository.count()).isEqualTo(countBefore);
    }

    // ===== Login flow =====

    @Test
    @DisplayName("POST /login with correct credentials redirects to /dashboard")
    void integrationTest_Login_Success_RedirectsToDashboard() throws Exception {
        userService.registerUser("loginuser", "login@test.com", "pass1234", null, null);

        mockMvc.perform(post("/login").with(csrf())
                .param("username", "loginuser")
                .param("password", "pass1234"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    @DisplayName("POST /login with wrong password redirects to /login?error=true")
    void integrationTest_Login_WrongPassword_RedirectsWithError() throws Exception {
        userService.registerUser("wrongpwuser", "wpw@test.com", "correct", null, null);

        mockMvc.perform(post("/login").with(csrf())
                .param("username", "wrongpwuser")
                .param("password", "wrongpassword"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error=true"));
    }

    @Test
    @DisplayName("POST /login with non-existent username redirects with error")
    void integrationTest_Login_UnknownUser_RedirectsWithError() throws Exception {
        mockMvc.perform(post("/login").with(csrf())
                .param("username", "ghostuser")
                .param("password", "anypassword"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?error=true"));
    }

    // ===== Protected routes — unauthenticated =====

    @Test
    @DisplayName("GET /dashboard without auth redirects to login")
    void integrationTest_Dashboard_RequiresAuth() throws Exception {
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("GET /game/snake without auth redirects to login")
    void integrationTest_Snake_RequiresAuth() throws Exception {
        mockMvc.perform(get("/game/snake"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("GET /calculator without auth redirects to login")
    void integrationTest_Calculator_RequiresAuth() throws Exception {
        mockMvc.perform(get("/calculator"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("**/login"));
    }

    // ===== Protected routes — authenticated =====

    @Test
    @DisplayName("GET /dashboard with authenticated user returns 200")
    void integrationTest_Dashboard_Authenticated_Returns200() throws Exception {
        userService.registerUser("dashuser", "dash@test.com", "pass1234", "Dash", "User");

        mockMvc.perform(get("/dashboard").with(user("dashuser").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(view().name("dashboard"));
    }

    @Test
    @DisplayName("GET /game/snake with authenticated user returns 200")
    void integrationTest_Snake_Authenticated_Returns200() throws Exception {
        userService.registerUser("snakeuser", "snake@test.com", "pass1234", null, null);

        mockMvc.perform(get("/game/snake").with(user("snakeuser").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(view().name("snake"));
    }

    @Test
    @DisplayName("GET /calculator with authenticated user returns 200")
    void integrationTest_Calculator_Authenticated_Returns200() throws Exception {
        userService.registerUser("calcuser", "calc@test.com", "pass1234", null, null);

        mockMvc.perform(get("/calculator").with(user("calcuser").roles("USER")))
            .andExpect(status().isOk())
            .andExpect(view().name("calculator"));
    }

    // ===== Logout =====

    @Test
    @DisplayName("POST /logout invalidates session and redirects to /login?logout=true")
    void integrationTest_Logout_Redirects() throws Exception {
        mockMvc.perform(post("/logout").with(csrf())
                .with(user("someuser").roles("USER")))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?logout=true"));
    }

    // ===== Multiple users persist independently =====

    @Test
    @DisplayName("Multiple users can be registered and persist independently in DB")
    void integrationTest_MultipleUsers_PersistIndependently() throws Exception {
        userService.registerUser("user1", "u1@t.com", "pass123", "User", "One");
        userService.registerUser("user2", "u2@t.com", "pass123", "User", "Two");
        userService.registerUser("user3", "u3@t.com", "pass123", "User", "Three");

        assertThat(userRepository.count()).isEqualTo(3L);
        assertThat(userRepository.existsByUsername("user1")).isTrue();
        assertThat(userRepository.existsByUsername("user2")).isTrue();
        assertThat(userRepository.existsByUsername("user3")).isTrue();
    }

    // ===== Public routes accessible without auth =====

    @Test
    @DisplayName("GET /login is publicly accessible")
    void integrationTest_LoginPage_PubliclyAccessible() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET /register is publicly accessible")
    void integrationTest_RegisterPage_PubliclyAccessible() throws Exception {
        mockMvc.perform(get("/register"))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("GET / redirects to /login")
    void integrationTest_Root_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }
}
