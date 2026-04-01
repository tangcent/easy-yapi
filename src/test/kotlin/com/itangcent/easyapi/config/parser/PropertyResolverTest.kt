package com.itangcent.easyapi.config.parser

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test

class PropertyResolverTest {

    private lateinit var resolver: PropertyResolver
    private var configValues: Map<String, List<String>> = emptyMap()

    @Before
    fun setUp() {
        resolver = PropertyResolver { key -> configValues[key] ?: emptyList() }
    }

    @Test
    fun testResolveSimpleValue() {
        configValues = emptyMap()
        assertEquals("simple", resolver.resolve("simple"))
    }

    @Test
    fun testResolveSinglePlaceholder() {
        configValues = mapOf("name" to listOf("test"))
        assertEquals("test", resolver.resolve("\${name}"))
    }

    @Test
    fun testResolveMultiplePlaceholders() {
        configValues = mapOf(
            "host" to listOf("localhost"),
            "port" to listOf("8080")
        )
        assertEquals("localhost:8080", resolver.resolve("\${host}:\${port}"))
    }

    @Test
    fun testResolveWithPrefixAndSuffix() {
        configValues = mapOf("name" to listOf("test"))
        assertEquals("prefix-test-suffix", resolver.resolve("prefix-\${name}-suffix"))
    }

    @Test
    fun testResolveNestedPlaceholder() {
        configValues = mapOf(
            "base" to listOf("/api"),
            "endpoint" to listOf("\${base}/users")
        )
        assertEquals("/api/users", resolver.resolve("\${endpoint}"))
    }

    @Test
    fun testResolveUnresolvedPlaceholder() {
        configValues = emptyMap()
        assertEquals("\${unknown}", resolver.resolve("\${unknown}"))
    }

    @Test
    fun testResolveUnresolvedPlaceholderWithIgnore() {
        configValues = emptyMap()
        assertEquals("", resolver.resolve("\${unknown}", ignoreUnresolved = true))
    }

    @Test
    fun testResolveMultiFirst() {
        configValues = mapOf("key" to listOf("first", "second", "third"))
        assertEquals("first", resolver.resolve("\${key}", ResolveMultiMode.FIRST))
    }

    @Test
    fun testResolveMultiLast() {
        configValues = mapOf("key" to listOf("first", "second", "third"))
        assertEquals("third", resolver.resolve("\${key}", ResolveMultiMode.LAST))
    }

    @Test
    fun testResolveMultiLongest() {
        configValues = mapOf("key" to listOf("a", "abc", "ab"))
        assertEquals("abc", resolver.resolve("\${key}", ResolveMultiMode.LONGEST))
    }

    @Test
    fun testResolveMultiShortest() {
        configValues = mapOf("key" to listOf("abc", "a", "ab"))
        assertEquals("a", resolver.resolve("\${key}", ResolveMultiMode.SHORTEST))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testResolveMultiError() {
        configValues = mapOf("key" to listOf("first", "second"))
        resolver.resolve("\${key}", ResolveMultiMode.ERROR)
    }

    @Test
    fun testResolveEmptyValue() {
        configValues = mapOf("key" to listOf(""))
        assertEquals("", resolver.resolve("\${key}"))
    }

    @Test
    fun testResolveNonExistentKey() {
        configValues = emptyMap()
        assertNull(PropertyResolver.pickValue(emptyList(), ResolveMultiMode.FIRST))
    }

    @Test
    fun testResolveSingleValue() {
        configValues = mapOf("key" to listOf("only"))
        assertEquals("only", resolver.resolve("\${key}"))
    }

    @Test
    fun testNoPlaceholders() {
        configValues = mapOf("key" to listOf("value"))
        assertEquals("plain text", resolver.resolve("plain text"))
    }

    @Test(expected = IllegalStateException::class)
    fun testCircularReference() {
        configValues = mapOf(
            "a" to listOf("\${b}"),
            "b" to listOf("\${a}")
        )
        resolver.resolve("\${a}")
    }

    @Test
    fun testPickValueFirst() {
        assertEquals("first", PropertyResolver.pickValue(listOf("first", "second"), ResolveMultiMode.FIRST))
    }

    @Test
    fun testPickValueLast() {
        assertEquals("second", PropertyResolver.pickValue(listOf("first", "second"), ResolveMultiMode.LAST))
    }

    @Test
    fun testPickValueLongest() {
        assertEquals("longest", PropertyResolver.pickValue(listOf("a", "longest", "ab"), ResolveMultiMode.LONGEST))
    }

    @Test
    fun testPickValueShortest() {
        assertEquals("a", PropertyResolver.pickValue(listOf("abc", "a", "ab"), ResolveMultiMode.SHORTEST))
    }

    @Test(expected = IllegalArgumentException::class)
    fun testPickValueError() {
        PropertyResolver.pickValue(listOf("a", "b"), ResolveMultiMode.ERROR)
    }

    @Test
    fun testPickValueEmpty() {
        assertNull(PropertyResolver.pickValue(emptyList(), ResolveMultiMode.FIRST))
    }

    @Test
    fun testPickValueSingle() {
        assertEquals("only", PropertyResolver.pickValue(listOf("only"), ResolveMultiMode.FIRST))
    }
}
