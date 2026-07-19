package com.itangcent.easyapi.format.yaml

import com.itangcent.easyapi.core.ide.PropertiesService
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.ResourceLoader
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Golden-file test for [PropertiesService.toYaml] and [YamlFieldFormatChannel]
 * with `properties.prefix=demo` configured.
 *
 * Mirrors [com.itangcent.easyapi.core.ide.PropertiesServiceWithPrefixTest]: a
 * separate class is required because [createConfigReader] is a class-level
 * override on [EasyApiLightCodeInsightFixtureTestCase], so the no-prefix and
 * with-prefix cases cannot share a class.
 *
 * Locks the bug fix where the YAML channel previously dropped the
 * `@ConfigurationProperties` prefix — YAML now nests the prefix as keys
 * (Spring Boot `application.yml` semantics), matching what Properties does
 * with flat dot-paths.
 */
class YamlFieldFormatWithPrefixTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader() =
        TestConfigReader.fromRules(project, "properties.prefix" to "demo")

    fun testToYamlWithPrefix() = runTest {
        loadFile("model/UserInfo.java")
        val psiClass = findClass("com.itangcent.model.UserInfo")!!
        val service = PropertiesService.getInstance(project)

        val actual = service.toYaml(psiClass)
        val expected = ResourceLoader.read(
            "/result/com.itangcent.easyapi.format.spi.FieldFormatExtensionsTest.toYamlWithPrefix.txt"
        )
        assertEquals(expected, actual)
    }

    fun testYamlChannelHonorsPrefix() = runTest {
        loadFile("model/UserInfo.java")
        val psiClass = findClass("com.itangcent.model.UserInfo")!!

        val channelResult = YamlFieldFormatChannel().format(project, psiClass)
        val serviceResult = PropertiesService.getInstance(project).toYaml(psiClass)
        // Channel must delegate to the service so the prefix is applied.
        assertEquals(serviceResult, channelResult)

        // And the prefixed output must actually wrap keys under "demo".
        assertTrue("YAML with prefix should start with 'demo:'", channelResult.startsWith("demo:"))
        assertFalse(
            "YAML with prefix must NOT leak un-prefixed top-level keys",
            channelResult.lines().any { it == "id: 0" || it == "name: \"\"" }
        )
    }
}
