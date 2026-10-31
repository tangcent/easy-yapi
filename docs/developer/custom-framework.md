# Custom Framework

The **Custom** framework is a rules-driven source framework under
`com.itangcent.easyapi.framework.custom` (id `"custom"`) whose entire
extraction logic — class recognition, method recognition, HTTP method,
path, and parameter binding — is driven by a unified `custom.*` rule
surface. It is the v3.0 replacement for the v2.x `mdoc.class.filter` /
`mdoc.method.filter` "generic export" subsystem that was dropped in the
rewrite (issue #1423).

Unlike the built-in frameworks (Spring MVC, JAX-RS, Feign, gRPC), the
Custom framework performs **no hard-coded annotation detection**. Every
extraction decision is delegated to a `custom.*` rule evaluated by the
`RuleEngine`. It produces standard `ApiEndpoint` / `HttpMetadata`, so
every existing output channel (Markdown, Postman, YApi, cURL, IntelliJ
HTTP Client, Hoppscotch, OpenAPI) consumes its output natively with
**zero channel-side changes**.

## Enablement

The Custom framework is **disabled by default** (`enabledByDefault = false`,
matching Feign). Enable it via Settings → Framework Support → "custom",
then supply extraction rules via your `.yapi.config` / rules file.

### Line-marker toggle

By default the Custom framework does **not** contribute gutter icons —
rule-driven recognition is not cheap, and marking every class that happens
to match a `custom.*` rule would surprise users. If you want gutter icons
on classes recognized by your `custom.class.is.api` rule, opt in via
Settings → EasyApi → Custom → "Enable line marker for Custom API classes".
This flips `CustomSettings.enableLineMarker` to `true`, which makes
`CustomApiRecognizer.matchesClass` return `true` (the line-marker
fast-path). The toggle is read fresh from settings on each call, so
toggling it off immediately disables the markers again.

> The `matchesClass` contract forbids consulting the rule engine — so the
> toggle is a coarse "claim every class for the line-marker provider"
> switch, not a per-class rule evaluation. The line-marker provider then
> proceeds to the more expensive per-method check via `ApiIndex` /
> `isApiMethod` as usual.

## The `custom.*` rule surface

All 18 keys (13 extraction + 5 framework-scoped lifecycle) are declared in
[`CustomRuleKeys`](../../src/main/kotlin/com/itangcent/easyapi/framework/custom/CustomRuleKeys.kt)
(an `object` in the framework's own package, mirroring the
`HoppscotchRuleKeys` / `OpenApiRuleKeys` pattern) so `RuleProvider.loadRules`
resolves them (undeclared keys are silently dropped — the root cause of
#1423). They are surfaced to the rule registry via
`CustomApiRecognizer.ruleKeys()`, which `RuleKeyRegistry` consumes as its
framework-specific source.

### Extraction keys (13)

| RuleKey constant | Config key | Type | Evaluated against | Returns |
|---|---|---|---|---|
| `CUSTOM_CLASS_IS_API` | `custom.class.is.api` | `BooleanKey` | `PsiClass` | `true` if the class is an API class |
| `CUSTOM_METHOD_IS_API` | `custom.method.is.api` | `BooleanKey` | `PsiMethod` | `true` if the method is an endpoint |
| `CUSTOM_HTTP_METHOD` | `custom.http.method` | `StringKey` | `PsiMethod` | HTTP verb (`GET`/`POST`/...) |
| `CUSTOM_PATH` | `custom.path` | `StringKey` | `PsiClass` *or* `PsiMethod` | base path (class) / method path (method) |
| `CUSTOM_PARAM_AS_JSON_BODY` | `custom.param.as.json.body` | `BooleanKey` | `PsiParameter` | `true` → bind as request body |
| `CUSTOM_PARAM_AS_FORM_BODY` | `custom.param.as.form.body` | `BooleanKey` | `PsiParameter` | `true` → bind as form field |
| `CUSTOM_PARAM_AS_PATH_VAR` | `custom.param.as.path.var` | `BooleanKey` | `PsiParameter` | `true` → bind as path variable |
| `CUSTOM_PARAM_AS_COOKIE` | `custom.param.as.cookie` | `BooleanKey` | `PsiParameter` | `true` → bind as cookie |
| `CUSTOM_PARAM_PATH_VAR` | `custom.param.path.var` | `StringKey` | `PsiParameter` | path-variable name override |
| `CUSTOM_PARAM_HEADER` | `custom.param.header` | `StringKey` | `PsiParameter` | header name (when binding=header) |
| `CUSTOM_PARAM_COOKIE` | `custom.param.cookie` | `StringKey` | `PsiParameter` | cookie name (when binding=cookie) |
| `CUSTOM_PARAM_COOKIE_VALUE` | `custom.param.cookie.value` | `StringKey` | `PsiParameter` | cookie value override |
| `CUSTOM_PARAM_NAME` | `custom.param.name` | `StringKey` | `PsiParameter` | parameter name override (query/form) |

### Framework-scoped lifecycle keys (5)

These `EventKey`s are fired by
[`CustomRuleEventAspect`](../../src/main/kotlin/com/itangcent/easyapi/framework/custom/CustomRuleEventAspect.kt)
— an `apiParseAspect` implementation with `order = 1` whose `appliesTo` matches
`CustomApiRecognizer.FRAMEWORK_NAME`. The exporter dispatches through
`ApiParseAspects`, and the aspect fires these hooks **alongside** the
corresponding shared hooks (`StandardRuleEventAspect` runs first because its
`order` is `0`). The shared hook fires first, then the
custom-specific hook, for each phase. They exist because the rule
evaluation context does not expose the framework name to user-written
rules — so a side-effect rule that must only run during Custom-framework
extraction (e.g. when both the built-in Spring framework and the Custom
framework are enabled simultaneously) has no other way to scope itself.

| RuleKey constant | Config key | Fired alongside (shared) | Evaluated against |
|---|---|---|---|
| `CUSTOM_CLASS_PARSE_BEFORE` | `custom.class.parse.before` | `api.class.parse.before` | `PsiClass` |
| `CUSTOM_CLASS_PARSE_AFTER` | `custom.class.parse.after` | `api.class.parse.after` | `PsiClass` |
| `CUSTOM_METHOD_PARSE_BEFORE` | `custom.method.parse.before` | `api.method.parse.before` | `ResolvedMethod` |
| `CUSTOM_METHOD_PARSE_AFTER` | `custom.method.parse.after` | `api.method.parse.after` | `ResolvedMethod` |
| `CUSTOM_EXPORT_AFTER` | `custom.export.after` | `export.after` | `ResolvedMethod` (with `ctx.setExt("api", endpoint)`) |

The Custom exporter also honors the shared keys read via
`DocMetadataResolver`: `api.name`, `method.doc`, `class.doc`,
`folder.name`, `param.ignore`, `param.name`, `param.doc`,
`param.required`, `param.type`, `param.default.value`, `param.demo`,
`param.mock`, `method.default.http.method`, `method.content.type`,
`method.additional.header`, `method.additional.param`,
`method.additional.response.header`, `class.prefix.path`,
`endpoint.prefix.path`, and the shared lifecycle hooks
(`api.class.parse.before`/`after`, `api.method.parse.before`/`after`,
`api.param.parse.before`/`after`, `export.after`). The Custom framework
fires its own `custom.*` lifecycle hooks alongside these (see above).

### Parameter binding precedence

1. Boolean classifiers (`custom.param.as.json.body`,
   `custom.param.as.form.body`, `custom.param.as.path.var`,
   `custom.param.as.cookie`) — most-specific wins.
2. The coarser `param.http.type` classifier (`"body"`/`"form"`/
   `"path"`/`"header"`/`"cookie"`/`"query"`).
3. Default: `Query` (with an `IdeaConsole.info` note).

### HTTP method fallback

`custom.http.method` → `method.default.http.method` → `POST` (with an
`IdeaConsole.info` note naming the method). The `POST` default matches
the v2.x generic-export behavior.

### Path composition

Final path = `class.prefix.path` + class base path + method path +
`endpoint.prefix.path`, joined with a single `/` and collapsing
duplicate slashes. The class base path comes from evaluating
`custom.path` against the `PsiClass`; the method path from evaluating
`custom.path` against the `PsiMethod`. The rule context exposes
`it.contextType()` (returning `"class"` vs `"method"`) so a single rule
body can branch.

## Worked example — Spring-equivalent reference ruleset

The file
[`custom-spring-reference.rules`](custom-spring-reference.rules)
reimplements Spring MVC recognition and extraction using **only**
`custom.*` keys (plus the shared `method.default.http.method` and
`param.http.type` keys). It is both a learning aid and the proof that
the Custom framework can express arbitrary conventions.

To use it: enable the Custom framework in Settings → Framework Support,
then copy the ruleset's contents into your `.yapi.config` / rules file
(or `###include` it).

The parity test
([`CustomSpringReferenceParityTest`](../../src/test/kotlin/com/itangcent/easyapi/framework/custom/CustomSpringReferenceParityTest.kt))
exercises the ruleset against a Spring sample and compares the output
to the built-in Spring MVC exporter. It asserts structural equality on
the load-bearing HTTP facts (method, path, parameter bindings,
Content-Type, response type, body presence, header set) with a small,
documented set of tolerances (default HTTP method when Spring omits
one; header/parameter ordering; path normalization edge cases).

## Migration from v2.x `mdoc.*` keys (#1423)

The v2.x `mdoc.class.filter` / `mdoc.method.filter` keys were dropped in
the v3.0 rewrite and now fail silently. The migration path:

| v2.x key | v3.0 Custom framework key |
|---|---|
| `mdoc.class.filter=groovy:it.hasAnn("xxx")` | `custom.class.is.api=groovy:it.hasAnn("xxx")` |
| `mdoc.method.filter=groovy:it.hasAnn("yyy")` | `custom.method.is.api=groovy:it.hasAnn("yyy")` |

The `mdoc.*` keys are **not** reintroduced (NFR-5: unified rule
surface). The `custom.*` surface is strictly more capable: it also
covers HTTP method, path, and parameter binding — none of which the
`mdoc.*` keys addressed.

## Implementation

All Custom-framework code lives under
`src/main/kotlin/com/itangcent/easyapi/framework/custom/`:

- [`CustomRuleKeys`](../../src/main/kotlin/com/itangcent/easyapi/framework/custom/CustomRuleKeys.kt)
  — `object` declaring the 18 `custom.*` keys (13 extraction + 5
  framework-scoped lifecycle; mirrors the `HoppscotchRuleKeys` /
  `OpenApiRuleKeys` pattern). Surfaced to `RuleKeyRegistry` via
  `CustomApiRecognizer.ruleKeys()`.
- [`CustomApiRecognizer`](../../src/main/kotlin/com/itangcent/easyapi/framework/custom/CustomApiRecognizer.kt)
  — rule-driven class recognition (`frameworkName = "custom"`,
  `targetAnnotations = emptySet()`, `enabledByDefault = false`).
  `matchesClass` is gated by `CustomSettings.enableLineMarker` (default
  `false`); `createSettingsPanel(project)` contributes the Custom settings
  tab via the general `SettingsPanelProvider` contract, but returns `null`
  when the framework is disabled (no panel for a disabled feature).
- [`CustomClassExporter`](../../src/main/kotlin/com/itangcent/easyapi/framework/custom/CustomClassExporter.kt)
  — rule-driven endpoint extraction. Lifecycle hooks (shared and
  framework-scoped `custom.*` class/method parse hooks, `EXPORT_AFTER` +
  `CUSTOM_EXPORT_AFTER`) are dispatched through `ApiParseAspects` to
  `StandardRuleEventAspect` / `CustomRuleEventAspect`, not evaluated inline.
  Value-read rules (`custom.class.is.api`, `custom.method.is.api`,
  `custom.http.method`, `custom.path`, the `custom.param.*` classifiers)
  still call `RuleEngine` directly. All PSI access stays under read actions.
- [`CustomSettings`](../../src/main/kotlin/com/itangcent/easyapi/framework/custom/CustomSettings.kt)
  — `data class` persisting the `enableLineMarker` toggle via the unified
  `UnifiedAppSettingsState` (APPLICATION scope; no per-module state class
  or `plugin.xml` registration needed).
- [`CustomSettingsPanel`](../../src/main/kotlin/com/itangcent/easyapi/framework/custom/CustomSettingsPanel.kt)
  — self-contained `SettingsPanel` with the `enableLineMarker` checkbox.
  Reads/writes `CustomSettings` via `SettingBinder` internally (mirrors
  `HoppscotchSettingsPanel`), ignoring the `Settings` arg passed to
  `resetFrom` / `applyTo` / `isModified`.
