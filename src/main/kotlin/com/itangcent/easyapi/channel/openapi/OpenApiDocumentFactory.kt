package com.itangcent.easyapi.channel.openapi

import com.itangcent.easyapi.core.export.ApiEndpoint
import com.intellij.openapi.project.Project

/**
 * Public entry point for building an [OpenApiDocument] from outside this package.
 *
 * [OpenApiEnvelope] and [OpenApiFormatter.format] are `internal` — they are
 * package implementation details. Other channels must go through this façade
 * (or their own single adapter class) instead of binding to those internals,
 * so the envelope/formatter contract can evolve without breaking consumers.
 *
 * Deliberately thin: the formatter stays pure (no rule engine, no I/O), and rule
 * resolution of `info.*` / `server.url` remains the caller's responsibility —
 * the same division of labour [OpenApiChannel] uses.
 *
 * Serialization is not part of this façade; use the already-public
 * [OpenApiSerializer.toJson] / [OpenApiSerializer.toYaml].
 */
object OpenApiDocumentFactory {

    /**
     * Builds a document from [endpoints].
     *
     * Non-HTTP endpoints are skipped by the formatter (defensively — callers
     * should filter first, as [OpenApiChannel.export] does).
     *
     * @param infoTitle OAS-required `info.title`; never null (falls back to the
     *   project name in [OpenApiChannel], but this façade trusts the caller).
     * @param infoVersion OAS-required `info.version`.
     * @param infoDescription optional; omitted from the output when null.
     * @param serverUrl document-level `servers[0].url`; **the URL of the API
     *   being documented**, not of any export target. Null omits `servers`.
     */
    fun build(
        project: Project,
        endpoints: List<ApiEndpoint>,
        infoTitle: String,
        infoVersion: String = "1.0.0",
        infoDescription: String? = null,
        serverUrl: String? = null,
    ): OpenApiDocument = OpenApiFormatter(project).format(
        endpoints,
        OpenApiEnvelope(
            infoTitle = infoTitle,
            infoVersion = infoVersion,
            infoDescription = infoDescription,
            serverUrl = serverUrl,
        ),
    )
}
