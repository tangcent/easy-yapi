package com.itangcent.easyapi.core.extension

import com.itangcent.easyapi.core.psi.JsonOption
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader

class BuiltInExtensionNameTransformScenarioTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var harness: BuiltInExtensionExecutionHarness

    override fun createConfigReader() = TestConfigReader.empty(project)

    override fun setUp() {
        super.setUp()
        ExtensionConfigRegistry.loadExtensions()
        harness = BuiltInExtensionExecutionHarness(
            project = project,
            loadPsiFile = { path, content -> loadFile(path, content) },
            waitForClass = { fqn -> waitForClass(fqn) }
        )
    }

    fun testUncapitalizeScenarioKeepsOnePropertyForFieldAndGetter() = runTest {
        assertTransformedNames(
            extensionCode = "field.name.uncapitalize",
            expectedPresent = listOf("barcode", "user_name", "displayName", "onlySetter", "uRLValue"),
            expectedAbsent = listOf("Barcode", "URLValue"),
            expectedSize = 5
        )
    }

    fun testCapitalizeScenarioUppercasesTheFirstLetter() = runTest {
        assertTransformedNames(
            extensionCode = "field.name.capitalize",
            expectedPresent = listOf("Barcode", "User_name", "DisplayName", "OnlySetter", "URLValue"),
            expectedAbsent = listOf("user_name", "displayName", "uRLValue")
        )
    }

    fun testCamelCaseScenarioJoinsDelimitedNames() = runTest {
        assertTransformedNames(
            extensionCode = "field.name.camel-case",
            expectedPresent = listOf("barcode", "userName", "displayName", "onlySetter", "urlValue"),
            expectedAbsent = listOf("user_name", "Barcode", "displayname", "onlysetter", "URLValue")
        )
    }

    fun testSnakeCaseScenarioSplitsCamelCaseNames() = runTest {
        assertTransformedNames(
            extensionCode = "field.name.snake-case",
            expectedPresent = listOf("barcode", "user_name", "display_name", "only_setter", "url_value"),
            expectedAbsent = listOf("displayName", "Barcode", "URLValue", "url-value", "urlvalue"),
            expectedSize = 5
        )
    }

    fun testKebabCaseScenarioSplitsCamelCaseNames() = runTest {
        assertTransformedNames(
            extensionCode = "field.name.kebab-case",
            expectedPresent = listOf("barcode", "user-name", "display-name", "only-setter", "url-value"),
            expectedAbsent = listOf("displayName", "Barcode", "URLValue", "urlvalue")
        )
    }

    fun testPascalCaseScenarioCapitalizesEveryWord() = runTest {
        assertTransformedNames(
            extensionCode = "field.name.pascal-case",
            expectedPresent = listOf("Barcode", "UserName", "DisplayName", "UrlValue", "OnlySetter"),
            expectedAbsent = listOf("barcode", "user_name", "URLValue", "URLVALUE"),
            expectedSize = 5
        )
    }

    fun testUpperSnakeCaseScenarioUppercasesEveryWord() = runTest {
        assertTransformedNames(
            extensionCode = "field.name.upper-snake-case",
            expectedPresent = listOf("BARCODE", "USER_NAME", "DISPLAY_NAME", "URL_VALUE", "ONLY_SETTER"),
            expectedAbsent = listOf("Barcode", "barcode", "urlvalue", "URLVALUE"),
            expectedSize = 5
        )
    }

    fun testLowerDotCaseScenarioJoinsWordsWithDots() = runTest {
        assertTransformedNames(
            extensionCode = "field.name.lower-dot-case",
            expectedPresent = listOf("barcode", "user.name", "display.name", "url.value", "only.setter"),
            expectedAbsent = listOf("Barcode", "URLValue", "url-value", "urlvalue"),
            expectedSize = 5
        )
    }

    fun testUpperKebabCaseScenarioUppercasesEveryWord() = runTest {
        assertTransformedNames(
            extensionCode = "field.name.upper-kebab-case",
            expectedPresent = listOf("BARCODE", "USER-NAME", "DISPLAY-NAME", "URL-VALUE", "ONLY-SETTER"),
            expectedAbsent = listOf("Barcode", "barcode", "urlvalue", "URLVALUE"),
            expectedSize = 5
        )
    }

    private suspend fun assertTransformedNames(
        extensionCode: String,
        expectedPresent: List<String>,
        expectedAbsent: List<String>,
        expectedSize: Int? = null
    ) {
        val scenario = requireNotNull(
            BuiltInExtensionScenarioLedger.resolvedScenarios().singleOrNull { it.extension.code == extensionCode }
        ) { "Missing extension scenario: $extensionCode" }

        harness.execute(scenario, fixturePlan()) { session ->
            session.installIsolatedReader()
            val services = session.reacquireServices()
            val psiClass = requireNotNull(findClass(CLASS_NAME)) {
                "Fixture class is not resolvable: $CLASS_NAME"
            }
            val model = requireNotNull(services.psiClassHelper.buildObjectModel(psiClass, JsonOption.ALL)?.asObject()) {
                "No object model built for $CLASS_NAME"
            }

            for (name in expectedPresent) {
                assertTrue(
                    "Expected exported name '$name' for $extensionCode, got ${model.fields.keys}",
                    model.fields.containsKey(name)
                )
            }
            for (name in expectedAbsent) {
                assertFalse(
                    "Unexpected exported name '$name' for $extensionCode, got ${model.fields.keys}",
                    model.fields.containsKey(name)
                )
            }
            if (expectedSize != null) {
                assertEquals(
                    "Unexpected exported field count for $extensionCode: ${model.fields.keys}",
                    expectedSize,
                    model.fields.size
                )
            }
        }
    }

    private fun fixturePlan(): ExtensionFixturePlan {
        val content = requireNotNull(javaClass.classLoader.getResourceAsStream(FIXTURE_RESOURCE)) {
            "Missing test fixture resource: $FIXTURE_RESOURCE"
        }.bufferedReader(Charsets.UTF_8).use { it.readText() }
        return ExtensionFixturePlan(psiStubs = mapOf(CLASS_NAME to content))
    }

    private companion object {
        const val FIXTURE_RESOURCE = "api/nametransform/NameTransformDTO.java"
        const val CLASS_NAME = "com.itangcent.nametransform.NameTransformDTO"
    }
}
