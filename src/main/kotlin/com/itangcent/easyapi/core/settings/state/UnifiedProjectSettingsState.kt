package com.itangcent.easyapi.core.settings.state

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * Unified project-level state backing all [com.itangcent.easyapi.core.settings.Settings]
 * instances whose properties are annotated with [com.itangcent.easyapi.core.settings.StorageScope]
 * using [com.itangcent.easyapi.core.settings.Scope.PROJECT].
 *
 * Replaces the per-module `*ProjectSettingsState` classes. All module state is stored in a single
 * nested map keyed by the module class's fully-qualified name, with property names as the inner
 * keys and values serialized as `String` (see [com.itangcent.easyapi.core.settings.DefaultSettingBinder]
 * for type coercion).
 *
 * Persisted in `easyapi.xml` under the `EasyApiUnifiedProjectSettings` component name.
 *
 * Requirement: R-A-17.
 */
@State(name = "EasyApiUnifiedProjectSettings", storages = [Storage("easyapi.xml")])
class UnifiedProjectSettingsState : PersistentStateComponent<UnifiedProjectSettingsState.State> {

    /**
     * Nested map: `module qualified name -> (property name -> serialized value)`.
     */
    data class State(
        var modules: MutableMap<String, MutableMap<String, String>> = mutableMapOf()
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    /**
     * Reads the serialized value of [property] for the module identified by [moduleKey].
     * Returns `null` if the module or property is not present (caller applies type default — R-A-5).
     */
    fun getValue(moduleKey: String, property: String): String? =
        state.modules[moduleKey]?.get(property)

    /**
     * Writes [value] for [property] under [moduleKey]. A `null` value removes the entry
     * so defaults resurface on next read.
     */
    fun setValue(moduleKey: String, property: String, value: String?) {
        val moduleMap = state.modules.getOrPut(moduleKey) { mutableMapOf() }
        if (value == null) {
            moduleMap.remove(property)
        } else {
            moduleMap[property] = value
        }
    }

    companion object {
        fun getInstance(project: Project): UnifiedProjectSettingsState = project.service()
    }
}
