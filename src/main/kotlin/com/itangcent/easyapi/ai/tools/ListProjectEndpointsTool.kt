package com.itangcent.easyapi.ai.tools

import com.itangcent.easyapi.cache.api.ApiIndex
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.util.json.GsonUtils

/**
 * Perception tool that lists cached API endpoints in the project.
 *
 * Returns a JSON array of endpoint summaries. v1 returns HTTP endpoints only;
 * gRPC endpoints are filtered out to keep the schema simple — the agent can be
 * taught gRPC in a later revision. Returns "cache not ready" if the initial
 * scan hasn't completed; the agent can retry on the next turn.
 */
class ListProjectEndpointsTool : AiTool {

    override val name: String = "list_project_endpoints"

    override val description: String =
        "List HTTP API endpoints cached for the project. Returns a JSON array of " +
            "{className, name, httpMethod, path}. v1 returns HTTP only (gRPC " +
            "filtered). Returns \"cache not ready\" if the initial scan hasn't " +
            "completed; retry on the next turn."

    override val kind: ToolKind = ToolKind.PERCEPTION

    override val parametersSchema: Map<String, Any?> = emptyMap()

    override suspend fun execute(args: Map<String, Any?>, ctx: ToolContext): ToolResult {
        val apiIndex = ApiIndex.getInstance(ctx.project)
        if (!apiIndex.isReady()) {
            return ToolResult.Text("cache not ready")
        }
        val endpoints = apiIndex.endpoints()
        val summaries = endpoints.mapNotNull { it.toHttpSummary() }
        return ToolResult.Text(GsonUtils.toJson(summaries))
    }

    private fun ApiEndpoint.toHttpSummary(): Map<String, Any?>? {
        val meta = metadata as? HttpMetadata ?: return null
        return mapOf(
            "className" to (className ?: sourceClass?.qualifiedName),
            "name" to name,
            "httpMethod" to meta.method.name,
            "path" to meta.path
        )
    }
}
