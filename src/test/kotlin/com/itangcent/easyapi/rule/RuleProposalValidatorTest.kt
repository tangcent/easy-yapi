package com.itangcent.easyapi.rule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for [RuleProposalValidator].
 *
 * Covers the v1 review-agent policy: hard errors on unknown keys, invalid
 * filter prefixes, and malformed JSON values; soft warnings only on the
 * deprecated bare `class:` filter form.
 */
class RuleProposalValidatorTest {

    fun testCleanRulePasses() {
        val content = """
            # A clean rule file
            api.name=My API
            method.additional.header={"name":"Authorization","value":"Bearer ${'$'}{token}","required":true}
            method.doc[${'$'}class:com.example.web.UserController]=user
        """.trimIndent()
        val result = RuleProposalValidator.validate(content)
        assertTrue("errors: ${result.errors}", result.ok)
        assertTrue("unexpected warnings: ${result.warnings}", result.warnings.isEmpty())
    }

    fun testUnknownKeyIsBlocked() {
        // The preamble is explicit that `api.header` does NOT exist.
        val content = "api.header=X-Foo:bar"
        val result = RuleProposalValidator.validate(content)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("unknown rule key") && it.contains("api.header") })
    }

    fun testBareClassFilterIsOnlyAWarning() {
        val content = "method.doc[class:com.example.web.UserController]=user"
        val result = RuleProposalValidator.validate(content)
        // `method.doc` is valid; bare `class:` is deprecated but not invalid.
        assertTrue("errors: ${result.errors}", result.ok)
        assertTrue(result.warnings.any { it.contains("deprecated") && it.contains("class:") })
    }

    fun testInvalidFilterPrefixIsBlocked() {
        val content = "method.doc[~com.example.UserController]=user"
        val result = RuleProposalValidator.validate(content)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("invalid filter") })
    }

    fun testMalformedJsonHeaderValueIsBlocked() {
        val content = "method.additional.header=Authorization:Bearer token"
        val result = RuleProposalValidator.validate(content)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("not valid JSON") })
    }

    fun testValidDollarClassFilterPasses() {
        val content = "method.doc[\${'$'}class:com.example.web.UserController]=user"
        val result = RuleProposalValidator.validate(content)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    fun testGroovyFilterAndValueBlockPass() {
        val content = """
            method.additional.header[groovy: it.containingClass().name().startsWith("com.example.web.")]={"name":"Authorization","value":"Bearer ${'$'}{token}","required":true}
            method.additional.header=groovy:```
            return '{"name":"Authorization","value":"Bearer ${'$'}{token}","required":true}'
            ```
        """.trimIndent()
        val result = RuleProposalValidator.validate(content)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    fun testAliasesAreAcceptedAsKnownKeys() {
        // `doc.param` is an alias of `param.doc` per RuleKeys.kt.
        val content = "doc.param=the current user"
        val result = RuleProposalValidator.validate(content)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    fun testCommentAndDirectiveLinesAreIgnored() {
        val content = """
            # a comment
            ###set resolveProperty=false
            api.name=My API
        """.trimIndent()
        val result = RuleProposalValidator.validate(content)
        assertTrue("errors: ${result.errors}", result.ok)
        assertEquals(0, result.warnings.size)
    }

    // -------------------------------------------------------------------------
    // JSON-value keys: each of the four keys must validate JSON when inline.
    // -------------------------------------------------------------------------

    fun testMalformedParamValueIsBlocked() {
        val content = "method.additional.param=not json"
        val result = RuleProposalValidator.validate(content)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("not valid JSON") && it.contains("method.additional.param") })
    }

    fun testMalformedResponseHeaderValueIsBlocked() {
        val content = "method.additional.response.header=X-Foo:bar"
        val result = RuleProposalValidator.validate(content)
        assertFalse(result.ok)
        assertTrue(
            result.errors.any { it.contains("not valid JSON") && it.contains("method.additional.response.header") }
        )
    }

    fun testMalformedJsonAdditionalFieldValueIsBlocked() {
        val content = "json.additional.field=plain string"
        val result = RuleProposalValidator.validate(content)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("not valid JSON") && it.contains("json.additional.field") })
    }

    fun testValidJsonParamValuePasses() {
        val content = """method.additional.param={"name":"page","value":"1"}"""
        val result = RuleProposalValidator.validate(content)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    fun testBlankJsonValueSkipsJsonCheck() {
        // An empty value for a JSON-value key should not trigger the JSON
        // parser — `value.isNotBlank()` short-circuits the check.
        val content = "method.additional.header="
        val result = RuleProposalValidator.validate(content)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    fun testGroovyPrefixedValueSkipsJsonCheck() {
        // `groovy:`-prefixed values are scripts, not JSON; the validator
        // must skip JSON validation for them.
        val content = """method.additional.header=groovy: return '{"name":"X"}'"""
        val result = RuleProposalValidator.validate(content)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    // -------------------------------------------------------------------------
    // Filter prefixes: every valid prefix must be accepted.
    // -------------------------------------------------------------------------

    fun testAtPrefixFilterPasses() {
        val content = "method.doc[@com.example.WebController]=user"
        val result = RuleProposalValidator.validate(content)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    fun testRegexPrefixFilterPasses() {
        val content = "method.doc[#regex:.*Controller]=user"
        val result = RuleProposalValidator.validate(content)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    fun testTagPrefixFilterPasses() {
        // `#<tag>` — a hash-prefixed tag, distinct from `#regex:`.
        val content = "method.doc[#spring]=user"
        val result = RuleProposalValidator.validate(content)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    fun testNotPrefixFilterPasses() {
        val content = "method.doc[!com.example.InternalController]=user"
        val result = RuleProposalValidator.validate(content)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    fun testGroovyPrefixFilterPasses() {
        val content = "method.doc[groovy: it.name().contains(\"Controller\")]=user"
        val result = RuleProposalValidator.validate(content)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    // -------------------------------------------------------------------------
    // splitKeyFilterValue edge cases
    // -------------------------------------------------------------------------

    fun testEmptyFilterBracketIsTreatedAsNoFilter() {
        // `method.doc[]=value` — empty filter → treated as no filter (no prefix
        // check), so no error. This exercises the `filter.isEmpty()` branch
        // in splitKeyFilterValue.
        val content = "method.doc[]=user"
        val result = RuleProposalValidator.validate(content)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    fun testNonKeyValueLinesAreIgnored() {
        // Stray text that is not `key=value` and not a comment should not
        // produce errors (the parser skips lines it can't parse).
        val content = """
            # header
            this is just text with no equals sign
            api.name=My API
        """.trimIndent()
        val result = RuleProposalValidator.validate(content)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    fun testBracketEqualsInsideFilterDoesNotSplitEarly() {
        // `=` inside `[...]` must not be treated as the key=value separator.
        // Here the filter contains `=` and the real `=` is after the `]`.
        val content = "method.doc[#regex:a=b]=user"
        val result = RuleProposalValidator.validate(content)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    // -------------------------------------------------------------------------
    // Multi-line groovy value-block handling
    // -------------------------------------------------------------------------

    fun testMultiLineGroovyBlockBodyIsSkipped() {
        // The body of a ```-delimited block is free-form script — lines inside
        // must not be parsed as key=value, even if they look like one.
        val content = """
            method.additional.header=groovy:```
            api.name=this looks like a rule but is inside a block
            return '{"name":"X"}'
            ```
            api.name=real rule
        """.trimIndent()
        val result = RuleProposalValidator.validate(content)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    fun testBlockClosingLineEndingWithBackticks() {
        // A value ending with ``` (block opener on the same line as `key=`)
        // must enter block mode and skip subsequent lines until the closing ``` .
        val content = """
            method.additional.header=groovy:```
            return '{"name":"X"}'
            ```
            api.name=after block
        """.trimIndent()
        val result = RuleProposalValidator.validate(content)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    fun testMultipleErrorsAreAllReported() {
        // Two distinct hard errors should both appear in the result.
        val content = """
            api.unknown_key=foo
            method.doc[~invalid]=bar
        """.trimIndent()
        val result = RuleProposalValidator.validate(content)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("unknown rule key") })
        assertTrue(result.errors.any { it.contains("invalid filter") })
    }

    fun testEmptyContentIsValid() {
        val result = RuleProposalValidator.validate("")
        assertTrue("errors: ${result.errors}", result.ok)
        assertEquals(0, result.warnings.size)
    }

    fun testBlankLinesAreIgnored() {
        val content = "\n\n   \napi.name=My API\n\n"
        val result = RuleProposalValidator.validate(content)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    fun testRuleValidationOkProperty() {
        // Direct coverage of the `ok` convenience property on the data class.
        val ok = RuleValidation(errors = emptyList(), warnings = listOf("w"))
        assertTrue(ok.ok)
        val notOk = RuleValidation(errors = listOf("e"), warnings = emptyList())
        assertFalse(notOk.ok)
    }

    // -------------------------------------------------------------------------
    // No-project fallback path: only general RuleKeys are recognized.
    // Channel-specific keys (hopp.*, yapi.*) and implicit keys (max.deep,
    // markdown.template.url.*) are NOT recognized when project == null.
    // The project-scoped path (which DOES recognize them) needs a real
    // IntelliJ Project + ChannelRegistry and is covered by integration tests.
    // -------------------------------------------------------------------------

    @Test
    fun testNoProjectAcceptsGeneralKey() {
        // Regression: the no-project fallback path still accepts general keys.
        val result = RuleProposalValidator.validate("api.name=Foo", project = null)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    @Test
    fun testNoProjectRejectsChannelSpecificKey() {
        // yapi.project is a channel-specific key (YapiRuleKeys) —
        // without a project, the fallback path only knows general RuleKeys,
        // so it must be rejected as unknown.
        val result = RuleProposalValidator.validate("yapi.project=123", project = null)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("unknown rule key") && it.contains("yapi.project") })
    }

    @Test
    fun testNoProjectRejectsImplicitKey() {
        // max.deep is an implicit key (read by DefaultPsiClassHelper via
        // configReader.getFirst("max.deep")) — without a project, the
        // fallback path doesn't know about implicit keys.
        val result = RuleProposalValidator.validate("max.deep=5", project = null)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("unknown rule key") && it.contains("max.deep") })
    }

    @Test
    fun testDefaultValidateUsesNoProjectFallback() {
        // Calling validate(content) without the project argument must still
        // work (backward compatibility) and use the general-only fallback.
        val result = RuleProposalValidator.validate("api.name=Foo")
        assertTrue("errors: ${result.errors}", result.ok)
    }
}
