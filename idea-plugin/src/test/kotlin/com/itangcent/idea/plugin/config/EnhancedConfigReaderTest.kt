package com.itangcent.idea.plugin.config

import com.google.inject.Inject
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.module.Module
import com.itangcent.common.kit.toJson
import com.itangcent.debug.LoggerCollector
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.plugin.settings.helper.RecommendConfigLoader
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.ContextSwitchListener
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.mock.toUnixString
import com.itangcent.test.ResultLoader
import com.itangcent.test.mock
import com.itangcent.testFramework.ContextLightCodeInsightFixtureTestCase
import org.mockito.kotlin.any
import org.mockito.kotlin.mock

/**
 * Test case of [EnhancedConfigReader]
 */
internal abstract class EnhancedConfigReaderTest : ContextLightCodeInsightFixtureTestCase() {

    @Inject
    protected lateinit var enhancedConfigReader: EnhancedConfigReader

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)

        builder.bind(Logger::class) { it.with(LoggerCollector::class) }

        builder.bind(SettingBinder::class) {
            it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                settings.recommendConfigs =
                    "-Jackson_JsonIgnoreProperties,-converts,-yapi_tag,-spring.ui,-import_spring_properties,-support_mock_for_general,-deprecated_java,-deprecated_kotlin,-spring_Entity,-spring_webflux,-javax.validation,-javax.validation(grouped),-support_mock_for_javax_validation"
                settings.builtInConfig = "ignore=@Ignore"
            }))
        }

        builder.bind(
            ConfigReader::class
        ) { it.with(EnhancedConfigReader::class).singleton() }

        builder.mock<ContextSwitchListener> { contextSwitchListener ->
            this.on(contextSwitchListener.onModuleChange(any()))
                .thenAnswer { it.getArgument<(Module) -> Unit>(0)(mock()) }
        }

        builder.mock<DataContext> { dataContext ->
            this.on(dataContext.getData(any()))
                .thenAnswer { null }
        }
    }

    internal class SimpleEnhancedConfigReaderTest : EnhancedConfigReaderTest() {

        fun testLoadConfig() {
            assertEquals("@Ignore", enhancedConfigReader.first("ignore"))
            run {
                var configs = ""
                enhancedConfigReader.foreach { key, value ->
                    if (configs.isNotEmpty()) {
                        configs = "$configs\n"
                    }
                    configs += "$key=$value"
                }
                assertEquals(ResultLoader.load("foreach"), configs)
            }
            run {
                var configs = ""
                enhancedConfigReader.foreach({ it.startsWith("api.") }) { key, value ->
                    if (configs.isNotEmpty()) {
                        configs = "$configs\n"
                    }
                    configs += "$key=$value"
                }
                assertEquals(ResultLoader.load("foreach.api"), configs)
            }
            assertEquals(listOf("@Ignore", "#ignore").toJson(), enhancedConfigReader.read("ignore").toJson())
            assertEquals("#mock", enhancedConfigReader.resolveProperty("\${field.mock}"))
        }
    }

    internal class EmptyRecommendEnhancedConfigReaderTest : EnhancedConfigReaderTest() {
        override fun bind(builder: ActionContextBuilder) {
            super.bind(builder)

            builder.bind(SettingBinder::class) {
                it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                    settings.recommendConfigs =
                        RecommendConfigLoader.defaultCodes().split(",").joinToString(separator = ",") { "-$it" }
                    settings.builtInConfig = "ignore=@Ignore"
                }))
            }
        }

        fun testLoadConfig() {
            assertEquals("@Ignore", enhancedConfigReader.first("ignore"))
            assertEquals(ResultLoader.load("log"), LoggerCollector.getLog().toUnixString())
        }
    }
}