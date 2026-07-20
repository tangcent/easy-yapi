package com.itangcent.easyapi.channel.yapi

import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.ApiParameter
import com.itangcent.easyapi.core.export.MutableExtension
import com.itangcent.easyapi.core.psi.model.FieldModel

/**
 * YApi-specific accessors for API/field metadata stored on the shared model
 * classes' [com.itangcent.easyapi.core.export.Extension] carrier.
 *
 * These are Kotlin extension properties so that easy-yapi code reads
 * `endpoint.tags` / `endpoint.status` / `endpoint.open` / `fieldModel.mock` /
 * `param.jsonType` exactly as before, while the shared model classes carry no
 * YApi-coupled fields. The shared [ApiEndpoint], [FieldModel], and
 * [ApiParameter] data classes remain minimal and byte-identical between repos.
 *
 * Writes go through the mutable view obtained when the model was constructed
 * with a [MutableExtension] (see [YapiMetadataPopulator]); reading works on any
 * `Extension`, mutable or not.
 */

// ── ApiEndpoint ─────────────────────────────────────────────────────────────

/** YApi tags for this endpoint (empty if unset). */
val ApiEndpoint.tags: List<String>
    @Suppress("UNCHECKED_CAST")
    get() = (extensions.exts["tags"] as? List<String>) ?: emptyList()

/** YApi lifecycle status (e.g. "done", "undone"); null if unset. */
val ApiEndpoint.status: String?
    get() = extensions.exts["status"] as? String

/** Whether the endpoint is open/exposed in YApi; false if unset. */
val ApiEndpoint.open: Boolean
    get() = extensions.exts["open"] as? Boolean ?: false

// ── FieldModel ──────────────────────────────────────────────────────────────

/** Mock value for this field (e.g. "@integer(0,100)"); null if unset. */
val FieldModel.mock: String?
    get() = extensions.exts["mock"] as? String

// ── ApiParameter ────────────────────────────────────────────────────────────

/** JSON Schema type hint for this parameter (e.g. "integer", "string"); null if unset. */
val ApiParameter.jsonType: String?
    get() = extensions.exts["jsonType"] as? String
