package com.gameapp;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

/**
 * Basic context load test to verify the Spring application context
 * starts up correctly with H2 in-memory database.
 */
@SpringBootTest
@ActiveProfiles("test")
class GameAppApplicationTests {

    @Test
    void contextLoads() {
        // Verifies that all beans are wired correctly and the application
        // context starts without errors.
    }
}
