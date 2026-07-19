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

## Class Types

### Project Service

Project-level services scoped to a specific IntelliJ project. The primary service pattern in this codebase.

```kotlin
@Service(Service.Level.PROJECT)
class MyProjectService(private val project: Project) {

    companion object {
        fun getInstance(project: Project): MyProjectService = project.service()
    }
}
```

**Examples:** [ApiScanner](file:///Users/tangcent/code/github/easy-api/src/main/kotlin/com/itangcent/easyapi/core/dashboard/ApiScanner.kt), [ApiDashboardService](file:///Users/tangcent/code/github/easy-api/src/main/kotlin/com/itangcent/easyapi/core/dashboard/ApiDashboardService.kt), [IdeaConsoleProvider](file:///Users/tangcent/code/github/easy-api/src/main/kotlin/com/itangcent/easyapi/core/logging/IdeaConsoleProvider.kt), [PostmanCollectionHelper](file:///Users/tangcent/code/github/easy-api/src/main/kotlin/com/itangcent/easyapi/channel/postman/PostmanCollectionHelper.kt), [HttpClientProvider](file:///Users/tangcent/code/github/easy-api/src/main/kotlin/com/itangcent/easyapi/core/http/HttpClientProvider.kt)

### Object Helper/Utils

Static utility `object`s providing stateless helper functions.

```kotlin
object MyHelperUtils {
    fun process(input: String): Result { /* pure computation */ }
}
```

**Examples:** [SpringMvcConstants](file:///Users/tangcent/code/github/easy-api/src/main/kotlin/com/itangcent/easyapi/framework/springmvc/SpringMvcConstants.kt), [MavenHelper](file:///Users/tangcent/code/github/easy-api/src/main/kotlin/com/itangcent/easyapi/core/util/ide/MavenHelper.kt), [ObjectModelUtils](file:///Users/tangcent/code/github/easy-api/src/main/kotlin/com/itangcent/easyapi/core/psi/model/ObjectModelUtils.kt)

## Code Style

- Follow Kotlin coding conventions
- Use meaningful variable and function names
- Keep functions small and focused
- Add KDoc comments for public APIs
- Prefer expression bodies for simple functions

## Threading Model

All PSI/VFS operations must follow IntelliJ's threading rules. Use [IdeDispatchers](file:///Users/tangcent/code/github/easy-api/src/main/kotlin/com/itangcent/easyapi/core/internal/threading/IdeDispatchers.kt).

### Dispatchers

| Dispatcher | Purpose |
|-----------|---------|
| `IdeDispatchers.ReadAction` | PSI/VFS read operations |
| `IdeDispatchers.WriteAction` | PSI/VFS write operations |
| `IdeDispatchers.Swing` | UI operations on EDT (non-modal) |
| `IdeDispatchers.Background` | General background work (network, CPU) |

### Convenience functions

```kotlin
// Suspend (switch context when needed)
suspend fun <T> read(block: suspend () -> T): T      // read action
suspend fun <T> write(block: suspend () -> T): T     // write action
suspend fun <T> swing(block: suspend () -> T): T     // EDT
suspend fun <T> background(block: suspend () -> T): T // background

// Sync (non-suspending)
fun <T> readSync(block: () -> T): T
fun <T> writeSync(block: () -> T): T
fun <T> swingSync(block: () -> T): T

// Fire-and-forget
fun backgroundAsync(block: suspend () -> Unit)
```

### Thread safety checks

```kotlin
IdeDispatchers.isReadAccessAllowed   // on read thread
IdeDispatchers.isWriteAccessAllowed  // on write thread
IdeDispatchers.isDispatchThread      // on EDT
```

### IntelliJ context propagation warning

IntelliJ wraps tasks submitted to managed executors (including `Dispatchers.Default`) with `ContextRunnable`, propagating EDT/write-intent markers across thread boundaries. **Use `IdeDispatchers.Background`** (or `actionContext.runAsync` / `backgroundAsync`) when launching from `StartupActivity` or `DumbService.runWhenSmart`, to avoid "slow operations on EDT" violations.

```kotlin
// Bad: inherits EDT context
launch(Dispatchers.Default) { /* work */ }

// Good: clean background context
backgroundAsync { /* work */ }
```

### Document threading requirements

Annotate functions with `@requires` in KDoc when they perform PSI/VFS read, PSI/VFS write, UI updates, or heavy computation:

```kotlin
/** @requires ReadAction context */
suspend fun getDocumentation(psiClass: PsiClass): String?

/** @requires WriteAction context */
suspend fun createClassFile(packageName: String, className: String): PsiClass
```

## Logging

The plugin has three output channels. **Pick one** by the first-match-wins rule below. `IdeaConsole.warn/error` and `NotificationUtils.notifyWarning/notifyError` mirror to `idea.log` automatically — so **never** pair `console.error` with `LOG.error`, nor `notifyError` with `LOG.error`. One call per event. The console is **off by default** (`logLevel=SILENT`); `console.warn`/`console.error` still mirror to `idea.log`, the rest are no-ops until the user lowers the log level.

### Channel selection (first match wins)

1. **`NotificationUtils`** — terminal outcome of a user-initiated operation the user must see right now (export success/failure, blocking precondition). Use `notifyInfo` / `notifyWarning` / `notifyError` / `notifyInfoWithLinks`. Never for intermediate progress or per-item batch failures.
2. **`IdeaConsole`** (`IdeaConsoleProvider.getInstance(project).getConsole()`) — what the plugin is doing/decided during a user-facing operation, per-item batch failures, user-fixable conditions. Levels: `info` (milestones), `warn`/`error` (recoverable/unrecoverable failures), `debug` (per-iteration detail, entry-point tracing), `trace` (fine-grained state).
3. **`IdeaLog`** (`LOG` via `IdeaLog` interface) — developer-facing diagnostic detail, or code running with no `Project` context (startup, background indexing). Always pass the throwable as the last arg.

### Placement rules (where logs MUST exist)

- **Entry points** (Actions): `console.info` on entry (action + selection); `console.error` + `NotificationUtils.notifyError` on failure; `console.info` + balloon on exit.
- **External I/O** (network/file): log target + throwable on failure before returning a failure result. Never swallow.
- **Error handling**: no silent `runCatching{}.getOrNull()` on a meaningful operation — add `.onFailure { …log with throwable… }`. No empty `catch` blocks — at minimum `LOG.info` the suppressed throwable.
- **Config & rules**: log load attempts (source + outcome), rule evaluation results, parse errors with location.
- **Decisions**: log endpoint/field skips at `info` with the reason.

### Anti-patterns (forbidden in production code)

- `LOG.error(...)` — IntelliJ treats `Logger.error` as a test failure and pops an error dialog to the user. Use `LOG.warn(msg, t)` (or `LOG.info(msg, t)` for the console/notification mirror, which already does this) instead.
- `LOG.debug(...)` / `LOG.trace(...)` — IntelliJ filters `debug`/`trace` out of `idea.log` by default; they are invisible unless a user manually enables debug logging for the `com.itangcent.easyapi` category. **`LOG.info` is the floor** for diagnostics that should be visible; use `LOG.warn` for recoverable failures. Prefer `console.info` over `console.debug`/`console.trace` (reserved for opt-in high-volume trace). A CI gate (`AntiPatternGateTest.noDebugTraceOnLogChannel`) enforces this.
- `println(...)` / `printStackTrace()`.
- Direct `Notifications.Bus.notify` / `NotificationGroupManager` outside `NotificationUtils`.
- `runCatching{}.getOrNull()` / empty `catch` without a log on a meaningful operation.
- Stringifying the throwable into the message — always pass it as the last arg.

### IdeaLog interface

Implement `IdeaLog` ([core/logging/IdeaLog.kt](file:///Users/tangcent/code/github/easy-api/src/main/kotlin/com/itangcent/easyapi/core/logging/IdeaLog.kt)) to get a `LOG` property; do **not** call `Logger.getLogger()` directly.

```kotlin
class MyService : IdeaLog {
    fun doSomething() {
        LOG.info("Processing started")
        LOG.warn("Unexpected condition", exception)
    }
}
```

## User Interaction

### Messages — simple blocking dialogs

Use `com.intellij.openapi.ui.Messages` for simple input, selection, and confirmation dialogs.

```kotlin
Messages.showInfoMessage(project, "Operation completed", "Success")
Messages.showErrorDialog(project, "Failed to export: ${error.message}", "Export Error")
Messages.showWarningDialog("Please enter a valid token", "Invalid Input")

val input = Messages.showInputDialog(project, "Enter URL", "Remote Config", Messages.getInformationIcon())

val selected = Messages.showChooseDialog(
    project, "Select format", "Export Format",
    Messages.getQuestionIcon(), arrayOf("Markdown", "Postman", "cURL"), "Markdown"
)

val choice = Messages.showYesNoCancelDialog("Overwrite?", "Confirm", Messages.getQuestionIcon())
```

**Example:** [DefaultHttpContextCacheHelper](file:///Users/tangcent/code/github/easy-api/src/main/kotlin/com/itangcent/easyapi/core/cache/http/DefaultHttpContextCacheHelper.kt) — host selection dialog.

### DialogWrapper — complex multi-field dialogs

Extend `DialogWrapper` for multi-field forms and custom layouts.

```kotlin
class MyConfigDialog(private val project: Project) : DialogWrapper(project) {
    private val nameField = JBTextField()

    init {
        title = "Configure Export"
        init()
    }

    override fun createCenterPanel(): JComponent = JPanel().apply {
        add(JBLabel("Name:")); add(nameField)
    }

    override fun doOKAction() {
        if (nameField.text.isBlank()) {
            Messages.showWarningDialog("Name is required", "Validation Error"); return
        }
        super.doOKAction()
    }
}
```

**Examples:** [ExportDialog](file:///Users/tangcent/code/github/easy-api/src/main/kotlin/com/itangcent/easyapi/core/ide/dialog/ExportDialog.kt), [ScriptExecutorDialog](file:///Users/tangcent/code/github/easy-api/src/main/kotlin/com/itangcent/easyapi/core/ide/script/ScriptExecutorDialog.kt)

## Project Structure

The codebase is organized into four top-level buckets under `src/main/kotlin/com/itangcent/easyapi/`. Adding a new channel, format, or framework is a one-package operation plus one `plugin.xml` line — see the [Package Layout](#package-layout) rule below for where new code goes.

```
com.itangcent.easyapi/
├── channel/                         # OUTPUT — export destinations
│   ├── spi/                         #   Channel EP contract + registry + shared scaffolding
│   │   ├── Channel.kt
│   │   ├── ChannelConfig.kt
│   │   ├── ChannelOptionsPanel.kt
│   │   ├── ChannelRegistry.kt
│   │   └── PlaceholderSyntaxConverter.kt
│   ├── curl/                        #   one package per channel
│   ├── hoppscotch/  (+ model/)
│   ├── httpclient/
│   ├── markdown/    (+ template/)
│   └── postman/     (+ model/)
│
├── format/                          # FIELD/OBJECT SERIALIZATION
│   ├── spi/                         #   FieldFormatChannel EP + registry + actions + shared exts
│   │   ├── FieldFormatChannel.kt
│   │   ├── FieldFormatChannelRegistry.kt
│   │   ├── FieldFormatAction.kt
│   │   ├── FieldFormatActionGroup.kt
│   │   └── FieldFormatExtensions.kt   # the to toJson/toJson5/toYaml/toProperties entry fns
│   ├── json/                        #   ObjectModelJsonConverter + Builder + Handlers + Value converter
│   ├── json5/                       #   Json5Handler (subset of json/ — see Decision F2)
│   ├── yaml/                        #   YamlFormatter
│   └── properties/                  #   PropertiesFormatter
│
├── framework/                       # INPUT — source framework exporters
│   ├── spi/                         #   Framework EP contract + registry (mirror of channel/spi, format/spi)
│   │   └── FrameworkRegistry.kt     #     single chokepoint for framework enablement, mirrors ChannelRegistry
│   ├── springmvc/                   #   Spring MVC + Actuator
│   ├── jaxrs/
│   ├── feign/
│   └── grpc/                        #   class exporter + type parser + service recognizer — runtime plumbing is core/grpc
│
└── core/                            # SHARED INFRASTRUCTURE (the umbrella)
    ├── internal/                    #   relocated narrow core/ (services, event, threading)
    │   ├── event/
    │   └── threading/
    ├── export/                      #   the neutral pipeline (see Decision C2)
    │   ├── ClassExporter.kt
    │   ├── ClassExporterRegistry.kt
    │   ├── EndpointBuilder.kt
    │   ├── ExportOrchestrator.kt
    │   ├── ApiModels.kt  ExportContext.kt  ExportMetadata.kt
    │   ├── ExportResult.kt  Extension.kt  PathSelector.kt
    │   └── recognizer/  # ApiClassRecognizer, CompositeApiClassRecognizer, MetaAnnotationResolver
    ├── psi/        (+ adapter/ doc/ helper/ model/ type/)  # ObjectModel lives here; format/ consumes it
    ├── config/     (+ model/ parser/ resource/ source/)
    ├── rule/       (+ context/ engine/ parser/)
    ├── http/
    ├── logging/
    ├── ide/        (+ action/ dialog/ linemarker/ script/ search/ support/)  # NO fieldformat/
    ├── dashboard/
    ├── script/     (+ env/ pm/)
    ├── util/       (+ file/ ide/ json/ storage/ text/)  # FormatterHelper stays — see Decision F1
    ├── cache/      (+ api/ http/ json/)
    ├── settings/   (+ migration/ module/ state/ ui/)
    ├── ai/         (+ agent/ credentials/ tools/ ui/)
    ├── grpc/       # runtime plumbing (descriptor reflection, proto) — peer of framework/grpc's consumer
    ├── repository/
    └── extension/
```

The narrow original `core/` package (services, events, threading) was renamed to `core/internal/` so `core/` could serve as the umbrella for all shared infrastructure. The `core.export/` sub-package is the **neutral pipeline home** — neither `framework/` (input) nor `channel/` (output) owns the `ClassExporter` SPI; it lives at `core.export/` so both can depend on it without pointing up to a sibling bucket. The four buckets form a DAG: `channel` may import from `format`, `framework`, and `core`; `format` and `framework` may import from `core` (and `core.grpc/` for `framework.grpc`); `core` imports only EP-contract seams (`channel.spi.*`, `format.spi.*`, `framework.spi.*`, `core.export.*`) from its siblings — concrete per-id implementations (`channel.<id>.*`, `format.<id>.*`, `framework.<id>.*`) imported from `core.*` are forbidden. The `framework.spi.*` carve-out (Decision CO3 broadened by the package-restructure-patch) is the single exception that lets `core.*` resolve framework enablement via `FrameworkRegistry` without pointing at a concrete `framework.<id>.*` implementation.

### Package Layout

When adding new code, apply this decision rule (first match wins) to pick the bucket:

1. **One output destination** (Postman, Markdown, cURL, Hoppscotch, IntelliJ HTTP Client, …) → `channel/<id>/`
2. **One field serialization format** (JSON, JSON5, YAML, Properties, TOML, …) → `format/<id>/`
3. **One source framework** (Spring MVC, JAX-RS, Feign, gRPC, Micronaut, …) → `framework/<id>/`
4. **Else** — shared by ≥2 buckets, or runtime/IDE plumbing with no extension target → `core/<sub-package>/`

The four top-level buckets are named explicitly: `channel`, `format`, `framework`, `core`. Code shared across exactly two buckets but with no natural home elsewhere lives in `core/` (e.g. the pipeline in `core/export/` is used by both `framework/` and `channel/`).

**EP-contract sub-packages.** Each input/output bucket owns a `spi/` sub-package for its EP contract surfaces, which `core.*` may legitimately import (the only sibling imports `core.*` allows): `channel.spi.*` (Channel + ChannelRegistry), `format.spi.*` (FieldFormatChannel + FieldFormatChannelRegistry), `framework.spi.*` (FrameworkRegistry — added by the package-restructure-patch under the broadened CO3 rule). Concrete per-id implementations (`channel.<id>.*`, `format.<id>.*`, `framework.<id>.*`) imported from `core.*` remain forbidden.

Step-by-step guides for adding a new channel/format/framework live in `docs/developer/`.

## Testing

Tests use JUnit 4 + Mockito/Mockito-Kotlin + the IntelliJ Platform Test Framework. **Always invoke the `write-test-case` skill before writing tests** — it guides test-pattern selection (simple unit, IDE fixture, ResultLoader, action mock, parity test) based on the target class. The brief notes below are only a reminder; the skill is the authoritative guide.

- Run tests: `./gradlew test`
- Base classes: `EasyApiLightCodeInsightFixtureTestCase` (PSI/Project-aware), plain JUnit for pure utilities
- Mocking: `mockito-kotlin`
- **Cross-platform golden-file rule:** Never read expected-output resources with `File.readText()`. Use `ResultLoader.load()` (trailing-trimmed) or `ResourceLoader.readRaw()` (strict byte parity) — both collapse CRLF→LF so snapshot tests pass on Windows CI where `core.autocrlf` may convert the on-disk golden. `.gitattributes` enforces `eol=lf`; do not weaken it.

## Tooling

- **`codegraph` MCP index refresh:** After a refactor that moves/renames files, refresh the index with `codegraph sync` (incremental) or `codegraph index` (full rebuild) from the project root. The `codegraph_explore` MCP tool is read-only and does not refresh the index.

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
