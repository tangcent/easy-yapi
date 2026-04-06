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
| Is it an `AnAction` subclass? | Action mock pattern |
| Does it involve coroutines / `ActionContext`? | `ActionContextTestKit` pattern |
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
**When:** Pure utility, data model, cache service, or any class with no IntelliJ platform dependency.

**Base:** None (plain JUnit 4 class)  
**Annotations:** `@Test`, `@Before`, `@After`  
**Mocking:** `mock<T>()` from `mockito-kotlin`

```kotlin
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock

class MyServiceTest {

    private lateinit var service: MyService

    @Before
    fun setUp() {
        service = MyService(mock()) // inject mocks via constructor
    }

    @Test
    fun testSomeBehavior() {
        val result = service.doSomething("input")
        assertEquals("expected", result)
    }

    @Test
    fun testNullCase() {
        assertNull(service.getString("nonexistent"))
    }
}
```

**Real examples:** `CacheServiceTest`, `GsonUtilsTest`, `HttpModelsTest`

---

### Pattern B — IDE Fixture Test (PSI / Project-aware)
**When:** Class needs a real `Project`, reads PSI elements, or is a project-level service.

**Base:** `EasyApiLightCodeInsightFixtureTestCase`  
**Key methods available:**
- `loadFile(path)` — loads a Java file from `src/test/resources/` into the fixture project
- `loadFile(path, content)` — loads inline Java source
- `findClass(qualifiedName)` — finds a `PsiClass` by FQN
- `findMethod(psiClass, name)` — finds a method on a class
- `runTest { }` — runs a suspend block inside the `ActionContext`
- `setSettings(settings)` / `updateSettings { }` — mutate test settings
- `instance<T>()` — resolve a bound type from `ActionContext`
- `customizeContext(builder)` — override to add extra bindings

**Test method naming:** use `fun test...()` (no `@Test` annotation — JUnit 3 style inherited from `LightJavaCodeInsightFixtureTestCase`)

```kotlin
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class MyExporterTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var exporter: MyExporter

    override fun setUp() {
        super.setUp()
        loadTestFiles()
        exporter = MyExporter(actionContext)
    }

    // Override to supply config rules for this test class
    override fun createConfigReader() = TestConfigReader.EMPTY

    // Override to inject extra services into ActionContext
    override fun customizeContext(builder: ActionContextBuilder) {
        builder.bind(MyHelper::class, MyHelperImpl())
    }

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

**Real examples:** `PostmanFormatterTest`, `CurlFormatterTest`, `MarkdownApiExporterTest`

---

### Pattern D — Action Mock Test
**When:** Testing an `AnAction` subclass or classes that receive `AnActionEvent`.

**Base:** Plain JUnit 4 (no IDE fixture needed for unit-level action tests)  
**Mocking:** `mock(Project::class.java)`, `mock(AnActionEvent::class.java)`

```kotlin
import org.junit.Before
import org.junit.Test
import org.mockito.Mockito.*

class MyActionTest {

    private lateinit var action: MyAction
    private lateinit var mockProject: Project
    private lateinit var mockEvent: AnActionEvent

    @Before
    fun setUp() {
        action = MyAction()
        mockProject = mock(Project::class.java)
        mockEvent = mock(AnActionEvent::class.java)
        `when`(mockEvent.project).thenReturn(mockProject)
        `when`(mockEvent.presentation).thenReturn(Presentation())
    }

    @Test
    fun testActionUpdate() {
        action.update(mockEvent)
        verify(mockEvent).presentation
    }
}
```

**Real examples:** `BaseExportActionTest`

---

### Pattern E — ActionContextTestKit (coroutine / context-scoped)
**When:** Testing a class that requires `ActionContext` but does NOT need a full IDE fixture (no PSI).

**Utility:** `ActionContextTestKit`

```kotlin
import com.itangcent.easyapi.testFramework.ActionContextTestKit
import com.itangcent.easyapi.testFramework.ActionContextTestKit.binding
import com.itangcent.easyapi.testFramework.TestConfigReader
import org.junit.Test

class MyContextAwareServiceTest {

    @Test
    fun testServiceBehavior() {
        ActionContextTestKit.withTestContext(
            project = mockProject,
            configReader = TestConfigReader.fromRules("rule.key" to "rule.value"),
            additionalBindings = listOf(binding<MyDep>(MyDepImpl()))
        ) {
            val service = instance<MyContextAwareService>()
            val result = service.compute()
            assertEquals("expected", result)
        }
    }
}
```

**Variants:**
- `withTestContext(project, settings, configReader, additionalBindings) { }` — full control
- `withSimpleContext { }` — minimal setup with SPI bindings only
- `createTestContext(...)` — manual lifecycle management

---

### Pattern F — Parity / Contract Test
**When:** Verifying that multiple implementations of the same interface all exist and behave consistently.

**Base:** `EasyApiLightCodeInsightFixtureTestCase`

```kotlin
class ExporterParityTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader() = TestConfigReader.EMPTY

    fun testAllExportersExist() = runTest {
        assertNotNull(SpringMvcClassExporter(actionContext))
        assertNotNull(FeignClassExporter(actionContext))
        assertNotNull(JaxRsClassExporter(actionContext))
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
val endpoints = ApiFixtures.createSampleEndpoints()  // 5 CRUD endpoints
val settings = ApiFixtures.createSettings()          // settings with test token
val postEndpoint = ApiFixtures.createPostEndpoint()  // POST with body params
val uploadEndpoint = ApiFixtures.createFileUploadEndpoint()
```

### `TestConfigReader` — Fake config rules
Use to inject `.easy.api.config`-style rules without touching the filesystem.

```kotlin
// No config
TestConfigReader.EMPTY

// From key=value pairs
TestConfigReader.fromRules(
    "method.return.main.status" to "200",
    "ignore" to "@Deprecated"
)

// From config file text
TestConfigReader.fromConfigText("""
    method.return.main.status=200
    ignore=@Deprecated
""".trimIndent())

// From a map
TestConfigReader.fromMap(mapOf("key" to "value"))
```

### `ConstantSettingBinder` — Mutable in-memory settings
Already wired into `EasyApiLightCodeInsightFixtureTestCase` as `testSettingBinder`. Use `updateSettings { }` to change settings mid-test.

```kotlin
updateSettings {
    postmanToken = "my-token"
    feignEnable = true
    httpTimeOut = 5000
}
```

### `ProjectWrapper` — Replace project services
Use when you need to swap out a specific project service without rebuilding the whole context.

```kotlin
val wrappedProject = wrap(project) {
    replaceService(MyProjectService::class, FakeMyProjectService())
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
| `spring/` | Spring MVC annotations (GetMapping, PostMapping, etc.) |
| `api/UserCtrl.java` | Sample Spring REST controller |
| `model/UserInfo.java`, `model/Result.java` | Common model classes |
| `constant/UserType.java` | Enum example |
| `jaxrs/` | JAX-RS annotations |
| `feign/` | Feign annotations |
| `validation/` | javax.validation annotations |

Always load the minimum set of files needed. Load Spring annotation stubs before loading controllers that use them.

---

## Step 5 — Decision Flowchart

```
Target class uses PSI / Project?
├── YES → Pattern B (EasyApiLightCodeInsightFixtureTestCase)
│         Output is a large formatted string?
│         └── YES → also use Pattern C (ResultLoader)
└── NO
    ├── Has ActionContext dependency (no PSI)?
    │   └── YES → Pattern E (ActionContextTestKit)
    ├── Is an AnAction subclass?
    │   └── YES → Pattern D (Action Mock)
    ├── Testing multiple implementations for parity?
    │   └── YES → Pattern F (Parity Test)
    └── Pure utility / data class?
        └── YES → Pattern A (Simple Unit Test)
```

---

## Step 6 — Naming & Location Conventions

- Test class: `src/test/kotlin/com/itangcent/easyapi/{package}/{ClassName}Test.kt`
- Golden files: `src/test/resources/result/{FQN}.{name}.txt`
- Test resources: `src/test/resources/{category}/`
- Method names: `fun testSomeBehavior()` (no `@Test` in IDE fixture tests), `@Test fun testSomeBehavior()` in plain JUnit 4 tests
- Assertion messages: always include a descriptive message as the first argument, e.g. `assertEquals("Should return same instance", a, b)`
