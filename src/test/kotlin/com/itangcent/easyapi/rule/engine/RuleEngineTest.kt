package com.itangcent.easyapi.rule.engine

import com.intellij.psi.PsiElement
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.rule.EventRuleMode
import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.StringRuleMode
import com.itangcent.easyapi.rule.context.RuleContext
import com.itangcent.easyapi.rule.parser.RuleParser
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.itangcent.easyapi.testFramework.TestConfigReader
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock

class RuleEngineTest : EasyApiLightCodeInsightFixtureTestCase() {

    override fun createConfigReader(): ConfigReader = TestConfigReader.EMPTY

    @Test
    fun testEvaluateStringWithSingleMode() = runTest {
        val configReader = TestConfigReader.fromRules(
            "api.name" to "Test API",
            "api.tag" to "tag1"
        )

        val ruleEngine = RuleEngine(project, configReader, listOf(TestLiteralParser()))
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.string("api.name", StringRuleMode.SINGLE), mockElement)
        assertEquals("Test API", result)
    }

    @Test
    fun testEvaluateStringWithMergeMode() = runTest {
        val configReader = TestConfigReader.fromRules(
            "api.tag" to "tag1",
            "api.tag" to "tag2",
            "api.tag" to "tag3"
        )

        val ruleEngine = RuleEngine(project, configReader, listOf(TestLiteralParser()))
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.string("api.tag", StringRuleMode.MERGE), mockElement)
        assertEquals("tag1\ntag2\ntag3", result)
    }

    @Test
    fun testEvaluateStringWithMergeDistinctMode() = runTest {
        val configReader = TestConfigReader.fromRules(
            "api.tag" to "tag1",
            "api.tag" to "tag2",
            "api.tag" to "tag1"
        )

        val ruleEngine = RuleEngine(project, configReader, listOf(TestLiteralParser()))
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.string("api.tag", StringRuleMode.MERGE_DISTINCT), mockElement)
        assertEquals("tag1\ntag2", result)
    }

    @Test
    fun testEvaluateStringWithNullValues() = runTest {
        val configReader = TestConfigReader.fromRules(
            "api.name" to "null"
        )

        val ruleEngine = RuleEngine(project, configReader, listOf(TestNullParser()))
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.string("api.name", StringRuleMode.SINGLE), mockElement)
        assertNull(result)
    }

    @Test
    fun testEvaluateStringWithEmptyConfig() = runTest {
        val configReader = TestConfigReader.EMPTY

        val ruleEngine = RuleEngine(project, configReader, listOf(TestLiteralParser()))
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.string("nonexistent.key", StringRuleMode.SINGLE), mockElement)
        assertNull(result)
    }

    @Test
    fun testEvaluateBooleanWithTrueValue() = runTest {
        val configReader = TestConfigReader.fromRules(
            "ignore" to "true"
        )

        val ruleEngine = RuleEngine(project, configReader, listOf(TestLiteralParser()))
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.boolean("ignore"), mockElement)
        assertTrue(result)
    }

    @Test
    fun testEvaluateBooleanWithFalseValue() = runTest {
        val configReader = TestConfigReader.fromRules(
            "ignore" to "false"
        )

        val ruleEngine = RuleEngine(project, configReader, listOf(TestLiteralParser()))
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.boolean("ignore"), mockElement)
        assertFalse(result)
    }

    @Test
    fun testEvaluateBooleanWithMultipleValues() = runTest {
        val configReader = TestConfigReader.fromRules(
            "ignore" to "false",
            "ignore" to "false",
            "ignore" to "true"
        )

        val ruleEngine = RuleEngine(project, configReader, listOf(TestLiteralParser()))
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.boolean("ignore"), mockElement)
        assertTrue(result)
    }

    @Test
    fun testEvaluateBooleanWithNumericValue() = runTest {
        val configReader = TestConfigReader.fromRules(
            "ignore" to "1"
        )

        val ruleEngine = RuleEngine(project, configReader, listOf(TestLiteralParser()))
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.boolean("ignore"), mockElement)
        assertTrue(result)
    }

    @Test
    fun testEvaluateBooleanWithStringYes() = runTest {
        val configReader = TestConfigReader.fromRules(
            "ignore" to "yes"
        )

        val ruleEngine = RuleEngine(project, configReader, listOf(TestLiteralParser()))
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.boolean("ignore"), mockElement)
        assertTrue(result)
    }

    @Test
    fun testEvaluateBooleanWithEmptyConfig() = runTest {
        val configReader = TestConfigReader.EMPTY

        val ruleEngine = RuleEngine(project, configReader, listOf(TestLiteralParser()))
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.boolean("nonexistent.key"), mockElement)
        assertFalse(result)
    }

    @Test
    fun testEvaluateIntWithValue() = runTest {
        val configReader = TestConfigReader.fromRules(
            "field.max.depth" to "5"
        )

        val ruleEngine = RuleEngine(project, configReader, listOf(TestLiteralParser()))
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.int("field.max.depth"), mockElement)
        assertEquals(5, result)
    }

    @Test
    fun testEvaluateIntWithMultipleValues() = runTest {
        val configReader = TestConfigReader.fromRules(
            "field.max.depth" to "10",
            "field.max.depth" to "20"
        )

        val ruleEngine = RuleEngine(project, configReader, listOf(TestLiteralParser()))
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.int("field.max.depth"), mockElement)
        assertEquals(10, result)
    }

    @Test
    fun testEvaluateIntWithNullValue() = runTest {
        val configReader = TestConfigReader.fromRules(
            "field.max.depth" to "null"
        )

        val ruleEngine = RuleEngine(project, configReader, listOf(TestNullParser()))
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.int("field.max.depth"), mockElement)
        assertNull(result)
    }

    @Test
    fun testEvaluateIntWithEmptyConfig() = runTest {
        val configReader = TestConfigReader.EMPTY

        val ruleEngine = RuleEngine(project, configReader, listOf(TestLiteralParser()))
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.int("nonexistent.key"), mockElement)
        assertNull(result)
    }

    @Test
    fun testEvaluateEventWithPsiElement() = runTest {
        var eventExecuted = false
        val configReader = TestConfigReader.fromRules(
            "http.call.before" to "execute"
        )

        val parser = object : RuleParser {
            override fun canParse(expression: String): Boolean = true
            override suspend fun parse(expression: String, context: RuleContext, ruleKey: RuleKey<*>?): Any? {
                eventExecuted = true
                return null
            }
        }
        val ruleEngine = RuleEngine(project, configReader, listOf(parser))
        val mockElement = mock<PsiElement>()

        ruleEngine.evaluate(RuleKey.event("http.call.before"), mockElement)
        assertTrue(eventExecuted)
    }

    @Test
    fun testEvaluateEventWithoutPsiElement() = runTest {
        var eventExecuted = false
        val configReader = TestConfigReader.fromRules(
            "http.call.after" to "execute"
        )

        val parser = object : RuleParser {
            override fun canParse(expression: String): Boolean = true
            override suspend fun parse(expression: String, context: RuleContext, ruleKey: RuleKey<*>?): Any? {
                eventExecuted = true
                return null
            }
        }
        val ruleEngine = RuleEngine(project, configReader, listOf(parser))

        ruleEngine.evaluate(RuleKey.event("http.call.after"))
        assertTrue(eventExecuted)
    }

    @Test
    fun testEvaluateEventWithContextHandle() = runTest {
        var capturedValue: String? = null
        val configReader = TestConfigReader.fromRules(
            "http.call.before" to "execute"
        )

        val parser = object : RuleParser {
            override fun canParse(expression: String): Boolean = true
            override suspend fun parse(expression: String, context: RuleContext, ruleKey: RuleKey<*>?): Any? {
                capturedValue = context.getExt("customKey") as? String
                return null
            }
        }
        val ruleEngine = RuleEngine(project, configReader, listOf(parser))

        ruleEngine.evaluate(RuleKey.event("http.call.before")) { ctx ->
            ctx.setExt("customKey", "customValue")
        }
        assertEquals("customValue", capturedValue)
    }

    @Test
    fun testEvaluateEventWithIgnoreError() = runTest {
        val configReader = TestConfigReader.fromRules(
            "http.call.before" to "throw"
        )

        val parser = object : RuleParser {
            override fun canParse(expression: String): Boolean = true
            override suspend fun parse(expression: String, context: RuleContext, ruleKey: RuleKey<*>?): Any? {
                throw RuntimeException("Test error")
            }
        }
        val ruleEngine = RuleEngine(project, configReader, listOf(parser))
        val mockElement = mock<PsiElement>()

        ruleEngine.evaluate(RuleKey.event("http.call.before", EventRuleMode.IGNORE_ERROR), mockElement)
    }

    @Test
    fun testEvaluateEventWithThrowOnError() {
        val configReader = TestConfigReader.fromRules(
            "http.call.before" to "throw"
        )

        val parser = object : RuleParser {
            override fun canParse(expression: String): Boolean = true
            override suspend fun parse(expression: String, context: RuleContext, ruleKey: RuleKey<*>?): Any? {
                throw RuntimeException("Test error")
            }
        }
        val ruleEngine = RuleEngine(project, configReader, listOf(parser))
        val mockElement = mock<PsiElement>()

        assertThrows(RuntimeException::class.java) {
            runBlocking {
                ruleEngine.evaluate(RuleKey.event("http.call.before", EventRuleMode.THROW_IN_ERROR), mockElement)
            }
        }
    }

    @Test
    fun testFilterExpressionPasses() = runTest {
        val config = mutableMapOf<String, List<String>>()
        config["api.name"] = listOf("Test API")
        config["api.name[true]"] = listOf("Filtered API")
        val configReader = TestConfigReader(config)

        val ruleEngine = RuleEngine(project, configReader, listOf(TestLiteralParser()))
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.string("api.name", StringRuleMode.MERGE), mockElement)
        assertTrue(result!!.contains("Test API"))
        assertTrue(result.contains("Filtered API"))
    }

    @Test
    fun testFilterExpressionFails() = runTest {
        val config = mutableMapOf<String, List<String>>()
        config["api.name"] = listOf("Test API")
        config["api.name[false]"] = listOf("Filtered API")
        val configReader = TestConfigReader(config)

        val ruleEngine = RuleEngine(project, configReader, listOf(TestLiteralParser()))
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.string("api.name", StringRuleMode.MERGE), mockElement)
        assertEquals("Test API", result)
    }

    @Test
    fun testParserSelection() = runTest {
        val configReader = TestConfigReader.fromRules(
            "api.name" to "literal:value",
            "api.tag" to "special:value"
        )

        val literalParser = object : RuleParser {
            override fun canParse(expression: String): Boolean = expression.startsWith("literal:")
            override suspend fun parse(expression: String, context: RuleContext, ruleKey: RuleKey<*>?): Any? =
                expression.removePrefix("literal:")
        }
        val specialParser = object : RuleParser {
            override fun canParse(expression: String): Boolean = expression.startsWith("special:")
            override suspend fun parse(expression: String, context: RuleContext, ruleKey: RuleKey<*>?): Any? =
                "SPECIAL: ${expression.removePrefix("special:")}"
        }
        val ruleEngine = RuleEngine(project, configReader, listOf(literalParser, specialParser))
        val mockElement = mock<PsiElement>()

        val nameResult = ruleEngine.evaluate(RuleKey.string("api.name"), mockElement)
        assertEquals("value", nameResult)

        val tagResult = ruleEngine.evaluate(RuleKey.string("api.tag"), mockElement)
        assertEquals("SPECIAL: value", tagResult)
    }

    @Test
    fun testErrorHandlingInEvaluateString() = runTest {
        val configReader = TestConfigReader.fromRules(
            "api.name" to "error"
        )

        val errorParser = object : RuleParser {
            override fun canParse(expression: String): Boolean = true
            override suspend fun parse(expression: String, context: RuleContext, ruleKey: RuleKey<*>?): Any? {
                throw RuntimeException("Parser error")
            }
        }
        val ruleEngine = RuleEngine(project, configReader, listOf(errorParser))
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.string("api.name"), mockElement)
        assertNull(result)
    }

    @Test
    fun testErrorHandlingInEvaluateBoolean() = runTest {
        val configReader = TestConfigReader.fromRules(
            "ignore" to "error"
        )

        val errorParser = object : RuleParser {
            override fun canParse(expression: String): Boolean = true
            override suspend fun parse(expression: String, context: RuleContext, ruleKey: RuleKey<*>?): Any? {
                throw RuntimeException("Parser error")
            }
        }
        val ruleEngine = RuleEngine(project, configReader, listOf(errorParser))
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.boolean("ignore"), mockElement)
        assertFalse(result)
    }

    @Test
    fun testGetInstance() {
        val ruleEngine = RuleEngine.getInstance(project)
        assertNotNull(ruleEngine)
    }

    @Test
    fun testDefaultParsersAreUsedWhenNoneProvided() = runTest {
        val configReader = TestConfigReader.fromRules(
            "api.name" to "literal value"
        )

        val ruleEngine = RuleEngine(project, configReader)
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.string("api.name"), mockElement)
        assertNotNull(result)
    }

    @Test
    fun testMultipleIndexedKeys() = runTest {
        val config = mutableMapOf<String, List<String>>()
        config["api.name"] = listOf("Base API")
        config["api.name[true]"] = listOf("First Filtered")
        config["api.name[false]"] = listOf("Second Filtered")
        config["api.name[true]"] = config["api.name[true]"]!! + "Third Filtered"
        val configReader = TestConfigReader(config)

        val ruleEngine = RuleEngine(project, configReader, listOf(TestLiteralParser()))
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.string("api.name", StringRuleMode.MERGE), mockElement)
        assertTrue(result!!.contains("Base API"))
        assertTrue(result.contains("First Filtered"))
        assertFalse(result.contains("Second Filtered"))
        assertTrue(result.contains("Third Filtered"))
    }

    @Test
    fun testToBooleanWithVariousInputs() = runTest {
        val configReader = TestConfigReader.fromRules(
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

        val ruleEngine = RuleEngine(project, configReader, listOf(TestLiteralParser()))
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

    @Test
    fun testNegationWithNullValue() = runTest {
        val configReader = TestConfigReader.fromRules(
            "test.negated" to "null"
        )

        val ruleEngine = RuleEngine(project, configReader, listOf(TestNullParser()))
        val mockElement = mock<PsiElement>()

        val result = ruleEngine.evaluate(RuleKey.boolean("test.negated"), mockElement)
        assertFalse(result)
    }
}

class TestLiteralParser : RuleParser {
    override fun canParse(expression: String): Boolean = true
    override suspend fun parse(expression: String, context: RuleContext, ruleKey: RuleKey<*>?): Any? = expression
}

class TestNullParser : RuleParser {
    override fun canParse(expression: String): Boolean = expression == "null"
    override suspend fun parse(expression: String, context: RuleContext, ruleKey: RuleKey<*>?): Any? = null
}
