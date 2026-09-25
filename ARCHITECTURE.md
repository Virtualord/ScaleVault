# ScaleVault Architecture & Design Document

## 1. System Overview

ScaleVault is structured as a **modular monolith** built with Java 21 and Spring Boot 3.x. It delivers high-throughput, secure authentication and user management using PostgreSQL for relational data persistence and Redis for token tracking, caching, and rate limiting.

### High-Level Architecture

```
                       +-----------------------+
                       |      HTTP Client      |
                       +-----------+-----------+
                                   |
                             HTTPS / JSON
                                   v
                    +-----------------------------+
                    |  Spring Security / Filters  |
                    |   (Rate Limiter, JWT Auth)  |
                    +--------------+--------------+
                                   |
                                   v
+-----------------------------------------------------------------+
|                       ScaleVault Application                    |
|                                                                 |
|  +-----------------------------------------------------------+  |
|  |                   REST Controllers Layer                  |  |
|  |  - AuthController       (/api/v1/auth)                    |  |
|  |  - UserController       (/api/v1/users)                   |  |
|  |  - AdminUserController  (/api/v1/admin/users)             |  |
|  |  - HealthController     (/api/v1/health)                  |  |
|  +-----------------------------+-----------------------------+  |
|                                | (DTOs only)                    |
|                                v                                |
|  +-----------------------------------------------------------+  |
|  |                       Service Layer                       |  |
|  |  - AuthService         - UserService                      |  |
|  |  - TokenService        - AuditLogService                  |  |
|  |  - RateLimiterService                                     |  |
|  +-------------------+-------------------+-------------------+  |
|                      |                   |                      |
|                      v                   v                      |
|  +-----------------------+   +-------------------------------+  |
|  |     Data Layer        |   |       Caching & State         |  |
|  | (Spring Data JPA Repos|   |        (Spring Redis)         |  |
|  +-----------+-----------+   +---------------+---------------+  |
+--------------|-------------------------------|------------------+
               |                               |
               v                               v
      +-----------------+             +-----------------+
      |   PostgreSQL    |             |      Redis      |
      | (Primary Store) |             | (Cache & Limits)|
      +-----------------+             +-----------------+
```

---

## 2. Layered Structure & Clean Architecture Rules

ScaleVault enforces strict separation of concerns across well-defined layers:

1. **Presentation / Web Layer (`controller`, `dto`)**
   - **Controllers remain thin**: only handle HTTP request routing, input validation triggers (`@Valid`), and mapping service outputs to HTTP responses.
   - **No entity exposure**: Controllers only accept Request DTOs and return Response DTOs. Database entities (`@Entity`) never cross this boundary.
   - **Standardized Error Handling**: A centralized `@RestControllerAdvice` translates internal exceptions into safe, structured API error responses.

2. **Security Layer (`security`)**
   - `SecurityFilterChain`: Explicit URL authorization rules (Public, Authenticated, Admin-only).
   - `JwtAuthenticationFilter`: Stateless, per-request JWT parsing, validation, and `SecurityContext` population.
   - Authentication Entry Point & Access Denied Handler: Secure JSON responses on 401 Unauthorized and 403 Forbidden.

3. **Application / Business Service Layer (`service`)**
   - All business rules, transactions (`@Transactional`), and workflow orchestration live here.
   - **Constructor Injection**: All dependencies are injected via `final` fields and constructor (Lombok `@RequiredArgsConstructor` or explicit constructors).

4. **Persistence Layer (`repository`, `entity`)**
   - Spring Data JPA repositories interfacing with PostgreSQL.
   - Explicit database schema migrations managed via Flyway. Hibernate is strictly configured with `ddl-auto=validate`.

5. **Infrastructure & Integration Layer (`config`, `redis`, `audit`)**
   - Redis templates and connection management.
   - Distributed rate-limiting implementation (Token Bucket / Sliding Window using Redis).
   - Async audit logging.

---

## 3. Key Architectural Decisions (ADR)

### ADR 001: Modular Monolith vs. Microservices
- **Decision**: Build ScaleVault as a single deployable modular monolith rather than microservices.
- **Rationale**: For authentication and user management at this stage, a modular monolith eliminates distributed transaction complexity, network overhead, and dual-write problems between services, while maintaining clean internal module boundaries.

### ADR 002: Dual-Token Architecture (JWT Access Token + Refresh Token Rotation)
- **Decision**: Issue short-lived stateless JWT access tokens (15-minute expiration) and persistent, single-use refresh tokens (7-day expiration).
- **Rotation Policy**: Every time a refresh token is used to acquire a new access token, the used refresh token is invalidated and replaced with a new refresh token (family-based tracking).
- **Reuse Detection**: If a previously consumed or revoked refresh token is presented, the entire token family for that user session is immediately revoked to prevent token hijacking.

### ADR 003: Redis for Rate Limiting and Token Revocation
- **Decision**: Use Redis for distributed rate limiting on sensitive authentication endpoints (e.g., login, register) and for caching/revocation checks.
- **Rationale**: PostgreSQL should not bear the load of high-frequency brute-force attempts and transient rate-limit counters. Redis provides in-memory atomic operations (e.g., `INCR`, `EXPIRE`) ideal for high-throughput rate limiting.

### ADR 004: Explicit Flyway Database Migrations
- **Decision**: Use Flyway for explicit, version-controlled SQL migration scripts (`V1__init.sql`, etc.).
- **Rationale**: Schema generation via `ddl-auto=update` is non-deterministic and hazardous for production environments. Explicit SQL migrations ensure auditable, reproducible schema changes across environments.

### ADR 005: Strict DTO Boundaries and Jakarta Validation
- **Decision**: Separate entity models from API representation completely. Validate all incoming payloads with Jakarta Bean Validation (`@NotBlank`, `@Email`, `@Size`, etc.).
- **Rationale**: Protects against over-posting/mass-assignment vulnerabilities, decouples internal database optimizations from API contracts, and provides deterministic validation error messages.

---

## 4. Package Structure Blueprint

```
com.scalevault
├── ScaleVaultApplication.java
├── common
│   ├── exception (GlobalExceptionHandler, ApiException, ErrorCode)
│   ├── response (ApiResponse, PagedResponse)
│   └── util (DateUtils, SecurityUtils)
├── config
│   ├── SecurityConfig.java
│   ├── RedisConfig.java
│   ├── OpenApiConfig.java
│   └── AuditingConfig.java
├── auth
│   ├── controller (AuthController)
│   ├── dto (RegisterRequest, LoginRequest, AuthResponse, RefreshRequest)
│   └── service (AuthService, TokenService, RefreshTokenService)
├── user
│   ├── controller (UserController, AdminUserController)
│   ├── dto (UserResponse, UpdateUserRequest, ChangeRoleRequest, ChangeStatusRequest, UserSearchCriteria)
│   ├── entity (User, Role, UserStatus)
│   ├── repository (UserRepository)
│   └── service (UserService, AdminUserService)
├── security
│   ├── JwtTokenProvider.java
│   ├── JwtAuthenticationFilter.java
│   ├── CustomUserDetailsService.java
│   ├── UserPrincipal.java
│   ├── RestAuthenticationEntryPoint.java
│   └── RestAccessDeniedHandler.java
├── ratelimit
│   ├── RateLimitingFilter.java
│   └── RateLimiterService.java
└── audit
    ├── entity (AuditLog)
    ├── repository (AuditLogRepository)
    └── service (AuditLogService)
```
