package com.itangcent.easyapi.channel.dummy

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

/**
 * V1 extensibility stub — channel state carrier for [DummyChannel].
 *
 * Proves the per-channel persistent-state slot (C3 in the Channel Author
 * Contract) is pluggable. Empty on purpose.
 */
@State(name = "EasyApiDummySettings", storages = [Storage("easyapi_app.xml")])
class DummySettingsState : PersistentStateComponent<DummySettingsState.State> {

    data class State(
        var dummyValue: String? = null
    )

    private var myState: State = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        myState = state
    }
}
