package com.itangcent.easyapi.exporter.model

import org.junit.Assert.*
import org.junit.Test

class ApiParameterTest {

    @Test
    fun testDefaultTypeIsText() {
        val param = ApiParameter(name = "id")
        assertEquals(ParameterType.TEXT, param.type)
    }

    @Test
    fun testFileType() {
        val param = ApiParameter(name = "avatar", type = ParameterType.FILE)
        assertEquals(ParameterType.FILE, param.type)
    }

    @Test
    fun testApiParameterCreation() {
        val param = ApiParameter(
            name = "userId",
            type = ParameterType.TEXT,
            required = true,
            binding = ParameterBinding.Path,
            defaultValue = "default",
            description = "User ID",
            example = "123",
            enumValues = listOf("A", "B", "C")
        )
        assertEquals("userId", param.name)
        assertEquals(ParameterType.TEXT, param.type)
        assertTrue(param.required)
        assertEquals(ParameterBinding.Path, param.binding)
        assertEquals("default", param.defaultValue)
        assertEquals("User ID", param.description)
        assertEquals("123", param.example)
        assertEquals(listOf("A", "B", "C"), param.enumValues)
    }

    @Test
    fun testApiParameterWithDefaults() {
        val param = ApiParameter(name = "id")
        assertEquals("id", param.name)
        assertEquals(ParameterType.TEXT, param.type)
        assertFalse(param.required)
        assertNull(param.binding)
        assertNull(param.defaultValue)
        assertNull(param.description)
        assertNull(param.example)
        assertNull(param.enumValues)
    }

    @Test
    fun testApiParameterEquality() {
        val param1 = ApiParameter("id", ParameterType.TEXT, true, ParameterBinding.Path)
        val param2 = ApiParameter("id", ParameterType.TEXT, true, ParameterBinding.Path)
        assertEquals(param1, param2)
    }

    @Test
    fun testApiParameterInequalityOnType() {
        val text = ApiParameter("upload", ParameterType.TEXT, binding = ParameterBinding.Form)
        val file = ApiParameter("upload", ParameterType.FILE, binding = ParameterBinding.Form)
        assertNotEquals(text, file)
    }

    @Test
    fun testApiParameterCopy() {
        val original = ApiParameter("id", ParameterType.TEXT, true, ParameterBinding.Path)
        val copy = original.copy(required = false)
        assertFalse(copy.required)
        assertTrue(original.required)
        assertEquals(ParameterType.TEXT, copy.type)
    }

    @Test
    fun testApiParameterCopyChangeType() {
        val original = ApiParameter("file", ParameterType.TEXT, binding = ParameterBinding.Form)
        val updated = original.copy(type = ParameterType.FILE)
        assertEquals(ParameterType.FILE, updated.type)
        assertEquals(ParameterType.TEXT, original.type)
    }

    @Test
    fun testApiParameterComponentFunctions() {
        val param = ApiParameter(
            name = "id",
            type = ParameterType.TEXT,
            required = true,
            binding = ParameterBinding.Query,
            defaultValue = "0",
            description = "ID",
            example = "123",
            enumValues = listOf("1", "2")
        )
        val (name, type, required, binding, default, desc, example, enumValues) = param
        assertEquals("id", name)
        assertEquals(ParameterType.TEXT, type)
        assertTrue(required)
        assertEquals(ParameterBinding.Query, binding)
        assertEquals("0", default)
        assertEquals("ID", desc)
        assertEquals("123", example)
        assertEquals(listOf("1", "2"), enumValues)
    }

    @Test
    fun testParameterTypeValues() {
        val values = ParameterType.values()
        assertEquals(2, values.size)
        assertTrue(values.contains(ParameterType.TEXT))
        assertTrue(values.contains(ParameterType.FILE))
    }

    @Test
    fun testFromTypeNameText() {
        assertEquals(ParameterType.TEXT, ParameterType.fromTypeName(null))
        assertEquals(ParameterType.TEXT, ParameterType.fromTypeName(""))
        assertEquals(ParameterType.TEXT, ParameterType.fromTypeName("String"))
        assertEquals(ParameterType.TEXT, ParameterType.fromTypeName("Long"))
        assertEquals(ParameterType.TEXT, ParameterType.fromTypeName("java.lang.String"))
        assertEquals(ParameterType.TEXT, ParameterType.fromTypeName("integer"))
    }

    @Test
    fun testFromTypeNameFile() {
        assertEquals(ParameterType.FILE, ParameterType.fromTypeName("file"))
        assertEquals(ParameterType.FILE, ParameterType.fromTypeName("file[]"))
        assertEquals(ParameterType.FILE, ParameterType.fromTypeName("MultipartFile"))
        assertEquals(ParameterType.FILE, ParameterType.fromTypeName("org.springframework.web.multipart.MultipartFile"))
        assertEquals(ParameterType.FILE, ParameterType.fromTypeName("org.springframework.web.multipart.MultipartFile[]"))
        assertEquals(ParameterType.FILE, ParameterType.fromTypeName("Part"))
        assertEquals(ParameterType.FILE, ParameterType.fromTypeName("jakarta.servlet.http.Part"))
        assertEquals(ParameterType.FILE, ParameterType.fromTypeName("java.io.File"))
        assertEquals(ParameterType.FILE, ParameterType.fromTypeName("java.nio.file.Path"))
    }

    @Test
    fun testFileParamWithFormBinding() {
        val param = ApiParameter(
            name = "document",
            type = ParameterType.FILE,
            required = true,
            binding = ParameterBinding.Form
        )
        assertEquals(ParameterType.FILE, param.type)
        assertEquals(ParameterBinding.Form, param.binding)
        assertTrue(param.required)
    }

    @Test
    fun testRawTypeText() {
        assertEquals("text", ParameterType.TEXT.rawType())
    }

    @Test
    fun testRawTypeFile() {
        assertEquals("file", ParameterType.FILE.rawType())
    }
}
