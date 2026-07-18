package com.itangcent.easyapi.psi.model

import com.itangcent.easyapi.psi.DefaultPsiClassHelper
import com.itangcent.easyapi.psi.JsonOption
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
 */
class FieldFormatExtensionsTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testToJson() = runTest {
        loadFile("model/UserInfo.java")
        val psiClass = findClass("com.itangcent.model.UserInfo")!!
        val helper = DefaultPsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass, JsonOption.READ_GETTER_OR_SETTER)!!

        val actual = model.toJson()
        val expected = ResourceLoader.read("/result/com.itangcent.easyapi.ide.FieldFormatServiceTest.toJson.txt")
        assertEquals(expected, actual)
    }

    fun testToJson5() = runTest {
        loadFile("model/UserInfo.java")
        val psiClass = findClass("com.itangcent.model.UserInfo")!!
        val helper = DefaultPsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass, option = JsonOption.ALL)!!

        val actual = model.toJson5()
        val expected = ResourceLoader.read("/result/com.itangcent.easyapi.ide.FieldFormatServiceTest.toJson5.txt")
        assertEquals(expected, actual)
    }

    fun testToProperties() = runTest {
        loadFile("model/UserInfo.java")
        val psiClass = findClass("com.itangcent.model.UserInfo")!!
        val helper = DefaultPsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass)!!

        val actual = model.toProperties()
        val expected = ResourceLoader.read("/result/com.itangcent.easyapi.ide.FieldFormatServiceTest.toProperties.txt")
        assertEquals(expected, actual)
    }

    fun testToYaml() = runTest {
        loadFile("model/UserInfo.java")
        val psiClass = findClass("com.itangcent.model.UserInfo")!!
        val helper = DefaultPsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass, option = JsonOption.ALL)!!

        val actual = model.toYaml()
        val expected = ResourceLoader.read("/result/com.itangcent.easyapi.psi.model.format.YamlFormatterTest.txt")
        assertEquals(expected, actual)
    }

    fun testToYamlWithPrefix() = runTest {
        loadFile("model/UserInfo.java")
        val psiClass = findClass("com.itangcent.model.UserInfo")!!
        val helper = DefaultPsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass, option = JsonOption.ALL)!!

        val actual = model.toYaml("demo")
        val expected = ResourceLoader.read(
            "/result/com.itangcent.easyapi.psi.model.FieldFormatExtensionsTest.toYamlWithPrefix.txt"
        )
        assertEquals(expected, actual)
    }
}
