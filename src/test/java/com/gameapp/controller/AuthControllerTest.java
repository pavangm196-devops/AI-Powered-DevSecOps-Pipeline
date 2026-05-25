package com.gameapp.controller;

import com.gameapp.config.SecurityConfig;
import com.gameapp.service.UserService;
import com.gameapp.model.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller tests for AuthController — uses MockMvc + Spring Security test support.
 * Verifies login page, register page, and registration form submission.
 */
@WebMvcTest(value = AuthController.class,
            excludeAutoConfiguration = {UserDetailsServiceAutoConfiguration.class})
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    // Prevent Spring from instantiating BCryptPasswordEncoder (incompatible with Mockito inline on Java 25+)
    @MockBean
    private PasswordEncoder passwordEncoder;

    // ===== GET /login =====

    @Test
    @DisplayName("GET /login returns 200 and renders login template")
    void getLogin_ReturnsLoginPage() throws Exception {
        mockMvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(view().name("login"));
    }

    @Test
    @DisplayName("GET /login?error=true sets errorMsg attribute")
    void getLogin_WithError_SetsErrorAttribute() throws Exception {
        mockMvc.perform(get("/login").param("error", "true"))
            .andExpect(status().isOk())
            .andExpect(view().name("login"))
            .andExpect(model().attributeExists("errorMsg"));
    }

    @Test
    @DisplayName("GET /login?logout=true sets successMsg attribute")
    void getLogin_WithLogout_SetsSuccessAttribute() throws Exception {
        mockMvc.perform(get("/login").param("logout", "true"))
            .andExpect(status().isOk())
            .andExpect(view().name("login"))
            .andExpect(model().attributeExists("successMsg"));
    }

    @Test
    @DisplayName("GET /login?expired=true sets errorMsg attribute about session")
    void getLogin_WithExpired_SetsErrorAttribute() throws Exception {
        mockMvc.perform(get("/login").param("expired", "true"))
            .andExpect(status().isOk())
            .andExpect(view().name("login"))
            .andExpect(model().attributeExists("errorMsg"));
    }

    @Test
    @DisplayName("GET /login?registered=true sets successMsg attribute")
    void getLogin_WithRegistered_SetsSuccessAttribute() throws Exception {
        mockMvc.perform(get("/login").param("registered", "true"))
            .andExpect(status().isOk())
            .andExpect(view().name("login"))
            .andExpect(model().attributeExists("successMsg"));
    }

    // ===== GET /register =====

    @Test
    @DisplayName("GET /register returns 200 and renders register template")
    void getRegister_ReturnsRegisterPage() throws Exception {
        mockMvc.perform(get("/register"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"));
    }

    // ===== POST /register — success =====

    @Test
    @DisplayName("POST /register with valid data redirects to /login?registered=true")
    void postRegister_ValidData_Redirects() throws Exception {
        User saved = new User("alice", "alice@e.com", "encodedPwd");
        when(userService.registerUser(anyString(), anyString(), anyString(), anyString(), anyString()))
            .thenReturn(saved);

        mockMvc.perform(post("/register").with(csrf())
                .param("username",        "alice")
                .param("email",           "alice@e.com")
                .param("password",        "secret123")
                .param("confirmPassword", "secret123")
                .param("firstName",       "Alice")
                .param("lastName",        "Smith"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login?registered=true"));

        verify(userService).registerUser("alice", "alice@e.com", "secret123", "Alice", "Smith");
    }

    // ===== POST /register — password mismatch =====

    @Test
    @DisplayName("POST /register with mismatched passwords returns register page with error")
    void postRegister_PasswordMismatch_ReturnsError() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                .param("username",        "bob")
                .param("email",           "bob@e.com")
                .param("password",        "abc123")
                .param("confirmPassword", "xyz999"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeExists("error"));

        verify(userService, never()).registerUser(any(), any(), any(), any(), any());
    }

    // ===== POST /register — short password =====

    @Test
    @DisplayName("POST /register with password shorter than 6 chars returns error")
    void postRegister_ShortPassword_ReturnsError() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                .param("username",        "bob")
                .param("email",           "bob@e.com")
                .param("password",        "abc")
                .param("confirmPassword", "abc"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeExists("error"));
    }

    // ===== POST /register — username too short =====

    @Test
    @DisplayName("POST /register with username shorter than 3 chars returns error")
    void postRegister_ShortUsername_ReturnsError() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                .param("username",        "ab")
                .param("email",           "ab@e.com")
                .param("password",        "password123")
                .param("confirmPassword", "password123"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeExists("error"));
    }

    // ===== POST /register — duplicate username from service =====

    @Test
    @DisplayName("POST /register with duplicate username shows service error on register page")
    void postRegister_DuplicateUsername_ShowsError() throws Exception {
        when(userService.registerUser(anyString(), anyString(), anyString(), any(), any()))
            .thenThrow(new IllegalArgumentException("Username 'carol' is already taken."));

        mockMvc.perform(post("/register").with(csrf())
                .param("username",        "carol")
                .param("email",           "carol@e.com")
                .param("password",        "password123")
                .param("confirmPassword", "password123"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attribute("error", "Username 'carol' is already taken."));
    }

    // ===== POST /register — blank email =====

    @Test
    @DisplayName("POST /register with blank email returns error")
    void postRegister_BlankEmail_ReturnsError() throws Exception {
        mockMvc.perform(post("/register").with(csrf())
                .param("username",        "validuser")
                .param("email",           "")
                .param("password",        "password123")
                .param("confirmPassword", "password123"))
            .andExpect(status().isOk())
            .andExpect(view().name("register"))
            .andExpect(model().attributeExists("error"));
    }

    // ===== GET / =====

    @Test
    @DisplayName("GET / redirects to /login")
    void getRoot_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrl("/login"));
    }
}
