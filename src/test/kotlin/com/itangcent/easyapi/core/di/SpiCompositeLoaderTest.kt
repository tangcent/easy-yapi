package com.itangcent.easyapi.core.di

import org.junit.Assert.*
import org.junit.Test

class SpiCompositeLoaderTest {

    @Test
    fun testLoadWithClass() {
        val loaders = SpiCompositeLoader.load(TestInterface::class.java)
        assertNotNull(loaders)
    }

    @Test
    fun testLoadReified() {
        val loaders: List<TestInterface> = SpiCompositeLoader.load()
        assertNotNull(loaders)
    }

    @Test
    fun testLoadFilteredWithClass() {
        val loaders = SpiCompositeLoader.loadFiltered(TestInterface::class.java)
        assertNotNull(loaders)
    }

    @Test
    fun testLoadFilteredReified() {
        val loaders: List<TestInterface> = SpiCompositeLoader.loadFiltered()
        assertNotNull(loaders)
    }

    @Test
    fun testLoadFilteredWithSettings() {
        val loaders: List<TestInterface> = SpiCompositeLoader.loadFiltered(settings = null)
        assertNotNull(loaders)
    }

    interface TestInterface
}
