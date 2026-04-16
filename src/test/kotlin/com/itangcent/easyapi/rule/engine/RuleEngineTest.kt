package com.itangcent.easyapi.rule.engine

import com.intellij.psi.PsiElement
import com.intellij.testFramework.registerServiceInstance
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.rule.EventRuleMode
import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.StringRuleMode
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock

class RuleEngineTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader(): ConfigReader = TestConfigReader.empty(project)

    @Test
    fun testEvaluateStringWithSingleMode() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "api.name" to "Test API",
                "api.tag" to "tag1"
            )
        )

        val ruleEngine = RuleEngine.getInstance(project)
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.string("api.name", StringRuleMode.SINGLE), mockElement)
        assertEquals("Test API", result)
    }

    @Test
    fun testEvaluateStringWithMergeMode() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "api.tag" to "tag1",
                "api.tag" to "tag2",
                "api.tag" to "tag3"
            )
        )

        val ruleEngine = RuleEngine.getInstance(project)
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.string("api.tag", StringRuleMode.MERGE), mockElement)
        assertEquals("tag1\ntag2\ntag3", result)
    }

    @Test
    fun testEvaluateStringWithMergeDistinctMode() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "api.tag" to "tag1",
                "api.tag" to "tag2",
                "api.tag" to "tag1"
            )
        )

        val ruleEngine = RuleEngine.getInstance(project)
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.string("api.tag", StringRuleMode.MERGE_DISTINCT), mockElement)
        assertEquals("tag1\ntag2", result)
    }

    @Test
    fun testEvaluateStringWithEmptyConfig() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.empty(project)
        )

        val ruleEngine = RuleEngine.getInstance(project)
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.string("nonexistent.key", StringRuleMode.SINGLE), mockElement)
        assertNull(result)
    }

    @Test
    fun testEvaluateBooleanWithTrueValue() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "ignore" to "true"
            )
        )

        val ruleEngine = RuleEngine.getInstance(project)
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.boolean("ignore"), mockElement)
        assertTrue(result)
    }

    @Test
    fun testEvaluateBooleanWithFalseValue() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "ignore" to "false"
            )
        )

        val ruleEngine = RuleEngine.getInstance(project)
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.boolean("ignore"), mockElement)
        assertFalse(result)
    }

    @Test
    fun testEvaluateBooleanWithMultipleValues() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "ignore" to "false",
                "ignore" to "false",
                "ignore" to "true"
            )
        )

        val ruleEngine = RuleEngine.getInstance(project)
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.boolean("ignore"), mockElement)
        assertTrue(result)
    }

    @Test
    fun testEvaluateBooleanWithNumericValue() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "ignore" to "1"
            )
        )

        val ruleEngine = RuleEngine.getInstance(project)
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.boolean("ignore"), mockElement)
        assertTrue(result)
    }

    @Test
    fun testEvaluateBooleanWithStringYes() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "ignore" to "yes"
            )
        )

        val ruleEngine = RuleEngine.getInstance(project)
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.boolean("ignore"), mockElement)
        assertTrue(result)
    }

    @Test
    fun testEvaluateBooleanWithEmptyConfig() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.empty(project)
        )

        val ruleEngine = RuleEngine.getInstance(project)
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.boolean("nonexistent.key"), mockElement)
        assertFalse(result)
    }

    @Test
    fun testEvaluateIntWithValue() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "field.max.depth" to "5"
            )
        )

        val ruleEngine = RuleEngine.getInstance(project)
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.int("field.max.depth"), mockElement)
        assertEquals(5, result)
    }

    @Test
    fun testEvaluateIntWithMultipleValues() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "field.max.depth" to "10",
                "field.max.depth" to "20"
            )
        )

        val ruleEngine = RuleEngine.getInstance(project)
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.int("field.max.depth"), mockElement)
        assertEquals(10, result)
    }

    @Test
    fun testEvaluateIntWithEmptyConfig() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.empty(project)
        )

        val ruleEngine = RuleEngine.getInstance(project)
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.int("nonexistent.key"), mockElement)
        assertNull(result)
    }

    @Test
    fun testEvaluateEventWithPsiElement() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "http.call.before" to "groovy:logger.info('event executed')"
            )
        )

        val ruleEngine = RuleEngine.getInstance(project)
        val mockElement = mock<PsiElement>()

        ruleEngine.evaluate(RuleKey.event("http.call.before"), mockElement)
    }

    @Test
    fun testEvaluateEventWithoutPsiElement() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "http.call.after" to "groovy:logger.info('event executed')"
            )
        )

        val ruleEngine = RuleEngine.getInstance(project)

        ruleEngine.evaluate(RuleKey.event("http.call.after"))
    }

    @Test
    fun testEvaluateEventWithContextHandle() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "http.call.before" to "groovy:logger.info('event executed')"
            )
        )

        val ruleEngine = RuleEngine.getInstance(project)

        var capturedValue: String? = null
        ruleEngine.evaluate(RuleKey.event("http.call.before")) { ctx ->
            capturedValue = ctx.getExt("customKey") as? String
            ctx.setExt("customKey", "customValue")
        }
    }

    @Test
    fun testEvaluateEventWithIgnoreError() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "http.call.before" to "groovy:throw new RuntimeException('test error')"
            )
        )

        val ruleEngine = RuleEngine.getInstance(project)
        val mockElement = mock<PsiElement>()

        ruleEngine.evaluate(RuleKey.event("http.call.before", EventRuleMode.IGNORE_ERROR), mockElement)
    }

    @Test
    fun testGetInstance() {
        val ruleEngine = RuleEngine.getInstance(project)
        assertNotNull(ruleEngine)
    }

    @Test
    fun testFilterExpressionPasses() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "api.name" to "Test API",
                "api.name[true]" to "Filtered API"
            )
        )

        val ruleEngine = RuleEngine.getInstance(project)
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.string("api.name", StringRuleMode.MERGE), mockElement)
        assertTrue(result!!.contains("Test API"))
        assertTrue(result.contains("Filtered API"))
    }

    @Test
    fun testFilterExpressionFails() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "api.name" to "Test API",
                "api.name[false]" to "Filtered API"
            )
        )

        val ruleEngine = RuleEngine.getInstance(project)
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.string("api.name", StringRuleMode.MERGE), mockElement)
        assertEquals("Test API", result)
    }

    @Test
    fun testErrorHandlingInEvaluateString() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "api.name" to "groovy:throw new RuntimeException('Parser error')"
            )
        )

        val ruleEngine = RuleEngine.getInstance(project)
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.string("api.name"), mockElement)
        assertNull(result)
    }

    @Test
    fun testErrorHandlingInEvaluateBoolean() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "ignore" to "groovy:throw new RuntimeException('Parser error')"
            )
        )

        val ruleEngine = RuleEngine.getInstance(project)
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.boolean("ignore"), mockElement)
        assertFalse(result)
    }

    @Test
    fun testMultipleIndexedKeys() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "api.name" to "Base API",
                "api.name[true]" to "First Filtered",
                "api.name[false]" to "Second Filtered",
                "api.name[true]" to "Third Filtered"
            )
        )

        val ruleEngine = RuleEngine.getInstance(project)
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.string("api.name", StringRuleMode.MERGE), mockElement)
        assertTrue(result!!.contains("Base API"))
        assertTrue(result.contains("First Filtered"))
        assertFalse(result.contains("Second Filtered"))
        assertTrue(result.contains("Third Filtered"))
    }

    @Test
    fun testToBooleanWithVariousInputs() = runTest {
        project.registerServiceInstance(
            serviceInterface = ConfigReader::class.java,
            instance = TestConfigReader.fromRules(
                project,
                "test.bool.true" to "true",
                "test.bool.True" to "True",
                "test.bool.TRUE" to "TRUE",
                "test.bool.one" to "1",
                "test.bool.yes" to "yes",
                "test.bool.Yes" to "Yes",
                "test.bool.y" to "y",
                "test.bool.Y" to "Y",
                "test.bool.false" to "false",
                "test.bool.zero" to "0",
                "test.bool.no" to "no"
            )
        )

        val ruleEngine = RuleEngine.getInstance(project)
        val mockElement = mock<PsiElement>()

        assertTrue(ruleEngine.evaluate(RuleKey.boolean("test.bool.true"), mockElement))
        assertTrue(ruleEngine.evaluate(RuleKey.boolean("test.bool.True"), mockElement))
        assertTrue(ruleEngine.evaluate(RuleKey.boolean("test.bool.TRUE"), mockElement))
        assertTrue(ruleEngine.evaluate(RuleKey.boolean("test.bool.one"), mockElement))
        assertTrue(ruleEngine.evaluate(RuleKey.boolean("test.bool.yes"), mockElement))
        assertTrue(ruleEngine.evaluate(RuleKey.boolean("test.bool.Yes"), mockElement))
        assertTrue(ruleEngine.evaluate(RuleKey.boolean("test.bool.y"), mockElement))
        assertTrue(ruleEngine.evaluate(RuleKey.boolean("test.bool.Y"), mockElement))
        assertFalse(ruleEngine.evaluate(RuleKey.boolean("test.bool.false"), mockElement))
        assertFalse(ruleEngine.evaluate(RuleKey.boolean("test.bool.zero"), mockElement))
        assertFalse(ruleEngine.evaluate(RuleKey.boolean("test.bool.no"), mockElement))
    }
}
