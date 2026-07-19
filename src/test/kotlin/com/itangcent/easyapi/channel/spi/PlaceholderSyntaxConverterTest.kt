package com.itangcent.easyapi.channel.spi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * Plain-JUnit tests for [PlaceholderSyntaxConverter] (no IntelliJ fixture required).
 *
 * The `isResolvable` lambda is supplied by the caller; in tests a simple lambda
 * treats `resolvedVar` as resolvable and everything else as unresolved.
 */
class PlaceholderSyntaxConverterTest {

    /** Treats only `resolvedVar` as config-resolvable; everything else is unresolved. */
    private val resolvedVarOnly: (String) -> Boolean = { name -> name == "resolvedVar" }

    @Test
    fun `unresolved dollar-brace placeholder is rewritten to double-brace for POSTMAN`() {
        val result = PlaceholderSyntaxConverter.convert(
            value = "Bearer \${order-service-token}",
            target = PlaceholderTargetSyntax.POSTMAN,
            isResolvable = resolvedVarOnly,
        )
        assertEquals("Bearer {{order-service-token}}", result)
    }

    @Test
    fun `unresolved dollar-brace placeholder is rewritten to hoppscotch syntax for HOPPSCOTCH`() {
        val result = PlaceholderSyntaxConverter.convert(
            value = "Bearer \${order-service-token}",
            target = PlaceholderTargetSyntax.HOPPSCOTCH,
            isResolvable = resolvedVarOnly,
        )
        assertEquals("Bearer <<order-service-token>>", result)
    }

    @Test
    fun `resolved placeholder is left untouched for POSTMAN`() {
        val result = PlaceholderSyntaxConverter.convert(
            value = "Host: \${resolvedVar}",
            target = PlaceholderTargetSyntax.POSTMAN,
            isResolvable = resolvedVarOnly,
        )
        assertEquals("Host: \${resolvedVar}", result)
    }

    @Test
    fun `resolved placeholder is left untouched for HOPPSCOTCH`() {
        val result = PlaceholderSyntaxConverter.convert(
            value = "Host: \${resolvedVar}",
            target = PlaceholderTargetSyntax.HOPPSCOTCH,
            isResolvable = resolvedVarOnly,
        )
        assertEquals("Host: \${resolvedVar}", result)
    }

    @Test
    fun `regex-capture reference numeric placeholder is left untouched`() {
        val result = PlaceholderSyntaxConverter.convert(
            value = "Basic \${1}",
            target = PlaceholderTargetSyntax.POSTMAN,
            isResolvable = resolvedVarOnly,
        )
        assertEquals("Basic \${1}", result)
    }

    @Test
    fun `multi-digit regex-capture reference is left untouched`() {
        val result = PlaceholderSyntaxConverter.convert(
            value = "Basic \${12}",
            target = PlaceholderTargetSyntax.HOPPSCOTCH,
            isResolvable = resolvedVarOnly,
        )
        assertEquals("Basic \${12}", result)
    }

    @Test
    fun `value with no dollar-brace returns unchanged byte-for-byte (fast path)`() {
        val value = "Bearer abc123 plain text no placeholders here"
        val result = PlaceholderSyntaxConverter.convert(
            value = value,
            target = PlaceholderTargetSyntax.POSTMAN,
            isResolvable = resolvedVarOnly,
        )
        // Fast path: the same instance is returned (no allocation).
        assertSame(value, result)
        assertEquals(value, result)
    }

    @Test
    fun `mixed resolved and unresolved placeholders in one value`() {
        val result = PlaceholderSyntaxConverter.convert(
            value = "\${resolvedVar} Bearer \${unresolvedVar}",
            target = PlaceholderTargetSyntax.POSTMAN,
            isResolvable = resolvedVarOnly,
        )
        assertEquals("\${resolvedVar} Bearer {{unresolvedVar}}", result)
    }

    @Test
    fun `already-present double-brace placeholder is left untouched for POSTMAN`() {
        val result = PlaceholderSyntaxConverter.convert(
            value = "Bearer {{existing-token}}",
            target = PlaceholderTargetSyntax.POSTMAN,
            isResolvable = resolvedVarOnly,
        )
        assertEquals("Bearer {{existing-token}}", result)
    }

    @Test
    fun `already-present hoppscotch placeholder is left untouched for HOPPSCOTCH`() {
        val result = PlaceholderSyntaxConverter.convert(
            value = "Bearer <<existing-token>>",
            target = PlaceholderTargetSyntax.HOPPSCOTCH,
            isResolvable = resolvedVarOnly,
        )
        assertEquals("Bearer <<existing-token>>", result)
    }

    @Test
    fun `malformed dollar-brace with no closing brace is left as-is`() {
        val result = PlaceholderSyntaxConverter.convert(
            value = "Bearer \${unterminated",
            target = PlaceholderTargetSyntax.POSTMAN,
            isResolvable = resolvedVarOnly,
        )
        assertEquals("Bearer \${unterminated", result)
    }

    @Test
    fun `UTF-8 multi-byte header value preserves bytes outside the placeholder`() {
        val value = "应用令牌: \${order-service-token} 中文测试"
        val result = PlaceholderSyntaxConverter.convert(
            value = value,
            target = PlaceholderTargetSyntax.POSTMAN,
            isResolvable = resolvedVarOnly,
        )
        assertEquals("应用令牌: {{order-service-token}} 中文测试", result)
    }

    @Test
    fun `JSON-escaped quotes are preserved during conversion`() {
        val value = "{\"auth\": \"Bearer \${unresolvedVar}\"}"
        val result = PlaceholderSyntaxConverter.convert(
            value = value,
            target = PlaceholderTargetSyntax.POSTMAN,
            isResolvable = resolvedVarOnly,
        )
        assertEquals("{\"auth\": \"Bearer {{unresolvedVar}}\"}", result)
    }

    @Test
    fun `multiple unresolved placeholders in one value are all rewritten`() {
        val result = PlaceholderSyntaxConverter.convert(
            value = "\${a}\${b}\${c}",
            target = PlaceholderTargetSyntax.POSTMAN,
            isResolvable = resolvedVarOnly,
        )
        assertEquals("{{a}}{{b}}{{c}}", result)
    }

    @Test
    fun `empty dollar-brace pair is not matched by the regex and left as-is`() {
        // DOLLAR_BRACE_PATTERN requires [^}]+ (one or more non-} chars), so `\${}`
        // does not match and is left untouched.
        val result = PlaceholderSyntaxConverter.convert(
            value = "Bearer \${}",
            target = PlaceholderTargetSyntax.HOPPSCOTCH,
            isResolvable = resolvedVarOnly,
        )
        assertEquals("Bearer \${}", result)
    }
}
