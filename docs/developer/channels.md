# Adding a new channel

A **channel** converts `ApiEndpoint` models into a specific output format and
handles file write or remote upload. Adding a new one is a one-package
operation plus one `plugin.xml` line.

> **Shared concerns** (threading, logging, enablement, testing) are in the
> [developer README](README.md). This page covers channel-specific concerns
> only.

## When to write a channel

Write a channel when you have a new **output destination** for `ApiEndpoint`
models. The 5 built-in channels:

| Channel | HTTP | gRPC | Output |
|---------|:----:|:----:|--------|
| [MarkdownChannel](../../src/main/kotlin/com/itangcent/easyapi/channel/markdown/MarkdownChannel.kt) | ✓ | ✓ | `.md` documentation file |
| [PostmanChannel](../../src/main/kotlin/com/itangcent/easyapi/channel/postman/PostmanChannel.kt) | ✓ | — | JSON file or direct upload to Postman |
| [CurlChannel](../../src/main/kotlin/com/itangcent/easyapi/channel/curl/CurlChannel.kt) | ✓ | ✓ | Executable shell script |
| [HttpClientChannel](../../src/main/kotlin/com/itangcent/easyapi/channel/httpclient/HttpClientChannel.kt) | ✓ | ✓ | IntelliJ HTTP Client scratch file |
| [HoppscotchChannel](../../src/main/kotlin/com/itangcent/easyapi/channel/hoppscotch/HoppscotchChannel.kt) | ✓ | — | JSON file or direct upload to Hoppscotch |

If you just need to *serialize fields* of a `PsiClass` to a new representation
(JSON, YAML, …), that's a [format](formats.md), not a channel.

## The `Channel` SPI

The full contract lives in
[`channel/spi/Channel.kt`](../../src/main/kotlin/com/itangcent/easyapi/channel/spi/Channel.kt).
The EP is `area="IDEA_PROJECT"`, but in-tree channels use a **no-arg
constructor** and receive `Project` via the `ExportContext` parameter (see
`MarkdownChannel`, `CurlChannel`, etc.).

| Member | Type | Default | Purpose |
|---|---|---|---|
| `id` | `String` | — | Unique identifier (`"markdown"`, `"postman"`, …). |
| `displayName` | `String` | — | Human-readable name shown in UI. |
| `supportsHttp` | `Boolean` | `true` | Whether HTTP/REST endpoints are supported. |
| `supportsGrpc` | `Boolean` | `false` | Whether gRPC endpoints are supported. |
| `exposeAsAction` | `Boolean` | `false` | Add a top-level IDE action menu entry. |
| `actionText` | `String?` | `null` | Action menu text (only when `exposeAsAction = true`). |
| `enabledByDefault` | `Boolean` | `true` | Compile-time enablement default. Set `false` for experimental. |
| `settingsTabOrder` | `Int` | `100` | Hint for settings-tab ordering (lower = earlier). |
| `settingsType` | `KClass<out Settings>?` | `null` | Optional: the settings module type this channel owns. |
| `export(context)` | `suspend fun` | — | Performs the export. Required. |
| `handleResult(project, result, config)` | `suspend fun → Boolean` | `false` | Post-export hook (file write, browser open, …). Return `true` to suppress default handling. |
| `createOptionsPanel(project)` | `fun → ChannelOptionsPanel?` | `null` | Per-export options panel (shown in export dialog). |
| `createSettingsPanel(project)` | `fun → SettingsPanel<*>?` | `null` | Persistent settings tab (Settings → EasyApi → <Channel>). |
| `configFiles()` | `fun → List<String>` | `emptyList()` | Config-file names contributed to the extension-config fallback list. |
| `ruleKeys()` | `fun → List<RuleKey<*>>` | `emptyList()` | Channel-specific `RuleKey`s (consumed by the AI agent). |
| `isAvailableFor(endpoints)` | `fun → Boolean` | derived | Whether this channel can handle the given endpoints. |

## Step-by-step

### Step 1 — Package + `Channel` class

Create `channel/<id>/` with a `Channel` implementation. **Constructor must be
no-arg** (the EP doesn't inject `Project`); you receive `Project` via
`ExportContext.project` inside `export()`.

Minimal skeleton (mirrors
[`MarkdownChannel`](../../src/main/kotlin/com/itangcent/easyapi/channel/markdown/MarkdownChannel.kt#L57-L67)):

```kotlin
package com.itangcent.easyapi.channel.example

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.channel.spi.Channel
import com.itangcent.easyapi.channel.spi.ChannelConfig
import com.itangcent.easyapi.core.export.ExportContext
import com.itangcent.easyapi.core.export.ExportResult
import com.itangcent.easyapi.core.logging.IdeaLog

class ExampleChannel : Channel, IdeaLog {
    override val id: String = "example"
    override val displayName: String = "Example"
    override val supportsGrpc: Boolean = false
    override val exposeAsAction: Boolean = false

    override suspend fun export(context: ExportContext): ExportResult {
        LOG.info("ExampleChannel.export: endpoints=${context.endpointsToExport.size}")
        val content = buildContent(context.endpointsToExport)
        return ExportResult.Success(
            count = context.endpointsToExport.size,
            target = "Example",
            metadata = ExampleExportMetadata(content = content)
        )
    }
}
```

### Step 2 — `plugin.xml` line

Add one line under `<extensions defaultExtensionNs="...">` in
[`plugin.xml`](../../src/main/resources/META-INF/plugin.xml#L48-L52):

```xml
<channel implementation="com.itangcent.easyapi.channel.example.ExampleChannel"/>
```

That's the minimum — the channel is now discoverable via `ChannelRegistry`,
auto-listed in Settings → General → "Export Channels", and offered as an
output in the export dialog.

### Step 3 — Config + options panel (optional)

`ChannelConfig` is **`open`, not `sealed`** (see
[`channel/spi/ChannelConfig.kt`](../../src/main/kotlin/com/itangcent/easyapi/channel/spi/ChannelConfig.kt)).
Declare a `data class` subtype in your channel's own package — zero core
edits. Consumers use `as?` casts (a `when`-exhaustive switch would be a
compile error).

```kotlin
package com.itangcent.easyapi.channel.example

import com.itangcent.easyapi.channel.spi.ChannelConfig

data class ExampleConfig(
    val outputDir: String? = null,
    val fileName: String? = null,
    val includeComments: Boolean = true,
) : ChannelConfig()
```

For channels that need only file output, reuse the SPI's
`ChannelConfig.FileConfig` / `ChannelConfig.Empty` — no subclass needed.

The options panel lives in the same package and implements
[`ChannelOptionsPanel`](../../src/main/kotlin/com/itangcent/easyapi/channel/spi/ChannelOptionsPanel.kt):

```kotlin
interface ChannelOptionsPanel {
    val component: JComponent
    fun buildConfig(): ChannelConfig
    fun onShown() {}  // optional — initialize defaults / refresh state
}
```

Wire it via `override fun createOptionsPanel(project: Project): ChannelOptionsPanel?`.
References: [`CurlOptionsPanel`](../../src/main/kotlin/com/itangcent/easyapi/channel/curl/CurlOptionsPanel.kt)
(uses `FormBuilder`), `MarkdownOptionsPanel` (uses `BorderLayout`).

### Step 4 — Settings tab (optional)

Persistent settings live in a `data class ... : Settings` with
`@StorageScope(Scope.APPLICATION)` on every `var` (all defaulted). Read via
`project.settings<XxxSettings>()`, write via
`SettingBinder.getInstance(project).update(XxxSettings::class) { }`.

```kotlin
package com.itangcent.easyapi.channel.example

import com.itangcent.easyapi.core.settings.Scope
import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.core.settings.StorageScope

data class ExampleSettings(
    @StorageScope(Scope.APPLICATION) var host: String = "https://api.example.com",
    @StorageScope(Scope.APPLICATION) var includeComments: Boolean = true,
) : Settings
```

The settings panel **must be typed as `SettingsPanel<Settings>`** (not
`SettingsPanel<XxxSettings>`) so the configurable's `ChannelPanelEntry.panel`
cast works (see
[`CurlSettingsPanel`](../../src/main/kotlin/com/itangcent/easyapi/channel/curl/CurlSettingsPanel.kt#L40)):

```kotlin
class ExampleSettingsPanel(private val project: Project) : SettingsPanel<Settings> {
    // ...fields seeded from project.settings<ExampleSettings>()...
}
```

On the `Channel` side, override three members:

```kotlin
override val settingsType: KClass<out Settings> = ExampleSettings::class
override val settingsTabOrder: Int = 120
override fun createSettingsPanel(project: Project): SettingsPanel<*>? = ExampleSettingsPanel(project)
```

`EasyApiSettingsConfigurable` discovers the panel via `settingsType` and
hosts it as a Settings tab. **No `plugin.xml` `<applicationService>` or
`<projectConfigurable>` line needed** — the unified `UnifiedAppSettingsState`
storage handles persistence automatically.

**Contrast:** cURL owns a settings tab (`CurlSettings` /
`CurlSettingsPanel`); Markdown does not (just a per-export options panel).

### Step 5 — Rule keys + config files (optional)

If your channel exposes user-configurable rule keys (e.g. Hoppscotch's
`hopp.prerequest`), declare them in a dedicated `object`:

```kotlin
object ExampleRuleKeys {
    val EXAMPLE_HOST = RuleKey.string("example.host")
    val EXAMPLE_FORMAT_AFTER = RuleKey.event("example.format.after", EventRuleMode.THROW_IN_ERROR)
}
```

Return them from `ruleKeys()`:

```kotlin
override fun ruleKeys(): List<RuleKey<*>> = RuleKey.collectFrom(ExampleRuleKeys)
```

`RuleKey.collectFrom(...)` reflects over the object's properties — no need to
list each key by name, and adding a new key is just a property declaration.

**Critical:** return only the keys that live in your channel's package. Keys
already declared in `core.rule.RuleKeys` (the shared general set) must NOT be
repeated — that would create duplicate lookups and confuse the AI agent.

Reference: [`HoppscotchRuleKeys`](../../src/main/kotlin/com/itangcent/easyapi/channel/hoppscotch/HoppscotchRuleKeys.kt)
(canonical). [`PostmanChannel`](../../src/main/kotlin/com/itangcent/easyapi/channel/postman/PostmanChannel.kt)
intentionally doesn't declare any (its rules reuse the general set).

`configFiles()` returns the channel-specific config-file names contributed to
the extension-config fallback list (e.g. `"yapi"`, `"hoppscotch"`). Return
`emptyList()` (the default) if you have none.

### Step 6 — Export result + `handleResult`

Return an `ExportResult.Success` with a channel-defined `ExportMetadata`
subtype so `handleResult` can recover the typed payload:

```kotlin
import com.itangcent.easyapi.core.export.ExportMetadata

data class ExampleExportMetadata(val content: String) : ExportMetadata {
    override fun formatDisplay(): String? = null
}
```

Override `handleResult` to write the file / open the browser / copy to
clipboard. Return `true` to suppress the default "export succeeded" toast.

The threading pattern from
[`MarkdownChannel.handleResult`](../../src/main/kotlin/com/itangcent/easyapi/channel/markdown/MarkdownChannel.kt#L150-L173)
is canonical:

```kotlin
override suspend fun handleResult(
    project: Project,
    result: ExportResult.Success,
    config: ChannelConfig
): Boolean {
    val metadata = result.metadata as? ExampleExportMetadata ?: return false
    val targetFile = resolveTargetFile(project, config, "example.txt")
        ?: throw CancellationException("User cancelled file selection")

    background { targetFile.writeText(metadata.content) }     // file I/O
    LOG.info("Example exported to ${targetFile.absolutePath}")

    swing {                                                    // UI on EDT
        Messages.showInfoMessage(
            project,
            "Successfully exported ${result.count} endpoints to ${targetFile.absolutePath}",
            "Export API"
        )
    }
    return true
}
```

Throw `CancellationException` if the user cancels a file-chooser — the
orchestrator translates it into `ExportResult.Cancelled`.

## What you get for free

A channel implementation gets substantial auto-wiring with **no extra code**:

- **Discovery** — `ChannelRegistry` (`project.service()`) iterates every
  registered `Channel` via the `channel` EP.
- **Options-panel hosting** — `ExportDialog` renders the active channel's
  `createOptionsPanel` result in a `CardLayout` keyed by `channel.id`. No
  per-channel dialog code needed.
- **Enablement toggle** — Settings → General → "Export Channels" is
  auto-built from `ChannelRegistry.allChannels()`. A disabled channel is
  hidden from every consumer (export dialog, quick-export action, etc.).
- **Top-level action (opt-in)** — set `exposeAsAction = true` and
  `actionText = "..."` to get a menu entry in `ChannelQuickExportGroup`.
- **Settings tab (opt-in)** — set `settingsType` and override
  `createSettingsPanel` to get a dedicated Settings → EasyApi → <Channel>
  tab, ordered by `settingsTabOrder`, persisted via `UnifiedAppSettingsState`.

No `<action>`, no `<applicationService>`, no `<projectConfigurable>` —
nothing beyond the single `<channel implementation="…"/>` line.

## Worked example — "echo to file" channel

A ~30-line channel that writes endpoint names to a file:

```kotlin
package com.itangcent.easyapi.channel.echo

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.channel.spi.Channel
import com.itangcent.easyapi.channel.spi.ChannelConfig
import com.itangcent.easyapi.core.export.ExportContext
import com.itangcent.easyapi.core.export.ExportMetadata
import com.itangcent.easyapi.core.export.ExportResult
import com.itangcent.easyapi.core.internal.threading.background
import com.itangcent.easyapi.core.logging.IdeaLog
import java.io.File

class EchoChannel : Channel, IdeaLog {
    override val id: String = "echo"
    override val displayName: String = "Echo (demo)"

    override suspend fun export(context: ExportContext): ExportResult {
        val names = context.endpointsToExport.joinToString("\n") { it.name ?: "(unnamed)" }
        return ExportResult.Success(
            count = context.endpointsToExport.size,
            target = "Echo",
            metadata = EchoMetadata(content = names)
        )
    }

    override suspend fun handleResult(
        project: Project,
        result: ExportResult.Success,
        config: ChannelConfig
    ): Boolean {
        val metadata = result.metadata as? EchoMetadata ?: return false
        val target = File(project.basePath ?: ".", "echo.txt")
        background { target.writeText(metadata.content) }
        LOG.info("Echo wrote ${result.count} endpoints to ${target.absolutePath}")
        return true
    }
}

data class EchoMetadata(val content: String) : ExportMetadata {
    override fun formatDisplay(): String? = null
}
```

`plugin.xml`:

```xml
<channel implementation="com.itangcent.easyapi.channel.echo.EchoChannel"/>
```

That's the entire integration — no other wiring.

## Import rules

A channel may import from:

- `core.export.*` (`ApiEndpoint`, `ExportContext`, `ExportResult`,
  `EndpointBuilder`, …)
- `core.psi.model.*` (`ObjectModel`, `FieldModel`, …)
- `core.psi.*` (`PsiClassHelper`, helpers, type system)
- `core.util.*`, `core.rule.*`, `core.config.*`, `core.settings.*`
- `format.spi.*` (if you call `ObjectModel.toJson()` etc.)
- `core.internal.threading.*` (`IdeDispatchers`, `read`/`swing`/`background`)
- `core.logging.*` (`IdeaLog`, `IdeaConsole`)
- IntelliJ SDK
- Your own `channel.<id>.*` package

A channel **MUST NOT** import from:

- `framework.*` (frameworks produce endpoints, channels consume them — they
  don't know about each other)
- Sibling `channel.<other-id>.*` packages

## Testing

- **Unit-test `export()`** by constructing an `ExportContext` with mocked
  `ApiEndpoint`s and a fake `Project`. Assert on the returned
  `ExportResult.Success.count` / `target` / `metadata` shape.
- **Round-trip `buildConfig()`** for your `ChannelOptionsPanel` — set
  fields, call `buildConfig()`, assert each property round-trips.
- **Pure helpers** (e.g. content builders with no `Project` dependency) get
  plain JUnit tests.
- **PSI-aware tests** extend `EasyApiLightCodeInsightFixtureTestCase`.

Reference: [`DummyChannelTest`](../../src/test/kotlin/com/itangcent/easyapi/channel/dummy/DummyChannelTest.kt),
[`CurlConfigTest`](../../src/test/kotlin/com/itangcent/easyapi/channel/curl/CurlConfigTest.kt).

See [shared testing concerns](README.md#testing) in the developer README,
and **invoke the `write-test-case` skill before writing tests**.
