package com.itangcent.easyapi.core.cache

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class AppCacheRepositoryTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var repository: AppCacheRepository

    override fun setUp() {
        super.setUp()
        repository = AppCacheRepository.getInstance()
        repository.clear()
    }

    override fun tearDown() {
        repository.clear()
        super.tearDown()
    }

    fun testGetInstance() {
        assertNotNull(repository)
        assertSame(repository, AppCacheRepository.getInstance())
    }

    fun testReadNonExistentKey() {
        assertNull(repository.read("nonexistent_test_key.txt"))
    }

    fun testDeleteNonExistent() {
        repository.delete("nonexistent_test_key.txt")
    }

    fun testCacheSizeEmpty() {
        val size = repository.cacheSize()
        assertTrue(size >= 0L)
    }
}
