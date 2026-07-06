package com.itangcent.easyapi.exporter.channel.yapi

import com.itangcent.easyapi.settings.Scope
import com.itangcent.easyapi.settings.Settings
import com.itangcent.easyapi.settings.StorageScope
import com.itangcent.easyapi.exporter.channel.yapi.YapiExportMode

/**
 * YApi channel settings.
 *
 * All fields are APPLICATION scope, persisted via the unified [com.itangcent.easyapi.settings.state.UnifiedAppSettingsState].
 */
data class YapiSettings(
    @StorageScope(Scope.APPLICATION) var yapiServer: String? = null,
    @StorageScope(Scope.APPLICATION) var yapiTokens: String? = null,
    @StorageScope(Scope.APPLICATION) var yapiExportMode: String = YapiExportMode.ALWAYS_UPDATE.name,
    @StorageScope(Scope.APPLICATION) var yapiReqBodyJson5: Boolean = false,
    @StorageScope(Scope.APPLICATION) var yapiResBodyJson5: Boolean = false
) : Settings
