package com.itangcent.easyapi.cache

import org.junit.Assert.*
import org.junit.Test

class AppCacheRepositoryTest {

    @Test
    fun testGetInstance() {
        val repository = AppCacheRepository.getInstance()
        assertNotNull(repository)
        assertSame(repository, AppCacheRepository.getInstance())
    }

    @Test
    fun testReadNonExistentKey() {
        val repository = AppCacheRepository()
        assertNull(repository.read("nonexistent_test_key.txt"))
    }

    @Test
    fun testDeleteNonExistent() {
        val repository = AppCacheRepository()
        repository.delete("nonexistent_test_key.txt")
    }

    @Test
    fun testCacheSizeEmpty() {
        val repository = AppCacheRepository()
        val size = repository.cacheSize()
        assertTrue(size >= 0L)
    }
}
