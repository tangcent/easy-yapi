package com.itangcent.easyapi.rule.parser

import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.context.RuleContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class RegexParserTest {

    private lateinit var parser: RegexParser
    private lateinit var context: RuleContext

    @Before
    fun setUp() {
        parser = RegexParser()
        context = mock()
    }

    // ── canParse tests ──────────────────────────────────────────────

    @Test
    fun testCanParse_withPrefix() {
        assertTrue("Should parse expression with #regex: prefix", parser.canParse("#regex:.*"))
        assertTrue("Should parse expression with empty pattern", parser.canParse("#regex:"))
        assertTrue("Should parse expression with character class", parser.canParse("#regex:[a-z]+"))
        assertTrue("Should parse expression with complex pattern", parser.canParse("#regex:/api/v\\d+/.*"))
        assertTrue("Should parse expression with capture groups", parser.canParse("#regex:reactor.core.publisher.Mono<(.*?)>"))
    }

    @Test
    fun testCanParse_withoutPrefix() {
        assertFalse("Should not parse expression without prefix", parser.canParse(".*"))
        assertFalse("Should not parse tag expression", parser.canParse("#tag"))
        assertFalse("Should not parse annotation expression", parser.canParse("@Annotation"))
        assertFalse("Should not parse empty string", parser.canParse(""))
        assertFalse("Should not parse expression with wrong prefix", parser.canParse("regex:.*"))
        assertFalse("Should not parse expression with partial prefix", parser.canParse("#regex.*"))
    }

    // ── parse with BooleanKey tests ─────────────────────────────────

    @Test
    fun testParse_booleanKey_matchesPattern() {
        whenever(context.typeText).thenReturn("reactor.core.publisher.Mono<User>")
        runBlocking {
            val key = RuleKey.boolean("test.key")
            val result = parser.parse("#regex:.*Mono.*", context, key)
            assertEquals("Should return true when pattern matches", true, result)
        }
    }

    @Test
    fun testParse_booleanKey_noMatch() {
        whenever(context.typeText).thenReturn("java.lang.String")
        runBlocking {
            val key = RuleKey.boolean("test.key")
            val result = parser.parse("#regex:.*Mono.*", context, key)
            assertEquals("Should return false when pattern does not match", false, result)
        }
    }

    @Test
    fun testParse_booleanKey_nullTypeText() {
        whenever(context.typeText).thenReturn(null)
        runBlocking {
            val key = RuleKey.boolean("test.key")
            val result = parser.parse("#regex:.*", context, key)
            assertEquals("Should return false when typeText is null", false, result)
        }
    }

    @Test
    fun testParse_booleanKey_emptyPattern() {
        whenever(context.typeText).thenReturn("some text")
        runBlocking {
            val key = RuleKey.boolean("test.key")
            val result = parser.parse("#regex:", context, key)
            assertEquals("Should return false when pattern is empty", false, result)
        }
    }

    @Test
    fun testParse_booleanKey_invalidRegex() {
        whenever(context.typeText).thenReturn("some text")
        runBlocking {
            val key = RuleKey.boolean("test.key")
            val result = parser.parse("#regex:[invalid", context, key)
            assertEquals("Should return false for invalid regex pattern", false, result)
        }
    }

    @Test
    fun testParse_booleanKey_exactMatch() {
        whenever(context.typeText).thenReturn("java.lang.String")
        runBlocking {
            val key = RuleKey.boolean("test.key")
            val result = parser.parse("#regex:^java\\.lang\\.String$", context, key)
            assertEquals("Should match exact pattern with anchors", true, result)
        }
    }

    @Test
    fun testParse_booleanKey_partialMatch() {
        whenever(context.typeText).thenReturn("public java.lang.String getName()")
        runBlocking {
            val key = RuleKey.boolean("test.key")
            val result = parser.parse("#regex:java\\.lang\\.String", context, key)
            assertEquals("Should find partial match within text", true, result)
        }
    }

    @Test
    fun testParse_booleanKey_multilineText() {
        whenever(context.typeText).thenReturn("line1\nline2\nline3")
        runBlocking {
            val key = RuleKey.boolean("test.key")
            val result = parser.parse("#regex:line2", context, key)
            assertEquals("Should match across newlines with DOTALL flag", true, result)
        }
    }

    // ── parse with StringKey tests ──────────────────────────────────

    @Test
    fun testParse_stringKey_withCaptureGroups() {
        whenever(context.typeText).thenReturn("reactor.core.publisher.Mono<User>")
        runBlocking {
            val key = RuleKey.string("test.key")
            val result = parser.parse("#regex:reactor.core.publisher.Mono<(.*?)>", context, key)
            assertEquals("Should return expression when no placeholders in expression",
                "#regex:reactor.core.publisher.Mono<(.*?)>", result)
        }
    }

    @Test
    fun testParse_stringKey_noMatch() {
        whenever(context.typeText).thenReturn("java.lang.String")
        runBlocking {
            val key = RuleKey.string("test.key")
            val result = parser.parse("#regex:reactor.core.publisher.Mono<(.*?)>", context, key)
            assertEquals("Should return empty string when no match", "", result)
        }
    }

    @Test
    fun testParse_stringKey_nullTypeText() {
        whenever(context.typeText).thenReturn(null)
        runBlocking {
            val key = RuleKey.string("test.key")
            val result = parser.parse("#regex:.*", context, key)
            assertEquals("Should return empty string when typeText is null", "", result)
        }
    }

    @Test
    fun testParse_stringKey_emptyPattern() {
        whenever(context.typeText).thenReturn("some text")
        runBlocking {
            val key = RuleKey.string("test.key")
            val result = parser.parse("#regex:", context, key)
            assertEquals("Should return empty string when pattern is empty", "", result)
        }
    }

    @Test
    fun testParse_stringKey_invalidRegex() {
        whenever(context.typeText).thenReturn("some text")
        runBlocking {
            val key = RuleKey.string("test.key")
            val result = parser.parse("#regex:[invalid", context, key)
            assertEquals("Should return empty string for invalid regex", "", result)
        }
    }

    // ── parse with other RuleKey types ───────────────────────────────

    @Test
    fun testParse_nullKey_returnsExpression() {
        whenever(context.typeText).thenReturn("some text")
        runBlocking {
            val result = parser.parse("#regex:.*", context, null)
            assertEquals("Should return expression when key is null", "#regex:.*", result)
        }
    }

    @Test
    fun testParse_intKey_returnsExpression() {
        whenever(context.typeText).thenReturn("some text")
        runBlocking {
            val key = RuleKey.int("test.key")
            val result = parser.parse("#regex:.*", context, key)
            assertEquals("Should return expression for IntKey", "#regex:.*", result)
        }
    }

    @Test
    fun testParse_eventKey_returnsExpression() {
        whenever(context.typeText).thenReturn("some text")
        runBlocking {
            val key = RuleKey.event("test.key")
            val result = parser.parse("#regex:.*", context, key)
            assertEquals("Should return expression for EventKey", "#regex:.*", result)
        }
    }

    // ── pattern caching tests ────────────────────────────────────────

    @Test
    fun testPatternCaching_returnsSameInstance() {
        val pattern1 = parser.getOrCompilePattern("test.*pattern")
        val pattern2 = parser.getOrCompilePattern("test.*pattern")
        assertSame("Should return cached pattern instance", pattern1, pattern2)
    }

    @Test
    fun testPatternCaching_differentPatterns() {
        val pattern1 = parser.getOrCompilePattern("pattern1")
        val pattern2 = parser.getOrCompilePattern("pattern2")
        assertNotSame("Should return different pattern for different input", pattern1, pattern2)
    }

    // ── edge cases ───────────────────────────────────────────────────

    @Test
    fun testParse_booleanKey_whitespaceInPattern() {
        whenever(context.typeText).thenReturn("hello world")
        runBlocking {
            val key = RuleKey.boolean("test.key")
            val result = parser.parse("#regex:hello\\sworld", context, key)
            assertEquals("Should handle whitespace patterns", true, result)
        }
    }

    @Test
    fun testParse_booleanKey_digitPattern() {
        whenever(context.typeText).thenReturn("api/v1/users")
        runBlocking {
            val key = RuleKey.boolean("test.key")
            val result = parser.parse("#regex:/v\\d+/", context, key)
            assertEquals("Should match digit patterns", true, result)
        }
    }

    @Test
    fun testParse_booleanKey_caseSensitive() {
        whenever(context.typeText).thenReturn("API")
        runBlocking {
            val key = RuleKey.boolean("test.key")
            val result = parser.parse("#regex:api", context, key)
            assertEquals("Should be case sensitive by default", false, result)
        }
    }

    @Test
    fun testParse_booleanKey_specialCharacters() {
        whenever(context.typeText).thenReturn("com.example.UserService\$Impl")
        runBlocking {
            val key = RuleKey.boolean("test.key")
            val result = parser.parse("#regex:UserService\\\$Impl", context, key)
            assertEquals("Should handle escaped special characters", true, result)
        }
    }

    @Test
    fun testParse_booleanKey_emptyTypeText() {
        whenever(context.typeText).thenReturn("")
        runBlocking {
            val key = RuleKey.boolean("test.key")
            val result = parser.parse("#regex:^$", context, key)
            assertEquals("Should match empty string with ^$ pattern", true, result)
        }
    }

    @Test
    fun testParse_booleanKey_emptyTypeText_noMatch() {
        whenever(context.typeText).thenReturn("")
        runBlocking {
            val key = RuleKey.boolean("test.key")
            val result = parser.parse("#regex:.+", context, key)
            assertEquals("Should not match empty string with .+ pattern", false, result)
        }
    }
}
