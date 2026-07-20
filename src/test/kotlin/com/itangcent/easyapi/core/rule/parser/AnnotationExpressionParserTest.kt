package com.itangcent.easyapi.core.rule.parser

import com.itangcent.easyapi.core.rule.RuleKey
import com.itangcent.easyapi.core.rule.context.RuleContext
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Assert.*
import org.junit.Test

class AnnotationExpressionParserPureTest {

    private val parser = AnnotationExpressionParser()

    @Test
    fun testCanParse_withAtPrefix() {
        assertTrue(parser.canParse("@RequestMapping"))
        assertTrue(parser.canParse("@org.springframework.web.bind.annotation.RequestMapping"))
        assertTrue(parser.canParse("@RequestMapping#path"))
        assertTrue(parser.canParse("@"))
    }

    @Test
    fun testCanParse_withoutAtPrefix() {
        assertFalse(parser.canParse("RequestMapping"))
        assertFalse(parser.canParse("#tag"))
        assertFalse(parser.canParse("!expression"))
        assertFalse(parser.canParse(""))
        assertFalse(parser.canParse("\$class:Foo"))
    }
}

/**
 * Integration tests for [AnnotationExpressionParser.parse] using real PSI elements
 * and annotations.
 */
class AnnotationExpressionParserTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var parser: AnnotationExpressionParser

    override fun setUp() {
        super.setUp()
        parser = AnnotationExpressionParser()
        // Load custom annotation stubs used across tests
        loadFile("rule/ValueAnnot.java", """
            package com.test.rule;
            public @interface ValueAnnot {
                String value() default "";
            }
        """.trimIndent())
        loadFile("rule/NamedAnnot.java", """
            package com.test.rule;
            public @interface NamedAnnot {
                String name() default "";
            }
        """.trimIndent())
    }

    // ── StringKey: defaults to "value" attribute ──────────────────

    fun testParseStringKeyDefaultsToValue() = runTest {
        loadFile("rule/AnnotatedComponent.java", """
            package com.test.rule;
            import com.test.rule.ValueAnnot;
            @ValueAnnot("testComponent")
            public class AnnotatedComponent {}
        """.trimIndent())

        val psiClass = findClass("com.test.rule.AnnotatedComponent")!!
        val context = RuleContext.from(project, psiClass)
        val result = parser.parse(
            "@com.test.rule.ValueAnnot",
            context,
            RuleKey.string("api.name")
        )

        assertEquals("StringKey without attr should default to 'value' attribute",
            "testComponent", result)
    }

    fun testParseStringKeyDefaultsToValueWhenValueIsDefault() = runTest {
        loadFile("rule/DefaultAnnotatedComponent.java", """
            package com.test.rule;
            import com.test.rule.ValueAnnot;
            @ValueAnnot
            public class DefaultAnnotatedComponent {}
        """.trimIndent())

        val psiClass = findClass("com.test.rule.DefaultAnnotatedComponent")!!
        val context = RuleContext.from(project, psiClass)
        val result = parser.parse(
            "@com.test.rule.ValueAnnot",
            context,
            RuleKey.string("api.name")
        )

        assertEquals("StringKey without attr should default to value even when default is empty",
            "", result)
    }

    // ── StringKey: explicit attribute ────────────────────────────

    fun testParseStringKeyWithExplicitAttr() = runTest {
        loadFile("rule/AnnotatedNamed.java", """
            package com.test.rule;
            import com.test.rule.NamedAnnot;
            @NamedAnnot(name = "testName")
            public class AnnotatedNamed {}
        """.trimIndent())

        val psiClass = findClass("com.test.rule.AnnotatedNamed")!!
        val context = RuleContext.from(project, psiClass)
        val result = parser.parse(
            "@com.test.rule.NamedAnnot#name",
            context,
            RuleKey.string("api.name")
        )

        assertEquals("StringKey with explicit attr should resolve that attribute",
            "testName", result)
    }

    // ── BooleanKey: presence check when no attr ───────────────────

    fun testParseBooleanKeyChecksPresenceWhenNoAttr() = runTest {
        loadFile("rule/DeprecatedClass.java", """
            package com.test.rule;
            @Deprecated
            public class DeprecatedClass {}
        """.trimIndent())

        val psiClass = findClass("com.test.rule.DeprecatedClass")!!
        val context = RuleContext.from(project, psiClass)
        val result = parser.parse(
            "@java.lang.Deprecated",
            context,
            RuleKey.boolean("api.deprecated")
        )

        assertEquals("BooleanKey without attr should return true when annotation present",
            true, result)
    }

    fun testParseBooleanKeyChecksAbsenceWhenNoAttr() = runTest {
        loadFile("rule/NotDeprecatedClass.java", """
            package com.test.rule;
            public class NotDeprecatedClass {}
        """.trimIndent())

        val psiClass = findClass("com.test.rule.NotDeprecatedClass")!!
        val context = RuleContext.from(project, psiClass)
        val result = parser.parse(
            "@java.lang.Deprecated",
            context,
            RuleKey.boolean("api.deprecated")
        )

        assertEquals("BooleanKey without attr should return false when annotation absent",
            false, result)
    }

    // ── BooleanKey: explicit attr ────────────────────────────────

    fun testParseBooleanKeyWithExplicitAttr() = runTest {
        loadFile("rule/NamedSince.java", """
            package com.test.rule;
            import com.test.rule.NamedAnnot;
            @NamedAnnot(name = "since1")
            public class NamedSince {}
        """.trimIndent())

        val psiClass = findClass("com.test.rule.NamedSince")!!
        val context = RuleContext.from(project, psiClass)
        val result = parser.parse(
            "@com.test.rule.NamedAnnot#name",
            context,
            RuleKey.boolean("api.deprecated")
        )

        assertEquals("BooleanKey with explicit attr should resolve that attribute",
            "since1", result)
    }

    // ── Null ruleKey: falls back to BooleanKey-like behavior ──────

    fun testParseNullRuleKeyChecksPresenceWhenNoAttr() = runTest {
        loadFile("rule/AnnotatedForNullKey.java", """
            package com.test.rule;
            @Deprecated
            public class AnnotatedForNullKey {}
        """.trimIndent())

        val psiClass = findClass("com.test.rule.AnnotatedForNullKey")!!
        val context = RuleContext.from(project, psiClass)
        val result = parser.parse("@java.lang.Deprecated", context, null)

        assertEquals("Null ruleKey without attr should check annotation presence",
            true, result)
    }

    fun testParseNullRuleKeyWithExplicitAttr() = runTest {
        loadFile("rule/NullKeyNamed.java", """
            package com.test.rule;
            import com.test.rule.NamedAnnot;
            @NamedAnnot(name = "someValue")
            public class NullKeyNamed {}
        """.trimIndent())

        val psiClass = findClass("com.test.rule.NullKeyNamed")!!
        val context = RuleContext.from(project, psiClass)
        val result = parser.parse(
            "@com.test.rule.NamedAnnot#name",
            context,
            null
        )

        assertEquals("Null ruleKey with explicit attr should resolve that attribute",
            "someValue", result)
    }

    // ── Edge cases ────────────────────────────────────────────────

    fun testParseReturnsNullWhenAnnotationIsBlank() = runTest {
        loadFile("rule/ComponentForBlank.java", """
            package com.test.rule;
            import com.test.rule.ValueAnnot;
            @ValueAnnot("testComponent")
            public class ComponentForBlank {}
        """.trimIndent())

        val psiClass = findClass("com.test.rule.ComponentForBlank")!!
        val context = RuleContext.from(project, psiClass)
        val result = parser.parse("@", context, RuleKey.string("api.name"))

        assertNull("Blank annotation name after @ should return null", result)
    }

    fun testParseReturnsNullWhenElementIsNull() = runTest {
        val context = RuleContext.withoutElement(project)
        val result = parser.parse(
            "@com.test.rule.ValueAnnot",
            context,
            RuleKey.string("api.name")
        )

        assertNull("Null element should return null", result)
    }
}

class NegationParserPureTest {

    private val parser = NegationParser()

    @Test
    fun testCanParse_withExclamation() {
        assertTrue(parser.canParse("!expression"))
        assertTrue(parser.canParse("!true"))
        assertTrue(parser.canParse("!@Annotation"))
        assertTrue(parser.canParse("!"))
        assertTrue(parser.canParse("  !expression"))
    }

    @Test
    fun testCanParse_withoutExclamation() {
        assertFalse(parser.canParse("expression"))
        assertFalse(parser.canParse("@Annotation"))
        assertFalse(parser.canParse("#tag"))
        assertFalse(parser.canParse(""))
    }
}
