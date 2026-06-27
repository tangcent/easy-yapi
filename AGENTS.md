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

## Logging

The plugin has three output channels:

- **`LOG`** (`IdeaLog` → `idea.log`) — background recording. **Use this in the vast majority of cases.**
- **`NotificationUtils`** (balloon toast, bottom-right) — progress updates and task-completion prompts.
- **`IdeaConsole`** (EasyAPI tool window) — diagnostic overlay, **off by default** (`logLevel=SILENT`). Use for errors/exceptions, operation failures, and verbose trace. `console.warn`/`console.error` always mirror to `LOG.warn` — failures are never lost.

### Rules

1. **Never call `LOG.error`.** IntelliJ's `Logger.error` triggers an intrusive error-report popup (and throws `TestLoggerAssertionError` in tests). If error-level severity is needed, use `LOG.warn` as the fallback.
2. **Never call `LOG.debug` / `LOG.trace`.** IntelliJ filters `debug`/`trace` out of `idea.log` by default — they are invisible unless a user manually enables debug logging for the `com.itangcent.easyapi` category (Help → Diagnostic Tools → Debug Log Settings), an obscure opt-in that is never on in practice. **`LOG.info` is the floor** for diagnostics that should be visible when investigating bugs; use `LOG.warn` for recoverable failures. A CI gate test (`AntiPatternGateTest.noDebugTraceOnLogChannel`) enforces this.
3. **Default to `LOG`.** Routine milestones, per-item decisions, diagnostic detail, and recoverable failures all go to `LOG` (`info` or `warn`).
4. **Use `NotificationUtils` for progress and completion.** "Export started", "Upload complete", "Export failed".
5. **Console is a diagnostic overlay, off by default.** Use `console.warn(msg, t)` for operation failures, `console.error(msg, t)` for errors/exceptions, `console.info` for diagnostics. Prefer `console.info` over `console.debug`/`console.trace` — at SILENT (default) the console is bypassed and [IdeaLogConsole] floors those levels to `LOG.info` anyway, so `debug`/`trace` carry no filtering benefit on this channel either. `console.debug`/`console.trace` are reserved for genuine high-volume trace that the user has explicitly opted into by lowering `logLevel` to DEBUG/TRACE (tool-window mode). Users enable the console by lowering `logLevel`.
6. **One call per event.** `IdeaConsole.warn/error` and `NotificationUtils.notifyWarning/notifyError` mirror to `idea.log` automatically — do not also write a `LOG.*` call.
7. **Always pass the throwable** as the last arg to `LOG.warn` / `console.warn` / `console.error` / `notifyError`.

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
