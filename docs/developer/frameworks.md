# Adding a new framework

A **framework** scans PSI for `ApiEndpoint`s declared with a specific
framework's annotations (Spring MVC, JAX-RS, Feign, gRPC, Micronaut, …).
Adding a new one is a one-package operation plus **two** `plugin.xml` lines.

> **Shared concerns** (threading, logging, enablement, testing) are in the
> [developer README](README.md). This page covers framework-specific concerns
> only.

## When to write a framework

Write a framework when you have a new **source framework** that declares API
endpoints in source code. The 5 built-in frameworks:

| Framework | Recognizer | Exporter | `enabledByDefault` |
|-----------|------------|-----------|--------------------|
| Spring MVC | [`SpringControllerRecognizer`](../../src/main/kotlin/com/itangcent/easyapi/framework/springmvc/SpringControllerRecognizer.kt) | [`SpringMvcClassExporter`](../../src/main/kotlin/com/itangcent/easyapi/framework/springmvc/SpringMvcClassExporter.kt) | `true` |
| Actuator | [`ActuatorEndpointRecognizer`](../../src/main/kotlin/com/itangcent/easyapi/framework/springmvc/ActuatorEndpointRecognizer.kt) | [`ActuatorEndpointExporter`](../../src/main/kotlin/com/itangcent/easyapi/framework/springmvc/ActuatorEndpointExporter.kt) | `false` |
| JAX-RS | [`JaxRsResourceRecognizer`](../../src/main/kotlin/com/itangcent/easyapi/framework/jaxrs/JaxRsResourceRecognizer.kt) | [`JaxRsClassExporter`](../../src/main/kotlin/com/itangcent/easyapi/framework/jaxrs/JaxRsClassExporter.kt) | `true` |
| Feign | [`FeignClientRecognizer`](../../src/main/kotlin/com/itangcent/easyapi/framework/feign/FeignClientRecognizer.kt) | [`FeignClassExporter`](../../src/main/kotlin/com/itangcent/easyapi/framework/feign/FeignClassExporter.kt) | `false` |
| gRPC | [`GrpcServiceRecognizer`](../../src/main/kotlin/com/itangcent/easyapi/framework/grpc/GrpcServiceRecognizer.kt) | [`GrpcClassExporter`](../../src/main/kotlin/com/itangcent/easyapi/framework/grpc/GrpcClassExporter.kt) | `true` |

If you need to *export `ApiEndpoint`s* (already extracted by a framework
exporter) to a new destination, that's a [channel](channels.md).

## The two EPs

A framework registers on **two** IntelliJ extension points — this is the
single most common contributor mistake (registering only the exporter and
silently breaking line markers, index scanning, and AI discovery).

| EP name | Interface FQN | Purpose |
|---|---|---|
| `classExporter` | [`com.itangcent.easyapi.core.export.ClassExporter`](../../src/main/kotlin/com/itangcent/easyapi/core/export/ClassExporter.kt) | Extract `ApiEndpoint`s from a `PsiClass`. Heavy; called per recognized class during a scan. |
| `apiClassRecognizer` | [`com.itangcent.easyapi.core.export.recognizer.ApiClassRecognizer`](../../src/main/kotlin/com/itangcent/easyapi/core/export/recognizer/ApiClassRecognizer.kt) | Cheap "is this an API class?" check. Drives line markers, `AnnotatedElementsSearch` index scanning, and AI discovery. **`FrameworkRegistry` keys enablement off this EP.** |

Both EPs are `area="IDEA_PROJECT"` → IntelliJ injects `Project` via the
constructor (one instance per project). The `frameworkName` you set on the
recognizer **is the join key** that ties the exporter, the recognizer, and
`FrameworkRegistry.isEnabled(frameworkName)` together — and is what the user
sees in Settings → General → "Framework Support". A mismatch between the two
silently disables the framework.

## The two SPIs

### `ClassExporter`

The full contract lives in
[`core/export/ClassExporter.kt`](../../src/main/kotlin/com/itangcent/easyapi/core/export/ClassExporter.kt).

| Member | Type | Default | Purpose |
|---|---|---|---|
| `frameworkName` | `String` | — | Join key — MUST match the recognizer's `frameworkName`. |
| `isEnabled()` | `suspend fun → Boolean` | `true` | One-liner: `FrameworkRegistry.getInstance(project).isEnabled(frameworkName)`. |
| `export(psiClass)` | `suspend fun → List<ApiEndpoint>` | — | Extract endpoints from a `PsiClass`. Called only when `isEnabled` is `true`. |

Constructor signature (required by `IDEA_PROJECT` area):

```kotlin
class XxxClassExporter(private val project: Project) : ClassExporter { … }
```

### `ApiClassRecognizer`

The full contract lives in
[`core/export/recognizer/ApiClassRecognizer.kt`](../../src/main/kotlin/com/itangcent/easyapi/core/export/recognizer/ApiClassRecognizer.kt).

| Member | Type | Default | Purpose |
|---|---|---|---|
| `frameworkName` | `String` | — | Join key — MUST match the exporter's `frameworkName`. |
| `targetAnnotations` | `Set<String>` | — | Annotation FQNs for `AnnotatedElementsSearch` (index scanning). |
| `isApiClass(psiClass)` | `suspend fun → Boolean` | — | Slow / accurate check; may consult `RuleEngine` + `MetaAnnotationResolver`. |
| `isEnabled(project)` | `fun → Boolean` | `true` | Per-project state gate (e.g. annotation presence); AND-combined with `FrameworkRegistry`. |
| `enabledByDefault` | `Boolean` | `true` | Compile-time enablement default. |
| `matchesClass(psiClass)` | `fun → Boolean` | `false` | Per-class fast-path for line markers. **MUST NOT consult the rule engine.** |

Constructor signature (EP instantiates with no-arg — recognizers receive
their dependencies via constructor parameters with defaults):

```kotlin
class XxxResourceRecognizer(
    private val ruleEngine: RuleEngine? = null,
    private val enabled: Boolean = true
) : ApiClassRecognizer { … }
```

The CompositeApiClassRecognizer discovers recognizers via the EP and filters
by `FrameworkRegistry.isEnabled(it) && it.isEnabled(project)`. The filter is
re-evaluated whenever settings change (the composite subscribes to
settings-change events and rebuilds its cache).

### `matchesClass` contract — load-bearing

`matchesClass` is a **fast-path** used by line-marker providers — it must
NOT consult the rule engine (which is consulted only in `isApiClass`).
Default `false` is correct for annotation-driven frameworks (Spring MVC,
JAX-RS, Feign, Actuator — `isApiClass` already returns `false` quickly for
non-matching classes). Only gRPC overrides this, because its
`extends BindableService` walk is a per-class fast-path the line marker
needs. See
[`GrpcServiceRecognizer.matchesClass`](../../src/main/kotlin/com/itangcent/easyapi/framework/grpc/GrpcServiceRecognizer.kt#L52-L55).

Getting this wrong regresses line-marker behavior — rule-engine overrides
would otherwise cause non-gRPC classes to be marked as gRPC services by the
line marker.

## Step-by-step

JAX-RS is the canonical mirror — its four-resolver split is the layout to
copy.

### Step 1 — Recognizer

Create `framework/<id>/<Id>ResourceRecognizer.kt`. Set `frameworkName`,
`targetAnnotations` (FQNs for `AnnotatedElementsSearch`), `enabledByDefault`.
Implement `isApiClass` (may consult `RuleEngine` +
[`MetaAnnotationResolver`](../../src/main/kotlin/com/itangcent/easyapi/core/export/recognizer/MetaAnnotationResolver.kt)).
Override `matchesClass` only if your framework needs an expensive
non-annotation fast-path (gRPC does; annotation-driven frameworks don't).

```kotlin
package com.itangcent.easyapi.framework.micronaut

import com.intellij.psi.PsiClass
import com.itangcent.easyapi.core.export.recognizer.ApiClassRecognizer
import com.itangcent.easyapi.core.export.recognizer.MetaAnnotationResolver
import com.itangcent.easyapi.core.rule.RuleKeys
import com.itangcent.easyapi.core.rule.engine.RuleEngine

class MicronautControllerRecognizer(
    private val ruleEngine: RuleEngine? = null,
    private val enabled: Boolean = true
) : ApiClassRecognizer {

    override val frameworkName: String = "Micronaut"
    override val targetAnnotations: Set<String> = CONTROLLER_ANNOTATIONS
    override val enabledByDefault: Boolean = false

    override suspend fun isApiClass(psiClass: PsiClass): Boolean {
        if (!enabled) return false
        if (ruleEngine?.evaluate(RuleKeys.CLASS_IS_MICRONAUT_CTRL, psiClass) == true) return true
        return MetaAnnotationResolver.hasMetaAnnotation(psiClass, CONTROLLER_ANNOTATIONS)
    }

    companion object {
        val CONTROLLER_ANNOTATIONS = setOf(
            "io.micronaut.http.annotation.Controller"
        )
    }
}
```

Reference: [`JaxRsResourceRecognizer`](../../src/main/kotlin/com/itangcent/easyapi/framework/jaxrs/JaxRsResourceRecognizer.kt).

### Step 2 — Exporter

Create `framework/<id>/<Id>ClassExporter.kt`. Constructor takes `Project`;
`frameworkName` matches the recognizer; `isEnabled` is one line:
`FrameworkRegistry.getInstance(project).isEnabled(frameworkName)`. In
`export(psiClass)` iterate `ResolvedType.suitableMethods()`, fire rule
lifecycle hooks (see below), and build `ApiEndpoint`s via `EndpointBuilder`.

```kotlin
package com.itangcent.easyapi.framework.micronaut

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.itangcent.easyapi.core.export.*
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.psi.helper.DocMetadataResolver
import com.itangcent.easyapi.core.psi.type.ResolvedType
import com.itangcent.easyapi.core.internal.threading.read
import com.itangcent.easyapi.core.rule.engine.RuleEngine
import com.itangcent.easyapi.framework.spi.FrameworkRegistry

class MicronautClassExporter(
    private val project: Project
) : ClassExporter {

    override val frameworkName: String = "Micronaut"

    override suspend fun isEnabled(): Boolean =
        FrameworkRegistry.getInstance(project).isEnabled("Micronaut")

    private val engine = RuleEngine.getInstance(project)
    private val recognizer = MicronautControllerRecognizer(engine)
    private val pathResolver = MicronautPathResolver(/* annotationHelper */)
    private val methodResolver = MicronautHttpMethodResolver(/* annotationHelper */)
    private val parameterResolver = MicronautParameterResolver(/* annotationHelper */)
    private val metadataResolver = DocMetadataResolver.getInstance(project)
    private val endpointBuilder = EndpointBuilder.getInstance(project)

    override suspend fun export(psiClass: PsiClass): List<ApiEndpoint> {
        if (!recognizer.isApiClass(psiClass)) return emptyList()
        // ... iterate suitableMethods(), fire rule hooks, build ApiEndpoint ...
    }
}
```

Reference: [`JaxRsClassExporter`](../../src/main/kotlin/com/itangcent/easyapi/framework/jaxrs/JaxRsClassExporter.kt)
(4-resolver split, full rule lifecycle, the canonical "annotation-driven
framework" template).

### Step 3 — Resolvers (optional, plain classes)

Split per-concern logic into plain Kotlin classes taking an
`AnnotationHelper` (or similar), wired into the exporter at construction:

| Resolver | Concern |
|---|---|
| `*PathResolver` | Combine class-level and method-level path annotations. |
| `*HttpMethodResolver` | Map framework annotations to `HttpMethod`. |
| `*ParameterResolver` | Map framework parameter annotations (`@QueryParam`, `@PathParam`, …) to `ApiParameter` + `ParameterBinding`. |
| `*ContentTypeResolver` | Extract `@Consumes` / `@Produces` or equivalent. |

These are **not registered anywhere** — they're construction-time
dependencies of the exporter. Reference the JAX-RS 4-resolver split:
[`JaxRsPathResolver`](../../src/main/kotlin/com/itangcent/easyapi/framework/jaxrs/JaxRsPathResolver.kt),
[`JaxRsHttpMethodResolver`](../../src/main/kotlin/com/itangcent/easyapi/framework/jaxrs/JaxRsHttpMethodResolver.kt),
[`JaxRsParameterResolver`](../../src/main/kotlin/com/itangcent/easyapi/framework/jaxrs/JaxRsParameterResolver.kt),
[`JaxRsContentTypeResolver`](../../src/main/kotlin/com/itangcent/easyapi/framework/jaxrs/JaxRsContentTypeResolver.kt).

### Step 4 — `plugin.xml` (two lines)

Add **two** lines under `<extensions defaultExtensionNs="...">` in
[`plugin.xml`](../../src/main/resources/META-INF/plugin.xml#L35-L47):

```xml
<classExporter implementation="com.itangcent.easyapi.framework.micronaut.MicronautClassExporter"/>
<apiClassRecognizer implementation="com.itangcent.easyapi.framework.micronaut.MicronautControllerRecognizer"/>
```

Both EPs are `area="IDEA_PROJECT"` → `Project` is injected via the
exporter's constructor. (The recognizer's no-arg constructor builds the
default `ruleEngine = null` / `enabled = true` shape; the
`CompositeApiClassRecognizer` re-evaluates enablement on each access via
`FrameworkRegistry`.)

## What core gives you

A framework implementation has substantial shared infrastructure to lean
on:

- [`EndpointBuilder`](../../src/main/kotlin/com/itangcent/easyapi/core/export/EndpointBuilder.kt)
  — shared utility for building endpoint components:
  - `buildHeaders(contentType, paramHeaders, additionalHeaders, additionalResponseHeaders)` — header dedup + merge
  - `buildResponseBody(method, resolvedReturnType)` — response model with `method.return` / `method.return.main` rule overrides
  - `expandBodyParam(resolvedParamType)` — request body model from a `@Body` parameter
  - `mergePathParameters(...)` — path-param enrichment (enum / default / description)
  - `applyReturnMain(...)` — apply `@return` doc to a specific field
- [`DocMetadataResolver`](../../src/main/kotlin/com/itangcent/easyapi/core/psi/helper/DocMetadataResolver.kt)
  — rule-aware resolvers for `api.name`, `method.doc`, folders,
  param defaults, additional headers, additional response headers, ignored
  params.
- [`TypeResolver`](../../src/main/kotlin/com/itangcent/easyapi/core/psi/type/ResolvedTypes.kt)
  / `ResolvedType` — type resolution with generics substitution; iterate
  methods via `ResolvedType.ClassType.suitableMethods()`.
- [`UnifiedAnnotationHelper`](../../src/main/kotlin/com/itangcent/easyapi/core/psi/helper/UnifiedAnnotationHelper.kt)
  — language-agnostic annotation lookup (works for Java, Kotlin, Groovy,
  Scala).
- `RuleEngine` + `RuleKeys` — rule evaluation for per-class / per-method
  overrides (`class.is.spring.controller`, `method.content.type`,
  `method.return`, …).
- [`FrameworkRegistry`](../../src/main/kotlin/com/itangcent/easyapi/framework/spi/FrameworkRegistry.kt)
  — the single enablement chokepoint. `isEnabled(frameworkId)` returns
  `true` if the user has explicitly enabled it (its id is in
  `GeneralSettings.enabledFrameworks`), OR it is `enabledByDefault` and not
  explicitly disabled.

[`CompositeApiClassRecognizer`](../../src/main/kotlin/com/itangcent/easyapi/core/export/recognizer/CompositeApiClassRecognizer.kt)
discovers recognizers via the EP, filters by `isEnabled`, caches, and
rebuilds on settings change. It exposes `recognizers()`,
`allTargetAnnotations`, and `isApiClass(psiClass)` to AI tools and other
core consumers — so **don't** hard-import your recognizer elsewhere. The
composite is the only legitimate way for `core.*` to reach a framework
recognizer (the `framework.spi.*` carve-out in the DAG rule).

## Rule lifecycle hooks

A framework exporter MUST fire the rule lifecycle hooks around its
parse loop so user rules (`api_class.parse.before`, `method.doc`,
`api.after`, …) can run. The exact `engine.evaluate(...)` calls (from
[`JaxRsClassExporter`](../../src/main/kotlin/com/itangcent/easyapi/framework/jaxrs/JaxRsClassExporter.kt)):

```kotlin
// before iterating methods:
withContext(IdeDispatchers.Background) {
    engine.evaluate(RuleKeys.API_CLASS_PARSE_BEFORE, psiClass)
}

try {
    for (resolvedMethod in resolvedType.suitableMethods()) {
        // before each method:
        withContext(IdeDispatchers.Background) {
            engine.evaluate(RuleKeys.API_METHOD_PARSE_BEFORE, resolvedMethod)
        }
        try {
            // ... build the ApiEndpoint ...
            withContext(IdeDispatchers.Background) {
                for (endpoint in endpoints) {
                    engine.evaluate(RuleKeys.EXPORT_AFTER, resolvedMethod) { ctx ->
                        ctx.setExt("api", endpoint)
                    }
                }
            }
        } finally {
            // after each method (success or failure):
            withContext(IdeDispatchers.Background) {
                engine.evaluate(RuleKeys.API_METHOD_PARSE_AFTER, resolvedMethod)
            }
        }
    }
} finally {
    // after the whole class:
    withContext(IdeDispatchers.Background) {
        engine.evaluate(RuleKeys.API_CLASS_PARSE_AFTER, psiClass)
    }
}
```

`API_METHOD_PARSE_AFTER` and `API_CLASS_PARSE_AFTER` go in `finally` blocks
so they fire even when a method throws. `EXPORT_AFTER` runs after the
endpoint is built and sets the `api` extension on the rule context so user
rules can mutate the endpoint before it leaves the exporter.

## The `ApiEndpoint` shape

The full contract lives in
[`core/export/ApiModels.kt`](../../src/main/kotlin/com/itangcent/easyapi/core/export/ApiModels.kt).

```kotlin
data class ApiEndpoint(
    val name: String? = null,
    val folder: String? = null,
    var description: String? = null,
    val sourceClass: PsiClass? = null,
    val sourceMethod: PsiMethod? = null,
    val className: String? = null,
    val classDescription: String? = null,
    val metadata: ApiMetadata,                  // HttpMetadata or GrpcMetadata
    val extensions: Extension = Extension.EMPTY
)
```

Build HTTP endpoints via the
[`httpMetadata(...)`](../../src/main/kotlin/com/itangcent/easyapi/core/export/ApiModels.kt#L316)
factory:

```kotlin
val endpoint = ApiEndpoint(
    name = name,
    folder = folder,
    description = description,
    sourceClass = psiClass,
    sourceMethod = method,
    className = classQualifiedName,
    classDescription = classFolder.description,
    metadata = httpMetadata(
        path = path,
        method = httpMethod,
        parameters = params + additionalParams,
        headers = headers,
        contentType = contentType,
        body = body,
        responseBody = responseBody,
        responseType = responseTypeName
    )
)
```

`HttpMetadata` fields: `path`, `method`, `parameters`, `headers`,
`contentType`, `bodyAttr`, `alternativePaths`, `body`, `responseBody`,
`responseType`. The `ParameterBinding` sealed class is what binds each
parameter to a request location:

| `ParameterBinding` | HTTP location |
|---|---|
| `Query` | Query string (`?name=value`) |
| `Path` | URL path (`/users/{id}`) |
| `Header` | HTTP header |
| `Cookie` | Cookie value |
| `Body` | Request body |
| `Form` | Form data |
| `Ignored` | Framework-injected (skip in output) |

For gRPC, build a `GrpcMetadata` instead (`path`, `serviceName`,
`methodName`, `packageName`, `streamingType`, `protoFile`, `body`,
`responseBody`, `responseType`).

## Worked example — minimal Micronaut framework skeleton

Recognizer + exporter + 2 resolvers + 2 `plugin.xml` lines:

`MicronautControllerRecognizer.kt` — see Step 1 above.

`MicronautPathResolver.kt`:

```kotlin
class MicronautPathResolver(private val annotationHelper: AnnotationHelper) {
    suspend fun resolve(psiClass: PsiClass, psiMethod: PsiMethod): String {
        val classPath = annotationHelper.findAttrAsString(psiClass, "io.micronaut.http.annotation.Controller", "value")
            ?.trim('/') ?: ""
        val methodPath = annotationHelper.findAttrAsString(psiMethod, "io.micronaut.http.annotation.Get", "value")
            ?: annotationHelper.findAttrAsString(psiMethod, "io.micronaut.http.annotation.Post", "value")
            ?: ""
        return "/" + listOf(classPath, methodPath.trim('/')).filter { it.isNotEmpty() }.joinToString("/")
    }
}
```

`MicronautHttpMethodResolver.kt`:

```kotlin
class MicronautHttpMethodResolver(private val annotationHelper: AnnotationHelper) {
    suspend fun resolve(psiMethod: PsiMethod): HttpMethod? {
        for ((ann, method) in ANNOTATION_TO_METHOD) {
            if (annotationHelper.findAnnotation(psiMethod, ann) != null) return method
        }
        return null
    }

    companion object {
        private val ANNOTATION_TO_METHOD = listOf(
            "io.micronaut.http.annotation.Get" to HttpMethod.GET,
            "io.micronaut.http.annotation.Post" to HttpMethod.POST,
            "io.micronaut.http.annotation.Put" to HttpMethod.PUT,
            "io.micronaut.http.annotation.Delete" to HttpMethod.DELETE,
            "io.micronaut.http.annotation.Patch" to HttpMethod.PATCH,
        )
    }
}
```

`MicronautClassExporter.kt` — see Step 2 above; follow
[`JaxRsClassExporter.exportMethod`](../../src/main/kotlin/com/itangcent/easyapi/framework/jaxrs/JaxRsClassExporter.kt#L100-L197)
for the per-method parse + endpoint-build pattern.

`plugin.xml`:

```xml
<classExporter implementation="com.itangcent.easyapi.framework.micronaut.MicronautClassExporter"/>
<apiClassRecognizer implementation="com.itangcent.easyapi.framework.micronaut.MicronautControllerRecognizer"/>
```

That's the entire integration. The recognizer drives line markers, index
scanning, AI discovery, and Settings → General → "Framework Support"; the
exporter drives the actual endpoint extraction.

## Import rules

A framework may import from:

- `core.export.*` (`ClassExporter`, `ApiEndpoint`, `EndpointBuilder`,
  `ExportContext`, `Extension`, `ParameterBinding`, `HttpMethod`,
  `HttpMetadata`, …)
- `core.export.recognizer.*` (`ApiClassRecognizer`,
  `MetaAnnotationResolver`)
- `core.psi.*` (`PsiClassHelper`, `ResolvedType`, `TypeResolver`,
  `UnifiedAnnotationHelper`, `DocMetadataResolver`, `ObjectModel`)
- `core.config.*`, `core.rule.*`, `core.settings.*`
- `core.grpc.*` (only for `framework.grpc` — descriptor reflection, proto)
- `core.util.*`, `core.logging.*`, `core.internal.threading.*`
- `framework.spi.*` (`FrameworkRegistry` — the only `framework.*` sibling
  import allowed for `core.*`; frameworks may of course import it too)
- IntelliJ SDK
- Your own `framework.<id>.*` package

A framework **MUST NOT** import from:

- `channel.*` (channels consume endpoints; frameworks produce them)
- `format.*` (formats consume field models; frameworks produce endpoints)
- Sibling `framework.<other-id>.*` packages

## Testing

- **Recognizer unit tests** — plain JUnit; build a `PsiClass` from a
  fixture, assert `isApiClass` returns the expected value. Test
  meta-annotations (a custom annotation annotated with `@Controller`).
- **Exporter integration tests** — extend
  `EasyApiLightCodeInsightFixtureTestCase`, load a fixture `.java` / `.kt`,
  call `export(psiClass)`, snapshot the resulting `ApiEndpoint` list via
  `ResultLoader.load(...)`. Test rule-lifecycle hooks by configuring a rule
  that mutates the endpoint and asserting the mutation landed.
- **Enablement tests** — `FrameworkRegistry.resolveEnabled` is extracted as
  a pure `internal companion fun`; unit-test the truth table without a
  `Project`.

Reference tests:
[`ApiEndpointTest`](../../src/test/kotlin/com/itangcent/easyapi/core/export/ApiEndpointTest.kt),
[`FrameworkRegistryTest`](../../src/test/kotlin/com/itangcent/easyapi/framework/spi)
(if present).

See [shared testing concerns](README.md#testing) in the developer README,
and **invoke the `write-test-case` skill before writing tests**.
