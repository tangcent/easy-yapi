package com.itangcent.easyapi.psi.type

import junit.framework.TestCase

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

    fun testIsDateTimeAsString() {
        assertTrue(SpecialTypeHandler.isDateTimeAsString("java.util.Date"))
        assertTrue(SpecialTypeHandler.isDateTimeAsString("java.sql.Date"))
        assertTrue(SpecialTypeHandler.isDateTimeAsString("java.sql.Timestamp"))
        assertTrue(SpecialTypeHandler.isDateTimeAsString("java.time.LocalDate"))
        assertTrue(SpecialTypeHandler.isDateTimeAsString("java.time.LocalDateTime"))
        assertTrue(SpecialTypeHandler.isDateTimeAsString("java.time.LocalTime"))
        assertTrue(SpecialTypeHandler.isDateTimeAsString("java.time.ZonedDateTime"))
        assertTrue(SpecialTypeHandler.isDateTimeAsString("java.time.OffsetDateTime"))
        assertTrue(SpecialTypeHandler.isDateTimeAsString("java.time.Instant"))
        assertTrue(SpecialTypeHandler.isDateTimeAsString("java.util.Calendar"))
        assertTrue(SpecialTypeHandler.isDateTimeAsString("org.joda.time.DateTime"))
        assertTrue(SpecialTypeHandler.isDateTimeAsString("org.joda.time.LocalDate"))
        assertTrue(SpecialTypeHandler.isDateTimeAsString("org.joda.time.LocalDateTime"))
        assertTrue(SpecialTypeHandler.isDateTimeAsString("org.joda.time.LocalTime"))
        
        assertFalse(SpecialTypeHandler.isDateTimeAsString("java.lang.String"))
        assertFalse(SpecialTypeHandler.isDateTimeAsString("java.lang.Integer"))
        assertFalse(SpecialTypeHandler.isDateTimeAsString(null))
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
        assertTrue(SpecialTypeHandler.isSpecialType("java.util.Date"))
        assertTrue(SpecialTypeHandler.isSpecialType("java.time.LocalDateTime"))
        assertTrue(SpecialTypeHandler.isSpecialType("java.lang.Integer"))
        
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

    fun testGetSimpleTypeNameForDateTimeTypes() {
        assertEquals("string", SpecialTypeHandler.getSimpleTypeName("java.util.Date"))
        assertEquals("string", SpecialTypeHandler.getSimpleTypeName("java.sql.Timestamp"))
        assertEquals("string", SpecialTypeHandler.getSimpleTypeName("java.time.LocalDate"))
        assertEquals("string", SpecialTypeHandler.getSimpleTypeName("java.time.LocalDateTime"))
        assertEquals("string", SpecialTypeHandler.getSimpleTypeName("java.time.LocalTime"))
        assertEquals("string", SpecialTypeHandler.getSimpleTypeName("org.joda.time.DateTime"))
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
        assertNull(SpecialTypeHandler.getSimpleTypeName(null))
    }

    fun testGetDefaultValueForSpecialTypeFileTypes() {
        assertEquals("(binary)", SpecialTypeHandler.getDefaultValueForSpecialType("org.springframework.web.multipart.MultipartFile"))
        assertEquals("(binary)", SpecialTypeHandler.getDefaultValueForSpecialType("javax.servlet.http.Part"))
        assertEquals("(binary)", SpecialTypeHandler.getDefaultValueForSpecialType("java.io.File"))
    }

    fun testGetDefaultValueForSpecialTypeDateTimeTypes() {
        assertEquals("", SpecialTypeHandler.getDefaultValueForSpecialType("java.util.Date"))
        assertEquals("", SpecialTypeHandler.getDefaultValueForSpecialType("java.sql.Timestamp"))
        assertEquals("", SpecialTypeHandler.getDefaultValueForSpecialType("java.time.LocalDate"))
        assertEquals("", SpecialTypeHandler.getDefaultValueForSpecialType("java.time.LocalDateTime"))
        assertEquals("", SpecialTypeHandler.getDefaultValueForSpecialType("java.time.LocalTime"))
        assertEquals("", SpecialTypeHandler.getDefaultValueForSpecialType("org.joda.time.DateTime"))
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
        assertNull(SpecialTypeHandler.getDefaultValueForSpecialType(null))
    }

    fun testGetAllFileTypePatterns() {
        val patterns = SpecialTypeHandler.getAllFileTypePatterns()
        assertTrue(patterns.isNotEmpty())
        assertTrue(patterns.any { it.contains("MultipartFile") })
        assertTrue(patterns.any { it.contains("java.io.File") })
        assertTrue(patterns.all { it.contains("__file__") })
    }

    fun testGetAllDateTimePatterns() {
        val patterns = SpecialTypeHandler.getAllDateTimePatterns()
        assertTrue(patterns.isNotEmpty())
        assertTrue(patterns.any { it.contains("java.util.Date") })
        assertTrue(patterns.any { it.contains("java.time.LocalDateTime") })
        assertTrue(patterns.all { it.contains("java.lang.String") })
    }

    fun testGetRecommendedConfig() {
        val config = SpecialTypeHandler.getRecommendedConfig()
        assertTrue(config.isNotEmpty())
        assertTrue(config.contains("File types"))
        assertTrue(config.contains("Date/Time types"))
        assertTrue(config.contains("MultipartFile"))
        assertTrue(config.contains("java.util.Date"))
        assertTrue(config.contains("java.time.LocalDateTime"))
    }

    fun testResolveSpecialTypeForFileTypes() {
    }

    fun testResolveSpecialTypeForDateTimeTypes() {
    }

    fun testResolveSpecialTypeForPrimitiveWrappers() {
    }

    fun testResolveSpecialTypeForNonSpecialTypes() {
    }
}
