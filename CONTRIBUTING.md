# ScaleVault Contributing & Engineering Guidelines

Welcome to ScaleVault! This document outlines engineering conventions, Git workflows, and coding standards. Every decision made in this codebase should be production-oriented and clear enough to be explained in a technical interview.

---

## 1. Core Principles

- **Clarity over Cleverness**: Write readable, maintainable code. Prefer simple, clean implementations over overly complex design patterns or unnecessary abstractions.
- **Strict Layering**:
  - **Controllers**: Thin HTTP adapters. Handle routing, request validation (`@Valid`), and response mapping.
  - **Services**: The brain of the application. Business logic, transaction boundaries (`@Transactional`), domain validation, and external service calls live here.
  - **Repositories**: Direct data access interfaces via Spring Data JPA.
  - **DTOs**: Sole boundary objects between clients and the application. Never expose `@Entity` classes in controllers.
- **Constructor Injection**: Always use constructor injection with `final` fields. Do not use field injection (`@Autowired` on fields).
- **Explicit Migrations**: All schema modifications must have corresponding Flyway migration scripts in `src/main/resources/db/migration/`.

---

## 2. Git & Commit Guidelines

We enforce **Conventional Commits**. Each commit represents a single logical unit of change.

### Commit Format
```
<type>(<scope>): <subject>
```
*Scope is optional.*

### Allowed Types
- `feat`: A new feature or endpoint.
- `fix`: A bug fix or security patch.
- `test`: Adding or refactoring tests.
- `docs`: Documentation changes (`README`, `ARCHITECTURE`, etc.).
- `refactor`: Code changes that neither fix a bug nor add a feature.
- `chore`: Build process, dependencies, Docker configurations.

### Rules
- **One logical change per commit**: Never bundle unrelated refactors, migrations, and features into a single giant commit.
- **No destructive Git commands**: Never use `git push --force` or reset commands without explicit justification and safety checks.
- **Review before commit**: Always run `git diff` and `git status` to verify no unwanted files, keys, or `.env` files are tracked.

---

## 3. Testing Conventions

- **Unit Tests**: Test service business logic in isolation using JUnit 5 and Mockito.
- **Integration Tests**: Test controller endpoints, security boundaries, and database repositories using `@SpringBootTest` / `@WebMvcTest`.
- **Testcontainers**: Use real PostgreSQL and Redis containers for integration test suites where realistic database/cache behavior is needed.
- **Test Integrity**: Fix the implementation instead of weakening assertions or tests when an error occurs.
- **Execution Verification**: Never claim a test passed unless it was actually executed.

---

## 4. Milestone Reporting Protocol

After completing a milestone or significant feature, provide a brief structured report:

1. **What Changed**: Summary of features or architectural additions.
2. **Important Files**: Key files created or modified with clickable paths.
3. **Tests Run**: List of unit/integration tests executed and results.
4. **Commit Created**: Git commit hash and commit message.
5. **Important Concepts to Understand**: Core architectural rationale and interview explanation points.
