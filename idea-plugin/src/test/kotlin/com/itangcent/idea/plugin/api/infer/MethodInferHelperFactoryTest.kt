package com.itangcent.idea.plugin.api.infer

import com.google.inject.Inject
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase
import kotlin.test.assertIs

/**
 * Test case of [MethodInferHelperFactory]
 */
class MethodInferHelperFactoryTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var methodInferHelperFactory: MethodInferHelperFactory

    private val settings = Settings()

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)

        builder.bind(SettingBinder::class) {
            it.toInstance(SettingBinderAdaptor(settings))
        }
    }

    fun testGetDefaultMethodInferHelper() {
        settings.aiEnable = false
        settings.aiMethodInferEnabled = false

        // Get the helper from the factory
        val helper = methodInferHelperFactory.getMethodInferHelper()

        // Verify it's the default helper
        assertTrue(helper is DefaultMethodInferHelper)
    }

    fun testGetAIMethodInferHelper() {
        settings.aiEnable = true
        settings.aiMethodInferEnabled = true
        settings.aiProvider = "OpenAI"
        settings.aiModel = "gpt-4"
        settings.aiToken = "test-token-123"
        // Verify it's the AI helper
        assertIs<AIMethodInferHelper>(methodInferHelperFactory.getMethodInferHelper())
    }
    
    fun testAIEnabledButMethodInferDisabled() {
        // Set AI enabled but method inference disabled
        settings.aiEnable = true
        settings.aiMethodInferEnabled = false
        settings.aiProvider = "OpenAI"
        settings.aiModel = "gpt-4"
        settings.aiToken = "test-token-123"
        
        // Get the helper from the factory
        val helper = methodInferHelperFactory.getMethodInferHelper()
        
        // Verify it's the default helper since method inference is disabled
        assertTrue(helper is DefaultMethodInferHelper)
    }
}