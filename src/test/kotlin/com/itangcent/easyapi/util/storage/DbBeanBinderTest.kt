package com.itangcent.easyapi.util.storage

import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DbBeanBinderTest {

    @Test
    fun testSaveDelegatesToUpsert() {
        val sqliteHelper = mock<SqliteDataResourceHelper>()
        val binder = DbBeanBinder<String>(
            sqliteHelper = sqliteHelper,
            keyPrefix = "test",
            serializer = { it },
            deserializer = { it }
        )
        binder.save("id1", "value1")
        verify(sqliteHelper).upsert("test:id1", "value1")
    }

    @Test
    fun testLoadDelegatesToQuery() {
        val sqliteHelper = mock<SqliteDataResourceHelper>()
        whenever(sqliteHelper.query("test:id1")).thenReturn("value1")
        val binder = DbBeanBinder<String>(
            sqliteHelper = sqliteHelper,
            keyPrefix = "test",
            serializer = { it },
            deserializer = { it }
        )
        val result = binder.load("id1")
        assertEquals("value1", result)
        verify(sqliteHelper).query("test:id1")
    }

    @Test
    fun testLoadReturnsNullWhenNotFound() {
        val sqliteHelper = mock<SqliteDataResourceHelper>()
        whenever(sqliteHelper.query("test:id1")).thenReturn(null)
        val binder = DbBeanBinder<String>(
            sqliteHelper = sqliteHelper,
            keyPrefix = "test",
            serializer = { it },
            deserializer = { it }
        )
        val result = binder.load("id1")
        assertNull(result)
    }

    @Test
    fun testDeleteDelegatesToDelete() {
        val sqliteHelper = mock<SqliteDataResourceHelper>()
        val binder = DbBeanBinder<String>(
            sqliteHelper = sqliteHelper,
            keyPrefix = "test",
            serializer = { it },
            deserializer = { it }
        )
        binder.delete("id1")
        verify(sqliteHelper).delete("test:id1")
    }

    @Test
    fun testSaveWithJsonSerializer() {
        val sqliteHelper = mock<SqliteDataResourceHelper>()
        val binder = DbBeanBinder<Map<String, String>>(
            sqliteHelper = sqliteHelper,
            keyPrefix = "map",
            serializer = { map -> map.entries.joinToString(",") { "${it.key}=${it.value}" } },
            deserializer = { str -> str.split(",").associate { p -> val (k, v) = p.split("="); k to v } }
        )
        val data = mapOf("name" to "test", "value" to "123")
        binder.save("item1", data)
        verify(sqliteHelper).upsert(eq("map:item1"), any())
    }
}
