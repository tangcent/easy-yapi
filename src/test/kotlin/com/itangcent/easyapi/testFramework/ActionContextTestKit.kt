package com.itangcent.easyapi.testFramework

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.Settings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlin.reflect.KClass

object ActionContextTestKit {

    fun createTestContext(
        project: Project,
        settings: Settings = Settings(),
        configReader: ConfigReader = TestConfigReader(),
        additionalBindings: List<Binding<*>> = emptyList()
    ): ActionContext {
        val builder = ActionContext.builder()
            .bind(Project::class, project)
            .bind(ConfigReader::class, configReader)
            .bind(SettingBinder::class, ConstantSettingBinder(settings))
            .dispatcher(Dispatchers.Unconfined)
        
        additionalBindings.forEach { binding ->
            @Suppress("UNCHECKED_CAST")
            builder.bind(binding.kClass as KClass<Any>, binding.instance)
        }
        
        return builder.build()
    }

    inline fun <T> withTestContext(
        project: Project,
        settings: Settings = Settings(),
        configReader: ConfigReader = TestConfigReader(),
        additionalBindings: List<Binding<*>> = emptyList(),
        crossinline block: suspend ActionContext.() -> T
    ): T {
        val context = createTestContext(project, settings, configReader, additionalBindings)
        return runBlocking {
            try {
                withContext(context.coroutineContext) {
                    context.block()
                }
            } finally {
                context.stop()
            }
        }
    }

    inline fun <T> withSimpleContext(
        crossinline block: suspend ActionContext.() -> T
    ): T {
        val context = ActionContext.builder()
            .dispatcher(Dispatchers.Unconfined)
            .withSpiBindings()
            .build()
        return runBlocking {
            try {
                withContext(context.coroutineContext) {
                    context.block()
                }
            } finally {
                context.stop()
            }
        }
    }

    class Binding<T : Any>(val kClass: KClass<T>, val instance: T)

    inline fun <reified T : Any> binding(instance: T): Binding<T> = Binding(T::class, instance)
}
