package com.itangcent.easyapi.dashboard

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.config.resolveVariables
import com.itangcent.easyapi.config.DOUBLE_BRACE_PATTERN
import com.itangcent.easyapi.config.DOLLAR_BRACE_PATTERN
import com.itangcent.easyapi.dashboard.env.EnvironmentService
import com.itangcent.easyapi.dashboard.script.*
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.GrpcMetadata
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.grpcMetadata
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.model.isGrpc
import com.itangcent.easyapi.grpc.DynamicJarClient
import com.itangcent.easyapi.http.*
import com.itangcent.easyapi.logging.IdeaConsoleProvider
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.core.threading.readSync
import kotlinx.coroutines.CancellationException
import kotlin.system.measureTimeMillis

/**
 * Pure-logic executor for HTTP and gRPC requests.
 *
 * This class encapsulates the full request execution pipeline:
 * 1. Variable resolution (host, path, headers, body)
 * 2. Pre-request script execution (modifies request before sending)
 * 3. HTTP/gRPC request execution
 * 4. Post-response script execution (test assertions)
 *
 * It has **no UI dependencies** — the caller (typically [EndpointDetailsPanel])
 * is responsible for reading UI state into [HttpRequestInput] and handling
 * the [RequestResult] output on the Swing thread.
 *
 * ## Usage
 * ```kotlin
 * val executor = RequestExecutor.getInstance(project)
 * val input = HttpRequestInput(
 *     host = "https://api.example.com",
 *     path = "/users/{id}",
 *     method = "GET",
 *     pathParams = listOf("id" to "42"),
 *     headers = listOf("Authorization" to "Bearer token"),
 *     query = emptyList(),
 *     body = null,
 *     formParams = emptyList(),
 *     contentType = null,
 *     preRequestScript = null,
 *     postResponseScript = null,
 *     endpointName = "Get User",
 *     endpointKey = "user-controller-getUser"
 * )
 * val result = executor.executeHttp(input)
 * ```
 *
 * @see HttpRequestInput for the input data class
 * @see GrpcRequestInput for gRPC request input
 * @see RequestResult for the output data class
 */
@Service(Service.Level.PROJECT)
class RequestExecutor(private val project: Project) : IdeaLog {

    private val httpClient: HttpClient get() = HttpClientProvider.getInstance(project).getClient()

    private val DYNAMIC_VARIABLE_PATTERN = Regex("\\{\\{\\$(\\w+)}}")

    private val configReader: ConfigReader get() = ConfigReader.getInstance(project)
    private val environmentService: EnvironmentService get() = EnvironmentService.getInstance(project)
    private val scriptCacheService: ScriptCacheService get() = ScriptCacheService.getInstance(project)
    private val pmScriptExecutor: PmScriptExecutor get() = PmScriptExecutor.getInstance(project)
    private val console by lazy { IdeaConsoleProvider.getInstance(project).getConsole() }

    private val envVars: Map<String, String> get() = environmentService.resolveAllVariables()

    private fun resolveScripts(input: HttpRequestInput): ResolvedScripts {
        val scopes = input.scriptScopes
        val scopeResolved = if (scopes.isNotEmpty()) {
            scriptCacheService.resolveScripts(scopes)
        } else {
            ResolvedScripts()
        }

        val endpointPre = input.preRequestScript?.takeIf { it.isNotBlank() }
        val endpointPost = input.postResponseScript?.takeIf { it.isNotBlank() }

        val preParts = mutableListOf<String>()
        scopeResolved.preRequestScript?.let { preParts.add(it) }
        endpointPre?.let { preParts.add(it) }

        val postParts = mutableListOf<String>()
        endpointPost?.let { postParts.add(it) }
        scopeResolved.postResponseScript?.let { postParts.add(it) }

        return ResolvedScripts(
            preRequestScript = preParts.takeIf { it.isNotEmpty() }?.joinToString("\n\n"),
            postResponseScript = postParts.takeIf { it.isNotEmpty() }?.joinToString("\n\n")
        )
    }

    private fun resolveVariables(input: String): String {
        var result = input
        val envMap = envVars
        if (envMap.isNotEmpty()) {
            result = DOUBLE_BRACE_PATTERN.replace(result) { match ->
                val key = match.groupValues[1].trim()
                envMap[key] ?: match.value
            }
            result = DOLLAR_BRACE_PATTERN.replace(result) { match ->
                val key = match.groupValues[1].trim()
                envMap[key] ?: match.value
            }
        }
        result = DYNAMIC_VARIABLE_PATTERN.replace(result) { match ->
            val varName = match.groupValues[1]
            DynamicVariables.resolve(varName) ?: match.value
        }
        return configReader.resolveVariables(result)
    }

    /**
     * Executes an HTTP request with the full script pipeline.
     *
     * The pipeline is:
     * 1. Resolve variables in host, path, headers, query, body
     * 2. If a pre-request script is provided, execute it to modify the request
     * 3. Execute the HTTP request
     * 4. If a post-response script is provided, execute it for test assertions
     * 5. Return the result with optional test results
     *
     * @param input The resolved request input (no UI references)
     * @return The request result including body, status, headers, and optional test results
     */
    suspend fun executeHttp(input: HttpRequestInput): RequestResult {
        return try {
            val resolvedHost = resolveVariables(input.host)

            val resolvedPathParams = input.pathParams.map { (name, value) ->
                name to resolveVariables(value)
            }
            val resolvedPath = EndpointDetailsPanelLogic.resolvePath(input.path, resolvedPathParams)
            val fullUrl = resolvedHost + (if (resolvedPath.startsWith("/")) resolvedPath else "/$resolvedPath")

            LOG.info("HTTP request to $fullUrl")

            val headers = input.headers.map {
                KeyValue(resolveVariables(it.name), resolveVariables(it.value))
            }
            val query = input.query.map {
                KeyValue(resolveVariables(it.name), resolveVariables(it.value))
            }

            val formParams: List<FormParam>
            val body: String?
            val finalHeaders: List<KeyValue>
            if (input.hasFormData) {
                formParams = input.formParams.map { param ->
                    when (param) {
                        is FormParam.Text -> FormParam.Text(
                            resolveVariables(param.name),
                            resolveVariables(param.value)
                        )
                        is FormParam.File -> param
                    }
                }
                body = null
                finalHeaders = headers
            } else {
                formParams = emptyList()
                body = input.body?.takeIf { it.isNotBlank() }?.let {
                    resolveVariables(it)
                }
                finalHeaders = headers
            }

            val method = input.method

            val pmRequest = PmRequest(
                url = fullUrl,
                method = method,
                body = PmRequestBody(raw = body)
            )
            finalHeaders.forEach { pmRequest.headers.add(it.name, it.value) }

            val resolvedScripts = resolveScripts(input)

            if (!resolvedScripts.preRequestScript.isNullOrBlank() || !resolvedScripts.postResponseScript.isNullOrBlank()) {
                executeWithScripts(
                    input, pmRequest, fullUrl, method, finalHeaders, query, body, formParams, resolvedScripts
                )
            } else {
                executeWithoutScripts(fullUrl, method, finalHeaders, query, body, formParams, input.contentType)
            }
        } catch (_: CancellationException) {
            LOG.debug("Request cancelled")
            RequestResult(body = "Request cancelled", isError = true)
        } catch (e: Exception) {
            LOG.warn("Request failed: ${e.message}", e)
            RequestResult(body = "Error: ${e.message}", isError = true)
        }
    }

    private suspend fun executeWithScripts(
        input: HttpRequestInput,
        pmRequest: PmRequest,
        originalUrl: String,
        originalMethod: String,
        originalHeaders: List<KeyValue>,
        query: List<KeyValue>,
        originalBody: String?,
        formParams: List<FormParam>,
        resolvedScripts: ResolvedScripts
    ): RequestResult {
        val scopeDesc = input.scriptScopes.joinToString(" → ") { it.displayLabel() }
        LOG.info("Executing request with scripts for [${input.endpointName}], scopes: [$scopeDesc]")
        console.info("[Script] Executing request for '${input.endpointName}' with scripts (scopes: $scopeDesc)")

        val envVars = LivePmVariableScope(environmentService)
        val globalVars = PmVariableScope()
        val collectionVars = PmVariableScope()
        val testCollector = PmTestCollector()
        val info = PmInfo(
            eventName = "prerequest",
            requestName = input.endpointName,
            requestId = input.endpointKey
        )
        val pm = PmObject.forPreRequest(
            request = pmRequest,
            environment = envVars,
            globals = globalVars,
            collectionVariables = collectionVars,
            testCollector = testCollector,
            info = info,
            httpClient = httpClient
        )

        if (!resolvedScripts.preRequestScript.isNullOrBlank()) {
            val preScriptLines = resolvedScripts.preRequestScript.lines().size
            LOG.info("Pre-request script: $preScriptLines lines, scopes: [$scopeDesc]")
            console.info("[Script] Running pre-request script ($preScriptLines lines)...")
            try {
                val elapsed = measureTimeMillis {
                    pmScriptExecutor.executePreRequestScript(resolvedScripts.preRequestScript, pm)
                }
                LOG.info("Pre-request script completed in ${elapsed}ms")
                console.info("[Script] Pre-request script completed (${elapsed}ms)")
            } catch (e: Exception) {
                LOG.warn("Pre-request script error: ${e.message}", e)
                console.error("[Script] Pre-request script failed: ${e.message}")
            }
        } else {
            LOG.info("No pre-request script to execute")
        }

        val modifiedUrl = pmRequest.url.ifBlank { originalUrl }
        val modifiedHeaders = pmRequest.headers.toPairs().map { KeyValue(it.first, it.second) }
        val modifiedBody = pmRequest.body.raw

        val request = HttpRequest(
            url = modifiedUrl,
            method = pmRequest.method.ifBlank { originalMethod },
            headers = modifiedHeaders.ifEmpty { originalHeaders },
            query = query,
            body = modifiedBody ?: originalBody,
            formParams = formParams,
            contentType = input.contentType
        )
        LOG.info("Request (after pre-script): ${request.method} ${request.url}")
        val response = httpClient.execute(request)
        LOG.info("Response: status=${response.code}, bodyLength=${response.body?.length ?: 0}")

        val pmResponse = PmResponse(
            code = response.code,
            status = "",
            headers = PmHeaderList(response.headers.map { (k, v) -> k to v.joinToString(", ") }),
            responseTime = 0,
            responseSize = response.body?.length?.toLong() ?: 0,
            rawBody = response.body ?: ""
        )

        var testResults: List<TestResult>? = null
        if (!resolvedScripts.postResponseScript.isNullOrBlank()) {
            val postScriptLines = resolvedScripts.postResponseScript.lines().size
            LOG.info("Post-response script: $postScriptLines lines, scopes: [$scopeDesc]")
            console.info("[Script] Running post-response script ($postScriptLines lines)...")
            val postTestCollector = PmTestCollector()
            val postInfo = PmInfo(
                eventName = "test",
                requestName = input.endpointName,
                requestId = input.endpointKey
            )
            val postPm = PmObject.forPostResponse(
                request = pmRequest,
                response = pmResponse,
                environment = envVars,
                globals = globalVars,
                collectionVariables = collectionVars,
                testCollector = postTestCollector,
                cookies = PmCookies(),
                info = postInfo,
                httpClient = httpClient
            )
            try {
                val elapsed = measureTimeMillis {
                    testResults = pmScriptExecutor.executePostResponseScript(resolvedScripts.postResponseScript, postPm)
                }
                LOG.info("Post-response script completed in ${elapsed}ms, tests: ${testResults?.size ?: 0}")
                console.info("[Script] Post-response script completed (${elapsed}ms, ${testResults?.size ?: 0} tests)")
                testResults?.forEach { tr ->
                    if (tr.passed) {
                        console.info("[Script]   ✓ ${tr.name}")
                    } else {
                        console.error("[Script]   ✗ ${tr.name}: ${tr.error ?: "failed"}")
                    }
                }
            } catch (e: Exception) {
                LOG.warn("Post-response script error: ${e.message}", e)
                console.error("[Script] Post-response script failed: ${e.message}")
            }
        } else {
            LOG.info("No post-response script to execute")
        }

        return RequestResult(
            body = response.body ?: "",
            isError = response.code !in 200..299,
            statusCode = response.code,
            headers = response.headers.map { (k, v) -> k to v.joinToString(", ") },
            testResults = testResults
        )
    }

    private suspend fun executeWithoutScripts(
        fullUrl: String,
        method: String,
        headers: List<KeyValue>,
        query: List<KeyValue>,
        body: String?,
        formParams: List<FormParam>,
        contentType: String?
    ): RequestResult {
        val request = HttpRequest(
            url = fullUrl,
            method = method,
            headers = headers,
            query = query,
            body = body,
            formParams = formParams,
            contentType = contentType
        )
        LOG.debug("Request: ${request.method} ${request.url}, headers=${request.headers.size}, hasBody=${request.body != null}")
        val response = httpClient.execute(request)
        LOG.info("Response: status=${response.code}, bodyLength=${response.body?.length ?: 0}")

        return RequestResult(
            body = response.body ?: "",
            isError = response.code !in 200..299,
            statusCode = response.code,
            headers = response.headers.map { (k, v) -> k to v.joinToString(", ") }
        )
    }

    /**
     * Executes a gRPC request.
     *
     * @param input The gRPC request input
     * @return The request result
     */
    suspend fun executeGrpc(input: GrpcRequestInput): RequestResult {
        val meta = input.grpcMetadata
        LOG.info("gRPC request: endpoint=${input.endpointName}, path=${meta.path}")

        val resolvedHost = resolveVariables(input.host)
        val grpcClient = DynamicJarClient.getInstance(project)

        if (!grpcClient.isAvailable()) {
            LOG.warn("gRPC client not available for request to ${meta.path}")
            return RequestResult(
                body = "Error: gRPC client not available",
                isError = true,
                requiresGrpcSetup = true
            )
        }

        val body = input.body?.takeIf { it.isNotBlank() }?.let { txt ->
            resolveVariables(txt)
        }
        LOG.info("gRPC request details: host=$resolvedHost, bodyLength=${body?.length ?: 0}")

        return try {
            val sm = input.sourceMethod?.let { method -> readSync { method } }
            val grpcResult = if (sm != null) {
                grpcClient.invoke(resolvedHost, meta.path, body, sm)
            } else {
                grpcClient.invoke(resolvedHost, meta.path, body)
            }

            LOG.info("gRPC response received: endpoint=${meta.path}, isError=${grpcResult.isError}, statusCode=${grpcResult.statusCode}, length=${grpcResult.body.length}")

            RequestResult(
                body = grpcResult.body,
                isError = grpcResult.isError,
                statusCode = grpcResult.statusCode,
                headers = if (grpcResult.statusName != null) listOf("grpc-status" to grpcResult.statusName) else emptyList()
            )
        } catch (e: Exception) {
            LOG.warn("gRPC request failed with exception: ${e.message}", e)
            RequestResult(body = "Error: ${e.message ?: e.javaClass.simpleName}", isError = true)
        }
    }

    companion object : IdeaLog {
        fun getInstance(project: Project): RequestExecutor = project.service()
    }
}

/**
 * Immutable input for an HTTP request execution.
 *
 * All fields should be pre-populated from UI state by the caller.
 * Variable resolution ($timestamp, etc.) is handled by [RequestExecutor].
 *
 * @property host The host URL (e.g., "https://api.example.com")
 * @property path The path template (e.g., "/users/{id}")
 * @property method The HTTP method (GET, POST, etc.)
 * @property pathParams Path parameter name-value pairs
 * @property headers Header name-value pairs
 * @property query Query parameter name-value pairs
 * @property body The request body (for JSON/XML), null if using form data
 * @property formParams Form parameters (text fields and file uploads)
 * @property hasFormData Whether the request uses multipart/form-data
 * @property contentType Optional content-type override
 * @property preRequestScript Optional pre-request Groovy script
 * @property postResponseScript Optional post-response Groovy script
 * @property endpointName Display name of the endpoint
 * @property endpointKey Unique key for the endpoint (used in script context)
 */
data class HttpRequestInput(
    val host: String,
    val path: String,
    val method: String,
    val pathParams: List<Pair<String, String>> = emptyList(),
    val headers: List<KeyValue> = emptyList(),
    val query: List<KeyValue> = emptyList(),
    val body: String? = null,
    val formParams: List<FormParam> = emptyList(),
    val hasFormData: Boolean = false,
    val contentType: String? = null,
    val preRequestScript: String? = null,
    val postResponseScript: String? = null,
    val endpointName: String = "",
    val endpointKey: String = "",
    val scriptScopes: List<ScriptScope> = emptyList()
)

/**
 * Immutable input for a gRPC request execution.
 *
 * @property host The gRPC server host
 * @property grpcMetadata The gRPC method metadata
 * @property body The request body (JSON)
 * @property endpointName Display name of the endpoint
 * @property sourceMethod Optional PSI method for reflection-based invocation
 */
data class GrpcRequestInput(
    val host: String,
    val grpcMetadata: GrpcMetadata,
    val body: String? = null,
    val endpointName: String = "",
    val sourceMethod: com.intellij.psi.PsiMethod? = null
)

/**
 * Result of a request execution.
 *
 * This is a UI-independent representation of the response.
 * The caller is responsible for rendering it in the UI.
 *
 * @property body The response body text
 * @property isError Whether the response indicates an error
 * @property statusCode The HTTP status code or gRPC status code
 * @property headers The response headers as name-value pairs
 * @property testResults Optional test results from post-response script execution
 * @property requiresGrpcSetup True if gRPC client is not configured (UI should prompt setup)
 */
data class RequestResult(
    val body: String,
    val isError: Boolean,
    val statusCode: Int? = null,
    val headers: List<Pair<String, String>> = emptyList(),
    val testResults: List<TestResult>? = null,
    val requiresGrpcSetup: Boolean = false
)
