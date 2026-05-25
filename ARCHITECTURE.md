# GameApp — Architecture Documentation

## Table of Contents
1. [Overview](#overview)
2. [Tech Stack](#tech-stack)
3. [Project Structure](#project-structure)
4. [Layered Architecture](#layered-architecture)
5. [Layer-by-Layer Breakdown](#layer-by-layer-breakdown)
   - [Entry Point](#1-entry-point)
   - [Data Model](#2-data-model--userjava)
   - [Repository](#3-repository--userrepositoryjava)
   - [Service](#4-service--userservicejava)
   - [Security](#5-security--securityconfigjava)
   - [Controllers](#6-controllers)
   - [Templates](#7-templates-thymeleaf)
   - [Database Configuration](#8-database-configuration)
6. [Request Flow Examples](#request-flow-examples)
7. [Test Architecture](#test-architecture)
8. [Running the Application](#running-the-application)

---

## Overview

GameApp is a **Spring Boot MVC web application** that provides:
- User registration and login with session management
- A personal dashboard per logged-in user
- A browser-based Snake game
- A browser-based Calculator

All data is persisted in a local H2 file-based database (no DB server required).

---

## Tech Stack

| Layer | Technology |
|---|---|
| Language | Java 17 |
| Framework | Spring Boot 3.3.2 |
| Security | Spring Security 6 |
| Persistence | Spring Data JPA + Hibernate 6 |
| Database | H2 (file mode — persistent local DB) |
| Templates | Thymeleaf 3 |
| Build Tool | Maven |
| Testing | JUnit 5, Mockito, MockMvc, Spring Boot Test |

---

## Project Structure

```
gameapp/
├── pom.xml                                         # Maven dependencies & build config
├── ARCHITECTURE.md                                 # This file
│
├── src/main/
│   ├── java/com/gameapp/
│   │   ├── GameAppApplication.java                 # Application entry point
│   │   ├── config/
│   │   │   └── SecurityConfig.java                 # Spring Security rules
│   │   ├── controller/
│   │   │   ├── AuthController.java                 # /login, /register, /
│   │   │   └── DashboardController.java            # /dashboard, /game/snake, /calculator
│   │   ├── model/
│   │   │   └── User.java                           # JPA entity (maps to users table)
│   │   ├── repository/
│   │   │   └── UserRepository.java                 # Database queries (Spring Data JPA)
│   │   └── service/
│   │       └── UserService.java                    # Business logic + Spring Security hook
│   │
│   └── resources/
│       ├── application.properties                  # H2 file DB config (default profile)
│       ├── application-local-mysql.properties      # Optional: MySQL config
│       ├── application-local-sqlite.properties     # Optional: SQLite config
│       └── templates/
│           ├── login.html                          # Login page (royal blue UI)
│           ├── register.html                       # Registration form
│           ├── dashboard.html                      # Dashboard with game tiles
│           ├── snake.html                          # Canvas Snake game (JavaScript)
│           └── calculator.html                     # Calculator (JavaScript)
│
└── src/test/
    ├── java/com/gameapp/
    │   ├── GameAppApplicationTests.java            # Context load smoke test
    │   ├── controller/
    │   │   ├── AuthControllerTest.java             # WebMvcTest — 13 tests
    │   │   └── DashboardControllerTest.java        # WebMvcTest — 11 tests
    │   ├── integration/
    │   │   └── GameAppIntegrationTest.java         # Full-stack — 16 tests
    │   ├── repository/
    │   │   └── UserRepositoryTest.java             # DataJpaTest — 17 tests
    │   └── service/
    │       └── UserServiceTest.java                # Unit tests — 15 tests
    └── resources/
        ├── application-test.properties             # H2 in-memory config for tests
        └── mockito-extensions/
            └── org.mockito.plugins.MockMaker       # Mockito subclass strategy (Java 25 compat)
```

---

## Layered Architecture

Every HTTP request passes through these layers in order:

```
Browser (HTML / CSS / JavaScript)
         │
         │  HTTP Request
         ▼
┌────────────────────────────────────┐
│      Spring Security Filter Chain  │  ← checks every request before it hits a controller
│      (SecurityConfig.java)         │
└────────────────────────────────────┘
         │ allowed?
         ▼
┌────────────────────────────────────┐
│          Controllers               │  ← receive request, call service, return view
│  AuthController  DashboardCtrl     │
└────────────────────────────────────┘
         │
         ▼
┌────────────────────────────────────┐
│           UserService              │  ← business logic, validation, password encoding
└────────────────────────────────────┘
         │
         ▼
┌────────────────────────────────────┐
│         UserRepository             │  ← database queries (auto-generated SQL)
└────────────────────────────────────┘
         │
         ▼
┌────────────────────────────────────┐
│    H2 File Database                │  ← ./data/gameapp.mv.db  (auto-created)
└────────────────────────────────────┘
         │
         ▼
┌────────────────────────────────────┐
│       Thymeleaf Templates          │  ← server renders HTML, sends to browser
└────────────────────────────────────┘
```

---

## Layer-by-Layer Breakdown

### 1. Entry Point

**`GameAppApplication.java`**

```java
@SpringBootApplication
public class GameAppApplication {
    public static void main(String[] args) {
        SpringApplication.run(GameAppApplication.class, args);
    }
}
```

`@SpringBootApplication` is a shortcut for three annotations:
- `@Configuration` — this class can define Spring beans
- `@ComponentScan` — scan `com.gameapp.*` and register all annotated classes as beans
- `@EnableAutoConfiguration` — auto-configure Spring MVC, Spring Security, JPA, H2, etc. based on what's on the classpath

---

### 2. Data Model — `User.java`

Maps to the `users` table in the database.

```
users table
┌─────────────┬───────────────────────────────────────────┐
│ Column      │ Details                                   │
├─────────────┼───────────────────────────────────────────┤
│ id          │ BIGINT, primary key, auto-increment       │
│ username    │ VARCHAR(50), unique, not null             │
│ email       │ VARCHAR(100), unique, not null            │
│ password    │ VARCHAR, BCrypt hash (never plain text)   │
│ first_name  │ VARCHAR(50), optional                     │
│ last_name   │ VARCHAR(50), optional                     │
│ created_at  │ TIMESTAMP, set once on insert             │
│ last_login  │ TIMESTAMP, updated on each login          │
└─────────────┴───────────────────────────────────────────┘
```

Key annotations:
- `@Entity` — marks this class as a JPA-managed object (Hibernate creates/manages the table)
- `@Table(uniqueConstraints = ...)` — enforces uniqueness at the **database level**, not just application level
- `@PrePersist` — a JPA lifecycle callback; `createdAt` is set automatically before the **very first INSERT** and never updated again (`updatable = false`)
- `getFullName()` — returns "First Last" if both are set, "First" if only first, or falls back to username

---

### 3. Repository — `UserRepository.java`

```java
public interface UserRepository extends JpaRepository<User, Long> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);

    @Modifying
    @Query("UPDATE User u SET u.lastLogin = :lastLogin WHERE u.username = :username")
    void updateLastLogin(@Param("username") String username,
                         @Param("lastLogin") LocalDateTime lastLogin);
}
```

- Extends `JpaRepository<User, Long>` — Spring Data JPA **auto-generates** implementations for `save()`, `findById()`, `findAll()`, `count()`, `delete()`, etc. No boilerplate SQL needed.
- **Derived queries** (`findByUsername`, `existsByUsername`) — Spring reads the method name and generates the SQL: `SELECT * FROM users WHERE username = ?`
- **Custom JPQL** (`@Query`) — for UPDATE/DELETE or complex queries, JPQL uses class/field names (not table/column names): `User` = class, `u.lastLogin` = field
- `@Modifying` — required on any non-SELECT query to tell Spring Data this is a write operation

---

### 4. Service — `UserService.java`

The business logic layer. Controllers call the service; the service calls the repository.

```
UserService
 ├── loadUserByUsername(username)
 │     └── Called by Spring Security during login form submission
 │         Returns UserDetails (username + hashed password + roles)
 │
 ├── registerUser(username, email, password, firstName, lastName)
 │     ├── Check username not already taken  → throw IllegalArgumentException if duplicate
 │     ├── Check email not already taken     → throw IllegalArgumentException if duplicate
 │     ├── Encode password with BCrypt       → never store plain text
 │     ├── Normalise email to lowercase      → "ALICE@TEST.COM" → "alice@test.com"
 │     └── Save user to DB
 │
 ├── recordLogin(username)
 │     └── Updates lastLogin timestamp in DB
 │
 ├── findByUsername(username) / findByEmail(email)
 │     └── Used by controllers to fetch the full User object
 │
 └── getTotalUserCount()
       └── Returns count(*) from users table for dashboard stats
```

Key concepts:
- `@Service` — marks this class as a Spring-managed bean (discovered by component scan)
- `@Transactional` on the class — every public method runs in a DB transaction; if an exception is thrown mid-method, all DB changes are rolled back
- `@Transactional(readOnly = true)` on read methods — hints to the DB that no writes will happen (slight performance gain, enables certain DB optimisations)
- `implements UserDetailsService` — the **contract Spring Security requires**. When the login form is submitted, Spring Security calls `loadUserByUsername()` on this service, then compares the submitted password against the stored BCrypt hash

---

### 5. Security — `SecurityConfig.java`

Defines which URLs are public and which require login, plus login/logout behaviour.

```
Every HTTP request
        │
        ▼
┌───────────────────────────────────────────────────────────┐
│  Is the URL in the public list?                           │
│                                                           │
│  Public:  /, /login, /register, /css/**, /js/**,          │
│           /images/**, /webjars/**, /favicon.ico, /error   │
│                                  │                        │
│           YES ──────────────────►│ pass through           │
│           NO                     │                        │
│            │                     │                        │
│            ▼                     │                        │
│     Is user authenticated?       │                        │
│            │                     │                        │
│     YES ───┘ pass through        │                        │
│     NO  → redirect to /login     │                        │
└───────────────────────────────────────────────────────────┘
```

**Form Login configuration:**
```
POST /login  {username, password}
        │
        ▼
  Spring Security verifies credentials via UserService.loadUserByUsername()
        │
  Success → create session → redirect to /dashboard
  Failure → redirect to /login?error=true
```

**Session management:**
- `sessionFixation().migrateSession()` — on login, the old session ID is replaced with a new one. Prevents **session fixation attacks** (where an attacker pre-sets a known session ID).
- `maximumSessions(5)` — one user account can be logged in from up to 5 devices/browsers simultaneously

**Password encoding:**
- `BCryptPasswordEncoder(12)` — the `12` is the cost factor (2¹² = 4096 hashing rounds). High enough to be slow for brute-force attackers, fast enough for normal login.

---

### 6. Controllers

#### `AuthController` — Public Routes

| Method | URL | Action |
|---|---|---|
| GET | `/` | Redirect to `/login` |
| GET | `/login` | Render login page; add error/success messages from query params |
| GET | `/register` | Render empty registration form |
| POST | `/register` | Validate input → register user → redirect to `/login?registered=true` |

Validation performed in the controller before calling the service:
1. Username blank or too short/long (3–50 chars)
2. Email blank
3. Password shorter than 6 characters
4. Password and confirmPassword do not match

On validation failure: returns the register page with an `error` attribute and re-populates form fields so the user doesn't retype everything.

On service-level failure (e.g. duplicate username): the `IllegalArgumentException` from the service is caught and its message shown to the user.

#### `DashboardController` — Protected Routes

| Method | URL | Action |
|---|---|---|
| GET | `/dashboard` | Show dashboard with username, display name, total user count |
| GET | `/game/snake` | Render the Snake game page |
| GET | `/calculator` | Render the Calculator page |

All routes require authentication (enforced by `SecurityConfig`). Spring Security injects a `Principal` object representing the logged-in user. The controller uses `principal.getName()` (the username) to load the full `User` record and put their display name into the Thymeleaf model.

---

### 7. Templates (Thymeleaf)

Thymeleaf is a **server-side template engine** — HTML files are processed on the server, values from the model are inserted, then the final HTML is sent to the browser.

```
Controller puts into Model:          Template reads:          Browser receives:
model.addAttribute("username","alice")  th:text="${username}"  → alice
model.addAttribute("totalUsers", 5L)    th:text="${totalUsers}" → 5
errorMsg != null                        th:if="${errorMsg}"     → <div>...</div>
```

| Template | Purpose |
|---|---|
| `login.html` | Login form with royal blue gradient background |
| `register.html` | Registration form; re-populates on validation errors |
| `dashboard.html` | Welcome banner + Snake tile + Calculator tile |
| `snake.html` | Full canvas-based Snake game (pure JavaScript) |
| `calculator.html` | Calculator with history panel (pure JavaScript) |

The **Snake game** and **Calculator** are entirely **frontend** — once Thymeleaf renders and sends the page, all game/calculation logic runs in the browser. The server is not involved during gameplay.

---

### 8. Database Configuration

#### Default (H2 file mode — used when running the app normally)

```properties
# application.properties
spring.datasource.url=jdbc:h2:file:./data/gameapp;AUTO_SERVER=TRUE
spring.datasource.driver-class-name=org.h2.Driver
spring.jpa.hibernate.ddl-auto=update
```

- Data is stored in `./data/gameapp.mv.db` (created automatically on first run)
- `AUTO_SERVER=TRUE` — allows multiple connections (useful during development)
- `ddl-auto=update` — Hibernate reads the `@Entity` classes and **creates or alters tables automatically**. You never write `CREATE TABLE` SQL.
- H2 web console available at `http://localhost:8080/h2-console` while the app is running

#### Test Profile (H2 in-memory — used during `mvn test`)

```properties
# application-test.properties
spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1
spring.jpa.hibernate.ddl-auto=create-drop
```

- In-memory only — no file, no persistence. Database is wiped after each test run.
- `create-drop` — schema is created fresh before tests and dropped after.
- `MODE=PostgreSQL` — H2 mimics PostgreSQL SQL syntax for better compatibility.

---

## Request Flow Examples

### Login Flow

```
1.  User fills in username + password, clicks "Log In"
2.  Browser → POST /login  {username=alice, password=secret}
3.  Spring Security's UsernamePasswordAuthenticationFilter intercepts
4.  Calls UserService.loadUserByUsername("alice")
5.  UserService → UserRepository.findByUsername("alice")
6.  UserRepository → SELECT * FROM users WHERE username = 'alice'
7.  DB returns the User row (with BCrypt hash in password column)
8.  Spring Security compares "secret" against the BCrypt hash
        Match?  YES → create SecurityContext, store session
                NO  → redirect to /login?error=true
9.  Redirect → GET /dashboard
10. SecurityFilter checks: session exists? YES → allow
11. DashboardController.dashboard(model, principal) is called
12. principal.getName() → "alice"
13. UserRepository.findByUsername("alice") → full User object
14. model ← username="alice", displayName="Alice Smith", totalUsers=42
15. Thymeleaf renders dashboard.html with those values
16. Browser displays the dashboard
```

### Registration Flow

```
1.  User fills in form, clicks "Register"
2.  Browser → POST /register  {username, email, password, confirmPassword, ...}
3.  Spring Security lets it through (permitAll on /register)
4.  AuthController.registerUser() runs
5.  Manual validation:
        username length OK?  email not blank?  password ≥ 6?  passwords match?
        Any failure → return "register" view with error message (form re-populated)
6.  UserService.registerUser() called
7.  existsByUsername()? → if true → throw IllegalArgumentException("Username taken")
8.  existsByEmail()?    → if true → throw IllegalArgumentException("Email registered")
9.  passwordEncoder.encode(password) → "$2a$12$..."
10. new User(username, email, hashedPassword, firstName, lastName)
11. @PrePersist sets createdAt = now()
12. UserRepository.save(user) → INSERT INTO users ...
13. Redirect → /login?registered=true
14. AuthController.loginPage() adds successMsg to model
15. Browser shows login page with "Account created! Please log in."
```

---

## Test Architecture

```
73 tests across 6 test classes
─────────────────────────────────────────────────────────────────────
Class                      Tests  Spring Context   DB
─────────────────────────────────────────────────────────────────────
UserServiceTest              15   None (Mockito)   None
AuthControllerTest           13   Web layer only   None
DashboardControllerTest      11   Web layer only   None
UserRepositoryTest           17   JPA layer only   H2 in-memory
GameAppIntegrationTest       16   Full stack        H2 in-memory
GameAppApplicationTests       1   Full stack        H2 in-memory
─────────────────────────────────────────────────────────────────────
```

### Unit Tests — `UserServiceTest`
- `@ExtendWith(MockitoExtension.class)` — no Spring context, no DB, no HTTP
- All dependencies (`UserRepository`, `PasswordEncoder`) are **Mockito mocks** — fake objects whose behaviour is scripted in each test
- Fastest tests; run in milliseconds
- Tests business logic in complete isolation

### Controller Tests — `AuthControllerTest`, `DashboardControllerTest`
- `@WebMvcTest` — loads only the Spring MVC layer (controllers, security, filters)
- `@Import(SecurityConfig.class)` — explicitly loads our security rules so the filter chain is applied correctly
- `excludeAutoConfiguration = {UserDetailsServiceAutoConfiguration.class}` — prevents Spring Boot from creating a default `InMemoryUserDetailsManager` alongside our mocked `UserService` (two `UserDetailsService` beans would cause the security filter chain to misconfigure)
- `@MockBean UserService` — service is a mock; controller tests verify HTTP behaviour, not service logic
- `MockMvc` — simulates HTTP requests without starting a real web server
- `@WithMockUser(username = "alice")` — injects a fake authenticated user into the security context for tests that need an authenticated session

### Repository Tests — `UserRepositoryTest`
- `@DataJpaTest` — loads only Spring Data JPA, Hibernate, and H2 in-memory
- Tests real SQL queries against a real (in-memory) database
- No web layer, no service layer
- Verifies uniqueness constraints, custom queries, save/find/delete operations

### Integration Tests — `GameAppIntegrationTest`
- `@SpringBootTest` — loads the **entire application** (all layers)
- Uses H2 in-memory instead of the file-based DB (`@ActiveProfiles("test")`)
- `@AutoConfigureMockMvc` — full HTTP simulation through the real filter chain
- Tests the complete flow: request → security → controller → service → DB → response

---

## Running the Application

```bash
# Run with default H2 file database
mvn spring-boot:run

# Run all 73 tests
mvn test

# Build a runnable JAR
mvn package
java -jar target/gameapp-1.0.0.jar
```

The app starts at `http://localhost:8080`.
On first run, Hibernate creates the `users` table automatically in `./data/gameapp.mv.db`.
The H2 console is available at `http://localhost:8080/h2-console`
(JDBC URL: `jdbc:h2:file:./data/gameapp`, username: `sa`, password: empty).
