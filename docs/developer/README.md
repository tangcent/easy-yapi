# EasyApi Developer Guides

Step-by-step guides for contributors adding a new **channel**, **format**, or
**framework** to the EasyApi IntelliJ plugin. These pages cover the SPI
contracts, the `plugin.xml` wiring, the enablement model, threading/logging
conventions, and worked examples — everything you need to ship a new
extension end-to-end.

> **End user?** The user / rule-author docs live in
> [`docs/knowledge-base/`](../knowledge-base/README.md) (rule files, settings,
> usage workflows). This developer suite is for contributors writing Kotlin
> that extends the plugin itself.

## Pages

| Page | What you'll learn |
|------|-------------------|
| [Channels](channels.md) | Add a new output destination (Postman variant, Insomnia, …) — convert `ApiEndpoint` models into a target format and write/upload the result. |
| [Formats](formats.md) | Add a new field serialization (TOML, XML, …) — render an `ObjectModel` to a target representation and wire a `FieldsTo*` action. |
| [Frameworks](frameworks.md) | Add a new source framework (Micronaut, …) — scan PSI for endpoints and feed them into the export pipeline. |

## Who this is for

You should already be familiar with:

- Kotlin coroutines (`suspend` functions, structured concurrency).
- IntelliJ Platform basics — `Project`, `PsiClass`, extension points,
  `@Service(Service.Level.PROJECT)`.
- The four-bucket package layout described in
  [AGENTS.md §"Project Structure"](../../AGENTS.md#project-structure) —
  `channel/`, `format/`, `framework/`, `core/`.

If you're adding **user-facing rule keys** or **per-project config** (no new
Kotlin), see the [Rule Authoring Guide](../knowledge-base/rule-guide.md)
instead.

## The three extension points at a glance

EasyApi exposes **three** IntelliJ extension points (EPs) for plugging in new
behavior. A framework registers on **two** EPs (`classExporter` +
`apiClassRecognizer`) — see [Frameworks](frameworks.md) for why.

| EP name (`plugin.xml`) | Interface FQN | Bucket | Scope | What it does |
|---|---|---|---|---|
| `channel` | `com.itangcent.easyapi.channel.spi.Channel` | `channel/` | `area="IDEA_PROJECT"` | Convert `ApiEndpoint`s to an output format and write/upload the result. |
| `fieldFormatChannel` | `com.itangcent.easyapi.format.spi.FieldFormatChannel` | `format/` | application (no `area`) | Serialize an `ObjectModel` to a target representation; auto-registered as a `FieldsTo*` action. |
| `classExporter` | `com.itangcent.easyapi.core.export.ClassExporter` | `framework/` | `area="IDEA_PROJECT"` | Extract `ApiEndpoint`s from a `PsiClass` for one source framework. |
| `apiClassRecognizer` | `com.itangcent.easyapi.core.export.recognizer.ApiClassRecognizer` | `framework/` | `area="IDEA_PROJECT"` | Cheap "is this an API class?" check; drives line markers, index scanning, AI discovery, and enablement. |

All four EPs are declared `dynamic="true"` so they can be loaded/unloaded
without a restart. The full declaration block lives at
[`src/main/resources/META-INF/plugin.xml`](../../src/main/resources/META-INF/plugin.xml#L23-L32):

```xml
<extensionPoints>
    <extensionPoint name="classExporter"     interface="com.itangcent.easyapi.core.export.ClassExporter"            area="IDEA_PROJECT" dynamic="true"/>
    <extensionPoint name="channel"           interface="com.itangcent.easyapi.channel.spi.Channel"                  area="IDEA_PROJECT" dynamic="true"/>
    <extensionPoint name="fieldFormatChannel" interface="com.itangcent.easyapi.format.spi.FieldFormatChannel"        dynamic="true"/>
    <extensionPoint name="apiClassRecognizer" interface="com.itangcent.easyapi.core.export.recognizer.ApiClassRecognizer" area="IDEA_PROJECT" dynamic="true"/>
</extensionPoints>
```

### EP scope: `IDEA_PROJECT` vs application

The `area` attribute is **load-bearing** — getting it wrong produces an
instantiation failure at startup.

- **`area="IDEA_PROJECT"`** (`channel`, `classExporter`, `apiClassRecognizer`)
  → IntelliJ creates a separate instance per project and injects `Project` via
  the constructor. Your constructor signature must be
  `class MyXxx(private val project: Project)`.
- **Application scope** (`fieldFormatChannel`, no `area` attribute) → IntelliJ
  constructs the instance with a **no-arg constructor**. `Project` arrives via
  the `format(project, psiClass)` parameter on each call.

Each topic page's "Step 1" repeats the required constructor signature for its
EP — don't skip it.

## The dependency DAG

The four top-level buckets form a directed-acyclic dependency graph:

```
                 ┌────────────────────────────────────────┐
                 │                  core/                   │
                 │  (export pipeline, psi, rule, settings, │
                 │   ide, logging, util, …)                │
                 └────┬───────────┬───────────┬────────────┘
                      │           │           │
        EP-contract   │           │           │   EP-contract
        seams only    │           │           │   seams only
                      ▼           ▼           ▼
                 ┌─────────┐ ┌─────────┐ ┌─────────────┐
                 │ format/ │ │framework/│ │  channel/   │
                 │         │ │          │ │             │
                 │  JSON   │ │  Spring  │ │  Markdown   │
                 │  YAML   │ │  JAX-RS  │ │  Postman    │
                 │  …      │ │  Feign   │ │  cURL       │
                 │         │ │  gRPC    │ │  Hoppscotch │
                 └─────────┘ └──────────┘ └─────────────┘
                      │                       │
                      └─────► channel ◄──────┘
                       (channel may import format)
```

**Import rules** (authoritative in
[AGENTS.md §"Project Structure"](../../AGENTS.md#project-structure)):

- `channel/` may import from `format`, `framework`, and `core` (via the
  `*.spi.*` seams — never concrete `format.<id>.*` / `framework.<id>.*`).
- `format/` and `framework/` may import from `core` (and `core.grpc/` for
  `framework.grpc`).
- `core/` imports **only** EP-contract seams from its siblings:
  `channel.spi.*`, `format.spi.*`, `framework.spi.*`, `core.export.*`.
  Concrete per-id packages (`channel.<id>.*`, `format.<id>.*`,
  `framework.<id>.*`) imported from `core.*` are **forbidden** — this is
  CI-enforced.

The DAG rule is the single most common review feedback on a new extension.
Each topic page restates the per-bucket import allow-list so you don't have to
flip back here.

## Package-layout decision rule

When adding a new package, apply this **first-match-wins** rule to pick the
bucket (mirrors [AGENTS.md §"Package Layout"](../../AGENTS.md#package-layout)):

1. **One output destination** (Postman, Markdown, cURL, Hoppscotch, IntelliJ
   HTTP Client, …) → `channel/<id>/`
2. **One field serialization format** (JSON, JSON5, YAML, Properties, TOML, …)
   → `format/<id>/`
3. **One source framework** (Spring MVC, JAX-RS, Feign, gRPC, Micronaut, …)
   → `framework/<id>/`
4. **Else** — shared by ≥2 buckets, or runtime/IDE plumbing with no extension
   target → `core/<sub-package>/`

Each input/output bucket also owns a `spi/` sub-package for its EP contract
surfaces, which `core.*` may legitimately import (the only sibling imports
`core.*` allows).

## `plugin.xml` basics

Each EP has **two** appearances in
[`plugin.xml`](../../src/main/resources/META-INF/plugin.xml):

1. **`<extensionPoints>`** (~L23-32) — declares the EP name, interface FQN,
   scope, and `dynamic="true"`. This block is owned by EasyApi core; you
   should not need to add a new entry here unless you're inventing a brand-new
   EP category.
2. **`<extensions defaultExtensionNs="com.itangcent.idea.plugin.easy-api">`**
   (~L34-56) — registers concrete implementations against the EPs declared
   above. **This is where your `<channel ... />`,
   `<fieldFormatChannel ... />`, `<classExporter ... />`, or
   `<apiClassRecognizer ... />` line goes.**

A new channel/format/framework needs exactly one (or, for frameworks, two)
`<… implementation="…"/>` line(s) here — no `<action>`, no
`<applicationService>`, no other XML wiring. The action menu entry, settings
tab, and registry discovery are all auto-wired by the SPI.

## Shared concerns

The following cross-cutting rules apply to **all three** extension kinds.
They're written once here and linked from each topic page so they don't
drift.

### Threading

All PSI/VFS access must run on the correct IntelliJ dispatcher. Use
[`IdeDispatchers`](../../src/main/kotlin/com/itangcent/easyapi/core/internal/threading/IdeDispatchers.kt):

| Dispatcher | Purpose |
|-----------|---------|
| `IdeDispatchers.ReadAction` | PSI/VFS read operations |
| `IdeDispatchers.WriteAction` | PSI/VFS write operations |
| `IdeDispatchers.Swing` | UI operations on EDT (non-modal) |
| `IdeDispatchers.Background` | General background work (network, CPU) |

Convenience wrappers (defined on `IdeDispatchers`):

```kotlin
suspend fun <T> read(block: suspend () -> T): T      // ReadAction
suspend fun <T> write(block: suspend () -> T): T    // WriteAction
suspend fun <T> swing(block: suspend () -> T): T    // EDT
suspend fun <T> background(block: suspend () -> T): T // Background
fun backgroundAsync(block: suspend () -> Unit)       // fire-and-forget
```

**Rule of thumb:** every method on your SPI that touches `PsiClass` /
`PsiMethod` should be `suspend` and wrap PSI reads in `read { … }`. Network
and file I/O belongs in `background { … }`; modal dialogs and file choosers
belong in `swing { … }`.

The full threading model — including the IntelliJ context-propagation warning
for `StartupActivity` and the `@requires` KDoc convention — is normative in
[AGENTS.md §"Threading Model"](../../AGENTS.md#threading-model). Link to it;
don't paraphrase.

### Logging

Implement [`IdeaLog`](../../src/main/kotlin/com/itangcent/easyapi/core/logging/IdeaLog.kt)
to get a `LOG` property; do **not** call `Logger.getLogger()` directly.

Hard rules (CI-enforced by `AntiPatternGateTest`):

- **`LOG.error(...)` is forbidden** — IntelliJ treats it as a test failure
  and pops an error dialog. Use `LOG.warn(msg, t)` instead.
- **`LOG.debug(...)` / `LOG.trace(...)` are forbidden** — IntelliJ filters
  them out of `idea.log` by default. `LOG.info` is the floor.
- **No `println(...)` / `printStackTrace()`.**
- **No `runCatching{}.getOrNull()`** on a meaningful operation without a
  `.onFailure { LOG.warn(...) }`. No empty `catch` blocks.
- **Pass the throwable as the last arg** — never stringify it into the
  message.

Three output channels exist; pick **one** by first-match-wins:

1. `NotificationUtils` — terminal user-visible outcome (export success/failure).
2. `IdeaConsole` (via `IdeaConsoleProvider.getInstance(project).getConsole()`)
   — what the plugin is doing/decided, per-item batch failures,
   user-fixable conditions.
3. `IdeaLog` (`LOG` via `IdeaLog`) — developer-facing diagnostic detail, or
   code running with no `Project` context.

The full channel-selection rule, anti-pattern list, and placement rules are
normative in [AGENTS.md §"Logging"](../../AGENTS.md#logging). Defer to it.

### Enablement model

All three EPs share an identical enablement pattern. A new extension is
enabled/disabled by the user via Settings → General → "Export Channels" /
"Field Format Channels" / "Framework Support". The plumbing is identical in
all three cases:

1. **`enabledByDefault`** on the SPI (a `val` with `get() = true` default) —
   the compile-time default.
2. **Two arrays on `GeneralSettings`** — `enabledX` / `disabledX`
   (e.g. `enabledChannels` / `disabledChannels`,
   `enabledFieldFormatChannels` / `disabledFieldFormatChannels`,
   `enabledFrameworks` / `disabledFrameworks`).
3. **A `*Registry.isEnabled(...)`** method on the corresponding registry
   (`ChannelRegistry`, `FieldFormatChannelRegistry`, `FrameworkRegistry`)
   that overlays the stored preference on `enabledByDefault`.

The resolution truth table is the same in all three registries — extracted as
a pure `internal companion fun resolveEnabled(...)` so unit tests can exercise
it without a `Project`:

```kotlin
internal fun resolveEnabled(
    ext: /* Channel | FieldFormatChannel | ApiClassRecognizer */,
    enabledIds: Array<String>,
    disabledIds: Array<String>
): Boolean =
    ext.id in enabledIds ||
        (ext.enabledByDefault && ext.id !in disabledIds)
```

> **Explicit-on wins.** If the id is in `enabledIds`, the extension is on
> regardless of `enabledByDefault`. If the id is in `disabledIds`, the
> extension is off unless explicitly enabled. Absence in both arrays falls
> back to `enabledByDefault`.

**Recipe for an experimental extension:** override
`val enabledByDefault: Boolean get() = false`. The extension is hidden from
all surfaces until the user opts in via Settings; no other wiring is needed.
See `HoppscotchChannel` (`enabledByDefault = false`) and the Feign / Actuator /
gRPC recognizers for examples.

### Testing

- **JUnit 4 + mockito-kotlin** for all tests.
- **Pure registry rules** (e.g. `ChannelRegistry.resolveEnabled`) are
  extracted as `internal companion fun` so they can be unit-tested without
  a `Project` / `plugin.xml`.
- **PSI / Project-aware tests** extend `EasyApiLightCodeInsightFixtureTestCase`
  (the project's base class for `LightCodeInsightFixtureTestCase`).
- **Cross-platform golden-file rule:** never read expected-output resources
  with `File.readText()`. Use `ResultLoader.load()` (trailing-trimmed) or
  `ResourceLoader.readRaw()` (strict byte parity) — both collapse CRLF→LF so
  snapshot tests pass on Windows CI.

**Always invoke the `write-test-case` skill before writing tests** — it
guides test-pattern selection (simple unit, IDE fixture, ResultLoader,
action mock, parity test) based on the target class. See
[AGENTS.md §"Testing"](../../AGENTS.md#testing) for the brief reminder.

## Table of contents

- [Channels — adding a new output destination](channels.md)
- [Formats — adding a new field serialization](formats.md)
- [Frameworks — adding a new source framework](frameworks.md)
