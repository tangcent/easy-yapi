package com.itangcent.easyapi.core.settings

/**
 * Marker interface for a modular settings data class.
 *
 * Each module is a `data class` with `@StorageScope`-annotated `var` properties.
 * State is persisted via the unified [com.itangcent.easyapi.core.settings.state.UnifiedAppSettingsState]
 * / [com.itangcent.easyapi.core.settings.state.UnifiedProjectSettingsState] components — no per-module
 * state class or `plugin.xml` registration is required.
 *
 * Modules are read/written via [SettingBinder]:
 * ```kotlin
 * val binder = SettingBinder.getInstance(project)
 * val general = binder.read<GeneralSettings>()
 * general.enabledFrameworks = arrayOf("Feign")
 * binder.save(general)
 * ```
 *
 * All constructor parameters must have default values (the binder constructs a
 * default instance then fills it from persistent state). Missing/unrecognized
 * state yields the type's defaults — no crash (R-A-5).
 *
 * @see StorageScope
 * @see SettingBinder
 */
interface Settings
