package com.itangcent.easyapi.ide

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.ResourceLoader
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Golden-file tests for [PropertiesService.toProperties].
 *
 * Locks the canonical Properties rendering of `model/UserInfo.java` with and
 * without `properties.prefix` configured. The golden files were captured from
 * the pre-refactor `FieldsToPropertiesAction` behavior and are byte-identical
 * to that output.
 *
 * Split into two classes because [createConfigReader] is a class-level
 * override on [EasyApiLightCodeInsightFixtureTestCase], so the no-prefix and
 * with-prefix cases cannot share a class. The with-prefix case lives in
 * [PropertiesServiceWithPrefixTest].
 */
class PropertiesServiceTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testToProperties() = runTest {
        loadFile("model/UserInfo.java")
        val psiClass = findClass("com.itangcent.model.UserInfo")!!
        val service = PropertiesService.getInstance(project)

        val actual = service.toProperties(psiClass)
        val expected = ResourceLoader.read("/result/com.itangcent.easyapi.ide.FieldFormatServiceTest.toProperties.txt")
        assertEquals(expected, actual)
    }
}
