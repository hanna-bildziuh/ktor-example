# WhatToEat API

A recipe management REST API built with Kotlin and Ktor, designed as a learning project for Kotlin coroutine patterns in a real-world server application.

## What it does

WhatToEat lets users create accounts, authenticate with JWT tokens, and manage their profiles. The API is built with an API-first approach using OpenAPI 3.0 and follows [Zalando RESTful API Guidelines](https://opensource.zalando.com/restful-api-guidelines/).

## Main Features

**Account Registration** -- Create an account with email and password. Passwords are validated for strength (min 8 chars, mixed case, digit, special character) and hashed with BCrypt. Duplicate registrations return an opaque success response to prevent user enumeration.

**JWT Authentication** -- Login returns short-lived access tokens (15 min) and long-lived refresh tokens (7 days). Access tokens protect endpoints like user profile. Refresh tokens can be rotated for new token pairs.

**User Profile** -- Authenticated users can retrieve their profile information including email and account timestamps.

**Health Monitoring** -- Structured health endpoint that checks database connectivity and JWT subsystem in parallel, reporting per-component status and response times.

**Background Notifications** -- Welcome emails are sent asynchronously after registration using fire-and-forget coroutines, so the HTTP response is not blocked.

**AI Recipe Search** -- Search for recipes by providing ingredients and dietary restrictions. The system builds a prompt, queries the Claude API (Haiku 4.5), and returns a structured recipe. Results are cached with Caffeine to minimize API calls, with per-key Mutex preventing cache stampedes.

## Coroutine Patterns

This project demonstrates several Kotlin coroutine patterns applied to genuine use cases:

| Pattern | Where | Why |
|---------|-------|-----|
| `withContext(Dispatchers.Default)` | Password hashing | Offloads CPU-heavy BCrypt from the event loop |
| `coroutineScope { async {} }` | User registration | Runs email-exists check and password hashing in parallel |
| `scope.launch` | Notification service | Fire-and-forget background work after registration |
| `async` + `withTimeoutOrNull` | Health checks | Resilient parallel subsystem checks with deadlines |
| Ktor HTTP Client (suspend) | Claude API client | Suspend-based external HTTP calls |
| `withTimeout` | Claude API client | Hard deadline on API calls (throws on timeout) |
| `Mutex` (per-key) | Recipe cache | Prevents cache stampede via double-check locking |
| `Semaphore` | Claude API client | Rate-limits concurrent API calls |

## Tech Stack

- **Kotlin** 2.2.20 with kotlinx.coroutines
- **Ktor** 3.0.3 (Netty engine)
- **Exposed** ORM with H2 in-memory database
- **kotlinx.serialization** for JSON
- **BCrypt** for password hashing
- **JWT** (auth0) for authentication
- **Caffeine** for in-memory caching
- **JUnit 5** + Ktor test host for testing

## API Endpoints

| Method | Path | Description | Auth |
|--------|------|-------------|------|
| `POST` | `/auth/register` | Create new account | No |
| `POST` | `/auth/login` | Login, receive tokens | No |
| `POST` | `/auth/refresh` | Rotate refresh token | No |
| `POST` | `/recipes/search` | AI-powered recipe search | JWT |
| `GET` | `/user/profile` | Get user profile | JWT |
| `GET` | `/health` | System health status | No |
| `GET` | `/swagger` | Interactive API docs | No |

## Getting Started

```bash
# Build
mvn clean install

# Run
mvn exec:java

# Test
mvn test
```

The server starts on `http://localhost:8080`. API documentation is available at `http://localhost:8080/swagger`.

## Project Structure

```
src/main/kotlin/
  WhatToEat.kt                    # Application entry point and configuration
  exceptions/                      # Custom exception types (ValidationException, etc.)
  plugins/                         # Ktor plugins (Auth, StatusPages)
  routes/                          # Route handlers (Auth, User, Recipe, Generic)
  repositories/                    # Data access layer (interfaces + implementations)
  services/                        # Business services (Health, Notification, RecipeCache, RecipeService)
  services/claude/                 # Claude API client (interface + implementation)
  utils/                           # Utilities (JWT, Password, Validation)
src/main/resources/
  openapi.yaml                     # OpenAPI 3.0 specification
```