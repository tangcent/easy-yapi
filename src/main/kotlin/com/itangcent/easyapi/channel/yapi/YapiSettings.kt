package com.itangcent.easyapi.channel.yapi

import com.itangcent.easyapi.core.settings.Scope
import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.core.settings.StorageScope
import com.itangcent.easyapi.channel.yapi.YapiExportMode

/**
 * YApi channel settings.
 *
 * All fields are APPLICATION scope, persisted via the unified [com.itangcent.easyapi.core.settings.state.UnifiedAppSettingsState].
 */
data class YapiSettings(
    @StorageScope(Scope.APPLICATION) var yapiServer: String? = null,
    @StorageScope(Scope.APPLICATION) var yapiTokens: String? = null,
    @StorageScope(Scope.APPLICATION) var yapiExportMode: String = YapiExportMode.ALWAYS_UPDATE.name,
    @StorageScope(Scope.APPLICATION) var yapiReqBodyJson5: Boolean = false,
    @StorageScope(Scope.APPLICATION) var yapiResBodyJson5: Boolean = false
) : Settings
