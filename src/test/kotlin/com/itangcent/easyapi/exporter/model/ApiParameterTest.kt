package com.itangcent.easyapi.exporter.model

import org.junit.Assert.*
import org.junit.Test

class ApiParameterTest {

    @Test
    fun testApiParameterCreation() {
        val param = ApiParameter(
            name = "userId",
            type = "String",
            required = true,
            binding = ParameterBinding.Path,
            defaultValue = "default",
            description = "User ID",
            example = "123",
            enumValues = listOf("A", "B", "C")
        )
        assertEquals("userId", param.name)
        assertEquals("String", param.type)
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
        assertNull(param.type)
        assertFalse(param.required)
        assertNull(param.binding)
        assertNull(param.defaultValue)
        assertNull(param.description)
        assertNull(param.example)
        assertNull(param.enumValues)
    }

    @Test
    fun testApiParameterEquality() {
        val param1 = ApiParameter("id", "String", true, ParameterBinding.Path)
        val param2 = ApiParameter("id", "String", true, ParameterBinding.Path)
        assertEquals(param1, param2)
    }

    @Test
    fun testApiParameterCopy() {
        val original = ApiParameter("id", "String", true, ParameterBinding.Path)
        val copy = original.copy(required = false)
        assertFalse(copy.required)
        assertTrue(original.required)
    }

    @Test
    fun testApiParameterComponentFunctions() {
        val param = ApiParameter(
            name = "id",
            type = "Long",
            required = true,
            binding = ParameterBinding.Query,
            defaultValue = "0",
            description = "ID",
            example = "123",
            enumValues = listOf("1", "2")
        )
        val (name, type, required, binding, default, desc, example, enumValues) = param
        assertEquals("id", name)
        assertEquals("Long", type)
        assertTrue(required)
        assertEquals(ParameterBinding.Query, binding)
        assertEquals("0", default)
        assertEquals("ID", desc)
        assertEquals("123", example)
        assertEquals(listOf("1", "2"), enumValues)
    }

    @Test
    fun testApiParameterWithQueryBinding() {
        val param = ApiParameter(
            name = "search",
            type = "String",
            binding = ParameterBinding.Query
        )
        assertEquals(ParameterBinding.Query, param.binding)
    }

    @Test
    fun testApiParameterWithBodyBinding() {
        val param = ApiParameter(
            name = "body",
            type = "UserDTO",
            binding = ParameterBinding.Body
        )
        assertEquals(ParameterBinding.Body, param.binding)
    }
}
