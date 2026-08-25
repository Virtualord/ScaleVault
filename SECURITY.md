# ScaleVault Security Specifications

## 1. Threat Model & Security Posture

ScaleVault is designed under the assumption of **Zero Trust** for external input. Because authentication and authorization are security-critical, defensive engineering practices are enforced across the entire application lifecycle.

---

## 2. Authentication & Credential Security

### Password Handling
- **Hashing Algorithm**: Strong cryptographic password hashing using **BCrypt** with an adaptive work factor (minimum strength: 12).
- **No Plaintext**: Passwords are never stored in plaintext, never logged to stdout/files, and never returned in DTO responses.
- **Password Policies**: Enforced at the DTO layer using Jakarta Validation:
  - Minimum 8 characters, maximum 64 characters.
  - Requires uppercase, lowercase, digit, and special character.

### JWT Access Tokens
- **Lifespan**: Short-lived (default: 15 minutes) to minimize the exposure window if compromised.
- **Signing**: HMAC-SHA256 (`HS256`) or asymmetric `RS256` with high-entropy cryptographic keys loaded strictly from environment variables.
- **Payload Minimization**: Only non-sensitive claims are encoded (`sub` = userId/UUID, `email`, `roles`, `iat`, `exp`). No PII or credentials.

### Refresh Token Rotation & Session Revocation
- **Lifespan**: Long-lived (default: 7 days).
- **Single-Use Rotation**: Using a refresh token immediately issues a new token pair and marks the previous refresh token as consumed.
- **Token Family / Reuse Detection**: Each refresh token belongs to a session family. If a previously consumed or revoked token is reused (indicating token theft/replay), the entire token family is immediately revoked, forcing re-authentication.
- **Revocation**:
  - Explicit logout invalidates the active refresh token.
  - Account suspension/password change invalidates all active sessions for that user.

---

## 3. Authorization & Role-Based Access Control (RBAC)

### Roles
- `ROLE_USER`: Standard authenticated user. Can access self-profile (`/api/v1/users/me`) and change personal information.
- `ROLE_ADMIN`: Administrative operator. Can list users, filter/search users, promote/demote roles, and enable/disable user accounts.

### Authorization Invariants
- **Public Self-Registration Restriction**: The registration endpoint (`/api/v1/auth/register`) strictly sets new accounts to `ROLE_USER`. Role assignment from client payloads is rejected or ignored.
- **Method & Endpoint Security**: Enforced via Spring Security `SecurityFilterChain` matchers and `@PreAuthorize("hasRole('ADMIN')")` annotations.
- **Self-Protection Rule**: An administrator cannot demote or disable their own account to prevent accidental lockout.

---

## 4. Rate Limiting & Abuse Prevention

- **Redis-Backed Rate Limiting**: Applied to public authentication endpoints (`/api/v1/auth/login`, `/api/v1/auth/register`, `/api/v1/auth/refresh`).
- **Policy**:
  - IP-based rate limiting for registration (e.g., 5 requests per minute per IP).
  - Identifier-based (IP + Username/Email) rate limiting for login (e.g., 5 failed attempts per 5 minutes) to protect against credential stuffing and brute-force attacks.
- **HTTP 429 Too Many Requests**: Returns standard rate limit headers (`Retry-After`, `X-RateLimit-Limit`, `X-RateLimit-Remaining`).

---

## 5. Input Validation & Injection Prevention

- **Jakarta Bean Validation**: All input DTOs are validated (`@Valid`, `@NotNull`, `@NotBlank`, `@Size`, `@Email`, `@Pattern`).
- **SQL Injection Prevention**: Exclusively use Spring Data JPA parameter binding and parameterized SQL. Raw unescaped SQL string concatenation is strictly prohibited.
- **Mass Assignment / Over-posting Protection**: Clients can only bind to dedicated Request DTOs. Database entities (`User`, `Role`) are never bound directly from request bodies.

---

## 6. Safe Error Handling & Information Disclosure

- **Centralized Exception Handler**: Handled via `@RestControllerAdvice`.
- **Response Sanitization**:
  - Stack traces, database constraint names, and SQL exceptions are suppressed in API responses.
  - Return standardized JSON errors with clear messages, HTTP status codes, and timestamps.
- **Timing Attack Mitigation**: Credential verification responds with consistent error messages (`Invalid email or password`) regardless of whether the user exists or the password was incorrect.

---

## 7. Audit Logging

Security-critical events must be logged with timestamp, user ID (if available), IP address, and outcome:
- User registration
- Successful login
- Failed login attempt
- Password change / update
- Refresh token rotation / reuse detection alert
- Admin role modifications
- Admin user status (enable/disable) changes

---

## 8. Secret Management & Git Hygiene

- **Zero Hardcoded Secrets**: Secrets such as `DB_PASSWORD`, `REDIS_PASSWORD`, `JWT_SECRET` are supplied exclusively via environment variables or `.env` (which is git-ignored).
- **Version Control Rules**:
  - `.gitignore` includes all environment files (`.env`, `.env.local`), compiled binaries (`target/`), and local IDE configurations.
  - Pre-commit checks must ensure no credentials or sensitive tokens are committed.
