# Adding a new format

A **format** serializes an `ObjectModel` to a specific representation (JSON,
YAML, Properties, …) and exposes a `FieldsTo<Format>` action on `PsiClass`.
Adding a new one is a one-package operation plus one `plugin.xml` line.

> **Shared concerns** (threading, logging, enablement, testing) are in the
> [developer README](README.md). This page covers format-specific concerns
> only.

## When to write a format

Write a format when you have a new **field serialization** for `ObjectModel`.
The 4 built-in formats:

| Format | Layer | Honors `properties.prefix`? |
|--------|-------|------------------------------|
| [JSON](../../src/main/kotlin/com/itangcent/easyapi/format/json/JsonFieldFormatChannel.kt) | Pure renderer | No |
| [JSON5](../../src/main/kotlin/com/itangcent/easyapi/format/json5/Json5FieldFormatChannel.kt) | Pure renderer (subset of JSON) | No |
| [Properties](../../src/main/kotlin/com/itangcent/easyapi/format/properties/PropertiesFieldFormatChannel.kt) | Project-scoped | Yes |
| [YAML](../../src/main/kotlin/com/itangcent/easyapi/format/yaml/YamlFieldFormatChannel.kt) | Project-scoped | Yes (renders as nested keys) |

If you need to *export `ApiEndpoint`s* (request/response shapes, headers,
paths, …) to a target destination, that's a [channel](channels.md), not a
format.

## The two-layer architecture

Formats have a deliberate two-layer split:

- **Pure renderer** — `ObjectModel → String`. Takes no `Project`, no
  `RuleEngine`, no PSI. Lives in a single `object` (or class) in your
  package. JSON, JSON5, and (the core of) Properties/YAML all live here.
- **Project-scoped channel** — `FieldFormatChannel.format(project, psiClass)`
  builds the `ObjectModel` (via `PsiClassHelper`), optionally resolves
  project-scoped rules (e.g. `properties.prefix` from
  `@ConfigurationProperties(prefix=…)`), then calls the pure renderer.

JSON / JSON5 are pure-renderer-only: their channel builds the model inline
and calls the renderer directly. Properties / YAML are prefix-sensitive:
their channel delegates to `PropertiesService`, which resolves the prefix
via `RuleEngine` and then calls the pure renderer.

Pick the layer based on whether you need `RuleEngine`. If you don't, your
channel can be a 5-line delegation to the pure renderer.

## The `FieldFormatChannel` SPI

The full contract lives in
[`format/spi/FieldFormatChannel.kt`](../../src/main/kotlin/com/itangcent/easyapi/format/spi/FieldFormatChannel.kt).
The EP is **application-scoped** (no `area` attribute in `plugin.xml`), so
your constructor must be **no-arg** — `Project` arrives via the
`format(project, psiClass)` parameter on each call.

| Member | Type | Default | Purpose |
|---|---|---|---|
| `id` | `String` | — | Unique identifier (`"json"`, `"yaml"`, …). |
| `displayName` | `String` | — | Human-readable name in notifications. |
| `actionText` | `String` | — | Menu text (`"ToJson"`, `"ToYaml"`, …). |
| `enabledByDefault` | `Boolean` | `true` | Compile-time enablement default. Set `false` for experimental. |
| `format(project, psiClass)` | `suspend fun → String` | — | Build the model and render it. Required. |

That's the whole SPI — much smaller than `Channel`. The action menu entry,
settings toggle, and registry are all auto-wired.

## The `ObjectModel` input

[`ObjectModel`](../../src/main/kotlin/com/itangcent/easyapi/core/psi/model/ObjectModel.kt)
is a sealed class with four variants:

| Variant | Fields | Meaning |
|---|---|---|
| `ObjectModel.Single(type)` | `type: String` | Primitive / scalar. `type` is a `JsonType` name string. |
| `ObjectModel.Object(fields)` | `fields: Map<String, FieldModel>` | Object with named fields. Each `Object` instance has a unique `id` (used by `ObjectModelVisitTracker`). |
| `ObjectModel.Array(item)` | `item: ObjectModel` | Array of items. |
| `ObjectModel.MapModel(keyType, valueType)` | `keyType`, `valueType` | Map / dictionary. |

`FieldModel` carries per-field metadata:

```kotlin
data class FieldModel(
    val model: ObjectModel,
    val comment: String? = null,
    val required: Boolean = false,
    val defaultValue: String? = null,
    val options: List<FieldOption>? = null,
    val demo: String? = null,
    val advanced: Map<String, Any?>? = null,
    val generic: Boolean = false,
    val extensions: Extension = Extension.EMPTY
)
```

### `Single.type` is a `JsonType` name string

`ObjectModel.Single.type` is **not** an arbitrary type — it's one of the
constants in
[`JsonType`](../../src/main/kotlin/com/itangcent/easyapi/core/psi/type/JsonType.kt)
(`STRING`, `INT`, `LONG`, `FLOAT`, `DOUBLE`, `BOOLEAN`, `ARRAY`, `OBJECT`,
`FILE`, `DATE`, `DATETIME`). Default values come from
`JsonType.defaultValueForType(type)`:

| `type` | Default |
|---|---|
| `STRING` | `""` |
| `INT` / `SHORT` / `int32` | `0` |
| `LONG` / `int64` | `0L` |
| `FLOAT` | `0.0f` |
| `DOUBLE` | `0.0` |
| `BOOLEAN` / `bool` | `false` |
| `bytes` | `""` |
| anything else | `null` |

### Cycle safety (load-bearing)

A naive recursive walker stack-overflows on self-referential models. Every
renderer must pair
[`ObjectModelVisitTracker.tryEnter`](../../src/main/kotlin/com/itangcent/easyapi/core/psi/model/ObjectModelVisitTracker.kt#L31-L36)
/ `exit` in a `finally`:

```kotlin
val tracker = ObjectModelVisitTracker()
if (!tracker.tryEnter(model)) {
    // visit limit (DEFAULT_MAX_VISITS = 2) reached — emit placeholder
    sb.append("{}")
    return
}
try {
    for ((name, field) in model.fields) {
        // recurse into field.model
    }
} finally {
    tracker.exit(model)  // restores count so sibling fields can re-enter
}
```

Or use the `withVisit` helper which encapsulates the `try/finally`:

```kotlin
tracker.withVisit(model) {
    // expand model.fields ...
} ?: run { sb.append("{}") }
```

`ObjectModel.DEFAULT_MAX_VISITS = 2` — an object is expanded up to twice
across the whole traversal; on the third attempt, `tryEnter` returns `false`
and the caller emits a placeholder (`{}`, `[]`). The count is restored on
`exit`, so sibling fields that reference the same instance each get their
own expansions.

The Properties, YAML, and JSON formatters all do this. Skipping it is the
single most common bug in a hand-written renderer.

## Step-by-step

### Step 1 — Pure renderer

Create `format/<id>/<Id>Formatter.kt`. Walk the `ObjectModel`, branch on
the four variants, read `JsonType.defaultValueForType` for scalar defaults,
read `FieldModel.comment` / `options` for comments. Use
`ObjectModelVisitTracker` for cycles.

Minimal structure (mirrors
[`YamlFormatter`](../../src/main/kotlin/com/itangcent/easyapi/format/yaml/YamlFormatter.kt)):

```kotlin
object TomlFormatter {
    fun format(model: ObjectModel, prefix: String = ""): String {
        val sb = StringBuilder()
        val tracker = ObjectModelVisitTracker()
        render(model, sb, tracker, indent = 0)
        return sb.toString().trimEnd()
    }

    private fun render(
        model: ObjectModel,
        sb: StringBuilder,
        tracker: ObjectModelVisitTracker,
        indent: Int
    ) {
        when (model) {
            is ObjectModel.Single -> {
                sb.append(JsonType.defaultValueForType(model.type) ?: "null")
            }
            is ObjectModel.Object -> {
                if (!tracker.tryEnter(model)) { sb.append("{}"); return }
                try {
                    for ((name, field) in model.fields) {
                        sb.append("  ".repeat(indent))
                            .append(name).append(" = ")
                        render(field.model, sb, tracker, indent + 1)
                        sb.appendLine()
                    }
                } finally {
                    tracker.exit(model)
                }
            }
            is ObjectModel.Array -> { /* render array */ }
            is ObjectModel.MapModel -> { /* render map */ }
        }
    }
}
```

Reference implementations:
[`PropertiesFormatter`](../../src/main/kotlin/com/itangcent/easyapi/format/properties/PropertiesFormatter.kt)
(simplest — flattens to `key=value`), [`YamlFormatter`](../../src/main/kotlin/com/itangcent/easyapi/format/yaml/YamlFormatter.kt)
(block style, 2-space indent).

### Step 2 — Entry extension

Add a top-level extension function in
[`format/spi/FieldFormatExtensions.kt`](../../src/main/kotlin/com/itangcent/easyapi/format/spi/FieldFormatExtensions.kt)
so callers can use the same ergonomic entry point as the built-in formats
(`toJson()`, `toJson5()`, `toYaml()`, `toProperties()`):

```kotlin
fun ObjectModel.toToml(prefix: String = ""): String =
    TomlFormatter.format(this, prefix)
```

> **Nullable-receiver convention:** the JSON/JSON5 extensions use a nullable
> receiver (`fun ObjectModel?.toJson(): String = ObjectModelJsonConverter.toJson(this)`)
> because they forward to a null-tolerant converter. If your formatter
> requires a non-null model, declare the receiver non-nullable.

### Step 3 — Channel

Create `format/<id>/<Id>FieldFormatChannel.kt`:

```kotlin
package com.itangcent.easyapi.format.toml

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.format.spi.FieldFormatChannel
import com.itangcent.easyapi.format.spi.toToml
import com.itangcent.easyapi.core.psi.JsonOption
import com.itangcent.easyapi.core.psi.PsiClassHelper

class TomlFieldFormatChannel : FieldFormatChannel {
    override val id: String = "toml"
    override val displayName: String = "TOML"
    override val actionText: String = "ToToml"

    override suspend fun format(project: Project, psiClass: PsiClass): String =
        PsiClassHelper.getInstance(project)
            .buildObjectModel(psiClass, JsonOption.READ_GETTER_OR_SETTER)
            ?.toToml() ?: ""
}
```

If your format needs `RuleEngine` (e.g. for `properties.prefix`), delegate to
a project service instead — see
[`YamlFieldFormatChannel`](../../src/main/kotlin/com/itangcent/easyapi/format/yaml/YamlFieldFormatChannel.kt)
(the minimal project-scoped example):

```kotlin
class YamlFieldFormatChannel : FieldFormatChannel {
    override val id: String = "yaml"
    override val displayName: String = "YAML"
    override val actionText: String = "ToYaml"

    override suspend fun format(project: Project, psiClass: PsiClass): String =
        PropertiesService.getInstance(project).toYaml(psiClass)
}
```

`PropertiesService.toYaml` / `toProperties` resolve the `properties.prefix`
rule via `RuleEngine`, then call the pure renderer (`ObjectModel.toYaml` /
`ObjectModel.toProperties`).

### Step 4 — `plugin.xml` line

Add one line under `<extensions defaultExtensionNs="...">` in
[`plugin.xml`](../../src/main/resources/META-INF/plugin.xml#L53-L56):

```xml
<fieldFormatChannel implementation="com.itangcent.easyapi.format.toml.TomlFieldFormatChannel"/>
```

**No `<action>` entry, no group entry** — the "FieldsTo*" menu item
auto-registers via `FieldFormatActionGroup` (see below).

### Step 5 — Experimental (optional)

If your format is experimental, override `enabledByDefault = false`. The
format is hidden from the action menu until the user enables it in Settings
→ General → "Field Format Channels". No further wiring needed.

## What you get for free

A format implementation gets substantial auto-wiring with **no extra code**:

- **Action auto-registration** — `FieldFormatActionGroup.ensureActionsRegistered`
  iterates `FieldFormatChannelRegistry.getEnabledChannels()` at startup (via
  `ChannelActionInitActivity`) and registers one
  [`FieldFormatAction`](../../src/main/kotlin/com/itangcent/easyapi/format/spi/FieldFormatAction.kt)
  per format with `ActionManager`, using a stable ID
  (`com.itangcent.easy_api.actions.fieldformat.<id>`) and the plugin's
  `PluginId` for keymap categorization. No `<action>` XML needed.
- **Enablement toggle** — `FieldFormatChannelRegistry` mirrors the channel
  enablement machinery: Settings → General → "Field Format Channels" is
  auto-built from `allChannels()`. A disabled format's action is hidden
  (presentation `visible=false` per-context) but **not** unregistered —
  keymap IDs stay stable across enable/disable cycles.
- **Re-active on settings change** — `EasyApiSettingsConfigurable.apply()`
  calls `FieldFormatActionGroup.refreshActions(project)` after a settings
  write; newly-enabled formats get their action registered, disabled
  formats' actions are hidden on the next menu show.
- **AI-visible** — `FieldFormatChannelRegistry.allChannels()` is consumed
  by the in-IDE AI assistant so the model knows what formats exist.

Reference:
[`FieldFormatActionGroup`](../../src/main/kotlin/com/itangcent/easyapi/format/spi/FieldFormatActionGroup.kt)
(action registration),
[`FieldFormatChannelRegistry`](../../src/main/kotlin/com/itangcent/easyapi/format/spi/FieldFormatChannelRegistry.kt)
(enablement).

## Worked example — minimal TOML format

`TomlFormatter.kt`:

```kotlin
package com.itangcent.easyapi.format.toml

import com.itangcent.easyapi.core.psi.model.ObjectModel
import com.itangcent.easyapi.core.psi.model.ObjectModelVisitTracker
import com.itangcent.easyapi.core.psi.type.JsonType

object TomlFormatter {
    fun format(model: ObjectModel): String {
        val sb = StringBuilder()
        val tracker = ObjectModelVisitTracker()
        when (model) {
            is ObjectModel.Object -> renderObject(model, sb, tracker, indent = 0)
            else -> sb.append("# unsupported top-level model")
        }
        return sb.toString().trimEnd()
    }

    private fun renderObject(
        model: ObjectModel.Object,
        sb: StringBuilder,
        tracker: ObjectModelVisitTracker,
        indent: Int
    ) {
        if (!tracker.tryEnter(model)) return
        try {
            for ((name, field) in model.fields) {
                sb.append("  ".repeat(indent)).append(name).append(" = ")
                when (val m = field.model) {
                    is ObjectModel.Single ->
                        sb.append(JsonType.defaultValueForType(m.type) ?: "null")
                    is ObjectModel.Object -> {
                        sb.appendLine()
                        renderObject(m, sb, tracker, indent + 1)
                    }
                    is ObjectModel.Array -> sb.append("[]")
                    is ObjectModel.MapModel -> sb.append("{}")
                }
                sb.appendLine()
            }
        } finally {
            tracker.exit(model)
        }
    }
}
```

Extension in `format/spi/FieldFormatExtensions.kt`:

```kotlin
fun ObjectModel.toToml(): String = TomlFormatter.format(this)
```

`TomlFieldFormatChannel.kt`:

```kotlin
class TomlFieldFormatChannel : FieldFormatChannel {
    override val id: String = "toml"
    override val displayName: String = "TOML"
    override val actionText: String = "ToToml"

    override suspend fun format(project: Project, psiClass: PsiClass): String =
        PsiClassHelper.getInstance(project)
            .buildObjectModel(psiClass, JsonOption.READ_GETTER_OR_SETTER)
            ?.toToml() ?: ""
}
```

`plugin.xml`:

```xml
<fieldFormatChannel implementation="com.itangcent.easyapi.format.toml.TomlFieldFormatChannel"/>
```

## Non-trivial formats

For anything beyond "walk the model and emit text", look at
[`format/json/`](../../src/main/kotlin/com/itangcent/easyapi/format/json/):

- [`ObjectModelJsonConverter`](../../src/main/kotlin/com/itangcent/easyapi/format/json/ObjectModelJsonConverter.kt)
  — entry point; delegates to a builder with a pluggable handler.
- [`ObjectModelJsonBuilder`](../../src/main/kotlin/com/itangcent/easyapi/format/json/ObjectModelJsonBuilder.kt)
  — walks the model and dispatches to a handler.
- [`ObjectModelJsonHandler`](../../src/main/kotlin/com/itangcent/easyapi/format/json/ObjectModelJsonHandler.kt)
  — strategy interface for per-variant rendering.
- [`RawJsonHandler`](../../src/main/kotlin/com/itangcent/easyapi/format/json/RawJsonHandler.kt)
  — standard JSON.
- [`Json5Handler`](../../src/main/kotlin/com/itangcent/easyapi/format/json5/Json5Handler.kt)
  — JSON5 (subset of the JSON package; just another handler).

JSON5 is illustrative: it's not a separate package-walk implementation, just
another `ObjectModelJsonHandler` plugged into the same builder.

> **`core.util.FormatterHelper` is NOT for format authors.** It's a UI
> pretty-printer (used by the export dialog and dashboard to format
> already-rendered JSON/XML/HTML for display). Format authors should never
> import it — your pure renderer is the source of truth for the output.

## Import rules

A format may import from:

- `core.psi.model.*` (`ObjectModel`, `FieldModel`, `FieldOption`,
  `ObjectModelVisitTracker`, `ObjectModelUtils`)
- `core.psi.type.*` (`JsonType`, special-case handlers — for type-default
  lookup only)
- `core.util.*` (text helpers — but NOT `FormatterHelper`; see above)
- `core.psi.*` (`PsiClassHelper`, `JsonOption`) — only in the
  `FieldFormatChannel` layer, not the pure renderer
- `core.ide.*` (`PropertiesService` — for prefix-sensitive formats)
- `core.rule.*` (only if your channel needs to evaluate rules directly)
- `format.spi.*` (the entry extensions and registry)
- IntelliJ SDK
- Your own `format.<id>.*` package

A format **MUST NOT** import from:

- `channel.*` (channels consume endpoints; formats consume field models)
- `framework.*` (formats don't know about source frameworks)
- `core.export.*` (formats don't touch `ApiEndpoint` / `ExportContext`)

The pure renderer specifically should import only `core.psi.model.*`,
`core.psi.type.JsonType`, and your own package — it must be testable with no
`Project` / no PSI.

## Testing

- **Pure renderer** — plain JUnit. Build an `ObjectModel` with
  `ObjectModelBuilder` (or `ObjectModel.Object(mapOf(...))`),
  call `TomlFormatter.format(model)`, assert on the string. Test
  self-referential models (build two `Object`s that point at each other) to
  confirm `ObjectModelVisitTracker` prevents stack overflow.
- **Channel** — extend `EasyApiLightCodeInsightFixtureTestCase`, load a
  fixture `.java` / `.kt`, call `format(project, psiClass)`, snapshot the
  output via `ResultLoader.load(...)` (never `File.readText()` — see
  [shared testing concerns](README.md#testing)).
- **Round-trip the extension** — call `ObjectModel.toToml()` on a known
  model and assert it equals `TomlFormatter.format(model)`.

Reference: [`YamlFormatterTest`](../../src/test/kotlin/com/itangcent/easyapi/format/yaml/YamlFormatterTest.kt),
[`Json5HandlerTest`](../../src/test/kotlin/com/itangcent/easyapi/format/json5/Json5HandlerTest.kt).

See [shared testing concerns](README.md#testing) in the developer README,
and **invoke the `write-test-case` skill before writing tests**.
