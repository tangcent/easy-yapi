package com.itangcent.easyapi.channel.apipost

import com.itangcent.easyapi.channel.openapi.OpenApiDocument
import com.itangcent.easyapi.channel.openapi.OpenApiDocumentFactory
import com.itangcent.easyapi.channel.openapi.OpenApiSerializer
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.intellij.openapi.project.Project

/**
 * **The ONLY class in `channel.apipost` allowed to import `channel.openapi`.**
 *
 * `channel.openapi`'s envelope and formatter are `internal`; rather than widen
 * them, the openapi side exposes [OpenApiDocumentFactory], and this bridge is
 * the single place that consumes it. Everything else in this package works with
 * opaque [Any] / [String] values, so the dependency direction stays
 * `apipost → openapi` with exactly one edge (NFR-7 / AC-11 — enforced by grep).
 *
 * ## Why two steps instead of a callback
 *
 * The natural shape would be `buildAndSerialize { document -> … }`, letting the
 * caller mutate the document before serialization. That does not work here:
 * `RuleEngine.evaluate` is a `suspend` function, so the `apipost.save.before`
 * hook cannot run inside a plain lambda. Returning an opaque handle from
 * [buildDocument] and accepting it in [toJson] lets the caller suspend in
 * between.
 */
internal object ApipostOpenApiBridge {

    /**
     * Builds an OpenAPI document and hands it back as an opaque handle.
     *
     * @param documentServerUrl the documented API's base URL, sourced **only**
     *   from [ApipostRuleKeys.APIPOST_SERVER_URL] — no fallback. Passing the
     *   ApiPost open-API host here would make every imported endpoint point at
     *   ApiPost itself.
     */
    fun buildDocument(
        project: Project,
        endpoints: List<ApiEndpoint>,
        infoTitle: String,
        infoVersion: String,
        documentServerUrl: String?,
    ): Any = OpenApiDocumentFactory.build(
        project = project,
        endpoints = endpoints,
        infoTitle = infoTitle,
        infoVersion = infoVersion,
        serverUrl = documentServerUrl,
    )

    /** Serializes a handle produced by [buildDocument]. */
    fun toJson(document: Any): String = OpenApiSerializer.toJson(document as OpenApiDocument)
}
