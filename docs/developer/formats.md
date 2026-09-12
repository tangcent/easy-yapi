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
| `ObjectModel.Single(type, ref)` | `type: String`, `ref: String?` | Primitive / scalar. `type` is one of the `IrType` words. |
| `ObjectModel.Object(fields, id, ref)` | `fields: Map<String, FieldModel>`, `id: Int`, `ref: String?` | Object with named fields. Each `Object` instance has a unique `id` (used by `ObjectModelVisitTracker`). |
| `ObjectModel.Array(item, ref)` | `item: ObjectModel`, `ref: String?` | Array of items. |
| `ObjectModel.MapModel(keyType, valueType, ref)` | `keyType`, `valueType`, `ref: String?` | Map / dictionary. |

### `ref` — the type the node was declared with

Every variant also carries `ref`: the declared type the node was projected from — `com.acme.User`
for an expanded class, `java.util.List<java.lang.String>` for a collection, `java.lang.Integer`
for a boxed primitive. `type` is a **lossy** projection (expanding `com.acme.User` yields the
word `object` and the class name is gone), so `ref` is where the original survives.

Two contracts to respect when writing a format:

- **It may be null.** Nodes nobody declared — rule-configured `json.additional.field` entries,
  raw `List`/`Map` placeholders, protobuf scalars with no declaration — have nothing to point at.
- **It is not guaranteed to be fully qualified.** It is the *declared* spelling, so an unresolved
  type contributes exactly what the parser saw (`User` as often as `com.acme.User`). Reduce it to
  a bare class name yourself when you need one — the OpenAPI channel keeps such a helper private
  inside `OpenApiSchemaConverter`, its only shipped consumer — and check for a `.` before
  treating it as a FQN.
- **A marker still carries its declaration.** `type = "file"` names no class at all, so the
  declaration is the only way to tell a `MultipartFile` field from any other file field. The
  resolver substitutes the marker while the declaration rides along in
  `ResolvedType.UnresolvedType.declaration` — which is why `ref` is populated even for values
  whose `type` is a domain marker rather than a JSON type.

Consume it only where `type` is not enough. `Object` is the interesting case — it is the only
variant whose identity the projection destroys — and even there the answer is usually "not needed":
an expanded object is rendered by its fields, so a document that only prints a shape has no use for
the name. The one shipped consumer is the OpenAPI converter, which needs a name to register
`components.schemas[<name>]` and emit a `$ref`; without it a nested class can never be referenced
and a class that is also a top-level type ends up in the document twice. It is also where the
date/uuid `format` annotations come from: the IR vocabulary has no such words, so
`OpenApiSchemaConverter.formatForRef` derives `format: date` / `date-time` / `uuid` from a
string-typed field's declared `ref`.

Markdown's type column and YApi's draft-04 schema deliberately do **not** consult `ref`: both
already spell the shape out, so `object` is sufficient and the class name would only add noise.
`Single`, `Array` and `MapModel` stay readable from `type` plus their children for the same reason.

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

### `Single.type` — the IR word

`ObjectModel.Single.type` is declared as `String`, not as `IrType`. `IrType` is a
*vocabulary* (an `object` full of `const val String`), never a type, so a node holds **one of its
words** rather than the vocabulary itself. Those words are the IR vocabulary — the type names that
survive from PSI to Doc. **They are not the JSON type vocabulary**: JSON has no `file`, and
`short`/`long`/`float`/`double` are finer-grained than JSON Schema's `integer`/`number`, so JSON's
own types are a proper subset. Date-like and UUID types have **no word here** — on the wire they
are a `string`, the declaration survives in `ref`, and a channel that wants to say more derives it
from the ref.

`type` lives on `Single` only — `Object` implies `object`, `Array` implies `array`, and `MapModel`
holds child nodes (`keyType` / `valueType`), not words.

#### What the domain actually is

Three rings. Only the first is closed:

| Ring | Values | Enforcement |
|---|---|---|
| **Core** | the 10 in `IrType.ALL_TYPES` — `STRING` `SHORT` `INT` `LONG` `FLOAT` `DOUBLE` `BOOLEAN` `ARRAY` `OBJECT` `FILE` | closed: every exit table must answer for all 10 |
| **Named extensions** | `file[]` (a file *collection* spelled as one word) and `null` (`ObjectModel.nullValue()`, the value of an unresolved or `void` type) | enumerated: exits handle them explicitly |
| **Pass-through** | protobuf type references (a failed deserialisation hands the FQN back verbatim, because that position renders a type *reference*), plus any word a third-party channel or rule script invents | open by design — an unrecognised value here is a case to handle, not a bug |

The core ring is closed **by machine rather than by the type system**. `Jsr223ScriptParserTest`
pins the draft-04 `toSchemaType` to `ALL_TYPES`; `ObjectModelValueConverterTypesTest` pins
`defaultValueForType`; `OpenApiSchemaConverterTest.REACHABLE_SINGLE_TYPES` pins the OAS table to
`ALL_TYPES + {"file[]", "null"}`. Adding an 11th IR word therefore breaks those tests, which is
what forces every exit table to answer for it.

That machine closure is why there is deliberately **no `enum` / `sealed` behind this field**. An
enum would buy compile-time exhaustiveness over a set that is already exhaustively checked, and it
still could not close the field: two members are not types (`file[]` is a *composite*, `null` is a
*value*) and the pass-through ring *is* the extension point. The cost would be real — `IrType` is
referenced 811 times.

Default values come from `IrType.defaultValueForType(type)`:

| `type` | Default |
|---|---|
| `STRING` | `""` |
| `INT` / `SHORT` / `int32` | `0` |
| `LONG` / `int64` | `0L` |
| `FLOAT` | `0.0f` |
| `DOUBLE` | `0.0` |
| `BOOLEAN` / `bool` | `false` |
| `FILE` | `"(binary)"` |
| `bytes` | `""` |
| `ARRAY` / `OBJECT` | `null` — structural shapes have no scalar default; `ObjectModelValueConverter.singleToValue` renders them as `[]` / `{}` |
| anything else | `null` |

This table is the only one of its kind: `ObjectModelValueConverter.singleToValue`
delegates its scalar cases here, so an exported example and its schema cannot
disagree (the retired `date` word used to be `""` in one path and `null` in the other).

#### Every exit re-maps the word — one table per protocol

The mapping cannot be avoided, and closing the IR would not remove it: the IR vocabulary is
*finer* than any target protocol's, so `file` collapses into draft-04's single `string`, and
`short`/`long`/`float`/`double` into `integer`/`number`. Exits are
therefore **one table each**, never one shared table:

| Exit | Mapping | Guarded by |
|---|---|---|
| draft-04 JSON Schema (YApi `res_body`, rule scripts) | `toSchemaType` (`core/rule/parser`) | `Jsr223ScriptParserTest` |
| OAS 3.0.3 `SchemaObject` | `OpenApiSchemaConverter.primitiveSchema` | `OpenApiSchemaConverterTest` |
| Date/uuid `format` (OAS) | `OpenApiSchemaConverter.formatForRef` — derived from `ref` | `OpenApiSchemaConverterTest` |
| Human-readable type column | `MarkdownFieldFormatter.formatType` | `TemplateHelpersTest` |
| Example / default values (yaml, properties, json5, json) | `IrType.defaultValueForType` | `ObjectModelValueConverterTypesTest` |
| YApi mock expressions | `MockDataGenerator` | `MockDataGeneratorTest` |

The first two must **not** be merged — draft-04 accepts `"null"` and needs no `items`, while OAS
3.0.3 has no `null` and requires `items` on every array.

For a **document's type column** the `Single` word is printed verbatim, never rewritten
(`MarkdownFieldFormatter.formatType`). There is nothing left to collapse: the pipeline no longer
produces `date`/`datetime`, so a `LocalDateTime` field already arrives as `string` with its
declaration in `ref`. `long`/`short`/`file`/`uuid` name a wire shape a reader can act on, so they
too are printed as-is. `Object` prints `object`, `Array` prints `<item>[]` and `MapModel` prints
`map`; a field's shape is spelled out by the rows beneath it, so the column has no use for `ref`.

#### Non-basic types are configuration, not code

Which non-JSON-native types (`java.util.Date`, `java.time.Duration`,
`java.util.UUID`, joda-time, …) collapse to a scalar — and to *which* scalar — is
declared by the active configuration, not by a table in Kotlin:

```properties
# src/main/resources/extensions/converts.config  (default-enabled)
json.rule.convert[java.time.Duration]=java.lang.String
```

Leave them unmapped and they are documented as objects (`java.time.Duration` →
`{seconds, nanos}`), which is why the shipped extension maps them. Turn the
extension off (Settings → Rule File → Extensions) and declare your own mapping with a
**psi spelling** — `=long` for a custom serializer. A target must name a class
(`java.lang.String`, `java.lang.Long`, a file type), not an IR word: the retired
`=date` / `=datetime` / `=uuid` targets are shimmed to `java.lang.String` with a warning, and the
OpenAPI channel derives `format: date` / `date-time` / `uuid` from the declared type instead.

Two consequences worth knowing when you write a channel:

- `IrType.fromJavaType` is **configuration-agnostic**. It owns the closed
  `IrType` vocabulary and the container spellings only, so
  `fromJavaType("java.time.LocalDate")` is `object` even with the extension on —
  the configured mapping is applied by `SpecialTypeHandler.resolveSpecialType`
  *before* this function is reached. Do not add a per-type table back here.
- File detection is **token-level**, never substring-based — for the container
  branch too. `SpecialTypeHandler.mentionsFileType` decides whether a
  declaration carries files, so `List<MultipartFile>` from a rule script is
  `file[]`, while `List<Department>` and `List<Partition>` are plain arrays
  (a `contains("part")` check used to make any element whose name contained
  "part" a file, and `com.acme.Department` → `file` directly). Unqualified
  spellings keep the loose suffix rules as the script-facing fallback.

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
the four variants, read `IrType.defaultValueForType` for scalar defaults,
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
                sb.append(IrType.defaultValueForType(model.type) ?: "null")
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
import com.itangcent.easyapi.core.psi.type.IrType

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
                        sb.append(IrType.defaultValueForType(m.type) ?: "null")
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
- `core.psi.type.*` (`IrType`, special-case handlers — for type-default
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
`core.psi.type.IrType`, and your own package — it must be testable with no
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
