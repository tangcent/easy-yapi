# AGENTS.md

Project rules and guidance for AI agents working on the EasyYapi codebase.

## Project Overview

EasyYapi is an IntelliJ IDEA plugin (v3.0 rewrite) for API development — export API documentation to YApi/Postman/Markdown, send requests, and manage endpoints directly from source code.

- **Language:** Kotlin (2.1.0+)
- **JDK:** 17+
- **IntelliJ Platform:** 2023.1+
- **Build:** Gradle (`./gradlew build`)
- **Tests:** `./gradlew test`
- **Run Plugin:** `./gradlew runIde`

## Architecture Principles

1. **Kotlin Coroutines** — Use structured concurrency for all async operations
2. **PSI Threading** — Methods accessing PSI must be `suspend` functions with internal read/write actions
3. **Type Safety** — Use sealed classes for type hierarchies, data classes for DTOs
4. **Dependency Injection** — Use IntelliJ `@Service` for singletons, `OperationScope` for per-operation objects

## Code Style

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Keep functions small and focused
- Add KDoc comments for public APIs
- Prefer expression bodies for simple functions

## Project Structure

```
src/main/kotlin/com/itangcent/easyapi/
├── core/          # Core infrastructure (events, threading, application services)
├── psi/           # PSI utilities (adapters, doc helpers, type resolution)
├── exporter/      # Export functionality (YApi, Postman, Markdown, cURL, gRPC)
├── config/        # Config parsing and management
├── rule/          # Rule engine and parsers
├── http/          # HTTP client implementations
├── ide/           # IDE integration (actions, dialogs, search)
├── logging/       # Logging infrastructure
├── dashboard/     # API Dashboard tool window
└── script/        # Script execution support
```

## Testing

- Write unit tests for new functionality
- Use `EasyApiLightCodeInsightFixtureTestCase` for PSI/Project-aware tests
- Use `mockito-kotlin` for mocking
- Aim for good test coverage

## Skills

The following skills are available in the `.skills/` folder. Invoke the appropriate skill when the user's request matches the described conditions.

| Skill | When to Use |
|-------|-------------|
| **[git-commit](.skills/git-commit/SKILL.md)** | User asks to write a commit message, create a commit, or needs help formatting a git commit. Generates standardized conventional commit messages. |
| **[bug-fixer](.skills/bug-fixer/SKILL.md)** | User reports an error, test failure, or unexpected behavior that needs debugging and fixing. Provides systematic bug-fixing workflow with test-first approach. |
| **[write-test-case](.skills/write-test-case/SKILL.md)** | User asks to write tests, add test coverage, or create test cases for any class. Guides test pattern selection (simple unit, IDE fixture, ResultLoader, action mock, etc.). |
| **[review-commit](.skills/review-commit/SKILL.md)** | User asks to review a commit, analyze changes, or create a test coverage plan for recent changes. Generates a test plan document from a git commit. |

### Skill Selection Guide

- **Need to commit changes?** → `git-commit`
- **Debugging an error or failure?** → `bug-fixer`
- **Writing new tests?** → `write-test-case`
- **Reviewing what tests a commit needs?** → `review-commit`
