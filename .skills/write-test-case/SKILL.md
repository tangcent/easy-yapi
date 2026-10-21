---
name: write-test-case
description: Guide how to write test cases in this project by first analyzing the target class to decide which test pattern, base class, and test utilities to use. Activate when user asks to write tests, add test coverage, or create test cases for any class.
---

# Write Test Case

## Overview

This project is an IntelliJ IDEA plugin (EasyAPI). Tests use **JUnit 4** + **Mockito/Mockito-Kotlin** + **IntelliJ Platform Test Framework**. Before writing any test, analyze the target class to pick the right pattern.

---

## Step 1 — Analyze the Target Class

Read the class under test and answer these questions:

| Question | Answer leads to |
|---|---|
| Is it an `AnAction` subclass? | Pattern D — Action test (extends IDE fixture) |
| Does it interact with PSI (PsiClass, PsiMethod, PsiField) or need a real `Project`? | Pattern B — IDE fixture |
| Is its output a large formatted string (Markdown, Postman JSON, OpenAPI, cURL)? | Pattern B + Pattern C — `ResultLoader` golden file |
| Are several implementations supposed to behave the same? | Pattern E — Parity test |
| Is it a pure utility / data class with no IDE dependency? | Pattern A — Simple unit test |

Before writing a new class, look for an existing `{ClassName}Test.kt` — extend it
rather than creating a parallel test file. Copy the setup (loaded files, config
reader, settings) from the nearest existing test of the same area.

---

## Principle — Test Behavior, Not Implementation

**Never test private methods via reflection.** Private methods are implementation details. Tests should verify *what* a class does, not *how* it does it internally.

If you feel the urge to test a private method directly, treat it as a signal:

- **The class is doing too much** — extract the logic into a new class with a public interface, then test that class directly.
- **Your public tests don't cover enough paths** — add more test cases that exercise the private logic through the public API.

Reflection-based tests are brittle: they break on renames, bypass access modifiers intentionally, and make refactoring painful without adding real confidence.

The only meaningful exception is legacy code you genuinely cannot refactor. Even then, prefer extracting over reflecting.

> Rule: if a private method is worth testing, it's worth making testable — either by testing it through the public API that calls it, or by extracting it into its own class.

---

## Step 2 — Choose the Right Pattern

### Pattern A — Simple Unit Test
**When:** Pure utility, data model, formatter with no IntelliJ platform dependency, or any class with no IDE dependency.

**Base:** None (plain JUnit 4 class)
**Annotations:** `@Test`, `@Before`, `@After`
**Mocking:** `mock<T>()` / `whenever(...)` from `mockito-kotlin`
**Names:** either `fun testSomeBehavior()` or a backtick sentence — both are used
widely; match the file you are extending.

```kotlin
import org.junit.Assert.*
import org.junit.Test

class MyServiceTest {

    @Test
    fun `returns the trimmed path`() {
        val result = MyService.doSomething("input")
        assertEquals("expected", result)
    }
}
```

**Real examples:** `GsonUtilsTest`, `HttpModelsTest`, `CurlFormatterTest`

---

### Pattern B — IDE Fixture Test (PSI / Project-aware)
**When:** Class needs a real `Project`, reads PSI elements, or is a project-level service.

**Base:** `EasyApiLightCodeInsightFixtureTestCase`
**Key methods available:**
- `loadFile(path)` — loads a Java file from `src/test/resources/` into the fixture project
- `loadFile(path, content)` — loads inline Java source
- `findClass(qualifiedName)` — finds a `PsiClass` by FQN
- `findMethod(psiClass, name)` — finds a method on a class
- `waitForClass(qualifiedName, timeoutMs)` — polls until a class is indexed (use when `findClass` returns null due to async indexing)
- `loadJDKClass(fqn)` — adds a JDK class stub to the fixture (generated from the runtime class); needed for collection types etc. `setUp()` already loads `Override`, `Deprecated`, `SuppressWarnings`, `Serializable` and `kotlin.Deprecated`
- `loadSource(clazz)` — loads a class from source into the fixture
- `runTest { }` — runs a suspend block inside `runBlocking`
- `settingBinder` — the project's `SettingBinder` (a fresh `DefaultSettingBinder` per test)
- `createConfigReader()` — override to supply custom config rules (default: `TestConfigReader.empty(project)`)

**Test method naming:** `fun test...()` — the `test` prefix is mandatory and there is no `@Test` annotation (JUnit 3 style inherited from `LightJavaCodeInsightFixtureTestCase`). A method without the prefix silently never runs.

**Different configs in one file:** nest one fixture subclass per config, each with its own `createConfigReader()` override — see the KDoc of `EasyApiLightCodeInsightFixtureTestCase`.

```kotlin
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class MyExporterTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: MyExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        exporter = MyExporter.getInstance(project) // or MyExporter(project)
    }

    // Override to supply config rules for this test class
    override fun createConfigReader() = TestConfigReader.empty(project)

    private fun loadTestFiles() {
        loadFile("spring/RequestMapping.java")
        loadFile("spring/GetMapping.java")
        loadFile("spring/RestController.java")
        loadFile("model/UserInfo.java")
        loadFile("api/UserCtrl.java")
    }

    fun testExportReturnsEndpoints() = runTest {
        val psiClass = findClass("com.itangcent.api.UserCtrl")
        assertNotNull(psiClass)
        val endpoints = exporter.export(psiClass!!)
        assertTrue(endpoints.isNotEmpty())
    }
}
```

**Real examples:** `ExportOrchestratorTest`, `ApiDashboardServiceTest`, `DefaultConfigReaderTest`, `EndToEndExportTest`

**Important — Same-Package Import Requirement:** In the IntelliJ PSI test fixture environment, all classes referenced by a Java file must be explicitly imported, even if they are in the same package. Without proper imports, the PSI will treat unresolved type references as `UnresolvedType`. For example, if `UserController.java` uses `OrderedDTO` from the same package, it must include `import com.itangcent.jackson.OrderedDTO;` even though they share the same package declaration.

**Important — Async PSI Indexing:** After `loadFile()`, `findClass()` may return `null` because the PSI index updates asynchronously. Use `waitForClass(qualifiedName)` which polls until the class is resolvable, instead of relying on a fixed `delay()`. For other asynchronous state (caches, the API index, background scans) use `waitUntil { … }`, `waitUntilNotNull { … }` or `waitUntilNotEmpty { … }` from `testFramework/AwaitUtils.kt` — they fail with an `AssertionError` on timeout instead of passing silently.

---

### Pattern C — ResultLoader (snapshot / golden-file) Test
**When:** The output is a large formatted string (Markdown, Postman JSON, curl commands) that should be compared against a saved expected result.

**Base:** `EasyApiLightCodeInsightFixtureTestCase`
**Utility:** `ResultLoader`

**How it works:**
1. Write the test comparing against `ResultLoader.load("name")`, run it once, and take the actual value from the failure message.
2. Save it to `src/test/resources/result/{FullyQualifiedTestClass}.{name}.txt` with LF line endings, and **read it through** — a golden file records what you checked, not whatever the code printed.
3. Re-run; the test now guards that output. When behavior changes on purpose, update the golden file in the same commit and say why in the commit body.

**File naming convention:**
- Default: `com.itangcent.easyapi.core.export.MyFormatterTest.txt`
- Named: `com.itangcent.easyapi.core.export.MyFormatterTest.testParseRequests.txt`
- Inner classes: `$` is replaced by `.` (e.g., `MyTest.InnerTest` → `MyTest.InnerTest.txt`)

```kotlin
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.ResultLoader

class MyFormatterTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testFormatOutput() = runTest {
        val endpoints = ApiFixtures.createSampleEndpoints()
        val result = MyFormatter().format(endpoints, "Test API")

        // Compare against saved golden file
        assertEquals(ResultLoader.load("testFormatOutput"), result.trimEnd())
    }
}
```

**Real examples:** `PostmanFormatterTest`, `CurlFormatterTest`, `OpenApiFormatterTest`, `HoppHeaderVarConversionTest`

---

### ⚠️ Cross-platform line endings — the golden-file parity trap

**The pitfall.** Code under test typically emits literal `\n`, but a golden file
checked out on a Windows CI runner (default `core.autocrlf=true`) becomes `\r\n`
on disk. A byte-for-byte `assertEquals` then fails on Windows only — passing on
Linux/macOS, hiding the bug until a Windows CI run.

**Always read golden/expected resources through the test framework**, never via
`File.readText()` / `Paths.readText()`. Two helpers, both in
`com.itangcent.easyapi.testFramework`:

| Helper | When to use | Normalization |
|---|---|---|
| `ResultLoader.load()` / `load("name")` | Snapshot/golden test where trailing whitespace doesn't matter (default choice) | CRLF→LF + `trimEnd()` |
| `ResourceLoader.read("/path/in/resources")` | Same, when you control the resource path explicitly | CRLF→LF + `trimEnd()` |
| `ResourceLoader.readRaw("/path/in/resources")` | **Byte-for-byte parity gate** where trailing whitespace MUST be preserved (e.g. `MarkdownTemplateParityTest`) | CRLF→LF only |

Both `read` and `readRaw` collapse CRLF→LF; the difference is whether trailing
whitespace is trimmed. Use `readRaw` only when the test's contract is strict
byte equality — for everything else, prefer `ResultLoader`/`read`.

**When writing a new parity/golden test, ask:** "Does my `actual` value carry
meaningful trailing whitespace?" If yes → `ResourceLoader.readRaw`. If no →
`ResultLoader.load` (which already calls `ResourceLoader.read`).

The repo also ships a `.gitattributes` forcing `eol=lf` for text files, so
golden files committed with LF stay LF on Windows checkouts. Do not weaken this.


---

### Pattern D — Action Test
**When:** Testing an `AnAction` subclass.

**Base:** `EasyApiLightCodeInsightFixtureTestCase` (provides a real `Project` and PSI)
**Key:** Build events with `AnActionEvent.createEvent(dataContext, Presentation(), place, ActionUiKind.NONE, null)` over a small map-backed `DataContext`, as `EasyApiActionTest` does. Put exactly the data keys the action reads (`PROJECT`, `PSI_ELEMENT`, `PSI_FILE`, `NAVIGATABLE_ARRAY`, …) so each test states the selection it simulates. Prefer `createEvent` over the older `createFromDataContext` factory, and check new platform calls against the since-build deprecation gate (see the **compat-fixer** skill).

```kotlin
import com.intellij.openapi.actionSystem.ActionUiKind
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.psi.PsiElement
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class MyActionTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun setUp() {
        super.setUp()
        loadFile("spring/RestController.java")
        loadFile("spring/RequestMapping.java")
        loadFile("api/UserCtrl.java")
    }

    fun testUpdateEnabledOnController() {
        val userCtrl = findClass("com.itangcent.api.UserCtrl")!!
        val event = createEvent(psiElement = userCtrl)

        MyAction().update(event)

        assertTrue("Action should be enabled on a controller", event.presentation.isEnabled)
    }

    private fun createEvent(psiElement: PsiElement? = null): AnActionEvent {
        val data = mutableMapOf<String, Any?>(CommonDataKeys.PROJECT.name to project)
        if (psiElement != null) data[CommonDataKeys.PSI_ELEMENT.name] = psiElement
        val context = DataContext { data[it] }
        return AnActionEvent.createEvent(context, Presentation(), "test", ActionUiKind.NONE, null)
    }
}
```

Test `update()` (enablement/visibility per selection) and `getActionUpdateThread()`. For `actionPerformed`, test the service it delegates to rather than the dialog/balloon flow.

**Real examples:** `EasyApiActionTest`, `ExportApiActionTest`, `ChannelExportActionTest`, `ApiCallActionTest`

---

### Pattern E — Parity / Contract Test
**When:** Verifying that multiple implementations of the same interface all exist and behave consistently.

**Base:** `EasyApiLightCodeInsightFixtureTestCase` (or plain JUnit if no implementation needs a `Project`)

Feed **the same input** to every implementation and assert on the property they must share (same endpoints found, same field set, byte-identical rendering). A test that only checks the implementations can be constructed proves almost nothing — write one only as a registration smoke test.

```kotlin
class ExporterParityTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testSpringAndJaxRsFindSamePaths() = runTest {
        // load equivalent Spring and JAX-RS controllers, export both,
        // then compare the (method, path) pairs
        assertEquals("Both frameworks should expose the same routes", springRoutes, jaxRsRoutes)
    }
}
```

**Real examples:** `MarkdownTemplateParityTest` (byte-for-byte rendering parity, uses `ResourceLoader.readRaw`), `FeatureParityTest`, `FormatterParityTest` (both under `gap/`)

---

## Step 3 — Use the Right Test Utilities

### `ApiFixtures` — Pre-built test data
Use when you need `ApiEndpoint`, `Settings`, or common endpoint lists without PSI.

Use when you need `ApiEndpoint` values without PSI.

```kotlin
val endpoint = ApiFixtures.createEndpoint(name = "getUser", path = "/api/users/{id}")
val getEndpoint = ApiFixtures.createGetEndpoint(name = "getUser", path = "/api/users/{id}")
val postEndpoint = ApiFixtures.createPostEndpoint()  // POST with body params
val endpoints = ApiFixtures.createSampleEndpoints()  // CRUD sample set
```

Check `testFramework/ApiFixtures.kt` for the parameters before relying on defaults; add a new factory there when two or more tests need the same shape.

### `TestConfigReader` — Fake config rules
Use to inject `.easy.api.config`-style rules without touching the filesystem. **All factory methods require `project` as the first parameter.**

```kotlin
// No config
TestConfigReader.empty(project)

// From key=value pairs
TestConfigReader.fromRules(
    project,
    "method.return.main.status" to "200",
    "ignore" to "@Deprecated"
)

// From config file text
TestConfigReader.fromConfigText(
    project,
    """
    method.return.main.status=200
    ignore=@Deprecated
    """.trimIndent()
)

// From a map
TestConfigReader.fromMap(project, mapOf("key" to "value"))
```

### `settingBinder.update(Module::class) { }` — Mutate settings in tests
Settings are split into module data classes under `core/settings/module/`: `GeneralSettings`, `HttpSettings`, `ParsingOutputSettings`, `EnvironmentSettings`, `RuleFileSettings`, `AiSettings` (channel-specific settings live with their channel). `update` takes the module class:

```kotlin
import com.itangcent.easyapi.core.settings.module.GeneralSettings
import com.itangcent.easyapi.core.settings.module.HttpSettings
import com.itangcent.easyapi.core.settings.update

// In setUp() — applies to all tests in the class
override fun setUp() {
    super.setUp()
    settingBinder.update(HttpSettings::class) {
        httpTimeOut = 5
    }
}

// In a test method — applies only to that test
fun testScanDisabled() {
    settingBinder.update(GeneralSettings::class) {
        apiScanEnabled = false
    }
    // test code
}
```

Outside a fixture (plain JUnit), use `ConstantSettingBinder()` — an in-memory binder returning default modules — or `ThrowingSettingBinder()` to exercise the "settings unavailable" fallback paths.

### `ProjectWrapper` — Replace project services
Use when you need to swap out a specific project service without rebuilding the whole context.

```kotlin
import com.itangcent.easyapi.testFramework.wrap

val wrappedProject = wrap(project) {
    replaceService(MyProjectService::class, FakeMyProjectService())
}
```

Alternatively, use `project.registerServiceInstance(...)` directly in `setUp()`:

```kotlin
override fun setUp() {
    super.setUp()
    project.registerServiceInstance(
        serviceInterface = MyService::class.java,
        instance = MockMyService()
    )
}
```

### `ResultLoader` — Load golden-file expected output
```kotlin
ResultLoader.load()              // loads {ClassName}.txt
ResultLoader.load("name")        // loads {ClassName}.name.txt
ResultLoader.loadOrNull("name")  // returns null if file missing
```

---

## Step 4 — Test Resource Files

Java source files used as PSI fixtures live in `src/test/resources/`. Common ones:

| Path | Contents |
|---|---|
| `spring/` | Spring MVC annotations (GetMapping, PostMapping, PutMapping, DeleteMapping, RequestMapping, RequestParam, PathVariable, RequestBody, etc.) |
| `api/UserCtrl.java` | Sample Spring REST controller |
| `model/UserInfo.java`, `model/Result.java` | Common model classes |
| `constant/UserType.java` | Enum example |
| `jaxrs/`, `feign/`, `grpc/`, `springfox/` | Annotation stubs per framework |
| `javax/`, `jakarta/`, `validation/` | `javax.*` / `jakarta.*` and validation annotation stubs |
| `cases/`, `enumcases/` | Focused input cases for specific features |
| `config/`, `custom/` | Rule/config inputs |
| `result/` | Golden files for `ResultLoader` (Pattern C) |

JDK classes are not stored here — `loadJDKClass(fqn)` generates their stubs.

Always load the minimum set of files needed. Load annotation stubs before loading the controllers that use them. When a new case needs a new source file, add it under the closest existing folder rather than inlining large Java sources in the test.

---

## Step 5 — Decision Flowchart

```
Is an AnAction subclass?
└── YES → Pattern D (Action Test, on the IDE fixture)
Uses PSI / needs a Project?
├── YES → Pattern B (EasyApiLightCodeInsightFixtureTestCase)
│         Output is a large formatted string? → also Pattern C (ResultLoader)
│         Several implementations must agree?  → also Pattern E (Parity)
└── NO
    ├── Several implementations must agree? → Pattern E on plain JUnit
    └── Otherwise                           → Pattern A (Simple Unit Test)
```

---

## Step 6 — Naming & Location Conventions

- Test class: `src/test/kotlin/com/itangcent/easyapi/{package}/{ClassName}Test.kt`
- Golden files: `src/test/resources/result/{FQN}.{name}.txt`
- Test resources: `src/test/resources/{category}/`
- Method names: `fun testSomeBehavior()` in IDE fixture tests (no `@Test`, `test` prefix required); `@Test fun testSomeBehavior()` or ``@Test fun `some behavior`()`` in plain JUnit 4 tests
- Assertion messages: include a descriptive message as the first argument whenever the values alone don't explain the failure, e.g. `assertEquals("Should return same instance", a, b)`
- One behavior per test method; a bug-fix regression test is named after the symptom (`testInterfaceMethodExportsImplementationEndpoint`), not the issue number

---

## Step 7 — Running Tests

```bash
# Run all tests
./gradlew test

# Run a specific test class
./gradlew test --tests "com.itangcent.easyapi.core.export.ExportOrchestratorTest"

# Run a specific test method
./gradlew test --tests "com.itangcent.easyapi.core.export.ExportOrchestratorTest.testGetInstanceReturnsSameInstance"

# Clean full run, as CI does (stops Gradle daemons under memory pressure)
./script/test.sh
```

The full suite is slow; run the touched test classes while iterating and the full suite once before committing. A new test must fail without the change it covers — when writing a regression test, run it against the unfixed code first.
