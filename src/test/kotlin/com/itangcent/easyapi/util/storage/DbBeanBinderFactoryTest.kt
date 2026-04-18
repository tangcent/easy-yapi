package com.itangcent.easyapi.util.storage

import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class DbBeanBinderFactoryTest {

    private lateinit var sqliteHelper: SqliteDataResourceHelper
    private lateinit var factory: DbBeanBinderFactory

    @Before
    fun setUp() {
        sqliteHelper = mock()
        factory = DbBeanBinderFactory(sqliteHelper)
    }

    @Test
    fun testCreateReturnsDbBeanBinder() {
        val binder = factory.create(
            namespace = "test",
            serializer = { it },
            deserializer = { it }
        )

        assertNotNull("Factory should create a DbBeanBinder", binder)
    }

    @Test
    fun testCreateWithCorrectNamespace() {
        val binder = factory.create<String>(
            namespace = "myNamespace",
            serializer = { it },
            deserializer = { it }
        )

        binder.save("key1", "value1")

        verify(sqliteHelper).upsert(
            org.mockito.kotlin.eq("bean:myNamespace:key1"),
            org.mockito.kotlin.any()
        )
    }

    @Test
    fun testCreateWithCustomSerializer() {
        var serialized = false
        val binder = factory.create<Map<String, String>>(
            namespace = "map",
            serializer = { 
                serialized = true
                it.entries.joinToString(",") { "${it.key}=${it.value}" }
            },
            deserializer = { 
                it.split(",").associate { p -> 
                    val (k, v) = p.split("=")
                    k to v 
                }
            }
        )

        binder.save("item1", mapOf("name" to "test"))

        assertTrue("Custom serializer should be called", serialized)
    }
}
