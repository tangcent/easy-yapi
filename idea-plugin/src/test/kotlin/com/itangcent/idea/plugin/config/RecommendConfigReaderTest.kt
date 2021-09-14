package com.itangcent.idea.plugin.config

import com.google.inject.Inject
import com.intellij.openapi.module.Module
import com.itangcent.common.kit.toJson
import com.itangcent.debug.LoggerCollector
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.Settings
import com.itangcent.idea.plugin.settings.helper.RecommendConfigLoader
import com.itangcent.intellij.config.AbstractConfigReader
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.ContextSwitchListener
import com.itangcent.mock.AdvancedContextTest
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.mock.toUnixString
import com.itangcent.test.ResultLoader
import com.itangcent.test.mock
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import kotlin.test.assertEquals
import kotlin.test.assertNull

/**
 * Test case of [RecommendConfigReader]
 */
internal abstract class RecommendConfigReaderTest : AdvancedContextTest() {

    @Inject
    protected lateinit var recommendConfigReader: RecommendConfigReader

    override fun bind(builder: ActionContext.ActionContextBuilder) {
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
            ConfigReader::class,
            "delegate_config_reader"
        ) { it.toInstance(ConfigReaderAdaptor(customConfig() ?: "")) }

        builder.bind(
            ConfigReader::class
        ) { it.with(RecommendConfigReader::class).singleton() }

        builder.mock<ContextSwitchListener> { contextSwitchListener ->
            this.on(contextSwitchListener.onModuleChange(any()))
                .thenAnswer { it.getArgument<(Module) -> Unit>(0)(mock()) }
        }
    }

    override fun afterBind(actionContext: ActionContext) {
        super.afterBind(actionContext)
        recommendConfigReader.init()
    }

    internal class SimpleRecommendConfigReaderTest : RecommendConfigReaderTest() {

        @Test
        fun test() {
            assertEquals(ResultLoader.load("log"), LoggerCollector.getLog().toUnixString())
            assertEquals("#ignore", recommendConfigReader.first("ignore"))
            run {
                var configs = ""
                recommendConfigReader.foreach { key, value ->
                    if (configs.isNotEmpty()) {
                        configs = "$configs\n"
                    }
                    configs += "$key=$value"
                }
                assertEquals(ResultLoader.load("foreach"), configs)
            }
            run {
                var configs = ""
                recommendConfigReader.foreach({ it.startsWith("api.") }) { key, value ->
                    if (configs.isNotEmpty()) {
                        configs = "$configs\n"
                    }
                    configs += "$key=$value"
                }
                assertEquals(ResultLoader.load("foreach.api"), configs)
            }
            assertEquals(listOf("#ignore", "@Ignore").toJson(), recommendConfigReader.read("ignore").toJson())
            assertEquals("#mock", recommendConfigReader.resolveProperty("\${field.mock}"))
        }
    }

    internal class EmptyRecommendRecommendConfigReaderTest : RecommendConfigReaderTest() {
        override fun bind(builder: ActionContext.ActionContextBuilder) {
            super.bind(builder)

            builder.bind(SettingBinder::class) {
                it.toInstance(SettingBinderAdaptor(Settings().also { settings ->
                    settings.recommendConfigs =
                        RecommendConfigLoader.defaultCodes().split(",").joinToString(separator = ",") { "-$it" }
                    settings.builtInConfig = "ignore=@Ignore"
                }))
            }
        }

        @Test
        fun test() {
            assertEquals(ResultLoader.load("log"), LoggerCollector.getLog().toUnixString())
            assertEquals("@Ignore", recommendConfigReader.first("ignore"))
        }
    }

    internal class ImmutableRecommendConfigReaderTest : RecommendConfigReaderTest() {
        override fun bind(builder: ActionContext.ActionContextBuilder) {
            super.bind(builder)

            builder.bind(
                ConfigReader::class,
                "delegate_config_reader"
            ) { it.toInstance(mock()) }
        }

        @Test
        fun test() {
            assertEquals(ResultLoader.load("log"), LoggerCollector.getLog().toUnixString())
            assertNull(recommendConfigReader.first("ignore"))
        }
    }

    private inner class ConfigReaderAdaptor(val config: String) : AbstractConfigReader() {

        @PostConstruct
        fun init() {
            loadConfigInfoContent(config, "properties")
        }

        override fun findConfigFiles(): List<String>? {
            return null
        }
    }
}