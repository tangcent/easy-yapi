package com.itangcent.easyapi.psi.model

import com.itangcent.easyapi.exporter.formatter.PropertiesFormatter
import com.itangcent.easyapi.exporter.formatter.YamlFormatter

/**
 * Extension functions for converting an [ObjectModel] into various text formats.
 *
 * Each function is a pure delegation to the corresponding formatter/converter:
 * - [toJson] / [toJson5] → [ObjectModelJsonConverter]
 * - [toProperties] → [PropertiesFormatter]
 * - [toYaml] → [YamlFormatter]
 *
 * These are top-level extensions (not service methods) because the conversions
 * are pure functions of the model — they need no `Project`, `RuleEngine`, or
 * PSI access. Project-scoped concerns (e.g. resolving `properties.prefix`)
 * belong in `PropertiesService`, which calls [toProperties] and [toYaml]
 * after resolving the prefix.
 *
 * ## Usage
 * ```kotlin
 * val model: ObjectModel = helper.buildObjectModel(psiClass) ?: return
 * val json: String = model.toJson()
 * val yaml: String = model.toYaml()
 * ```
 */

/** Standard JSON rendering — delegates to [ObjectModelJsonConverter.toJson]. */
fun ObjectModel.toJson(): String = ObjectModelJsonConverter.toJson(this)

/** JSON5 rendering with comments — delegates to [ObjectModelJsonConverter.toJson5]. */
fun ObjectModel.toJson5(): String = ObjectModelJsonConverter.toJson5(this)

/**
 * Java Properties rendering with an optional [prefix] prepended to every key.
 *
 * Delegates to [PropertiesFormatter.format]. The [prefix] is resolved by the
 * caller (typically `PropertiesService` via `RuleEngine`).
 */
fun ObjectModel.toProperties(prefix: String = ""): String =
    PropertiesFormatter().format(this, prefix)

/**
 * YAML rendering with an optional dot-separated [prefix] rendered as nested
 * keys (mirrors `@ConfigurationProperties(prefix = ...)` for `application.yml`).
 *
 * Delegates to [YamlFormatter.format]. The [prefix] is resolved by the caller
 * (typically `PropertiesService.toYaml` via `RuleEngine`).
 */
fun ObjectModel.toYaml(prefix: String = ""): String =
    YamlFormatter.format(this, prefix)
