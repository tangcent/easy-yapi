package com.itangcent.easyapi.core.settings.module

import com.itangcent.easyapi.core.settings.Scope
import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.core.settings.StorageScope

/**
 * gRPC export settings: artifact configs, additional JARs, call enabled flag,
 * repositories.
 *
 * Framework enablement for gRPC is managed via [GeneralSettings.enabledFrameworks]/
 * [GeneralSettings.disabledFrameworks] arrays, resolved by
 * [com.itangcent.easyapi.framework.spi.FrameworkRegistry].
 *
 * Tab-aligned with the "gRPC" settings tab.
 *
 * Persisted at APPLICATION scope via the unified [com.itangcent.easyapi.core.settings.state.UnifiedAppSettingsState].
 */
data class GrpcSettings(
    @StorageScope(Scope.APPLICATION) var grpcArtifactConfigs: Array<String> = emptyArray(),
    @StorageScope(Scope.APPLICATION) var grpcAdditionalJars: Array<String> = emptyArray(),
    @StorageScope(Scope.APPLICATION) var grpcCallEnabled: Boolean = false,
    @StorageScope(Scope.APPLICATION) var grpcRepositories: Array<String> = emptyArray()
) : Settings {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GrpcSettings
        if (!grpcArtifactConfigs.contentEquals(other.grpcArtifactConfigs)) return false
        if (!grpcAdditionalJars.contentEquals(other.grpcAdditionalJars)) return false
        if (grpcCallEnabled != other.grpcCallEnabled) return false
        if (!grpcRepositories.contentEquals(other.grpcRepositories)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = grpcArtifactConfigs.contentHashCode()
        result = 31 * result + grpcAdditionalJars.contentHashCode()
        result = 31 * result + grpcCallEnabled.hashCode()
        result = 31 * result + grpcRepositories.contentHashCode()
        return result
    }
}
