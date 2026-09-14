package com.itangcent.easyapi.format.spi

import com.itangcent.easyapi.core.extension.ExtensionConfigRegistry
import com.itangcent.easyapi.format.json.ObjectModelJsonConverter
import com.itangcent.easyapi.core.psi.DefaultPsiClassHelper
import com.itangcent.easyapi.core.psi.JsonOption
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.ResourceLoader
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Golden-file tests for the [ObjectModel] format extension functions
 * ([toJson], [toJson5], [toProperties], [toYaml]).
 *
 * Locks the canonical rendering of `model/UserInfo.java` so any drift
 * introduced by refactoring is caught at CI time. The golden files were
 * captured from the pre-refactor `FieldsToJsonAction` / `FieldsToJson5Action`
 * / `FieldsToPropertiesAction` behavior and are byte-identical to that output.
 *
 * The extensions are pure delegations:
 * - [toJson] → [ObjectModelJsonConverter.toJson]
 * - [toJson5] → [ObjectModelJsonConverter.toJson5]
 * - [toProperties] → [PropertiesFormatter.format]
 * - [toYaml] → [YamlFormatter.format]
 *
 * The `testToJson*ParityWithDirectConverterCall` / `testToJson5*ParityWithDirectConverterCall`
 * methods are the **characterization tests**: they verify that the
 * pre-migration direct call (`ObjectModelJsonConverter.toJson(model)`, as used
 * by `ScriptPsiContexts.toJson()` today) and the post-migration extension
 * (`model.toJson()`, which `ScriptPsiContexts.toJson()` will use after the
 * migration) produce byte-identical output. Both must equal the golden file
 * captured from pre-refactor behavior. If either parity check fails after the
 * migration, the migration broke the output.
 */
class FieldFormatExtensionsTest : EasyApiLightCodeInsightFixtureTestCase() {

    /**
     * The shipped `converts` extension, which is default-enabled in production. It is what maps
     * `UserInfo`'s `java.time.LocalDate`/`LocalDateTime` fields (`birthDay`/`regtime`) to
     * `java.lang.String` — i.e. to the `""` the goldens hold. Without it those fields are an
     * unmapped type and render `null`, which is not what a user sees.
     */
    override fun createConfigReader(): TestConfigReader {
        val extension = ExtensionConfigRegistry.getExtension("converts")
        assertNotNull("converts extension should exist", extension)
        return TestConfigReader.fromConfigText(project, extension?.content ?: "")
    }

    fun testToJson() = runTest {
        loadFile("model/UserInfo.java")
        val psiClass = findClass("com.itangcent.model.UserInfo")!!
        val helper = DefaultPsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass, JsonOption.READ_GETTER_OR_SETTER)!!

        val actual = model.toJson()
        val expected = ResourceLoader.read("/result/com.itangcent.easyapi.core.ide.FieldFormatServiceTest.toJson.txt")
        assertEquals(expected, actual)
    }

    fun testToJson5() = runTest {
        loadFile("model/UserInfo.java")
        val psiClass = findClass("com.itangcent.model.UserInfo")!!
        val helper = DefaultPsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass, option = JsonOption.ALL)!!

        val actual = model.toJson5()
        val expected = ResourceLoader.read("/result/com.itangcent.easyapi.core.ide.FieldFormatServiceTest.toJson5.txt")
        assertEquals(expected, actual)
    }

    /**
     * **Characterization test (JSON).**
     *
     * Builds the same `ObjectModel` that `ScriptPsiContexts.toJson()` builds
     * (same `PsiClass`, same `JsonOption.READ_GETTER_OR_SETTER`) and asserts:
     * 1. The pre-migration direct call `ObjectModelJsonConverter.toJson(model)`
     *    equals the golden output (captured from pre-refactor behavior).
     * 2. The post-migration extension `model.toJson()` equals the same golden
     *    output.
     * 3. Therefore the two are byte-identical — the migration preserves
     *    output. This test MUST pass both BEFORE the migration and
     *    AFTER the migration.
     */
    fun testToJsonParityWithDirectConverterCall() = runTest {
        loadFile("model/UserInfo.java")
        val psiClass = findClass("com.itangcent.model.UserInfo")!!
        val helper = DefaultPsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass, JsonOption.READ_GETTER_OR_SETTER)!!

        val expected = ResourceLoader.read("/result/com.itangcent.easyapi.core.ide.FieldFormatServiceTest.toJson.txt")
        val directCallOutput = ObjectModelJsonConverter.toJson(model)
        val extensionOutput = model.toJson()

        assertEquals("direct call must match golden", expected, directCallOutput)
        assertEquals("extension must match golden", expected, extensionOutput)
        assertEquals("direct call must match extension (parity)", directCallOutput, extensionOutput)
    }

    /**
     * **Characterization test (JSON5).**
     *
     * Builds the same `ObjectModel` that `ScriptPsiContexts.toJson5()` builds
     * (same `PsiClass`, same `JsonOption.ALL`) and asserts:
     * 1. The pre-migration direct call `ObjectModelJsonConverter.toJson5(model)`
     *    equals the golden output (captured from pre-refactor behavior).
     * 2. The post-migration extension `model.toJson5()` equals the same golden
     *    output.
     * 3. Therefore the two are byte-identical — the migration preserves
     *    output. This test MUST pass both BEFORE the migration and
     *    AFTER the migration.
     */
    fun testToJson5ParityWithDirectConverterCall() = runTest {
        loadFile("model/UserInfo.java")
        val psiClass = findClass("com.itangcent.model.UserInfo")!!
        val helper = DefaultPsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass, option = JsonOption.ALL)!!

        val expected = ResourceLoader.read("/result/com.itangcent.easyapi.core.ide.FieldFormatServiceTest.toJson5.txt")
        val directCallOutput = ObjectModelJsonConverter.toJson5(model)
        val extensionOutput = model.toJson5()

        assertEquals("direct call must match golden", expected, directCallOutput)
        assertEquals("extension must match golden", expected, extensionOutput)
        assertEquals("direct call must match extension (parity)", directCallOutput, extensionOutput)
    }

    fun testToProperties() = runTest {
        loadFile("model/UserInfo.java")
        val psiClass = findClass("com.itangcent.model.UserInfo")!!
        val helper = DefaultPsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass)!!

        val actual = model.toProperties()
        val expected = ResourceLoader.read("/result/com.itangcent.easyapi.core.ide.FieldFormatServiceTest.toProperties.txt")
        assertEquals(expected, actual)
    }

    fun testToYaml() = runTest {
        loadFile("model/UserInfo.java")
        val psiClass = findClass("com.itangcent.model.UserInfo")!!
        val helper = DefaultPsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass, option = JsonOption.ALL)!!

        val actual = model.toYaml()
        val expected = ResourceLoader.read("/result/com.itangcent.easyapi.format.yaml.YamlFormatterTest.txt")
        assertEquals(expected, actual)
    }

    fun testToYamlWithPrefix() = runTest {
        loadFile("model/UserInfo.java")
        val psiClass = findClass("com.itangcent.model.UserInfo")!!
        val helper = DefaultPsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass, option = JsonOption.ALL)!!

        val actual = model.toYaml("demo")
        val expected = ResourceLoader.read(
            "/result/com.itangcent.easyapi.format.spi.FieldFormatExtensionsTest.toYamlWithPrefix.txt"
        )
        assertEquals(expected, actual)
    }
}
