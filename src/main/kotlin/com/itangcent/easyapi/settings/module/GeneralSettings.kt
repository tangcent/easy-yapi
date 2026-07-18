package com.itangcent.easyapi.settings.module

import com.itangcent.easyapi.settings.Scope
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.settings.StorageScope

/**
 * General framework toggles, scan options, and output preferences.
 *
 * Tab-aligned with the "General" settings tab.
 *
 * Persisted at APPLICATION scope via the unified [com.itangcent.easyapi.settings.state.UnifiedAppSettingsState].
 */
data class GeneralSettings(
    @StorageScope(Scope.APPLICATION) var feignEnable: Boolean = false,
    @StorageScope(Scope.APPLICATION) var jaxrsEnable: Boolean = true,
    @StorageScope(Scope.APPLICATION) var actuatorEnable: Boolean = false,
    @StorageScope(Scope.APPLICATION) var autoScanEnabled: Boolean = true,
    @StorageScope(Scope.APPLICATION) var concurrentScanEnabled: Boolean = false,
    @StorageScope(Scope.APPLICATION) var gutterIconEnabled: Boolean = true,
    @StorageScope(Scope.APPLICATION) var switchNotice: Boolean = true,
    @StorageScope(Scope.APPLICATION) var logLevel: Int = 100,
    @StorageScope(Scope.APPLICATION) var outputCharset: String = "UTF-8",
    @StorageScope(Scope.APPLICATION) var enabledChannels: Array<String> = emptyArray(),
    @StorageScope(Scope.APPLICATION) var disabledChannels: Array<String> = emptyArray(),
    @StorageScope(Scope.APPLICATION) var enabledFieldFormatChannels: Array<String> = emptyArray(),
    @StorageScope(Scope.APPLICATION) var disabledFieldFormatChannels: Array<String> = emptyArray()
) : Settings {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as GeneralSettings
        if (feignEnable != other.feignEnable) return false
        if (jaxrsEnable != other.jaxrsEnable) return false
        if (actuatorEnable != other.actuatorEnable) return false
        if (autoScanEnabled != other.autoScanEnabled) return false
        if (concurrentScanEnabled != other.concurrentScanEnabled) return false
        if (gutterIconEnabled != other.gutterIconEnabled) return false
        if (switchNotice != other.switchNotice) return false
        if (logLevel != other.logLevel) return false
        if (outputCharset != other.outputCharset) return false
        if (!enabledChannels.contentEquals(other.enabledChannels)) return false
        if (!disabledChannels.contentEquals(other.disabledChannels)) return false
        if (!enabledFieldFormatChannels.contentEquals(other.enabledFieldFormatChannels)) return false
        if (!disabledFieldFormatChannels.contentEquals(other.disabledFieldFormatChannels)) return false
        return true
    }

    override fun hashCode(): Int {
        var result = feignEnable.hashCode()
        result = 31 * result + jaxrsEnable.hashCode()
        result = 31 * result + actuatorEnable.hashCode()
        result = 31 * result + autoScanEnabled.hashCode()
        result = 31 * result + concurrentScanEnabled.hashCode()
        result = 31 * result + gutterIconEnabled.hashCode()
        result = 31 * result + switchNotice.hashCode()
        result = 31 * result + logLevel
        result = 31 * result + outputCharset.hashCode()
        result = 31 * result + enabledChannels.contentHashCode()
        result = 31 * result + disabledChannels.contentHashCode()
        result = 31 * result + enabledFieldFormatChannels.contentHashCode()
        result = 31 * result + disabledFieldFormatChannels.contentHashCode()
        return result
    }
}
