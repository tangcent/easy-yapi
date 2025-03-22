package com.itangcent.idea.plugin.api.export.markdown

import com.itangcent.common.model.Header
import com.itangcent.common.model.Request
import com.itangcent.common.model.Response
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

/**
 * Tests for [TemplateExpression]
 */
class TemplateExpressionsTest {

    @Test
    fun testPlainText() {
        val expression = PlainText("Hello World")
        val request = Request()
        val result = StringBuilder()
        expression.eval(request, result)
        assertEquals("Hello World", result.toString())
    }

    @Test
    fun testRequestProperty() {
        val request = Request().apply {
            name = "Test API"
            method = "GET"
        }
        val result = StringBuilder()

        // Test existing property
        RequestProperty("name").eval(request, result)
        assertEquals("Test API", result.toString())

        // Test non-existing property
        result.clear()
        RequestProperty("nonExisting").eval(request, result)
        assertEquals("", result.toString())
    }

    @Test
    fun testResponseProperty() {
        val request = Request().apply {
            response = mutableListOf(Response().apply {
                code = 200
                body = "Success"
            })
        }
        val result = StringBuilder()

        // Test existing property
        ResponseProperty("code").eval(request, result)
        assertEquals("200", result.toString())

        // Test non-existing property
        result.clear()
        ResponseProperty("nonExisting").eval(request, result)
        assertEquals("", result.toString())
    }

    @Test
    fun testMDTableExpression() {
        val rows = listOf(
            Header("content-type", "application/json"),
            Header("Authorization", "Bearer token123")
        )
        val titles = listOf(
            "name" to "Header Name",
            "value" to "Header Value"
        )
        val expression = MDTableExpression(rows, titles)
        val request = Request()
        val result = StringBuilder()

        expression.eval(request, result)
        val expected = """
            | Header Name | Header Value |
            |------|------|
            | content-type | application/json |
            | Authorization | Bearer token123 |
        """.trimIndent()
        assertEquals(expected, result.toString().trim())
    }

    @Test
    fun testConditionalExpression() {
        val request = Request().apply {
            method = "POST"
        }
        val result = StringBuilder()

        // Test true condition
        val condition = SinglePropertyCondition("method")
        val expression = ConditionalExpression(condition, PlainText("POST Request"))
        expression.eval(request, result)
        assertEquals("POST Request", result.toString())

        // Test false condition
        result.clear()
        val falseCondition = SinglePropertyCondition("nonExisting")
        val falseExpression = ConditionalExpression(falseCondition, PlainText("Should not appear"))
        falseExpression.eval(request, result)
        assertEquals("", result.toString())
    }

    @Test
    fun testParseTemplate() {
        val template = "Hello \${name}, your method is \${method}"
        val request = Request().apply {
            name = "John"
            method = "GET"
        }
        val result = StringBuilder()

        val expression = TemplateExpression.parseTemplate(template)
        expression.eval(request, result)
        assertEquals("Hello John, your method is GET", result.toString())
    }

    @Test
    fun testParseTemplateWithCondition() {
        val template = "\${if method}This is a \${method} request\${end}"

        val request = Request().apply {
            method = "POST"
        }
        val result = StringBuilder()

        val expression = TemplateExpression.parseTemplate(template)
        expression.eval(request, result)
        assertEquals("This is a POST request", result.toString().trim())
    }

    @Test
    fun testMDObjectExpression() {
        val value = mapOf(
            "name" to "Test Object",
            "age" to 42,
            "@comment" to mapOf<String, String>(
                "name" to "This is a name",
                "age" to "This is an age"
            )
        )
        val titles = mapOf(
            "name" to "Name",
            "type" to "Type"
        )
        val expression = MDObjectExpression(value, titles)
        val request = Request()
        val result = StringBuilder()

        expression.eval(request, result)
        val expected = """
| Name | Type |
|------|------|
| name | string |
| age | integer |
        """.trimIndent()
        assertEquals(expected, result.toString().trim())
    }

    @Test
    fun testMDHelperTable() {
        val request = Request()
        val result = StringBuilder()

        // Test table helper
        val tableExpression = MD.table(
            listOf(
                Header("content-type", "application/json"),
                Header("Authorization", "Bearer token123"),

                )
        ).title(
            mapOf(
                "name" to "Header Name",
                "value" to "Header Value"
            )
        )
        tableExpression.eval(request, result)
        val expectedTable = """
| Header Name | Header Value |
|------|------|
| content-type | application/json |
| Authorization | Bearer token123 |
        """.trimIndent()
        assertEquals(expectedTable, result.toString().trim())
    }

    @Test
    fun testMDHelperJson() {
        val request = Request()
        val result = StringBuilder()
        val value = mapOf(
            "name" to "Test Object",
            "age" to 42,
            "@comment" to mapOf<String, String>(
                "name" to "This is a name",
                "age" to "This is an age"
            )
        )

        // Test json helper
        val tableExpression = MD.json(value)
        tableExpression.eval(request, result)
        val expectedTable = """
{
  "name": "Test Object",
  "age": 42
}
        """.trimIndent()
        assertEquals(expectedTable, result.toString().trim())
    }

    @Test
    fun testPropertyAccessPatterns() {
        val request = Request().apply {
            name = "Test API"
            method = "GET"
            response = mutableListOf(Response().apply {
                code = 200
                body = "Success"
            })
        }
        val result = StringBuilder()

        // Test simple property access ($xx)
        val simpleExpression = TemplateExpression.parseTemplate("\$name")
        result.clear()
        simpleExpression.eval(request, result)
        assertEquals("Test API", result.toString())

        // Test nested property access ($xx.xx)
        val nestedExpression = TemplateExpression.parseTemplate("\$response.code")
        result.clear()
        nestedExpression.eval(request, result)
        assertEquals("200", result.toString())

        // Test special response property access ($response.xx)
        val responseExpression = TemplateExpression.parseTemplate("\$response.body")
        result.clear()
        responseExpression.eval(request, result)
        assertEquals("Success", result.toString())
    }
} 