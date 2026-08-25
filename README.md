# ScaleVault

ScaleVault is a production-oriented, secure authentication and user-management REST API built with Java 21 and Spring Boot 3.x. It demonstrates clean architecture, production-grade security patterns, and resilient infrastructure design suitable for real-world enterprise backends and technical interview discussions.

---

## 🏛️ Architecture & Documentation

Before diving into code, review our Source of Truth documents:

- 📋 [**PROJECT_SPEC.md**](PROJECT_SPEC.md) — Product requirements, endpoint definitions, and non-goals.
- 📐 [**ARCHITECTURE.md**](ARCHITECTURE.md) — Modular monolith architecture, package layering, and Architectural Decision Records (ADRs).
- 🛡️ [**SECURITY.md**](SECURITY.md) — Security posture, JWT + refresh-token rotation, RBAC, Redis rate limiting, and threat mitigations.
- 🤝 [**CONTRIBUTING.md**](CONTRIBUTING.md) — Development conventions, Conventional Commits, testing guidelines, and milestone reporting.

---

## 🚀 Tech Stack

- **Language**: Java 21 (LTS)
- **Framework**: Spring Boot 3.x
- **Build Tool**: Maven (`./mvnw`)
- **Security**: Spring Security 6.x, JWT (JJWT), Refresh-Token Rotation
- **Persistence**: Spring Data JPA, PostgreSQL, Flyway Migrations
- **Cache & Rate Limiting**: Redis, Spring Data Redis (Lettuce)
- **Validation**: Jakarta Bean Validation (`hibernate-validator`)
- **API Docs**: Springdoc OpenAPI / Swagger UI
- **Testing**: JUnit 5, Mockito, Testcontainers (PostgreSQL, Redis), MockMvc
- **Containerization**: Docker & Docker Compose

---

## 🧭 Core API Endpoints

All endpoints are versioned under the `/api/v1` prefix.

| Method | Endpoint | Access | Description |
| :--- | :--- | :--- | :--- |
| `GET` | `/api/v1/health` | Public | Service health and liveness probe |
| `POST` | `/api/v1/auth/register` | Public | Register a new user (`ROLE_USER`) |
| `POST` | `/api/v1/auth/login` | Public | Authenticate with email/password; returns JWT pair |
| `POST` | `/api/v1/auth/refresh` | Public | Rotate refresh token and obtain new access token |
| `POST` | `/api/v1/auth/logout` | Authenticated | Revoke refresh token and invalidate session |
| `GET` | `/api/v1/users/me` | Authenticated | Retrieve current authenticated user profile |
| `PATCH` | `/api/v1/users/me` | Authenticated | Update current user profile |
| `GET` | `/api/v1/admin/users` | Admin (`ROLE_ADMIN`) | Paginated user search and listing |
| `GET` | `/api/v1/admin/users/{id}` | Admin (`ROLE_ADMIN`) | Get specific user by ID |
| `PATCH` | `/api/v1/admin/users/{id}/role` | Admin (`ROLE_ADMIN`) | Update user role (`ROLE_USER` / `ROLE_ADMIN`) |
| `PATCH` | `/api/v1/admin/users/{id}/status` | Admin (`ROLE_ADMIN`) | Enable or disable user account |
