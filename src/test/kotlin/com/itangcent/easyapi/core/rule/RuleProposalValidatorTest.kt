package com.itangcent.easyapi.core.rule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import org.junit.Test

/**
 * Tests for [RuleProposalValidator].
 *
 * Covers the v1 review-agent policy: hard errors on unknown keys, invalid
 * filter prefixes, and malformed JSON values; soft warnings for deprecated
 * filters, class-context `name()` calls that may be mistaken for FQNs, and
 * `respondsTo(` probes that guess the context kind instead of calling
 * `it.contextType()` (issue #756).
 */
class RuleProposalValidatorTest : EasyApiLightCodeInsightFixtureTestCase() {

    @Test
    fun testCleanRulePasses() = runTest {
        val content = """
            # A clean rule file
            api.name=My API
            method.additional.header={"name":"Authorization","value":"Bearer ${'$'}{token}","required":true}
            method.doc[${'$'}class:com.example.web.UserController]=user
        """.trimIndent()
        val result = RuleProposalValidator.validate(content, project)
        assertTrue("errors: ${result.errors}", result.ok)
        assertTrue("unexpected warnings: ${result.warnings}", result.warnings.isEmpty())
    }

    @Test
    fun testUnknownKeyIsBlocked() = runTest {
        // The preamble is explicit that `api.header` does NOT exist.
        val content = "api.header=X-Foo:bar"
        val result = RuleProposalValidator.validate(content, project)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("unknown rule key") && it.contains("api.header") })
    }

    @Test
    fun testBareClassFilterIsOnlyAWarning() = runTest {
        val content = "method.doc[class:com.example.web.UserController]=user"
        val result = RuleProposalValidator.validate(content, project)
        // `method.doc` is valid; bare `class:` is deprecated but not invalid.
        assertTrue("errors: ${result.errors}", result.ok)
        assertTrue(result.warnings.any { it.contains("deprecated") && it.contains("class:") })
    }

    @Test
    fun testInvalidFilterPrefixIsBlocked() = runTest {
        val content = "method.doc[~com.example.UserController]=user"
        val result = RuleProposalValidator.validate(content, project)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("invalid filter") })
    }

    @Test
    fun testMalformedJsonHeaderValueIsBlocked() = runTest {
        val content = "method.additional.header=Authorization:Bearer token"
        val result = RuleProposalValidator.validate(content, project)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("not valid JSON") })
    }

    @Test
    fun testValidDollarClassFilterPasses() = runTest {
        val content = "method.doc[\$class:com.example.web.UserController]=user"
        val result = RuleProposalValidator.validate(content, project)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    @Test
    fun testGroovyFilterAndValueBlockPass() = runTest {
        val content = """
            method.additional.header[groovy: it.containingClass()?.qualifiedName()?.startsWith("com.example.web.")]={"name":"Authorization","value":"Bearer ${'$'}{token}","required":true}
            method.additional.header=groovy:```
            return '{"name":"Authorization","value":"Bearer ${'$'}{token}","required":true}'
            ```
        """.trimIndent()
        val result = RuleProposalValidator.validate(content, project)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    @Test
    fun testDefineClassSimpleNameProducesSoftWarning() = runTest {
        val content = """field.ignore=groovy: it.defineClass()?.name() == "com.example.dto.TraceBean""""
        val result = RuleProposalValidator.validate(content, project)

        assertTrue("errors: ${result.errors}", result.ok)
        assertTrue(
            "warnings: ${result.warnings}",
            result.warnings.any {
                it.contains("line 1") && it.contains("class-context name()") && it.contains("qualifiedName()")
            }
        )
    }

    @Test
    fun testContainingClassSimpleNameProducesSoftWarning() = runTest {
        val content =
            """field.ignore=groovy: it.containingClass().name().startsWith("com.example.")"""
        val result = RuleProposalValidator.validate(content, project)

        assertTrue("errors: ${result.errors}", result.ok)
        assertTrue("warnings: ${result.warnings}", result.warnings.any { it.contains("class-context name()") })
    }

    @Test
    fun testParamContextCanonicalTextProducesSoftWarning() = runTest {
        // On a parameter context canonicalText() is the element path, not the
        // type — a scalar-type check written with it matches every parameter.
        val content = """param.doc=groovy: it.canonicalText() == "java.lang.String""""
        val result = RuleProposalValidator.validate(content, project)

        assertTrue("errors: ${result.errors}", result.ok)
        assertTrue(
            "warnings: ${result.warnings}",
            result.warnings.any {
                it.contains("line 1") && it.contains("canonicalText()") && it.contains("type().name()")
            }
        )
    }

    @Test
    fun testParamContextCanonicalTextInMultiLineBlockProducesSoftWarning() = runTest {
        val content = """
            param.doc=groovy:```
            return it.canonicalText() != "java.lang.Long"
            ```
        """.trimIndent()
        val result = RuleProposalValidator.validate(content, project)

        assertTrue("errors: ${result.errors}", result.ok)
        assertTrue(
            "warnings: ${result.warnings}",
            result.warnings.any { it.contains("line 1") && it.contains("canonicalText()") }
        )
    }

    @Test
    fun testNonParamContextCanonicalTextIsNotWarned() = runTest {
        // On a field context canonicalText() IS the type — no warning.
        val content = """field.ignore=groovy: it.canonicalText() == "java.lang.String""""
        val result = RuleProposalValidator.validate(content, project)

        assertTrue("errors: ${result.errors}", result.ok)
        assertTrue("unexpected warnings: ${result.warnings}", result.warnings.isEmpty())
    }

    @Test
    fun testClassSimpleNameInMultiLineGroovyBlockProducesSoftWarning() = runTest {
        val content = """
            field.ignore=groovy:```
            def declaringType = it.defineClass()?.name()
            return declaringType == "com.example.dto.TraceBean"
            ```
        """.trimIndent()
        val result = RuleProposalValidator.validate(content, project)

        assertTrue("errors: ${result.errors}", result.ok)
        assertTrue(
            "warnings: ${result.warnings}",
            result.warnings.any { it.contains("line 2") && it.contains("class-context name()") }
        )
    }

    @Test
    fun testClassSimpleNameSplitAcrossGroovyBlockLinesProducesSoftWarning() = runTest {
        val content = """
            field.ignore=groovy:```
            def declaringType = it.defineClass()
                ?.name()
            return declaringType == "com.example.dto.TraceBean"
            ```
        """.trimIndent()
        val result = RuleProposalValidator.validate(content, project)

        assertTrue("errors: ${result.errors}", result.ok)
        assertTrue(
            "warnings: ${result.warnings}",
            result.warnings.any { it.contains("line 2") && it.contains("class-context name()") }
        )
    }

    @Test
    fun testClassQualifiedNameDoesNotProduceSimpleNameWarning() = runTest {
        val content =
            """field.ignore=groovy: it.defineClass()?.qualifiedName() == "com.example.dto.TraceBean"""
        val result = RuleProposalValidator.validate(content, project)

        assertTrue("errors: ${result.errors}", result.ok)
        assertTrue("unexpected warnings: ${result.warnings}", result.warnings.isEmpty())
    }

    @Test
    fun testRespondsToProbeProducesSoftWarning() = runTest {
        // Issue #756: the model guesses the context kind from the method
        // surface instead of calling the built-in discriminator.
        val content =
            """field.ignore=groovy: !it.respondsTo('containingClass').isEmpty()"""
        val result = RuleProposalValidator.validate(content, project)

        assertTrue("errors: ${result.errors}", result.ok)
        assertTrue(
            "warnings: ${result.warnings}",
            result.warnings.any {
                it.contains("line 1") && it.contains("respondsTo()") && it.contains("contextType()")
            }
        )
    }

    @Test
    fun testRespondsToInsideGroovyBlockProducesSoftWarning() = runTest {
        val content = """
            field.ignore=groovy:```
            def isMethod = !it.respondsTo('containingClass').isEmpty()
            return isMethod
            ```
        """.trimIndent()
        val result = RuleProposalValidator.validate(content, project)

        assertTrue("errors: ${result.errors}", result.ok)
        assertTrue(
            "warnings: ${result.warnings}",
            result.warnings.any { it.contains("line 2") && it.contains("respondsTo()") }
        )
    }

    @Test
    fun testRespondsToInCommentProducesNoWarning() = runTest {
        val content = """
            # it.respondsTo('containingClass') is the old workaround
            api.name=My API
        """.trimIndent()
        val result = RuleProposalValidator.validate(content, project)

        assertTrue("errors: ${result.errors}", result.ok)
        assertTrue("unexpected warnings: ${result.warnings}", result.warnings.isEmpty())
    }

    @Test
    fun testContextTypeDiscriminatorProducesNoWarning() = runTest {
        val content =
            """field.ignore=groovy: it.contextType() == "method" && it.name() == "toString""""
        val result = RuleProposalValidator.validate(content, project)

        assertTrue("errors: ${result.errors}", result.ok)
        assertTrue("unexpected warnings: ${result.warnings}", result.warnings.isEmpty())
    }

    @Test
    fun testAliasesAreAcceptedAsKnownKeys() = runTest {
        // `doc.param` is an alias of `param.doc` per RuleKeys.kt.
        val content = "doc.param=the current user"
        val result = RuleProposalValidator.validate(content, project)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    @Test
    fun testCommentAndDirectiveLinesAreIgnored() = runTest {
        val content = """
            # a comment
            ###set resolveProperty=false
            api.name=My API
        """.trimIndent()
        val result = RuleProposalValidator.validate(content, project)
        assertTrue("errors: ${result.errors}", result.ok)
        assertEquals(0, result.warnings.size)
    }

    // -------------------------------------------------------------------------
    // JSON-value keys: each of the four keys must validate JSON when inline.
    // -------------------------------------------------------------------------

    @Test
    fun testMalformedParamValueIsBlocked() = runTest {
        val content = "method.additional.param=not json"
        val result = RuleProposalValidator.validate(content, project)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("not valid JSON") && it.contains("method.additional.param") })
    }

    @Test
    fun testMalformedResponseHeaderValueIsBlocked() = runTest {
        val content = "method.additional.response.header=X-Foo:bar"
        val result = RuleProposalValidator.validate(content, project)
        assertFalse(result.ok)
        assertTrue(
            result.errors.any { it.contains("not valid JSON") && it.contains("method.additional.response.header") }
        )
    }

    @Test
    fun testMalformedJsonAdditionalFieldValueIsBlocked() = runTest {
        val content = "json.additional.field=plain string"
        val result = RuleProposalValidator.validate(content, project)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("not valid JSON") && it.contains("json.additional.field") })
    }

    @Test
    fun testValidJsonParamValuePasses() = runTest {
        val content = """method.additional.param={"name":"page","value":"1"}"""
        val result = RuleProposalValidator.validate(content, project)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    @Test
    fun testBlankJsonValueSkipsJsonCheck() = runTest {
        // An empty value for a JSON-value key should not trigger the JSON
        // parser — `value.isNotBlank()` short-circuits the check.
        val content = "method.additional.header="
        val result = RuleProposalValidator.validate(content, project)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    @Test
    fun testGroovyPrefixedValueSkipsJsonCheck() = runTest {
        // `groovy:`-prefixed values are scripts, not JSON; the validator
        // must skip JSON validation for them.
        val content = """method.additional.header=groovy: return '{"name":"X"}'"""
        val result = RuleProposalValidator.validate(content, project)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    // -------------------------------------------------------------------------
    // Filter prefixes: every valid prefix must be accepted.
    // -------------------------------------------------------------------------

    @Test
    fun testAtPrefixFilterPasses() = runTest {
        val content = "method.doc[@com.example.WebController]=user"
        val result = RuleProposalValidator.validate(content, project)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    @Test
    fun testRegexPrefixFilterPasses() = runTest {
        val content = "method.doc[#regex:.*Controller]=user"
        val result = RuleProposalValidator.validate(content, project)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    @Test
    fun testTagPrefixFilterPasses() = runTest {
        // `#<tag>` — a hash-prefixed tag, distinct from `#regex:`.
        val content = "method.doc[#spring]=user"
        val result = RuleProposalValidator.validate(content, project)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    @Test
    fun testNotPrefixFilterPasses() = runTest {
        val content = "method.doc[!com.example.InternalController]=user"
        val result = RuleProposalValidator.validate(content, project)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    @Test
    fun testGroovyPrefixFilterPasses() = runTest {
        val content = "method.doc[groovy: it.name().contains(\"Controller\")]=user"
        val result = RuleProposalValidator.validate(content, project)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    // -------------------------------------------------------------------------
    // Filter syntax edge cases (post parser-unification)
    // -------------------------------------------------------------------------

    @Test
    fun testEmptyFilterBracketIsRejectedAsUnknownKey() = runTest {
        // An empty filter is not valid `key[filter]` syntax. After parsing is
        // unified with ConfigTextParser, the whole left-hand side (`method.doc[]`)
        // is preserved as the key; the bare key is `method.doc` and the filter
        // is empty, so the validator surfaces an explicit empty-filter error.
        val content = "method.doc[]=user"
        val result = RuleProposalValidator.validate(content, project)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("empty filter") && it.contains("method.doc") })
    }

    @Test
    fun testNonKeyValueLinesAreIgnored() = runTest {
        // Stray text that is not `key=value` and not a comment should not
        // produce errors (the parser skips lines it can't parse).
        val content = """
            # header
            this is just text with no equals sign
            api.name=My API
        """.trimIndent()
        val result = RuleProposalValidator.validate(content, project)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    @Test
    fun testBracketEqualsInsideFilterDoesNotSplitEarly() = runTest {
        // `=` inside `[...]` must not be treated as the key=value separator.
        // Here the filter contains `=` and the real `=` is after the `]`.
        val content = "method.doc[#regex:a=b]=user"
        val result = RuleProposalValidator.validate(content, project)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    // -------------------------------------------------------------------------
    // Multi-line groovy value-block handling
    // -------------------------------------------------------------------------

    @Test
    fun testMultiLineGroovyBlockBodyIsSkipped() = runTest {
        // The body of a ```-delimited block is free-form script — lines inside
        // must not be parsed as key=value, even if they look like one.
        val content = """
            method.additional.header=groovy:```
            api.name=this looks like a rule but is inside a block
            return '{"name":"X"}'
            ```
            api.name=real rule
        """.trimIndent()
        val result = RuleProposalValidator.validate(content, project)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    @Test
    fun testBlockClosingLineEndingWithBackticks() = runTest {
        // A value ending with ``` (block opener on the same line as `key=`)
        // must enter block mode and skip subsequent lines until the closing ``` .
        val content = """
            method.additional.header=groovy:```
            return '{"name":"X"}'
            ```
            api.name=after block
        """.trimIndent()
        val result = RuleProposalValidator.validate(content, project)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    @Test
    fun testMultipleErrorsAreAllReported() = runTest {
        // Two distinct hard errors should both appear in the result.
        val content = """
            api.unknown_key=foo
            method.doc[~invalid]=bar
        """.trimIndent()
        val result = RuleProposalValidator.validate(content, project)
        assertFalse(result.ok)
        assertTrue(result.errors.any { it.contains("unknown rule key") })
        assertTrue(result.errors.any { it.contains("invalid filter") })
    }

    @Test
    fun testEmptyContentIsValid() = runTest {
        val result = RuleProposalValidator.validate("", project)
        assertTrue("errors: ${result.errors}", result.ok)
        assertEquals(0, result.warnings.size)
    }

    @Test
    fun testBlankLinesAreIgnored() = runTest {
        val content = "\n\n   \napi.name=My API\n\n"
        val result = RuleProposalValidator.validate(content, project)
        assertTrue("errors: ${result.errors}", result.ok)
    }

    @Test
    fun testRuleValidationOkProperty() = runTest {
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

}
