package com.itangcent.easyapi.testFramework

import com.itangcent.easyapi.core.settings.SettingBinder
import com.itangcent.easyapi.core.settings.Settings
import java.util.concurrent.ConcurrentHashMap
import kotlin.reflect.KClass
import kotlin.reflect.full.primaryConstructor

/**
 * Test-only [SettingBinder] that holds module instances in an in-memory
 * map. Reading a module that has not been saved returns a default-constructed
 * instance (all module data classes have default constructor parameters).
 *
 * Replaces the legacy `ConstantSettingBinder` which wrapped a single `Settings`
 * god-object. Each module is now stored independently.
 */
class ConstantSettingBinder : SettingBinder {

    private val modules: MutableMap<KClass<*>, Settings> = ConcurrentHashMap()

    @Suppress("UNCHECKED_CAST")
    override fun <T : Settings> read(type: KClass<T>): T {
        return tryRead(type) ?: defaultInstance(type)
            ?: error("Cannot construct settings module ${type.qualifiedName}; ensure all constructor parameters have defaults")
    }

    @Suppress("UNCHECKED_CAST")
    override fun <T : Settings> tryRead(type: KClass<T>): T? {
        return modules[type] as? T
    }

    override fun <T : Settings> save(settings: T) {
        modules[settings::class] = settings
    }

    private fun <T : Settings> defaultInstance(type: KClass<T>): T? {
        val constructor = type.primaryConstructor ?: return null
        return try {
            constructor.callBy(emptyMap())
        } catch (e: Exception) {
            null
        }
    }
}
