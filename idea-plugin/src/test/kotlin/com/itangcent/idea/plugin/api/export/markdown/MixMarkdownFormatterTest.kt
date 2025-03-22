package com.itangcent.idea.plugin.api.export.markdown

import com.itangcent.common.model.Doc
import com.itangcent.common.model.MethodDoc
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.mock.AdvancedContextTest
import org.junit.jupiter.api.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import kotlin.test.assertEquals

/**
 * Test case of [MixMarkdownFormatter]
 */
internal class MixMarkdownFormatterTest : AdvancedContextTest() {

    private val apiMarkdownFormatter = mock<MarkdownFormatter>()
    private val methodDocMarkdownFormatter = mock<MarkdownFormatter>()
    private val mixMarkdownFormatter = MixMarkdownFormatter(apiMarkdownFormatter, methodDocMarkdownFormatter)

    @Test
    fun testParseSingleTypeMethodDocs() {
        // Given
        val methodDocs = listOf(MethodDoc(), MethodDoc())
        whenever(methodDocMarkdownFormatter.parseDocs(methodDocs)).thenReturn("method docs markdown")

        // When
        val result = mixMarkdownFormatter.parseDocs(methodDocs)

        // Then
        assertEquals("method docs markdown", result)
        verify(methodDocMarkdownFormatter).parseDocs(methodDocs)
    }

    @Test
    fun testParseSingleTypeApiDocs() {
        // Given
        val apiDocs = listOf(TestDoc(), TestDoc())
        whenever(apiMarkdownFormatter.parseDocs(apiDocs)).thenReturn("api docs markdown")

        // When
        val result = mixMarkdownFormatter.parseDocs(apiDocs)

        // Then
        assertEquals("api docs markdown", result)
        verify(apiMarkdownFormatter).parseDocs(apiDocs)
    }

    @Test
    fun testParseMixedTypeDocs() {
        // Given
        val methodDocs = listOf(MethodDoc(), MethodDoc())
        val apiDocs = listOf(TestDoc(), TestDoc())
        whenever(methodDocMarkdownFormatter.parseDocs(methodDocs)).thenReturn("method docs markdown")
        whenever(apiMarkdownFormatter.parseDocs(apiDocs)).thenReturn("api docs markdown")

        // When
        val result = mixMarkdownFormatter.parseDocs(methodDocs + apiDocs)

        // Then
        assertEquals("method docs markdownapi docs markdown", result)
        verify(methodDocMarkdownFormatter).parseDocs(methodDocs)
        verify(apiMarkdownFormatter).parseDocs(apiDocs)
    }

    private class TestDoc : Doc()
} 