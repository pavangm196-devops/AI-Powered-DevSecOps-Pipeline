package com.gameapp.controller;

import com.gameapp.config.SecurityConfig;
import com.gameapp.model.User;
import com.gameapp.service.UserService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Tests for DashboardController — verifies protected routes, session state,
 * and proper model attributes for authenticated users.
 */
@WebMvcTest(value = DashboardController.class,
            excludeAutoConfiguration = {UserDetailsServiceAutoConfiguration.class})
@Import(SecurityConfig.class)
@ActiveProfiles("test")
class DashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserService userService;

    // Prevent Spring from instantiating BCryptPasswordEncoder (incompatible with Mockito inline on Java 25+)
    @MockBean
    private PasswordEncoder passwordEncoder;

    // ===== GET /dashboard — unauthenticated =====

    @Test
    @DisplayName("GET /dashboard without auth redirects to login")
    void getDashboard_Unauthenticated_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/dashboard"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("**/login"));
    }

    // ===== GET /dashboard — authenticated =====

    @Test
    @WithMockUser(username = "alice")
    @DisplayName("GET /dashboard with auth returns 200 and dashboard view")
    void getDashboard_Authenticated_ReturnsView() throws Exception {
        User alice = new User("alice", "alice@e.com", "enc");
        alice.setFirstName("Alice");
        alice.setLastName("Wonder");

        when(userService.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(userService.getTotalUserCount()).thenReturn(5L);

        mockMvc.perform(get("/dashboard"))
            .andExpect(status().isOk())
            .andExpect(view().name("dashboard"))
            .andExpect(model().attribute("username", "alice"))
            .andExpect(model().attribute("totalUsers", 5L));
    }

    @Test
    @WithMockUser(username = "bob")
    @DisplayName("GET /dashboard sets displayName from full name when available")
    void getDashboard_SetsDisplayName() throws Exception {
        User bob = new User("bob", "bob@e.com", "enc");
        bob.setFirstName("Bob");
        bob.setLastName("Builder");

        when(userService.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(userService.getTotalUserCount()).thenReturn(1L);

        mockMvc.perform(get("/dashboard"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("displayName", "Bob Builder"));
    }

    @Test
    @WithMockUser(username = "noname")
    @DisplayName("GET /dashboard handles user with no first/last name")
    void getDashboard_UserWithNoName() throws Exception {
        User noname = new User("noname", "n@e.com", "enc");
        // No first/last name set

        when(userService.findByUsername("noname")).thenReturn(Optional.of(noname));
        when(userService.getTotalUserCount()).thenReturn(1L);

        mockMvc.perform(get("/dashboard"))
            .andExpect(status().isOk())
            .andExpect(view().name("dashboard"));
    }

    // ===== GET /game/snake — unauthenticated =====

    @Test
    @DisplayName("GET /game/snake without auth redirects to login")
    void getSnake_Unauthenticated_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/game/snake"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("**/login"));
    }

    // ===== GET /game/snake — authenticated =====

    @Test
    @WithMockUser(username = "player1")
    @DisplayName("GET /game/snake with auth returns snake view")
    void getSnake_Authenticated_ReturnsView() throws Exception {
        User player = new User("player1", "p1@e.com", "enc");
        when(userService.findByUsername("player1")).thenReturn(Optional.of(player));

        mockMvc.perform(get("/game/snake"))
            .andExpect(status().isOk())
            .andExpect(view().name("snake"))
            .andExpect(model().attribute("username", "player1"));
    }

    @Test
    @WithMockUser(username = "gamer")
    @DisplayName("GET /game/snake sets displayName model attribute")
    void getSnake_SetsDisplayName() throws Exception {
        User gamer = new User("gamer", "g@e.com", "enc");
        gamer.setFirstName("Gamer");
        gamer.setLastName("One");
        when(userService.findByUsername("gamer")).thenReturn(Optional.of(gamer));

        mockMvc.perform(get("/game/snake"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("displayName", "Gamer One"));
    }

    // ===== GET /calculator — unauthenticated =====

    @Test
    @DisplayName("GET /calculator without auth redirects to login")
    void getCalculator_Unauthenticated_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/calculator"))
            .andExpect(status().is3xxRedirection())
            .andExpect(redirectedUrlPattern("**/login"));
    }

    // ===== GET /calculator — authenticated =====

    @Test
    @WithMockUser(username = "mathguy")
    @DisplayName("GET /calculator with auth returns calculator view")
    void getCalculator_Authenticated_ReturnsView() throws Exception {
        User mg = new User("mathguy", "mg@e.com", "enc");
        when(userService.findByUsername("mathguy")).thenReturn(Optional.of(mg));

        mockMvc.perform(get("/calculator"))
            .andExpect(status().isOk())
            .andExpect(view().name("calculator"))
            .andExpect(model().attribute("username", "mathguy"));
    }

    @Test
    @WithMockUser(username = "mathpro")
    @DisplayName("GET /calculator with named user sets displayName")
    void getCalculator_SetsDisplayName() throws Exception {
        User mp = new User("mathpro", "mp@e.com", "enc");
        mp.setFirstName("Math");
        mp.setLastName("Pro");
        when(userService.findByUsername("mathpro")).thenReturn(Optional.of(mp));

        mockMvc.perform(get("/calculator"))
            .andExpect(status().isOk())
            .andExpect(model().attribute("displayName", "Math Pro"));
    }

    // ===== Session state: different users independent =====

    @Test
    @WithMockUser(username = "user_a")
    @DisplayName("Each authenticated user sees their own username in model")
    void getDashboard_MultipleUsers_OwnUsername() throws Exception {
        User userA = new User("user_a", "a@e.com", "enc");
        when(userService.findByUsername("user_a")).thenReturn(Optional.of(userA));
        when(userService.getTotalUserCount()).thenReturn(2L);

        mockMvc.perform(get("/dashboard"))
            .andExpect(model().attribute("username", "user_a"));
    }
}
