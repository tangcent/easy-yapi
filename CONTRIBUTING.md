# Contributing to EasyAPI

Thank you for your interest in contributing to EasyAPI! This document provides guidelines and instructions for contributing.

## Code of Conduct

Please be respectful and constructive in all interactions with the community.

## How to Contribute

### Reporting Bugs

Before creating a bug report, please check existing issues to avoid duplicates. When creating a bug report, include:

- A clear and descriptive title
- Steps to reproduce the issue
- Expected behavior
- Actual behavior
- Your environment (IDE version, JDK version, OS)
- Screenshots if applicable

### Suggesting Enhancements

Enhancement suggestions are tracked as GitHub issues. When creating an enhancement suggestion, include:

- A clear and descriptive title
- A detailed description of the proposed functionality
- Why this enhancement would be useful
- Examples of how it would be used

### Pull Requests

1. Fork the repository and create your branch from `master`
2. Follow the project's architecture principles (see `.kiro/steering/project.md`)
3. Write clear, concise commit messages
4. Include tests for new functionality
5. Ensure all tests pass
6. Update documentation as needed

## Development Setup

### Prerequisites

- IntelliJ IDEA 2023.1 or higher
- JDK 17 or higher
- Kotlin 2.1.0

### Building the Project

```bash
./gradlew build
```

### Running Tests

```bash
./gradlew test
```

### Running the Plugin

```bash
./gradlew runIde
```

## Architecture Guidelines

### Key Principles

1. **Kotlin Coroutines**: Use structured concurrency for all async operations
2. **PSI Threading**: Methods accessing PSI must be `suspend` functions with internal read/write actions
3. **Type Safety**: Use sealed classes for type hierarchies, data classes for DTOs
4. **Dependency Injection**: Use IntelliJ `@Service` for singletons, `OperationScope` for per-operation objects

### Code Style

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Keep functions small and focused
- Add KDoc comments for public APIs
- Prefer expression bodies for simple functions

### Testing

- Write unit tests for new functionality
- Use `LightCodeInsightFixtureTestCase` for PSI tests
- Use `mockito-kotlin` for mocking
- Aim for good test coverage

## Project Structure

```
easy-api/
├── src/main/kotlin/com/itangcent/easyapi/
│   ├── core/          # Core infrastructure
│   ├── psi/           # PSI utilities
│   ├── exporter/      # Export functionality
│   ├── settings/      # Plugin settings
│   └── ide/           # IDE integration
└── src/test/kotlin/   # Tests
```

## Commit Message Guidelines

Use conventional commit format:

- `feat:` New feature
- `fix:` Bug fix
- `refactor:` Code refactoring
- `docs:` Documentation changes
- `test:` Test changes
- `chore:` Build/tooling changes

Example: `feat: add support for Kotlin sealed classes in type resolution`

## Questions?

Feel free to open an issue for any questions about contributing.
