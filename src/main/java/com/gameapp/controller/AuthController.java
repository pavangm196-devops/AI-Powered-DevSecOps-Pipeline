package com.gameapp.controller;

import com.gameapp.service.UserService;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class AuthController {

    @Autowired
    private UserService userService;

    @GetMapping("/")
    public String home() {
        return "redirect:/login";
    }

    @GetMapping("/login")
    public String loginPage(
            @RequestParam(required = false) String error,
            @RequestParam(required = false) String logout,
            @RequestParam(required = false) String expired,
            @RequestParam(required = false) String registered,
            Model model) {

        if (error != null) {
            model.addAttribute("errorMsg", "Invalid username or password. Please try again.");
        }
        if (logout != null) {
            model.addAttribute("successMsg", "You have been successfully logged out.");
        }
        if (expired != null) {
            model.addAttribute("errorMsg", "Your session has expired. Please log in again.");
        }
        if (registered != null) {
            model.addAttribute("successMsg", "Account created! Welcome — please log in.");
        }
        return "login";
    }

    @GetMapping("/register")
    public String registerPage(Model model) {
        return "register";
    }

    @PostMapping("/register")
    public String registerUser(
            @RequestParam String username,
            @RequestParam String email,
            @RequestParam String password,
            @RequestParam String confirmPassword,
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            Model model,
            RedirectAttributes redirectAttributes) {

        // Validate required fields
        if (username == null || username.isBlank()) {
            model.addAttribute("error", "Username is required.");
            return "register";
        }
        if (username.length() < 3 || username.length() > 50) {
            model.addAttribute("error", "Username must be between 3 and 50 characters.");
            return "register";
        }
        if (email == null || email.isBlank()) {
            model.addAttribute("error", "Email is required.");
            return "register";
        }
        if (password == null || password.length() < 6) {
            model.addAttribute("error", "Password must be at least 6 characters.");
            return "register";
        }
        if (!password.equals(confirmPassword)) {
            model.addAttribute("error", "Passwords do not match.");
            model.addAttribute("username", username);
            model.addAttribute("email", email);
            model.addAttribute("firstName", firstName);
            model.addAttribute("lastName", lastName);
            return "register";
        }

        try {
            userService.registerUser(username, email, password, firstName, lastName);
            redirectAttributes.addFlashAttribute("successMsg",
                "Account created successfully! Please log in.");
            return "redirect:/login?registered=true";
        } catch (IllegalArgumentException e) {
            model.addAttribute("error", e.getMessage());
            model.addAttribute("username", username);
            model.addAttribute("email", email);
            model.addAttribute("firstName", firstName);
            model.addAttribute("lastName", lastName);
            return "register";
        }
    }
}
