# ScaleVault Product Specification

## Purpose

ScaleVault is a secure authentication and user-management REST API designed to demonstrate production-oriented backend engineering.

## Core Features

### Authentication

* Register
* Login
* Access JWT
* Refresh token
* Refresh token rotation
* Logout
* Token revocation

### Authorization

* USER role
* ADMIN role
* Protected user endpoints
* Protected admin endpoints

### User Management

* Current-user profile
* Admin user listing
* Pagination
* Search/filter support
* Account enable/disable
* Role management

### Infrastructure

* PostgreSQL
* Redis
* Docker
* Docker Compose

### Security

* Password hashing
* JWT validation
* RBAC
* Rate limiting
* Request validation
* CORS
* Safe error handling
* Audit logging
* Secret management

### Quality

* Unit tests
* Integration tests
* Testcontainers
* OpenAPI
* GitHub Actions CI

## API Prefix

`/api/v1`

## Public Endpoints

`GET /api/v1/health`

`POST /api/v1/auth/register`

`POST /api/v1/auth/login`

`POST /api/v1/auth/refresh`

## Authenticated Endpoints

`POST /api/v1/auth/logout`

`GET /api/v1/users/me`

`PATCH /api/v1/users/me`

## Admin Endpoints

`GET /api/v1/admin/users`

`GET /api/v1/admin/users/{id}`

`PATCH /api/v1/admin/users/{id}/role`

`PATCH /api/v1/admin/users/{id}/status`

## Non-Goals

Do not introduce:

* microservices
* Kafka
* Kubernetes
* complex distributed systems
* unnecessary third-party services

unless there is a clear project requirement added later.

The project should first become an excellent modular monolith.
