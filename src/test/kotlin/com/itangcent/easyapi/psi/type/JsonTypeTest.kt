package com.itangcent.easyapi.psi.type

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonTypeTest {
    
    @Test
    fun testConstants() {
        assertEquals("string", JsonType.STRING)
        assertEquals("short", JsonType.SHORT)
        assertEquals("int", JsonType.INT)
        assertEquals("long", JsonType.LONG)
        assertEquals("float", JsonType.FLOAT)
        assertEquals("double", JsonType.DOUBLE)
        assertEquals("boolean", JsonType.BOOLEAN)
        assertEquals("array", JsonType.ARRAY)
        assertEquals("object", JsonType.OBJECT)
        assertEquals("file", JsonType.FILE)
        assertEquals("date", JsonType.DATE)
        assertEquals("datetime", JsonType.DATETIME)
    }
    
    @Test
    fun testAllTypes() {
        assertTrue(JsonType.ALL_TYPES.contains("string"))
        assertTrue(JsonType.ALL_TYPES.contains("int"))
        assertTrue(JsonType.ALL_TYPES.contains("long"))
        assertTrue(JsonType.ALL_TYPES.contains("array"))
        assertTrue(JsonType.ALL_TYPES.contains("object"))
    }
    
    @Test
    fun testNumberTypes() {
        assertTrue(JsonType.isNumber("short"))
        assertTrue(JsonType.isNumber("int"))
        assertTrue(JsonType.isNumber("long"))
        assertTrue(JsonType.isNumber("float"))
        assertTrue(JsonType.isNumber("double"))
        assertFalse(JsonType.isNumber("string"))
        assertFalse(JsonType.isNumber("array"))
        assertFalse(JsonType.isNumber(null))
    }
    
    @Test
    fun testIsPrimitive() {
        assertTrue(JsonType.isPrimitive("string"))
        assertTrue(JsonType.isPrimitive("int"))
        assertTrue(JsonType.isPrimitive("boolean"))
        assertFalse(JsonType.isPrimitive("array"))
        assertFalse(JsonType.isPrimitive("object"))
        assertFalse(JsonType.isPrimitive(null))
    }
    
    @Test
    fun testIsValid() {
        assertTrue(JsonType.isValid("string"))
        assertTrue(JsonType.isValid("int"))
        assertTrue(JsonType.isValid("array"))
        assertTrue(JsonType.isValid("object"))
        assertFalse(JsonType.isValid("integer"))
        assertFalse(JsonType.isValid("list"))
        assertFalse(JsonType.isValid(null))
    }
    
    @Test
    fun testFromJavaType_string() {
        assertEquals(JsonType.STRING, JsonType.fromJavaType("java.lang.String"))
        assertEquals(JsonType.STRING, JsonType.fromJavaType("String"))
        assertEquals(JsonType.STRING, JsonType.fromJavaType("char"))
        assertEquals(JsonType.STRING, JsonType.fromJavaType("java.lang.Character"))
    }
    
    @Test
    fun testFromJavaType_boolean() {
        assertEquals(JsonType.BOOLEAN, JsonType.fromJavaType("boolean"))
        assertEquals(JsonType.BOOLEAN, JsonType.fromJavaType("java.lang.Boolean"))
    }
    
    @Test
    fun testFromJavaType_integerTypes() {
        assertEquals(JsonType.INT, JsonType.fromJavaType("int"))
        assertEquals(JsonType.INT, JsonType.fromJavaType("java.lang.Integer"))
        assertEquals(JsonType.SHORT, JsonType.fromJavaType("short"))
        assertEquals(JsonType.SHORT, JsonType.fromJavaType("java.lang.Short"))
        assertEquals(JsonType.LONG, JsonType.fromJavaType("long"))
        assertEquals(JsonType.LONG, JsonType.fromJavaType("java.lang.Long"))
        assertEquals(JsonType.LONG, JsonType.fromJavaType("java.math.BigInteger"))
        assertEquals(JsonType.INT, JsonType.fromJavaType("byte"))
    }
    
    @Test
    fun testFromJavaType_floatTypes() {
        assertEquals(JsonType.FLOAT, JsonType.fromJavaType("float"))
        assertEquals(JsonType.FLOAT, JsonType.fromJavaType("java.lang.Float"))
        assertEquals(JsonType.DOUBLE, JsonType.fromJavaType("double"))
        assertEquals(JsonType.DOUBLE, JsonType.fromJavaType("java.lang.Double"))
        assertEquals(JsonType.DOUBLE, JsonType.fromJavaType("java.math.BigDecimal"))
    }
    
    @Test
    fun testFromJavaType_dateTypes() {
        assertEquals(JsonType.DATE, JsonType.fromJavaType("java.util.Date"))
        assertEquals(JsonType.DATE, JsonType.fromJavaType("java.time.LocalDate"))
        assertEquals(JsonType.DATETIME, JsonType.fromJavaType("java.time.LocalDateTime"))
        assertEquals(JsonType.DATETIME, JsonType.fromJavaType("java.sql.Timestamp"))
    }
    
    @Test
    fun testFromJavaType_collectionTypes() {
        assertEquals(JsonType.ARRAY, JsonType.fromJavaType("java.util.List"))
        assertEquals(JsonType.ARRAY, JsonType.fromJavaType("java.util.ArrayList"))
        assertEquals(JsonType.ARRAY, JsonType.fromJavaType("java.util.Set"))
        assertEquals(JsonType.ARRAY, JsonType.fromJavaType("java.util.HashSet"))
        assertEquals(JsonType.ARRAY, JsonType.fromJavaType("java.util.Collection"))
        assertEquals(JsonType.ARRAY, JsonType.fromJavaType("List<String>"))
        assertEquals(JsonType.ARRAY, JsonType.fromJavaType("Set<Integer>"))
    }
    
    @Test
    fun testFromJavaType_mapTypes() {
        assertEquals(JsonType.OBJECT, JsonType.fromJavaType("java.util.Map"))
        assertEquals(JsonType.OBJECT, JsonType.fromJavaType("java.util.HashMap"))
        assertEquals(JsonType.OBJECT, JsonType.fromJavaType("Map<String, Object>"))
    }
    
    @Test
    fun testFromJavaType_fileTypes() {
        assertEquals(JsonType.FILE, JsonType.fromJavaType("org.springframework.web.multipart.MultipartFile"))
    }
    
    @Test
    fun testFromJavaType_nullAndEmpty() {
        assertEquals(JsonType.STRING, JsonType.fromJavaType(null))
        assertEquals(JsonType.STRING, JsonType.fromJavaType(""))
        assertEquals(JsonType.STRING, JsonType.fromJavaType("   "))
    }
    
    @Test
    fun testFromJavaType_customClass() {
        assertEquals(JsonType.OBJECT, JsonType.fromJavaType("com.example.MyCustomClass"))
        assertEquals(JsonType.OBJECT, JsonType.fromJavaType("UserDTO"))
    }
}
