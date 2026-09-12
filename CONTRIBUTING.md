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
2. Follow the conventions in [`AGENTS.md`](AGENTS.md)
3. Write clear, concise commit messages
4. Include tests for new functionality
5. Ensure all tests pass
6. Update documentation as needed
7. If you touched a generated asset, run `./gradlew syncSkill` (see below)

## Development Setup

### Prerequisites

- IntelliJ IDEA 2025.2 or higher
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

## Conventions & guides

The authoritative conventions — project structure and the four-bucket dependency
graph, threading model, logging channels, testing rules — live in
[`AGENTS.md`](AGENTS.md). Link to it rather than restating it.

Step-by-step guides live in [`docs/developer/`](docs/developer/README.md); that page
index is the current list, so it stays correct as guides are added.

## Generated assets — keep them in sync

Some files under `skills/easy-yapi-assistant/` are generated from code or mirrored
from a single source. Two of the tasks are **manual** — they are not on the
`processResources` chain, so a stale copy passes a local build and only fails in CI.

```bash
./gradlew syncSkill            # runs all of the below
./gradlew syncKnowledgeBase    # docs/knowledge-base → plugin resource + skill mirror
./gradlew syncAgentCatalog     # ai/detection + ai/key-guides → skill mirror
./gradlew syncRuleKeySchemes   # reflect *RuleKeys → rule-keys.{json,md}            (MANUAL)
./gradlew syncRuleContexts     # script-object signatures → rule-contexts.{json,md} (MANUAL)
./gradlew syncSkillFacts       # tool inventory / locales / EP wiring →                     (MANUAL)
                               #   tools.md, locales.md, extensions.md
```

**If you changed a `*RuleKeys` object or a script-object signature, run
`./gradlew syncSkill` and commit the regenerated files.** The guard tests
(`RuleKeySchemeExporterTest`, `EasyYapiAssistantSkillTest`) fail when the committed
copies drift.

## Project Structure

The codebase is organized into four top-level buckets under `src/main/kotlin/com/itangcent/easyapi/`: `channel/` (output destinations), `format/` (field serialization), `framework/` (source framework exporters), and `core/` (shared infrastructure). The four buckets form a directed-acyclic dependency graph — see the [Project Structure section in README](README.md#project-structure) and [AGENTS.md §"Project Structure"](AGENTS.md#project-structure) for the authoritative layout, dependency rules, and package-placement decision rule. Step-by-step guides for adding a new extension live in [`docs/developer/`](docs/developer/README.md).

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
