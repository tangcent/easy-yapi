package com.itangcent.easyapi.core.di

import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class OperationScopeElementTest {

    @Test
    fun testKey() {
        assertNotNull(OperationScopeElement.Key)
        assertEquals(OperationScopeElement.Key, OperationScopeElement.Key)
    }

    @Test
    fun testConstruction() {
        val scope = OperationScope.builder().build()
        val element = OperationScopeElement(scope)
        assertSame(scope, element.scope)
    }

    @Test
    fun testCoroutineContextKey() {
        val scope = OperationScope.builder().build()
        val element = OperationScopeElement(scope)
        
        val key = element.key
        assertSame(OperationScopeElement.Key, key)
    }

    @Test
    fun testInCoroutineContext() = runBlocking {
        val scope = OperationScope.builder().build()
        val element = OperationScopeElement(scope)
        
        val context = element
        
        val retrievedElement = context[OperationScopeElement.Key]
        assertNotNull(retrievedElement)
        assertSame(element, retrievedElement)
        assertSame(scope, retrievedElement?.scope)
    }
}
