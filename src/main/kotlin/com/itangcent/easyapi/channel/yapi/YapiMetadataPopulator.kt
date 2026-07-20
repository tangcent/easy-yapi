package com.itangcent.easyapi.channel.yapi

import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.MutableExtension
import com.itangcent.easyapi.core.rule.engine.RuleEngine

/**
 * Populates YApi-specific endpoint metadata (tags, status, open) onto an
 * [ApiEndpoint]'s [com.itangcent.easyapi.core.export.Extension] carrier.
 *
 * The shared `SpringMvcClassExporter` no longer populates these (they are
 * YApi-specific); instead easy-yapi's export pipeline calls
 * [populate] on each endpoint after the shared exporter builds it. This keeps
 * the shared exporter free of YApi concerns while letting easy-yapi attach the
 * data its formatter needs.
 *
 * Field-level `mock` and parameter-level `jsonType` are populated during model
 * construction by easy-yapi's divergent copies of `DefaultPsiClassHelper` /
 * `SpringMvcClassExporter` (they write into the same carrier).
 */
object YapiMetadataPopulator {

    /**
     * Returns a copy of [endpoint] with YApi tags/status/open resolved from the
     * method via [YapiMetadataResolver] and written into a [MutableExtension].
     * The original endpoint's existing extension entries are preserved.
     */
    suspend fun populate(endpoint: ApiEndpoint, engine: RuleEngine): ApiEndpoint {
        val method = endpoint.sourceMethod ?: return endpoint
        val resolver = YapiMetadataResolver(engine)

        val tags = resolver.resolveApiTag(method)
            ?.split(",", "\n")?.map { it.trim() }?.distinct()?.filter { it.isNotBlank() }
            ?: emptyList()
        val status = resolver.resolveApiStatus(method)
        val open = resolver.isApiOpen(method)

        // Carry over any existing entries (e.g. mock/jsonType populated at field/param level)
        // and add the endpoint-level YApi metadata.
        val exts = MutableExtension()
        exts.putAll(endpoint.extensions.exts)
        if (tags.isNotEmpty()) exts["tags"] = tags
        if (status != null) exts["status"] = status
        if (open) exts["open"] = open

        return endpoint.copy(extensions = exts)
    }
}
