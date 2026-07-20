package com.itangcent.easyapi.format.spi

import com.itangcent.easyapi.format.json.ObjectModelJsonConverter
import com.itangcent.easyapi.format.json.ObjectModelValueConverter
import com.itangcent.easyapi.format.properties.PropertiesFormatter
import com.itangcent.easyapi.format.yaml.YamlFormatter
import com.itangcent.easyapi.core.psi.model.ObjectModel

/**
 * Extension functions for converting an [ObjectModel] into various representations.
 *
 * Each function is a pure delegation to the corresponding formatter/converter:
 * - [toJson] / [toJson5] → [ObjectModelJsonConverter]
 * - [toProperties] → [PropertiesFormatter]
 * - [toYaml] → [YamlFormatter]
 * - [toSimpleValue] → [ObjectModelValueConverter]
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
 * val value: Any? = model.toSimpleValue()
 * ```
 */

/** Standard JSON rendering — delegates to [ObjectModelJsonConverter.toJson]. */
fun ObjectModel?.toJson(): String = ObjectModelJsonConverter.toJson(this)

/** JSON5 rendering with comments — delegates to [ObjectModelJsonConverter.toJson5]. */
fun ObjectModel?.toJson5(): String = ObjectModelJsonConverter.toJson5(this)

/**
 * Converts this [ObjectModel] to its corresponding Kotlin value representation
 * (Map / List / primitive). Delegates to [ObjectModelValueConverter.toSimpleValue].
 *
 * Useful for tests and tooling that need to inspect the model's structure as
 * plain Kotlin values rather than as serialized text. Handles circular
 * references by tracking visited [ObjectModel.Object] instances.
 *
 * The receiver is nullable to mirror [ObjectModelValueConverter.toSimpleValue];
 * a null model returns null.
 */
fun ObjectModel?.toSimpleValue(): Any? = ObjectModelValueConverter.toSimpleValue(this)

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
