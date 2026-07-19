package com.itangcent.easyapi.core.settings.migration

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service

/**
 * One-time migration flag for settings modularization (DD-2).
 *
 * Tracks whether the legacy `ApplicationSettingsState` / `ProjectSettingsState`
 * have been migrated to the new per-module state components.
 *
 * Stored in `easyapi_app.xml` under the `EasyApiSettingsMigration` component name.
 */
@State(name = "EasyApiSettingsMigration", storages = [Storage("easyapi_app.xml")])
class MigrationFlag : PersistentStateComponent<MigrationFlag.State> {

    data class State(
        var migrated: Boolean = false,
        var version: Int = 0
    )

    private var state = State()

    override fun getState(): State = state

    override fun loadState(state: State) {
        this.state = state
    }

    companion object {
        fun getInstance(): MigrationFlag = ApplicationManager.getApplication().service()
    }
}
