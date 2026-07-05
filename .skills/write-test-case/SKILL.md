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
| Does it interact with PSI (PsiClass, PsiMethod, PsiField)? | IDE fixture pattern |
| Does it need a real IntelliJ `Project` object? | IDE fixture pattern |
| Does it export/format API endpoints? | IDE fixture + `ResultLoader` pattern |
| Is it a pure utility / data class with no IDE dependency? | Simple unit test pattern |
| Is it an `AnAction` subclass? | Action test pattern (extends IDE fixture) |
| Is it testing that multiple implementations share the same interface? | Parity test pattern |

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
**Mocking:** `mock<T>()` from `mockito-kotlin`

```kotlin
import org.junit.Assert.*
import org.junit.Test

class MyServiceTest {

    @Test
    fun testSomeBehavior() {
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
- `loadJDKClass(fqn)` — loads a JDK class stub into the fixture (needed for collection types, etc.)
- `loadSource(clazz)` — loads a class from source into the fixture
- `runTest { }` — runs a suspend block inside `runBlocking`
- `settingBinder` — property to access/modify test settings (via `SettingBinder.getInstance(project)`)
- `createConfigReader()` — override to supply custom config rules

**Test method naming:** use `fun test...()` (no `@Test` annotation — JUnit 3 style inherited from `LightJavaCodeInsightFixtureTestCase`)

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

**Important — Async PSI Indexing:** After `loadFile()`, `findClass()` may return `null` because the PSI index updates asynchronously. Use `waitForClass(qualifiedName)` which polls until the class is resolvable, instead of relying on a fixed `delay()`.

---

### Pattern C — ResultLoader (snapshot / golden-file) Test
**When:** The output is a large formatted string (Markdown, Postman JSON, curl commands) that should be compared against a saved expected result.

**Base:** `EasyApiLightCodeInsightFixtureTestCase`
**Utility:** `ResultLoader`

**How it works:**
1. Run the test once to produce actual output.
2. Save the output to `src/test/resources/result/{FullyQualifiedTestClass}.{optionalName}.txt`.
3. In the test, compare with `ResultLoader.load()` or `ResultLoader.load("name")`.

**File naming convention:**
- Default: `com.itangcent.easyapi.exporter.MyFormatterTest.txt`
- Named: `com.itangcent.easyapi.exporter.MyFormatterTest.testParseRequests.txt`
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

**Real examples:** `PostmanFormatterTest`, `CurlFormatterTest`

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

**Base:** `EasyApiLightCodeInsightFixtureTestCase` (provides a real `Project`)
**Key:** Use `AnActionEvent.createFromDataContext` to construct events with the test `project`.

```kotlin
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.*

class MyActionTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var action: MyAction

    override fun setUp() {
        super.setUp()
        action = MyAction()
    }

    fun testUpdateWithProject() {
        val presentation = Presentation()
        val event = AnActionEvent.createFromDataContext(
            "test",
            presentation
        ) { project }

        action.update(event)

        assertTrue("Action should be enabled with project", event.presentation.isEnabled)
    }

    fun testUpdateWithoutProject() {
        val presentation = Presentation()
        val event = AnActionEvent.createFromDataContext(
            "test",
            presentation
        ) { null }

        action.update(event)

        assertFalse("Action should be disabled without project", event.presentation.isEnabled)
    }
}
```

**Real examples:** `ExportApiActionTest`, `ChannelExportActionTest`, `ApiCallActionTest`, `EasyApiActionTest`

---

### Pattern E — Parity / Contract Test
**When:** Verifying that multiple implementations of the same interface all exist and behave consistently.

**Base:** `EasyApiLightCodeInsightFixtureTestCase`

```kotlin
import com.itangcent.easyapi.exporter.feign.FeignClassExporter
import com.itangcent.easyapi.exporter.jaxrs.JaxRsClassExporter
import com.itangcent.easyapi.exporter.springmvc.SpringMvcClassExporter
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import org.junit.Assert.*

class ExporterParityTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testAllExportersExist() = runTest {
        assertNotNull(SpringMvcClassExporter(project))
        assertNotNull(FeignClassExporter(project))
        assertNotNull(JaxRsClassExporter(project))
    }
}
```

**Real examples:** `FeatureParityTest`, `FormatterParityTest`, `SettingBinderParityTest`

---

## Step 3 — Use the Right Test Utilities

### `ApiFixtures` — Pre-built test data
Use when you need `ApiEndpoint`, `Settings`, or common endpoint lists without PSI.

```kotlin
val endpoint = ApiFixtures.createEndpoint(name = "getUser", path = "/api/users/{id}")
val getEndpoint = ApiFixtures.createGetEndpoint(name = "getUser", path = "/api/users/{id}")
val postEndpoint = ApiFixtures.createPostEndpoint()  // POST with body params
val uploadEndpoint = ApiFixtures.createFileUploadEndpoint()
val endpoints = ApiFixtures.createSampleEndpoints()  // 5 CRUD endpoints
val settings = ApiFixtures.createSettings()          // settings with test token
```

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

### `SettingBinder.update { }` — Mutate settings in tests
The `settingBinder` property in `EasyApiLightCodeInsightFixtureTestCase` provides access to settings. Use the `update` extension function to modify settings:

```kotlin
// In setUp() — applies to all tests in the class
override fun setUp() {
    super.setUp()
    settingBinder.update {
        postmanToken = "my-token"
        feignEnable = true
        httpTimeOut = 5000
    }
}

// In a test method — applies only to that test
fun testSomething() {
    settingBinder.update {
        postmanToken = "custom-token"
    }
    // test code
}
```

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
| `jaxrs/` | JAX-RS annotations |
| `feign/` | Feign annotations |
| `validation/` | javax.validation annotations |
| `jdk/` | JDK class stubs for the light fixture |

Always load the minimum set of files needed. Load Spring annotation stubs before loading controllers that use them.

---

## Step 5 — Decision Flowchart

```
Target class uses PSI / Project?
├── YES → Pattern B (EasyApiLightCodeInsightFixtureTestCase)
│         Output is a large formatted string?
│         └── YES → also use Pattern C (ResultLoader)
└── NO
    ├── Is an AnAction subclass?
    │   └── YES → Pattern D (Action Test — still extends IDE fixture for real Project)
    ├── Testing multiple implementations for parity?
    │   └── YES → Pattern E (Parity Test)
    └── Pure utility / data class / formatter?
        └── YES → Pattern A (Simple Unit Test)
```

---

## Step 6 — Naming & Location Conventions

- Test class: `src/test/kotlin/com/itangcent/easyapi/{package}/{ClassName}Test.kt`
- Golden files: `src/test/resources/result/{FQN}.{name}.txt`
- Test resources: `src/test/resources/{category}/`
- Method names: `fun testSomeBehavior()` (no `@Test` in IDE fixture tests — JUnit 3 style), `@Test fun testSomeBehavior()` in plain JUnit 4 tests
- Assertion messages: always include a descriptive message as the first argument, e.g. `assertEquals("Should return same instance", a, b)`

---

## Step 7 — Running Tests

```bash
# Run all tests
./gradlew test

# Run a specific test class
./gradlew test --tests "com.itangcent.easyapi.exporter.ExportOrchestratorTest"

# Run a specific test method
./gradlew test --tests "com.itangcent.easyapi.exporter.ExportOrchestratorTest.testGetInstanceReturnsSameInstance"
```
