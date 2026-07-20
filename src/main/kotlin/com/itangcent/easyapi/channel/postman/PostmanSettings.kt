package com.itangcent.easyapi.channel.postman

import com.itangcent.easyapi.core.settings.Scope
import com.itangcent.easyapi.core.settings.Settings
import com.itangcent.easyapi.core.settings.StorageScope
import com.itangcent.easyapi.core.settings.PostmanJson5FormatType
import com.itangcent.easyapi.core.settings.PostmanExportMode

/**
 * Postman channel settings.
 *
 * Mixed-scope module:
 * - APP fields: `postmanToken`, `wrapCollection`, `autoMergeScript`, `postmanJson5FormatType`
 * - PROJ fields: `postmanWorkspace`, `postmanExportMode`, `postmanCollections`, `postmanBuildExample`
 *
 * `postmanToken` is standardized to APP only (resolves the old APP+PROJ duplication).
 * Migration prefers the non-empty value between old APP/PROJ copies.
 *
 * Persisted via the unified state components ([com.itangcent.easyapi.core.settings.state.UnifiedAppSettingsState] / [com.itangcent.easyapi.core.settings.state.UnifiedProjectSettingsState]).
 */
data class PostmanSettings(
    // ---- APPLICATION scope ----
    @StorageScope(Scope.APPLICATION) var postmanToken: String? = null,
    @StorageScope(Scope.APPLICATION) var wrapCollection: Boolean = false,
    @StorageScope(Scope.APPLICATION) var autoMergeScript: Boolean = false,
    @StorageScope(Scope.APPLICATION) var postmanJson5FormatType: String = PostmanJson5FormatType.EXAMPLE_ONLY.name,

    // ---- PROJECT scope ----
    @StorageScope(Scope.PROJECT) var postmanWorkspace: String? = null,
    @StorageScope(Scope.PROJECT) var postmanExportMode: String? = PostmanExportMode.CREATE_NEW.name,
    @StorageScope(Scope.PROJECT) var postmanCollections: String? = null,
    @StorageScope(Scope.PROJECT) var postmanBuildExample: Boolean = true
) : Settings
