package com.itangcent.easyapi.ide

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.ResourceLoader
import com.itangcent.easyapi.testFramework.TestConfigReader
import org.junit.Assert.assertEquals

/**
 * Golden-file test for [PropertiesService.toProperties] with
 * `properties.prefix=demo` configured.
 *
 * Split from [PropertiesServiceTest] because [createConfigReader] is a
 * class-level override on [EasyApiLightCodeInsightFixtureTestCase], so the
 * no-prefix and with-prefix cases cannot share a class.
 */
class PropertiesServiceWithPrefixTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader() =
        TestConfigReader.fromRules(project, "properties.prefix" to "demo")

    fun testToPropertiesWithPrefix() = runTest {
        loadFile("model/UserInfo.java")
        val psiClass = findClass("com.itangcent.model.UserInfo")!!
        val service = PropertiesService.getInstance(project)

        val actual = service.toProperties(psiClass)
        val expected = ResourceLoader.read(
            "/result/com.itangcent.easyapi.ide.FieldFormatServiceWithPrefixTest.toPropertiesWithPrefix.txt"
        )
        assertEquals(expected, actual)
    }
}
