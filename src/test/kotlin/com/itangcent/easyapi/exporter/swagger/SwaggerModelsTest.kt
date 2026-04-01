package com.itangcent.easyapi.exporter.swagger

import org.junit.Assert.*
import org.junit.Test

class SwaggerClassInfoTest {

    @Test
    fun testConstruction() {
        val info = SwaggerClassInfo(
            description = "User API",
            tags = listOf("user", "api"),
            hidden = false
        )
        
        assertEquals("User API", info.description)
        assertEquals(2, info.tags.size)
        assertFalse(info.hidden)
    }

    @Test
    fun testConstructionWithNulls() {
        val info = SwaggerClassInfo(
            description = null,
            tags = emptyList(),
            hidden = true
        )
        
        assertNull(info.description)
        assertTrue(info.tags.isEmpty())
        assertTrue(info.hidden)
    }

    @Test
    fun testCopy() {
        val info = SwaggerClassInfo(
            description = "Test",
            tags = listOf("test"),
            hidden = false
        )
        
        val copy = info.copy(description = "Updated")
        assertEquals("Updated", copy.description)
        assertEquals(1, copy.tags.size)
    }

    @Test
    fun testEquality() {
        val info1 = SwaggerClassInfo(
            description = "API",
            tags = listOf("api"),
            hidden = false
        )
        val info2 = SwaggerClassInfo(
            description = "API",
            tags = listOf("api"),
            hidden = false
        )
        
        assertEquals(info1, info2)
    }
}

class SwaggerMethodInfoTest {

    @Test
    fun testConstruction() {
        val info = SwaggerMethodInfo(
            summary = "Get user by ID",
            description = "Returns a user by their unique identifier",
            tags = listOf("user"),
            httpMethod = "GET"
        )
        
        assertEquals("Get user by ID", info.summary)
        assertEquals("Returns a user by their unique identifier", info.description)
        assertEquals(1, info.tags.size)
        assertEquals("GET", info.httpMethod)
    }

    @Test
    fun testConstructionWithNulls() {
        val info = SwaggerMethodInfo(
            summary = null,
            description = null,
            tags = emptyList(),
            httpMethod = null
        )
        
        assertNull(info.summary)
        assertNull(info.description)
        assertTrue(info.tags.isEmpty())
        assertNull(info.httpMethod)
    }

    @Test
    fun testCopy() {
        val info = SwaggerMethodInfo(
            summary = "Test",
            description = "Desc",
            tags = emptyList(),
            httpMethod = "POST"
        )
        
        val copy = info.copy(httpMethod = "PUT")
        assertEquals("PUT", copy.httpMethod)
    }

    @Test
    fun testEquality() {
        val info1 = SwaggerMethodInfo(
            summary = "Test",
            description = "Desc",
            tags = listOf("api"),
            httpMethod = "GET"
        )
        val info2 = SwaggerMethodInfo(
            summary = "Test",
            description = "Desc",
            tags = listOf("api"),
            httpMethod = "GET"
        )
        
        assertEquals(info1, info2)
    }
}

class SwaggerParamInfoTest {

    @Test
    fun testConstruction() {
        val info = SwaggerParamInfo(
            description = "User ID",
            defaultValue = "1",
            required = true,
            hidden = false
        )
        
        assertEquals("User ID", info.description)
        assertEquals("1", info.defaultValue)
        assertTrue(info.required)
        assertFalse(info.hidden)
    }

    @Test
    fun testConstructionWithNulls() {
        val info = SwaggerParamInfo(
            description = null,
            defaultValue = null,
            required = false,
            hidden = true
        )
        
        assertNull(info.description)
        assertNull(info.defaultValue)
        assertFalse(info.required)
        assertTrue(info.hidden)
    }

    @Test
    fun testCopy() {
        val info = SwaggerParamInfo(
            description = "Test",
            defaultValue = null,
            required = false,
            hidden = false
        )
        
        val copy = info.copy(required = true)
        assertTrue(copy.required)
    }

    @Test
    fun testEquality() {
        val info1 = SwaggerParamInfo(
            description = "ID",
            defaultValue = "1",
            required = true,
            hidden = false
        )
        val info2 = SwaggerParamInfo(
            description = "ID",
            defaultValue = "1",
            required = true,
            hidden = false
        )
        
        assertEquals(info1, info2)
    }
}

class SwaggerFieldInfoTest {

    @Test
    fun testConstruction() {
        val info = SwaggerFieldInfo(
            description = "User name",
            name = "username",
            required = true,
            hidden = false
        )
        
        assertEquals("User name", info.description)
        assertEquals("username", info.name)
        assertTrue(info.required)
        assertFalse(info.hidden)
    }

    @Test
    fun testConstructionWithNulls() {
        val info = SwaggerFieldInfo(
            description = null,
            name = null,
            required = false,
            hidden = true
        )
        
        assertNull(info.description)
        assertNull(info.name)
        assertFalse(info.required)
        assertTrue(info.hidden)
    }

    @Test
    fun testCopy() {
        val info = SwaggerFieldInfo(
            description = "Test",
            name = "test",
            required = false,
            hidden = false
        )
        
        val copy = info.copy(name = "updated")
        assertEquals("updated", copy.name)
    }

    @Test
    fun testEquality() {
        val info1 = SwaggerFieldInfo(
            description = "Field",
            name = "field",
            required = true,
            hidden = false
        )
        val info2 = SwaggerFieldInfo(
            description = "Field",
            name = "field",
            required = true,
            hidden = false
        )
        
        assertEquals(info1, info2)
    }
}
