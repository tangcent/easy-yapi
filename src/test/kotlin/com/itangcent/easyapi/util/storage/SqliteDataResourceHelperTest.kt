package com.itangcent.easyapi.util.storage

import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Path

class SqliteDataResourceHelperTest {

    private lateinit var dbPath: Path
    private lateinit var helper: SqliteDataResourceHelper

    @Before
    fun setUp() {
        dbPath = Files.createTempFile("test-db", ".sqlite")
        Files.delete(dbPath)
        helper = SqliteDataResourceHelper(dbPath)
    }

    @After
    fun tearDown() {
        try {
            Files.deleteIfExists(dbPath)
        } catch (e: Exception) {
        }
    }

    @Test
    fun testUpsertAndQuery() {
        helper.upsert("key1", "value1")

        val result = helper.query("key1")

        assertEquals("Should retrieve the inserted value", "value1", result)
    }

    @Test
    fun testQueryNonExistentKey() {
        val result = helper.query("nonexistent")

        assertNull("Should return null for non-existent key", result)
    }

    @Test
    fun testDelete() {
        helper.upsert("key1", "value1")
        helper.delete("key1")

        val result = helper.query("key1")

        assertNull("Should return null after delete", result)
    }

    @Test
    fun testUpsertOverwrites() {
        helper.upsert("key1", "value1")
        helper.upsert("key1", "value2")

        val result = helper.query("key1")

        assertEquals("Should return the updated value", "value2", result)
    }

    @Test
    fun testAllKeys() {
        helper.upsert("key1", "value1")
        helper.upsert("key2", "value2")
        helper.upsert("key3", "value3")

        val keys = helper.allKeys()

        assertEquals("Should return all keys", setOf("key1", "key2", "key3"), keys)
    }

    @Test
    fun testAllKeysEmpty() {
        val keys = helper.allKeys()

        assertTrue("Should return empty set when no keys", keys.isEmpty())
    }

    @Test
    fun testMultipleOperations() {
        helper.upsert("key1", "value1")
        helper.upsert("key2", "value2")
        helper.delete("key1")

        val keys = helper.allKeys()

        assertEquals("Should only have key2", setOf("key2"), keys)
    }
}
