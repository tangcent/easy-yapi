package com.itangcent.easyapi.testFramework

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.settings.Settings
import org.junit.After
import org.junit.Before

abstract class IntegrationTestBase {

    protected var actionContext: ActionContext? = null

    @Before
    open fun setUp() {
    }

    @After
    open fun tearDown() {
        actionContext?.let { 
            kotlinx.coroutines.runBlocking { it.stop() }
        }
        actionContext = null
    }

    protected fun createActionContext(
        project: Project,
        settings: Settings = ApiFixtures.createSettings()
    ): ActionContext {
        val context = ActionContext.builder()
            .bind(Project::class, project)
            .bind(Settings::class, settings)
            .build()
        actionContext = context
        return context
    }
}
