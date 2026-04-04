package com.itangcent.easyapi.exporter.model

import org.junit.Assert.*
import org.junit.Test

class ParameterBindingTest {

    @Test
    fun testQueryBinding() {
        val binding = ParameterBinding.Query
        assertSame(ParameterBinding.Query, binding)
    }

    @Test
    fun testPathBinding() {
        val binding = ParameterBinding.Path
        assertSame(ParameterBinding.Path, binding)
    }

    @Test
    fun testHeaderBinding() {
        val binding = ParameterBinding.Header
        assertSame(ParameterBinding.Header, binding)
    }

    @Test
    fun testCookieBinding() {
        val binding = ParameterBinding.Cookie
        assertSame(ParameterBinding.Cookie, binding)
    }

    @Test
    fun testBodyBinding() {
        val binding = ParameterBinding.Body
        assertSame(ParameterBinding.Body, binding)
    }

    @Test
    fun testFormBinding() {
        val binding = ParameterBinding.Form
        assertSame(ParameterBinding.Form, binding)
    }

    @Test
    fun testIgnoredBinding() {
        val binding = ParameterBinding.Ignored
        assertSame(ParameterBinding.Ignored, binding)
    }

    @Test
    fun testWhenExpression() {
        val bindings = listOf(
            ParameterBinding.Query,
            ParameterBinding.Path,
            ParameterBinding.Header,
            ParameterBinding.Cookie,
            ParameterBinding.Body,
            ParameterBinding.Form,
            ParameterBinding.Ignored
        )

        val names = bindings.map { binding ->
            when (binding) {
                ParameterBinding.Query -> "query"
                ParameterBinding.Path -> "path"
                ParameterBinding.Header -> "header"
                ParameterBinding.Cookie -> "cookie"
                ParameterBinding.Body -> "body"
                ParameterBinding.Form -> "form"
                ParameterBinding.Ignored -> "ignored"
            }
        }

        assertEquals(listOf("query", "path", "header", "cookie", "body", "form", "ignored"), names)
    }

    @Test
    fun testParameterWithBinding() {
        val param = ApiParameter(
            name = "id",
            type = ParameterType.TEXT,
            binding = ParameterBinding.Path
        )
        assertEquals(ParameterBinding.Path, param.binding)
    }

    @Test
    fun testParameterWithQueryBinding() {
        val param = ApiParameter(
            name = "search",
            type = ParameterType.TEXT,
            binding = ParameterBinding.Query
        )
        assertEquals(ParameterBinding.Query, param.binding)
    }
}
