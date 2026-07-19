package com.itangcent.easyapi.core.settings

/**
 * Storage scope for a settings property.
 *
 * Annotates `var` properties of [Settings] data classes to declare
 * whether the property persists at the project level or the application level.
 *
 * When absent, the default scope is [Scope.APPLICATION].
 *
 * @see Scope
 * @see Settings
 */
@Target(AnnotationTarget.PROPERTY)
annotation class StorageScope(val value: Scope)

/**
 * Storage scope for a settings property.
 *
 * - [PROJECT]    — persisted per-project (in `easyapi.xml`, via [com.itangcent.easyapi.core.settings.state.UnifiedProjectSettingsState])
 * - [APPLICATION] — persisted globally (in `easyapi_app.xml`, via [com.itangcent.easyapi.core.settings.state.UnifiedAppSettingsState])
 */
enum class Scope { PROJECT, APPLICATION }
