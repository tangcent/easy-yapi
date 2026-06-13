package com.itangcent.easyapi.exporter.feign

import com.intellij.psi.PsiElement
import com.itangcent.easyapi.psi.helper.AnnotationHelper
import org.junit.Assert.*
import org.junit.Test

/**
 * Tests for NativeFeignAnnotationParser pure logic methods.
 * PSI-dependent methods are tested via integration tests.
 */
class NativeFeignAnnotationParserLogicTest {

    private val parser = NativeFeignAnnotationParser(
        annotationHelper = object : AnnotationHelper {
            override suspend fun hasAnn(element: PsiElement, annFqn: String): Boolean = false
            override suspend fun findAnnMap(element: PsiElement, annFqn: String): Map<String, Any?>? = null
            override suspend fun findAnnMaps(element: PsiElement, annFqn: String): List<Map<String, Any?>>? = null
            override suspend fun findAttr(element: PsiElement, annFqn: String, attr: String): Any? = null
            override suspend fun findAttrAsString(element: PsiElement, annFqn: String, attr: String): String? = null
        }
    )

    // ==================== extractTemplateVariables ====================

    @Test
    fun `extractTemplateVariables finds single variable`() {
        val result = parser.extractTemplateVariables("/users/{id}")
        assertEquals(listOf("id"), result)
    }

    @Test
    fun `extractTemplateVariables finds multiple variables`() {
        val result = parser.extractTemplateVariables("/users/{userId}/posts/{postId}")
        assertEquals(listOf("userId", "postId"), result)
    }

    @Test
    fun `extractTemplateVariables returns empty for no variables`() {
        val result = parser.extractTemplateVariables("/users/list")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `extractTemplateVariables deduplicates variables`() {
        val result = parser.extractTemplateVariables("/users/{id}/posts/{id}")
        assertEquals(listOf("id"), result)
    }

    @Test
    fun `extractTemplateVariables handles empty string`() {
        val result = parser.extractTemplateVariables("")
        assertTrue(result.isEmpty())
    }

    @Test
    fun `extractTemplateVariables handles complex template`() {
        val result = parser.extractTemplateVariables("Authorization: Bearer {token}")
        assertEquals(listOf("token"), result)
    }

    @Test
    fun `extractTemplateVariables handles adjacent variables`() {
        val result = parser.extractTemplateVariables("{a}{b}")
        assertEquals(listOf("a", "b"), result)
    }

    // ==================== RequestLine data class ====================

    @Test
    fun `RequestLine data class`() {
        val requestLine = RequestLine(
            method = com.itangcent.easyapi.exporter.model.HttpMethod.GET,
            path = "/api/users"
        )
        assertEquals(com.itangcent.easyapi.exporter.model.HttpMethod.GET, requestLine.method)
        assertEquals("/api/users", requestLine.path)
    }

    @Test
    fun `RequestLine copy`() {
        val requestLine = RequestLine(
            method = com.itangcent.easyapi.exporter.model.HttpMethod.GET,
            path = "/api/users"
        )
        val copy = requestLine.copy(method = com.itangcent.easyapi.exporter.model.HttpMethod.POST)
        assertEquals(com.itangcent.easyapi.exporter.model.HttpMethod.POST, copy.method)
        assertEquals("/api/users", copy.path)
    }
}
