package com.itangcent.easyapi.exporter.formatter

import com.itangcent.easyapi.psi.DefaultPsiClassHelper
import com.itangcent.easyapi.psi.JsonOption
import com.itangcent.easyapi.psi.model.toYaml
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.ResultLoader
import com.itangcent.easyapi.testFramework.TestConfigReader

/**
 * Golden-file test for [YamlFormatter] / [ObjectModel.toYaml].
 *
 * Locks the canonical YAML rendering of `model/UserInfo.java` so any drift
 * introduced by refactoring the recursion is caught at CI time. The YAML
 * output follows the same default-value convention as JSON/JSON5:
 * string → `""`, number → `0`, unknown → `null`.
 */
class YamlFormatterTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader() = TestConfigReader.empty(project)

    fun testGoldenParity() = runTest {
        loadFile("model/UserInfo.java")
        val psiClass = findClass("com.itangcent.model.UserInfo")!!
        val helper = DefaultPsiClassHelper.getInstance(project)
        val model = helper.buildObjectModel(psiClass, option = JsonOption.ALL)!!

        val actual = model.toYaml()
        // Use the explicit-class overload — the no-arg load() stack-walks to
        // find the caller, which mis-resolves inside runTest/coroutines (it
        // sees the continuation class, not the test class).
        val expected = ResultLoader.load(YamlFormatterTest::class.java, "")
        assertEquals(expected, actual)
    }
}
