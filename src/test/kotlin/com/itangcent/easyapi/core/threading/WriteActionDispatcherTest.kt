package com.itangcent.easyapi.core.threading

import com.itangcent.easyapi.core.di.OperationScope
import com.itangcent.easyapi.core.di.OperationScopeElement
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import com.intellij.openapi.project.Project
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.runBlocking

class WriteActionDispatcherTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var writeActionDispatcher: WriteActionDispatcher

    override fun setUp() {
        super.setUp()
        writeActionDispatcher = WriteActionDispatcher()
    }

    fun testDispatchBlock() = runBlocking {
        var executed = false
        val block = Runnable { executed = true }
        
        writeActionDispatcher.dispatch(coroutineContext, block)
        
        assertTrue(executed)
    }

    fun testDispatchWithContext() = runBlocking {
        val scope = OperationScope.builder()
            .bind(Project::class, project)
            .build()
        
        val element = OperationScopeElement(scope)
        val context = coroutineContext + element
        
        var executed = false
        val block = Runnable { executed = true }
        
        writeActionDispatcher.dispatch(context, block)
        
        assertTrue(executed)
    }
}
