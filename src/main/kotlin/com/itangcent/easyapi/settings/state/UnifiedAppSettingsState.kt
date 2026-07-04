package com.itangcent.easyapi.settings.state

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * Unified application-level state backing all [com.itangcent.easyapi.settings.Settings]
 * instances whose properties are annotated with [com.itangcent.easyapi.settings.StorageScope]
 * using [com.itangcent.easyapi.settings.Scope.APPLICATION].
 *
 * Replaces the per-module `*AppSettingsState` classes. All module state is stored in a single
 * nested map keyed by the module class's fully-qualified name, with property names as the inner
 * keys and values serialized as `String` (see [com.itangcent.easyapi.settings.DefaultSettingBinder]
 * for type coercion).
 *
 * Persisted in `easyapi_app.xml` under the `EasyApiUnifiedAppSettings` component name.
 *
 * Requirement: R-A-17.
 */
@State(name = "EasyApiUnifiedAppSettings", storages = [Storage("easyapi_app.xml")])
class UnifiedAppSettingsState : PersistentStateComponent<UnifiedAppSettingsState.State> {

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
        fun getInstance(): UnifiedAppSettingsState = ApplicationManager.getApplication().service()
    }
}
