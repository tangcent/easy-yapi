package com.itangcent.easyapi.core.psi.type

import junit.framework.TestCase

/**
 * Pure tests for the file / primitive-wrapper machinery of [SpecialTypeHandler].
 *
 * The non-basic type mapping (dates, `UUID`, `Duration`, …) is **not** covered here: it is
 * declared by the active configuration as `json.rule.convert[<fqn>]` rules, so it needs a
 * project to read them from — see `DefaultPsiClassHelperIntegrationTest`.
 */
class SpecialTypeHandlerTest : TestCase() {

    fun testIsFileType() {
        assertTrue(SpecialTypeHandler.isFileType("org.springframework.web.multipart.MultipartFile"))
        assertTrue(SpecialTypeHandler.isFileType("javax.servlet.http.Part"))
        assertTrue(SpecialTypeHandler.isFileType("jakarta.servlet.http.Part"))
        assertTrue(SpecialTypeHandler.isFileType("java.io.File"))
        assertTrue(SpecialTypeHandler.isFileType("java.nio.file.Path"))
        assertTrue(SpecialTypeHandler.isFileType("org.springframework.core.io.Resource"))
        assertTrue(SpecialTypeHandler.isFileType("org.springframework.web.multipart.commons.CommonsMultipartFile"))

        assertFalse(SpecialTypeHandler.isFileType("java.lang.String"))
        assertFalse(SpecialTypeHandler.isFileType("java.util.Date"))
        assertFalse(SpecialTypeHandler.isFileType(null))
    }

    fun testIsFileTypeCanonical() {
        assertTrue(SpecialTypeHandler.isFileTypeCanonical("org.springframework.web.multipart.MultipartFile"))
        assertTrue(SpecialTypeHandler.isFileTypeCanonical("javax.servlet.http.Part"))
        assertTrue(SpecialTypeHandler.isFileTypeCanonical("jakarta.servlet.http.Part"))
        assertTrue(SpecialTypeHandler.isFileTypeCanonical("java.io.File"))
        assertTrue(SpecialTypeHandler.isFileTypeCanonical("java.nio.file.Path"))
        assertTrue(SpecialTypeHandler.isFileTypeCanonical("org.springframework.core.io.Resource"))
        assertTrue(SpecialTypeHandler.isFileTypeCanonical("org.springframework.web.multipart.commons.CommonsMultipartFile"))
        assertTrue(SpecialTypeHandler.isFileTypeCanonical("MultipartFile"))
        assertTrue(SpecialTypeHandler.isFileTypeCanonical("Part"))
        assertTrue(SpecialTypeHandler.isFileTypeCanonical("File"))
        assertTrue(SpecialTypeHandler.isFileTypeCanonical("Path"))
        assertTrue(SpecialTypeHandler.isFileTypeCanonical("Resource"))

        assertFalse(SpecialTypeHandler.isFileTypeCanonical("java.lang.String"))
        assertFalse(SpecialTypeHandler.isFileTypeCanonical("java.util.Date"))
        assertFalse(SpecialTypeHandler.isFileTypeCanonical("String"))
        assertFalse(SpecialTypeHandler.isFileTypeCanonical("Date"))
        assertFalse(SpecialTypeHandler.isFileTypeCanonical(null))
    }

    fun testIsPrimitiveWrapper() {
        assertTrue(SpecialTypeHandler.isPrimitiveWrapper("java.lang.Boolean"))
        assertTrue(SpecialTypeHandler.isPrimitiveWrapper("java.lang.Byte"))
        assertTrue(SpecialTypeHandler.isPrimitiveWrapper("java.lang.Character"))
        assertTrue(SpecialTypeHandler.isPrimitiveWrapper("java.lang.Short"))
        assertTrue(SpecialTypeHandler.isPrimitiveWrapper("java.lang.Integer"))
        assertTrue(SpecialTypeHandler.isPrimitiveWrapper("java.lang.Long"))
        assertTrue(SpecialTypeHandler.isPrimitiveWrapper("java.lang.Float"))
        assertTrue(SpecialTypeHandler.isPrimitiveWrapper("java.lang.Double"))

        assertFalse(SpecialTypeHandler.isPrimitiveWrapper("java.lang.String"))
        assertFalse(SpecialTypeHandler.isPrimitiveWrapper("int"))
        assertFalse(SpecialTypeHandler.isPrimitiveWrapper(null))
    }

    fun testIsSpecialType() {
        assertTrue(SpecialTypeHandler.isSpecialType("org.springframework.web.multipart.MultipartFile"))
        assertTrue(SpecialTypeHandler.isSpecialType("java.lang.Integer"))

        // Non-basic types are no longer "special" by construction: whether they are scalar is
        // decided by the active configuration, not by this object.
        assertFalse(SpecialTypeHandler.isSpecialType("java.util.Date"))
        assertFalse(SpecialTypeHandler.isSpecialType("java.time.LocalDateTime"))
        assertFalse(SpecialTypeHandler.isSpecialType("java.util.UUID"))

        assertFalse(SpecialTypeHandler.isSpecialType("java.lang.String"))
        assertFalse(SpecialTypeHandler.isSpecialType("com.example.CustomClass"))
        assertFalse(SpecialTypeHandler.isSpecialType(null))
    }

    fun testGetSimpleTypeNameForFileTypes() {
        assertEquals("file", SpecialTypeHandler.getSimpleTypeName("org.springframework.web.multipart.MultipartFile"))
        assertEquals("file", SpecialTypeHandler.getSimpleTypeName("javax.servlet.http.Part"))
        assertEquals("file", SpecialTypeHandler.getSimpleTypeName("java.io.File"))
        assertEquals("file", SpecialTypeHandler.getSimpleTypeName("java.nio.file.Path"))
    }

    fun testGetSimpleTypeNameForPrimitiveWrappers() {
        assertEquals("boolean", SpecialTypeHandler.getSimpleTypeName("java.lang.Boolean"))
        assertEquals("byte", SpecialTypeHandler.getSimpleTypeName("java.lang.Byte"))
        assertEquals("char", SpecialTypeHandler.getSimpleTypeName("java.lang.Character"))
        assertEquals("short", SpecialTypeHandler.getSimpleTypeName("java.lang.Short"))
        assertEquals("int", SpecialTypeHandler.getSimpleTypeName("java.lang.Integer"))
        assertEquals("long", SpecialTypeHandler.getSimpleTypeName("java.lang.Long"))
        assertEquals("float", SpecialTypeHandler.getSimpleTypeName("java.lang.Float"))
        assertEquals("double", SpecialTypeHandler.getSimpleTypeName("java.lang.Double"))
    }

    fun testGetSimpleTypeNameForNonSpecialTypes() {
        assertNull(SpecialTypeHandler.getSimpleTypeName("java.lang.String"))
        assertNull(SpecialTypeHandler.getSimpleTypeName("com.example.CustomClass"))
        // Non-basic types are resolved through the configuration, not here.
        assertNull(SpecialTypeHandler.getSimpleTypeName("java.util.Date"))
        assertNull(SpecialTypeHandler.getSimpleTypeName("java.util.UUID"))
        assertNull(SpecialTypeHandler.getSimpleTypeName(null))
    }

    fun testGetDefaultValueForSpecialTypeFileTypes() {
        assertEquals("(binary)", SpecialTypeHandler.getDefaultValueForSpecialType("org.springframework.web.multipart.MultipartFile"))
        assertEquals("(binary)", SpecialTypeHandler.getDefaultValueForSpecialType("javax.servlet.http.Part"))
        assertEquals("(binary)", SpecialTypeHandler.getDefaultValueForSpecialType("java.io.File"))
    }

    fun testGetDefaultValueForSpecialTypePrimitiveWrappers() {
        assertEquals(false, SpecialTypeHandler.getDefaultValueForSpecialType("java.lang.Boolean"))
        assertEquals(0.toByte(), SpecialTypeHandler.getDefaultValueForSpecialType("java.lang.Byte"))
        assertEquals('\u0000', SpecialTypeHandler.getDefaultValueForSpecialType("java.lang.Character"))
        assertEquals(0.toShort(), SpecialTypeHandler.getDefaultValueForSpecialType("java.lang.Short"))
        assertEquals(0, SpecialTypeHandler.getDefaultValueForSpecialType("java.lang.Integer"))
        assertEquals(0L, SpecialTypeHandler.getDefaultValueForSpecialType("java.lang.Long"))
        assertEquals(0.0f, SpecialTypeHandler.getDefaultValueForSpecialType("java.lang.Float"))
        assertEquals(0.0, SpecialTypeHandler.getDefaultValueForSpecialType("java.lang.Double"))
    }

    fun testGetDefaultValueForSpecialTypeNonSpecialTypes() {
        assertNull(SpecialTypeHandler.getDefaultValueForSpecialType("java.lang.String"))
        assertNull(SpecialTypeHandler.getDefaultValueForSpecialType("com.example.CustomClass"))
        assertNull(SpecialTypeHandler.getDefaultValueForSpecialType("java.util.Date"))
        assertNull(SpecialTypeHandler.getDefaultValueForSpecialType(null))
    }

    fun testGetAllFileTypePatterns() {
        val patterns = SpecialTypeHandler.getAllFileTypePatterns()
        assertTrue(patterns.isNotEmpty())
        assertTrue(patterns.any { it.contains("MultipartFile") })
        assertTrue(patterns.any { it.contains("java.io.File") })
        assertTrue(patterns.all { it.contains("__file__") })
    }

    fun testSingleTypeName() {
        assertEquals("String", SpecialTypeHandler.singleTypeName("String"))
        assertEquals("MultipartFile", SpecialTypeHandler.singleTypeName("MultipartFile[]"))
        assertEquals("org.springframework.web.multipart.MultipartFile", SpecialTypeHandler.singleTypeName("org.springframework.web.multipart.MultipartFile[]"))
        assertEquals("file", SpecialTypeHandler.singleTypeName("file[]"))
        assertEquals("file", SpecialTypeHandler.singleTypeName("file"))
    }

    fun testIsFileTypeName() {
        // json type strings
        assertTrue(SpecialTypeHandler.isFileTypeName("file"))
        assertTrue(SpecialTypeHandler.isFileTypeName("file[]"))
        // internal file marker (produced by SpecialTypeHandler.resolveSpecialType)
        assertTrue(SpecialTypeHandler.isFileTypeName("__file__"))
        // simple class names
        assertTrue(SpecialTypeHandler.isFileTypeName("MultipartFile"))
        assertTrue(SpecialTypeHandler.isFileTypeName("MultipartFile[]"))
        assertTrue(SpecialTypeHandler.isFileTypeName("Part"))
        // fully-qualified class names
        assertTrue(SpecialTypeHandler.isFileTypeName("org.springframework.web.multipart.MultipartFile"))
        assertTrue(SpecialTypeHandler.isFileTypeName("org.springframework.web.multipart.MultipartFile[]"))
        assertTrue(SpecialTypeHandler.isFileTypeName("javax.servlet.http.Part"))
        assertTrue(SpecialTypeHandler.isFileTypeName("jakarta.servlet.http.Part"))
        assertTrue(SpecialTypeHandler.isFileTypeName("java.io.File"))
        assertTrue(SpecialTypeHandler.isFileTypeName("java.io.File[]"))
        assertTrue(SpecialTypeHandler.isFileTypeName("java.nio.file.Path"))
        // non-file types
        assertFalse(SpecialTypeHandler.isFileTypeName(null))
        assertFalse(SpecialTypeHandler.isFileTypeName(""))
        assertFalse(SpecialTypeHandler.isFileTypeName("String"))
        assertFalse(SpecialTypeHandler.isFileTypeName("java.lang.String"))
        assertFalse(SpecialTypeHandler.isFileTypeName("Long"))
    }

    fun testMentionsFileType() {
        // A bare file type — same answers as isFileTypeName.
        assertTrue(SpecialTypeHandler.mentionsFileType("file"))
        assertTrue(SpecialTypeHandler.mentionsFileType("file[]"))
        assertTrue(SpecialTypeHandler.mentionsFileType("__file__"))
        assertTrue(SpecialTypeHandler.mentionsFileType("MultipartFile"))
        assertTrue(SpecialTypeHandler.mentionsFileType("java.io.File"))

        // Files nested in a container — the reason this variant exists.
        assertTrue(SpecialTypeHandler.mentionsFileType("MultipartFile[]"))
        assertTrue(SpecialTypeHandler.mentionsFileType("java.util.List<org.springframework.web.multipart.MultipartFile>"))
        assertTrue(SpecialTypeHandler.mentionsFileType("List<MultipartFile>"))
        assertTrue(SpecialTypeHandler.mentionsFileType("Map<String, Part>"))

        // Case-insensitive: `JsonType.fromJavaType` hands this predicate a lowercased
        // spelling, so the lowercased forms must match the same file types.
        assertTrue(SpecialTypeHandler.mentionsFileType("list<multipartfile>"))
        assertTrue(SpecialTypeHandler.mentionsFileType("multipartfile"))
        assertTrue(SpecialTypeHandler.mentionsFileType("javax.servlet.http.part"))
        assertFalse(SpecialTypeHandler.mentionsFileType("department"))

        // Names that merely *contain* a file type name are not files — the bug the old
        // `contains("Part")` check had (`Department` -> file upload).
        assertFalse(SpecialTypeHandler.mentionsFileType("Department"))
        assertFalse(SpecialTypeHandler.mentionsFileType("com.acme.Department"))
        assertFalse(SpecialTypeHandler.mentionsFileType("java.io.FileInputStream"))
        assertFalse(SpecialTypeHandler.mentionsFileType("PartialResult"))
        assertFalse(SpecialTypeHandler.mentionsFileType("java.nio.file.Paths"))

        // Non-files / empty.
        assertFalse(SpecialTypeHandler.mentionsFileType(null))
        assertFalse(SpecialTypeHandler.mentionsFileType(""))
        assertFalse(SpecialTypeHandler.mentionsFileType("String"))
        assertFalse(SpecialTypeHandler.mentionsFileType("java.util.List<String>"))
    }
}
