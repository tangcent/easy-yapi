package com.itangcent.easyapi.channel.spi

import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project

/**
 * SPI for channels that support bidirectional environment synchronization
 * (push local environments to a remote, pull remote environments into local).
 *
 * **Decision CO7**: Extracted to break the `core.dashboard` →
 * `channel.postman.sync` concrete-impl dependency. The Postman channel
 * provides the implementation; `core.*` callers (dashboard panels) depend
 * only on this SPI.
 *
 * Implementations are registered in `plugin.xml` as a project service with
 * `serviceInterface="...EnvironmentSyncSupport"`. Look up via
 * [getInstance] — never import a concrete impl from `core.*`.
 *
 * Future channels that want env-sync (e.g. Hoppscotch) can provide their own
 * impl; if multiple impls become necessary, switch to an EP-based registry
 * (similar to [Channel]) and expose `firstAvailable()` here.
 */
interface EnvironmentSyncSupport {

    /**
     * Whether this sync support is usable right now (e.g. an API token is
     * configured). UI callers use this to enable/disable the sync button.
     */
    fun isAvailable(): Boolean

    /**
     * Opens the channel-specific sync dialog and runs the sync operation on a
     * background thread. The dialog and all channel-specific UI live inside
     * the impl — callers only need [SyncMode] and an optional pull-complete
     * callback.
     *
     * @param mode PUSH or PULL
     * @param onPullComplete optional EDT callback invoked after a successful
     *        pull (e.g. to refresh the caller's environment list)
     */
    fun showSyncDialogAndExecute(mode: SyncMode, onPullComplete: (() -> Unit)? = null)

    /**
     * Sync direction.
     *
     * Lives in the SPI (not in a channel-specific dialog class) so that
     * `core.*` callers can refer to it without importing concrete impls.
     */
    enum class SyncMode { PUSH, PULL }

    companion object {
        /**
         * Returns the registered [EnvironmentSyncSupport] for [project].
         *
         * Throws if no impl is registered — IntelliJ's `service()` semantics.
         * If env-sync is optional in a future configuration, switch to
         * `serviceIfCreated()` and return nullable.
         */
        fun getInstance(project: Project): EnvironmentSyncSupport = project.service()
    }
}
