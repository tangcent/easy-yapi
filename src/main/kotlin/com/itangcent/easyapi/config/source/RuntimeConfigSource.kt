package com.itangcent.easyapi.config.source

import com.itangcent.easyapi.config.model.ConfigEntry
import com.itangcent.easyapi.config.model.ConfigSource

/**
 * Configuration source for runtime-computed values.
 *
 * Provides dynamic configuration values computed at runtime, such as
 * the current module path. This source has the highest priority (0)
 * and can override all other sources.
 *
 * @param modulePath The current module path
 */
class RuntimeConfigSource(
    private var modulePath: String
) : ConfigSource {
    override val priority: Int = 0
    override val sourceId: String = "runtime"

    override suspend fun collect(): Sequence<ConfigEntry> {
        return sequenceOf(ConfigEntry("module_path", modulePath, sourceId))
    }

    fun setModulePath(modulePath: String) {
        this.modulePath = modulePath
    }
}
