package com.gameapp.controller;

import com.gameapp.model.User;
import com.gameapp.service.UserService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.security.Principal;

@Controller
public class DashboardController {

    @Autowired
    private UserService userService;

    @GetMapping("/dashboard")
    public String dashboard(Model model, Principal principal) {
        String username = principal.getName();
        model.addAttribute("username", username);

        userService.findByUsername(username).ifPresent(user -> {
            model.addAttribute("user", user);
            model.addAttribute("displayName", user.getFullName());
        });

        model.addAttribute("totalUsers", userService.getTotalUserCount());
        return "dashboard";
    }

    @GetMapping("/game/snake")
    public String snakeGame(Model model, Principal principal) {
        String username = principal.getName();
        model.addAttribute("username", username);

        userService.findByUsername(username).ifPresent(user ->
            model.addAttribute("displayName", user.getFullName())
        );
        return "snake";
    }

    @GetMapping("/calculator")
    public String calculator(Model model, Principal principal) {
        String username = principal.getName();
        model.addAttribute("username", username);

        userService.findByUsername(username).ifPresent(user ->
            model.addAttribute("displayName", user.getFullName())
        );
        return "calculator";
    }
}
