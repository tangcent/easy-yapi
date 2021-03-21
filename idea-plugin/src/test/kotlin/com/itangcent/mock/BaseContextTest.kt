package com.itangcent.mock

import com.google.inject.Inject
import com.intellij.openapi.project.Project
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.jvm.dev.DevEnv
import com.itangcent.intellij.logger.Logger
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.fail
import org.mockito.Mockito

/**
 * Test case with [ActionContext]
 */
abstract class BaseContextTest {

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
        builder.bind(DevEnv::class) { it.toInstance(mockDevEnv) }
        builder.bind(ModuleHelper::class) { it.toInstance(ConstantModuleHelper.INSTANCE) }
        bind(builder)
        val actionContext = builder.build()
        try {
            actionContext.init(this)
            afterBind()
        } catch (e: Exception) {
            e.printStackTrace()
            fail("buildContext failed")
        }
    }

    protected open fun afterBind() {
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
        val mockDevEnv = Mockito.mock(DevEnv::class.java)
    }
}