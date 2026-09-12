# EasyApi Developer Guides

Contributor guides for the EasyApi IntelliJ plugin. Most pages are step-by-step
walkthroughs for adding a new **channel**, **format**, or **framework** — the
SPI contracts, the `plugin.xml` wiring, the enablement model, threading/logging
conventions, and worked examples — everything you need to ship a new extension
end-to-end. [`ai.md`](ai.md) differs in kind: it is a subsystem reference for
`core/ai/`, not an extension walkthrough.

> **End user?** The user / rule-author docs live in
> [`docs/knowledge-base/`](../knowledge-base/README.md) (rule files, settings,
> usage workflows). This developer suite is for contributors writing Kotlin
> that extends the plugin itself.

## Pages

| Page | What you'll learn |
|------|-------------------|
| [AI Module](ai.md) | The whole AI subsystem — agent runtime (entry paths, turn loop, events, gates), the three tool registries, prompt resources and the catalog, knowledge-asset sync (generated catalogs and their guards), known limitations, and the maintenance playbooks. Read this before touching anything under `core/ai/`. |
| [Channels](channels.md) | Add a new output destination (Postman variant, Insomnia, …) — convert `ApiEndpoint` models into a target format and write/upload the result. |
| [Formats](formats.md) | Add a new field serialization (TOML, XML, …) — render an `ObjectModel` to a target representation and wire a `FieldsTo*` action. |
| [Frameworks](frameworks.md) | Add a new source framework (Micronaut, …) — scan PSI for endpoints and feed them into the export pipeline. |
| [Custom framework](custom-framework.md) | The rules-driven **Custom** framework (id `custom`) — no hard-coded annotation detection; its entire extraction surface (class/method recognition, HTTP method, path, parameter binding) is the `custom.*` rules. The v3.0 replacement for the v2.x generic-export subsystem dropped in the rewrite (issue #1423). |

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

## The extension points at a glance

Three extension **kinds** — a channel, a field format, a framework — are
plugged in through **four** EPs. A framework alone registers on **two**
(`classExporter` + `apiClassRecognizer`) — see [Frameworks](frameworks.md) for
why.

| EP name (`plugin.xml`) | Interface FQN | Bucket | Scope | What it does |
|---|---|---|---|---|
| `channel` | `com.itangcent.easyapi.channel.spi.Channel` | `channel/` | `area="IDEA_PROJECT"` | Convert `ApiEndpoint`s to an output format and write/upload the result. |
| `fieldFormatChannel` | `com.itangcent.easyapi.format.spi.FieldFormatChannel` | `format/` | application (no `area`) | Serialize an `ObjectModel` to a target representation; auto-registered as a `FieldsTo*` action. |
| `classExporter` | `com.itangcent.easyapi.core.export.ClassExporter` | `framework/` | `area="IDEA_PROJECT"` | Extract `ApiEndpoint`s from a `PsiClass` for one source framework. |
| `apiClassRecognizer` | `com.itangcent.easyapi.core.export.recognizer.ApiClassRecognizer` | `framework/` | `area="IDEA_PROJECT"` | Cheap "is this an API class?" check; drives line markers, index scanning, AI discovery, and enablement. |

All four are declared `dynamic="true"` so they can be loaded/unloaded without a
restart. `plugin.xml` declares one further EP, `featureContributor`, which
supplies per-project feature metadata (settings groups and descriptors) — it is
not a surface for adding a channel/format/framework. The whole block lives at
[`plugin.xml#L23-L33`](../../src/main/resources/META-INF/plugin.xml#L23-L33):

```xml
<extensionPoints>
    <extensionPoint name="classExporter"      interface="com.itangcent.easyapi.core.export.ClassExporter"                  area="IDEA_PROJECT" dynamic="true"/>
    <extensionPoint name="channel"            interface="com.itangcent.easyapi.channel.spi.Channel"                        area="IDEA_PROJECT" dynamic="true"/>
    <extensionPoint name="fieldFormatChannel" interface="com.itangcent.easyapi.format.spi.FieldFormatChannel"                              dynamic="true"/>
    <extensionPoint name="apiClassRecognizer" interface="com.itangcent.easyapi.core.export.recognizer.ApiClassRecognizer" area="IDEA_PROJECT" dynamic="true"/>
    <extensionPoint name="featureContributor" interface="com.itangcent.easyapi.core.feature.FeatureContributor"           area="IDEA_PROJECT" dynamic="true"/>
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

**Import rules** — the part that bites an extension author: reach a sibling
bucket only through its `spi/` seam, never through its concrete
`<bucket>.<id>.*` classes.

The authoritative statement of the whole DAG lives in
[AGENTS.md §"Project Structure"](../../AGENTS.md#project-structure). No
automated gate enforces it — the rule is review-only, which is exactly why it
is the single most common review feedback on a new extension. Read it there
before your first PR.

## Package-layout decision rule

The first-match-wins rule for picking a bucket is normative in
[AGENTS.md §"Package Layout"](../../AGENTS.md#package-layout).

For a new extension it is mechanical: a channel goes in `channel/<id>/`, a
field format in `format/<id>/`, a framework in `framework/<id>/`. Each
input/output bucket owns a `spi/` sub-package for its EP contract surface —
that is the only sibling surface `core.*` may import.

## `plugin.xml` basics

Each EP appears **twice** in
[`plugin.xml`](../../src/main/resources/META-INF/plugin.xml):

1. **`<extensionPoints>`** declares the EP name, interface FQN, scope, and
   `dynamic="true"`. Owned by EasyApi core — you only add here when inventing a
   brand-new EP category.
2. **`<extensions defaultExtensionNs="com.itangcent.idea.plugin.easy-yapi">`**
   registers concrete implementations. **This is where your `<channel … />`,
   `<fieldFormatChannel … />`, `<classExporter … />`, or
   `<apiClassRecognizer … />` line goes** — one line for a channel or format,
   two for a framework.

No `<action>`, no `<applicationService>`, no other XML wiring: the action menu
entry, settings tab, and registry discovery are all auto-wired by the SPI.

The generated [`extensions.md`](../../skills/easy-yapi-assistant/extensions.md)
lists every EP and its registered implementations — consult it instead of
counting lines in `plugin.xml`.

## Registering rule keys

If your extension ships its own rule keys (a `XxxRuleKeys` object, like
`PostmanRuleKeys` / `CustomRuleKeys`), register the source so the generated
catalogs and the `list_rule_keys` tool can see it — in
[`core/rule/RuleKeyCatalog.kt`](../../src/main/kotlin/com/itangcent/easyapi/core/rule/RuleKeyCatalog.kt):

```kotlin
val SOURCES: List<Pair<String, () -> List<RuleKey<*>>>> = listOf(
    // …
    "<your-id>" to { RuleKey.collectFrom(YourRuleKeys) },
)
```

Then run `./gradlew syncSkill` and commit the regenerated files.
`RuleKeySchemeExporterTest` and `EasyYapiAssistantSkillTest` fail when a
registered `RuleKeyRegistry` source is missing from `SOURCES`, or when the
committed catalogs drift from the code.

Most extensions need no rule keys at all — skip this section unless yours does.

## Design notes (`.spec/`)

Larger changes get a design note before implementation: the problem, the root
cause, a **file-level change matrix**, and verifiable acceptance criteria (a
note with only ideas is not accepted). They live in
[`.spec/`](../../.spec/README.md):

| Spec | Topic |
|------|-------|
| [`dashboard-request-state.md`](../../.spec/dashboard-request-state.md) | API Dashboard — the PSI-derived vs UI-saved request states and how they reconcile. |
| [`api-scan-performance.md`](../../.spec/api-scan-performance.md) | API scan cost / UI-freeze root cause, anti-freeze work, scan-health monitoring. |
| [`dashboard-file-response.md`](../../.spec/dashboard-file-response.md) | API Dashboard file responses — binary support (Save as) and response-size limits. |

## Shared concerns

Cross-cutting rules that apply to **all three** extension kinds. Each is
normative in `AGENTS.md`; what follows is only the part an extension author
actually has to act on.

### Threading

Your SPI methods touch PSI, so: make them `suspend` and wrap the PSI reads in
`read { … }`; put network / file I/O in `background { … }` and dialogs in
`swing { … }`.

Everything else — the dispatcher table, the `@requires` KDoc convention, the
boundary-class self-protection rules, the `StartupActivity`
context-propagation warning — is normative in
[AGENTS.md §"Threading Model"](../../AGENTS.md#threading-model). Link to it;
don't paraphrase.

### Logging

Implement [`IdeaLog`](../../src/main/kotlin/com/itangcent/easyapi/core/logging/IdeaLog.kt)
to get a `LOG` property (never `Logger.getLogger()` directly), and pick **one**
channel per event: `NotificationUtils` for a terminal user-visible outcome,
`IdeaConsole` for what the plugin is doing, `LOG` for developer-facing detail.

The channel-selection rule, the anti-pattern list (`LOG.error`, `LOG.debug`,
`println`, swallowed exceptions, …) and the placement rules are normative in
[AGENTS.md §"Logging"](../../AGENTS.md#logging) and CI-enforced by
`AntiPatternGateTest`. Defer to it.

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

PSI / `Project`-aware tests extend `EasyApiLightCodeInsightFixtureTestCase`
(the project's base class for `LightCodeInsightFixtureTestCase`); pure registry
rules are extracted as `internal companion fun` so they can be unit-tested
without a `Project` / `plugin.xml`.

**Invoke the `write-test-case` skill before writing tests** — it guides
test-pattern selection (simple unit, IDE fixture, `ResultLoader`, action mock,
parity test) based on the target class. The full testing rules — including the
cross-platform golden-file rule that every snapshot test depends on — are
normative in [AGENTS.md §"Testing"](../../AGENTS.md#testing).
