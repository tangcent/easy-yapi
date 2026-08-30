## Description

<!-- Provide a brief description of the changes in this PR -->

## Type of Change

<!-- Mark the relevant option with an "x" -->

- [ ] Bug fix (non-breaking change which fixes an issue)
- [ ] New feature (non-breaking change which adds functionality)
- [ ] Breaking change (fix or feature that would cause existing functionality to not work as expected)
- [ ] Refactoring (no functional changes)
- [ ] Documentation update
- [ ] Performance improvement
- [ ] Test coverage improvement

## Related Issues

<!-- Link to related issues using #issue_number -->

Fixes #

## Affected Area

<!-- Which entry points / capability does this change touch? -->

- [ ] YApi export
- [ ] Postman export
- [ ] Markdown export
- [ ] Send HTTP request (Call)
- [ ] API scanning / dashboard
- [ ] Rule engine / custom rules / config
- [ ] Settings / UI
- [ ] Other / not user-facing

## Changes Made

<!-- List the main changes made in this PR -->

-

-

## How I Tested

<!-- Describe the tests you ran to verify your changes -->

- [ ] `./gradlew test` passes
- [ ] Manual verification in a sandbox IDE (`./gradlew runIde`) — describe the entry action exercised
      (e.g. "Right-click a Spring controller → EasyYapi → ExportMarkdown, checked the generated markdown")
- [ ] New tests added for new functionality

## Architecture & Threading Checklist

<!-- Mirrors the rules in AGENTS.md — if an item doesn't apply, note why instead of deleting it -->

- [ ] New code lives in the right bucket (`channel/` / `format/` / `framework/` / `core/` per the Package Layout rule in AGENTS.md); no `core.*` → concrete sibling imports
- [ ] PSI/VFS reads wrapped in `read {}` / `readSync {}` — or documented with `@requires ReadAction` **and** all callers are internal (never on a boundary class)
- [ ] Boundary classes (script contexts handed to Groovy, EP implementations) self-protect every public member — including implicit `toString()`/`hashCode()`/`equals` and reachable `by lazy` initializers
- [ ] PSI writes and UI updates go through `write {}` / `swing {}`; never bypassed
- [ ] No new `launch(Dispatchers.Default)` from EDT / startup contexts — background work uses `backgroundAsync` / `IdeDispatchers.Background`

## Logging Checklist

<!-- Mirrors the Logging rules in AGENTS.md -->

- [ ] Exactly one channel per event (`NotificationUtils` / `IdeaConsole` / `IdeaLog`, first-match-wins) — no `console.error` + `LOG.error` or `notifyError` + `LOG.error` pairing
- [ ] No `LOG.error`, `LOG.debug`, `LOG.trace`, `println`, or `printStackTrace()` in production code (`LOG.info` is the floor)
- [ ] Throwables passed as the last argument, never stringified into the message; no silent `runCatching{}.getOrNull()` or empty `catch` on meaningful operations

## General Checklist

- [ ] My code follows the project's architecture principles
- [ ] I have performed a self-review of my code
- [ ] I have commented my code, particularly in hard-to-understand areas
- [ ] I have made corresponding changes to the documentation
- [ ] My changes generate no new warnings
- [ ] I have added tests that prove my fix is effective or that my feature works
- [ ] New and existing unit tests pass locally with my changes

## Screenshots (if applicable)

<!-- Add screenshots to help explain your changes -->

## Additional Notes

<!-- Add any additional notes or context about the PR -->
