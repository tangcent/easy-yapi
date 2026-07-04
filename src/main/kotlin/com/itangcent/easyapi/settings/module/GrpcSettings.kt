package com.itangcent.easyapi.settings.module

import com.itangcent.easyapi.settings.Scope
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.settings.StorageScope

/**
 * gRPC export settings: enable toggle, artifact configs, additional JARs,
 * call enabled flag, repositories.
 *
 * Tab-aligned with the "gRPC" settings tab.
 *
 * Persisted at APPLICATION scope via the unified [com.itangcent.easyapi.settings.state.UnifiedAppSettingsState].
 */
data class GrpcSettings(
    @StorageScope(Scope.APPLICATION) var grpcEnable: Boolean = true,
    @StorageScope(Scope.APPLICATION) var grpcArtifactConfigs: Array<String> = emptyArray(),
    @StorageScope(Scope.APPLICATION) var grpcAdditionalJars: Array<String> = emptyArray(),
    @StorageScope(Scope.APPLICATION) var grpcCallEnabled: Boolean = false,
    @StorageScope(Scope.APPLICATION) var grpcRepositories: Array<String> = emptyArray()
) : Settings {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GrpcSettings
        if (grpcEnable != other.grpcEnable) return false
        if (!grpcArtifactConfigs.contentEquals(other.grpcArtifactConfigs)) return false
        if (!grpcAdditionalJars.contentEquals(other.grpcAdditionalJars)) return false
        if (grpcCallEnabled != other.grpcCallEnabled) return false
        if (!grpcRepositories.contentEquals(other.grpcRepositories)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = grpcEnable.hashCode()
        result = 31 * result + grpcArtifactConfigs.contentHashCode()
        result = 31 * result + grpcAdditionalJars.contentHashCode()
        result = 31 * result + grpcCallEnabled.hashCode()
        result = 31 * result + grpcRepositories.contentHashCode()
        return result
    }
}
