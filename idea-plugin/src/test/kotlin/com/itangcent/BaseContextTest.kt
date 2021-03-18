package com.itangcent

import com.google.inject.Inject
import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.logger.Logger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import org.mockito.Mockito

/**
 * Test case with [ActionContext]
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
internal abstract class BaseContextTest {

    @Inject
    protected lateinit var actionContext: ActionContext

    @Inject
    protected lateinit var logger: Logger

    @BeforeEach
    fun buildContext() {
        val builder = ActionContext.builder()
        builder.bind(SettingBinder::class) { it.with(SettingBinderAdaptor::class) }
        builder.bind(Logger::class) { it.with(PrintLogger::class) }
        builder.bind(Project::class) { it.toInstance(mockProject) }
        builder.bind(ConfigReader::class) { it.toInstance(mockConfigReader) }
        bind(builder)
        builder.build().init(this)
    }

    @AfterEach
    fun tearDown() {
        actionContext.waitComplete()
        actionContext.stop(true)
    }

    protected open fun bind(builder: ActionContext.ActionContextBuilder) {}

    companion object {
        val mockProject = Mockito.mock(Project::class.java)
        val mockConfigReader = Mockito.mock(ConfigReader::class.java)
    }
}