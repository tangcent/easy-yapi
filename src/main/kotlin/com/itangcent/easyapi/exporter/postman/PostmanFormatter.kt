package com.itangcent.easyapi.exporter.postman

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.exporter.model.ParameterType
import com.itangcent.easyapi.exporter.postman.model.*
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.model.ObjectModelJsonConverter
import com.itangcent.easyapi.rule.RuleKeys
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.settings.PostmanJson5FormatType
import com.itangcent.easyapi.util.GsonUtils
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * Context for formatting an API endpoint into a Postman item.
 *
 * @param endpoint The API endpoint to format
 * @param responses Response data for the endpoint
 * @param preRequestScript Pre-request script to execute
 * @param testScript Test script to execute
 * @param psiElement The source PSI element
 * @param psiClass The containing PSI class
 */
data class PostmanEndpointContext(
    val endpoint: ApiEndpoint,
    val responses: List<PostmanResponseData> = emptyList(),
    val preRequestScript: String? = null,
    val testScript: String? = null,
    val psiElement: PsiElement? = null,
    val psiClass: PsiClass? = null
)

/**
 * Response data for a Postman request.
 *
 * @param name Response name
 * @param statusCode HTTP status code
 * @param headers Response headers
 * @param body Response body model
 * @param description Response description
 */
data class PostmanResponseData(
    val name: String? = null,
    val statusCode: Int? = null,
    val headers: List<PostmanHeaderData> = emptyList(),
    val body: ObjectModel? = null,
    val description: String? = null
)

/**
 * Header data for a Postman request or response.
 *
 * @param name Header name
 * @param value Header value
 */
data class PostmanHeaderData(
    val name: String,
    val value: String? = null
)

/**
 * Options for formatting Postman collections.
 *
 * @param buildExample Whether to build example responses
 * @param autoMergeScript Whether to automatically merge scripts
 * @param wrapCollection Whether to wrap items in a collection folder
 * @param json5FormatType Format type for JSON5 bodies
 * @param defaultHost Default host variable for URLs
 * @param appendTimestamp Whether to append timestamp to collection name
 */
data class PostmanFormatOptions(
    val buildExample: Boolean = false,
    val autoMergeScript: Boolean = true,
    val wrapCollection: Boolean = false,
    val json5FormatType: PostmanJson5FormatType = PostmanJson5FormatType.EXAMPLE_ONLY,
    val defaultHost: String = "{{host}}",
    val appendTimestamp: Boolean = true
)

/**
 * Formats API endpoints into Postman collections.
 *
 * Handles:
 * - Endpoint grouping by folder
 * - URL and path construction
 * - Parameter formatting (query, path, body)
 * - Header and authentication
 * - Pre-request and test scripts
 * - Response examples
 *
 * @see PostmanExporter for the export workflow
 * @see PostmanScriptMerger for script merging logic
 */
class PostmanFormatter(
    private val actionContext: ActionContext,
    private val options: PostmanFormatOptions = PostmanFormatOptions(),
    private val systemTimeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val ruleEngine: RuleEngine by lazy { RuleEngine.getInstance(actionContext) }

    suspend fun format(endpoints: List<ApiEndpoint>, moduleName: String): PostmanCollection {
        val contexts = endpoints.flatMap { endpoint ->
            val paths = endpoint.alternativePaths
            if (paths.isNullOrEmpty()) {
                listOf(endpoint.toContext())
            } else {
                paths.map { path ->
                    endpoint.copy(path = path).toContext()
                }
            }
        }
        return formatWithContext(contexts, moduleName)
    }

    private fun ApiEndpoint.toContext(): PostmanEndpointContext = PostmanEndpointContext(
        endpoint = this,
        psiElement = this.sourceMethod ?: this.sourceClass,
        psiClass = this.sourceClass
    )

    suspend fun formatWithContext(
        contexts: List<PostmanEndpointContext>,
        moduleName: String
    ): PostmanCollection {
        val grouped = contexts.groupBy { it.endpoint.folder ?: "" }
        val folderItems = grouped.map { (folder, list) ->
            if (folder.isBlank()) {
                PostmanItem(name = moduleName, item = list.map { toItemWithContext(it) })
            } else {
                PostmanItem(name = folder, item = list.map { toItemWithContext(it) }, description = "")
            }
        }

        // When wrapCollection is false and there's only one folder, flatten it
        // (use the folder's items directly instead of wrapping in an extra level)
        val items = if (!options.wrapCollection && folderItems.size == 1) {
            val singleFolder = folderItems.first()
            // If the single folder has sub-items (endpoints), use them directly
            if (singleFolder.item.isNotEmpty() && singleFolder.request == null) {
                singleFolder.item
            } else {
                folderItems
            }
        } else {
            folderItems
        }

        val collectionEvents = buildCollectionEvents(contexts)
        val events = if (options.autoMergeScript) {
            PostmanScriptMerger.merge(collectionEvents)
        } else {
            collectionEvents
        }

        val collectionName = if (options.appendTimestamp) {
            "${moduleName}-${formatTimestamp(systemTimeProvider())}"
        } else {
            moduleName
        }

        return PostmanCollection(
            info = CollectionInfo(
                name = collectionName,
                description = "exported at ${formatYMD_HMS(systemTimeProvider())}"
            ),
            item = items,
            event = events
        )
    }

    private suspend fun buildCollectionEvents(contexts: List<PostmanEndpointContext>): List<PostmanEvent> {
        val events = mutableListOf<PostmanEvent>()

        val preRequestScripts = mutableListOf<String>()
        val testScripts = mutableListOf<String>()

        for (ctx in contexts) {
            val psiClass = ctx.psiClass ?: (ctx.psiElement as? PsiClass)
            val psiMethod = ctx.psiElement as? PsiMethod

            if (psiMethod != null) {
                ruleEngine.evaluate(RuleKeys.POSTMAN_PREREQUEST, psiMethod)
                    ?.takeIf { it.isNotBlank() }?.let { preRequestScripts.add(it) }
                ruleEngine.evaluate(RuleKeys.POSTMAN_TEST, psiMethod)
                    ?.takeIf { it.isNotBlank() }?.let { testScripts.add(it) }
            }

            if (psiClass != null) {
                ruleEngine.evaluate(RuleKeys.POSTMAN_CLASS_PREREQUEST, psiClass)
                    ?.takeIf { it.isNotBlank() }?.let { preRequestScripts.add(it) }
                ruleEngine.evaluate(RuleKeys.POSTMAN_CLASS_TEST, psiClass)
                    ?.takeIf { it.isNotBlank() }?.let { testScripts.add(it) }
            }
        }

        ruleEngine.evaluate(RuleKeys.POSTMAN_COLLECTION_PREREQUEST) { ctx ->
            ctx.setExt("collection", contexts)
        }
        ruleEngine.evaluate(RuleKeys.POSTMAN_COLLECTION_TEST) { ctx ->
            ctx.setExt("collection", contexts)
        }

        if (preRequestScripts.isNotEmpty()) {
            events.add(
                PostmanEvent(
                    listen = "prerequest",
                    script = PostmanScript(exec = preRequestScripts.flatMap {
                        it.lines().filter { l -> l.isNotBlank() }
                    })
                )
            )
        }

        if (testScripts.isNotEmpty()) {
            events.add(
                PostmanEvent(
                    listen = "test",
                    script = PostmanScript(exec = testScripts.flatMap { it.lines().filter { l -> l.isNotBlank() } })
                )
            )
        }

        return events
    }

    suspend fun toItem(endpoint: ApiEndpoint): PostmanItem {
        return toItemWithContext(PostmanEndpointContext(endpoint))
    }

    suspend fun toItemWithContext(context: PostmanEndpointContext): PostmanItem {
        val endpoint = context.endpoint
        val hostVar = resolveHost(context)
        val url = buildUrl(endpoint, hostVar)
        val headers = endpoint.headers.map {
            PostmanHeader(
                key = it.name,
                value = it.value ?: "",
                type = "text",
                description = ""
            )
        }
        val body = buildBody(endpoint)
        val request = PostmanRequest(
            method = endpoint.method.name,
            header = headers,
            body = body,
            url = url,
            description = endpoint.description ?: ""
        )
        val responses = if (options.buildExample && context.responses.isNotEmpty()) {
            context.responses.mapIndexed { index, response ->
                buildResponse(endpoint.name, request, response, index)
            }
        } else emptyList()

        val events = buildEvents(context)

        val item = PostmanItem(
            name = endpoint.name ?: "${endpoint.method.name} ${endpoint.path}",
            request = request,
            response = responses,
            event = events
        )

        fireEvent(context, item)

        return item
    }

    private suspend fun resolveHost(context: PostmanEndpointContext): String {
        val psiClass = context.psiClass ?: (context.psiElement as? PsiClass)
        val hostByRule = if (psiClass != null) {
            ruleEngine.evaluate(RuleKeys.POSTMAN_HOST, psiClass)
        } else {
            // Fallback: evaluate without element context for global/builtin rules
            ruleEngine.evaluate(RuleKeys.POSTMAN_HOST)
        }
        if (!hostByRule.isNullOrBlank()) {
            return hostByRule
        }
        return options.defaultHost
    }

    private suspend fun buildEvents(context: PostmanEndpointContext): List<PostmanEvent> {
        val events = mutableListOf<PostmanEvent>()

        val explicitPreRequest = context.preRequestScript?.takeIf { it.isNotBlank() }
        val explicitTest = context.testScript?.takeIf { it.isNotBlank() }

        val rulePreRequest = context.psiElement?.let { element ->
            ruleEngine.evaluate(RuleKeys.POSTMAN_PREREQUEST, element)
        }?.takeIf { it.isNotBlank() }

        val ruleTest = context.psiElement?.let { element ->
            ruleEngine.evaluate(RuleKeys.POSTMAN_TEST, element)
        }?.takeIf { it.isNotBlank() }

        val preRequestScripts = mutableListOf<String>()
        explicitPreRequest?.let { preRequestScripts.add(it) }
        rulePreRequest?.let { preRequestScripts.add(it) }

        val testScripts = mutableListOf<String>()
        explicitTest?.let { testScripts.add(it) }
        ruleTest?.let { testScripts.add(it) }

        if (preRequestScripts.isNotEmpty()) {
            events.add(
                PostmanEvent(
                    listen = "prerequest",
                    script = PostmanScript(exec = preRequestScripts.flatMap {
                        it.lines().filter { l -> l.isNotBlank() }
                    })
                )
            )
        }

        if (testScripts.isNotEmpty()) {
            events.add(
                PostmanEvent(
                    listen = "test",
                    script = PostmanScript(exec = testScripts.flatMap { it.lines().filter { l -> l.isNotBlank() } })
                )
            )
        }

        return events
    }

    private suspend fun fireEvent(context: PostmanEndpointContext, item: PostmanItem) {
        context.psiElement?.let { element ->
            ruleEngine.evaluate(RuleKeys.POSTMAN_FORMAT_AFTER, element) { ctx ->
                ctx.setExt("item", item)
                ctx.setExt("endpoint", context.endpoint)
            }
        }
    }

    private fun buildResponse(
        endpointName: String?,
        request: PostmanRequest,
        response: PostmanResponseData,
        index: Int
    ): PostmanResponse {
        val exampleName = "${endpointName ?: "Example"}-Example${if (index > 0) (index + 1) else ""}"

        val responseHeaders = mutableListOf<PostmanHeader>()

        if (response.headers.none { it.name.equals("content-type", ignoreCase = true) }) {
            responseHeaders.add(
                PostmanHeader(
                    name = "content-type",
                    key = "content-type",
                    value = "application/json;charset=UTF-8",
                    description = "The mime type of this content"
                )
            )
        }

        if (response.headers.none { it.name.equals("date", ignoreCase = true) }) {
            responseHeaders.add(
                PostmanHeader(
                    name = "date",
                    key = "date",
                    value = formatDateGMT(systemTimeProvider()),
                    description = "The date and time that the message was sent"
                )
            )
        }

        if (response.headers.none { it.name.equals("server", ignoreCase = true) }) {
            responseHeaders.add(
                PostmanHeader(
                    name = "server",
                    key = "server",
                    value = "Apache-Coyote/1.1",
                    description = "A name for the server"
                )
            )
        }

        if (response.headers.none { it.name.equals("transfer-encoding", ignoreCase = true) }) {
            responseHeaders.add(
                PostmanHeader(
                    name = "transfer-encoding",
                    key = "transfer-encoding",
                    value = "chunked",
                    description = "The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity."
                )
            )
        }

        response.headers.forEach {
            responseHeaders.add(
                PostmanHeader(
                    name = it.name,
                    key = it.name,
                    value = it.value ?: ""
                )
            )
        }

        return PostmanResponse(
            name = exampleName,
            originalRequest = request,
            status = "OK",
            code = response.statusCode ?: 200,
            header = responseHeaders,
            body = response.body?.let {
                val useJson5 = options.json5FormatType.needUseJson5(RESPONSE_BODY_TYPE)
                if (useJson5) {
                    ObjectModelJsonConverter.toJson5(it)
                } else {
                    ObjectModelJsonConverter.toJson(it)
                }
            } ?: "",
            _postman_previewlanguage = "json"
        )
    }

    private fun buildUrl(endpoint: ApiEndpoint, hostVar: String): PostmanUrl {
        val query = endpoint.parameters
            .filter { it.binding == ParameterBinding.Query }
            .map {
                PostmanQuery(
                    key = it.name,
                    value = it.example ?: it.defaultValue ?: "",
                    equals = true,
                    description = it.description
                )
            }

        val pathSegments = parsePath(endpoint.path)

        val pathVariables = endpoint.parameters
            .filter { it.binding == ParameterBinding.Path }
            .map {
                PostmanPathVariable(
                    key = it.name,
                    value = it.example ?: it.defaultValue ?: "",
                    description = it.description
                )
            }

        val queryString = if (query.isEmpty()) "" else "?" + query.joinToString("&") { "${it.key}=${it.value}" }
        val raw = hostVar.trimEnd('/') + "/" + pathSegments.joinToString("/") + queryString

        return PostmanUrl(
            raw = raw,
            host = listOf(hostVar),
            path = pathSegments,
            query = query,
            variable = pathVariables
        )
    }

    private fun buildBody(endpoint: ApiEndpoint): PostmanBody? {
        val contentType = endpoint.contentType?.lowercase().orEmpty()
        val bodyParams = endpoint.parameters.filter { it.binding == ParameterBinding.Body }
        val formParams = endpoint.parameters.filter { it.binding == ParameterBinding.Form }

        if (contentType.contains("json")) {
            val useJson5 = options.json5FormatType.needUseJson5(REQUEST_BODY_TYPE)
            val raw = when {
                endpoint.body != null -> if (useJson5) {
                    ObjectModelJsonConverter.toJson5(endpoint.body)
                } else {
                    ObjectModelJsonConverter.toJson(endpoint.body)
                }

                bodyParams.isNotEmpty() -> bodyParams.associate { it.name to (it.example ?: it.defaultValue ?: "") }
                    .let { GsonUtils.prettyJson(it) }

                else -> "{}"
            }
            return PostmanBody(
                mode = "raw",
                raw = raw,
                options = mapOf("raw" to mapOf("language" to "json"))
            )
        }

        if (contentType.contains("x-www-form-urlencoded")) {
            return PostmanBody(
                mode = "urlencoded",
                urlencoded = formParams.map {
                    PostmanFormParam(
                        key = it.name,
                        value = it.example ?: it.defaultValue ?: "",
                        type = it.type.rawType(),
                        description = it.description
                    )
                }
            )
        }

        if (contentType.contains("multipart") || contentType.contains("form-data")) {
            return PostmanBody(
                mode = "formdata",
                formdata = formParams.map {
                    PostmanFormParam(
                        key = it.name,
                        value = it.example ?: it.defaultValue ?: "",
                        type = it.type.rawType(),
                        description = it.description
                    )
                }
            )
        }

        if (endpoint.body != null) {
            val useJson5 = options.json5FormatType.needUseJson5(REQUEST_BODY_TYPE)
            return PostmanBody(
                mode = "raw",
                raw = if (useJson5) {
                    ObjectModelJsonConverter.toJson5(endpoint.body)
                } else {
                    ObjectModelJsonConverter.toJson(endpoint.body)
                },
                options = mapOf("raw" to mapOf("language" to "json"))
            )
        }

        return null
    }

    fun buildScript(listen: String, content: String?): PostmanEvent? {
        val lines = content?.lines()?.filter { it.isNotBlank() } ?: return null
        return PostmanEvent(listen = listen, script = PostmanScript(exec = lines))
    }

    companion object {
        /** Type flag for request body formatting */
        const val REQUEST_BODY_TYPE = 1

        /** Type flag for response body formatting */
        const val RESPONSE_BODY_TYPE = 8

        fun parsePath(path: String): List<String> {
            val paths = path.trim().trim('/').split("/")
            return paths.map { it.resolvePathVariable() }
        }

        private fun String.resolvePathVariable(): String {
            if (!contains('{')) {
                return this
            }
            val p = if (contains(':')) {
                substring(0, indexOf(':'))
            } else {
                this
            }
            return p.replace("{", ":").replace("}", "")
        }

        private fun formatDateGMT(timestamp: Long): String {
            val instant = Instant.ofEpochMilli(timestamp)
            val formatter = DateTimeFormatter
                .ofPattern("EEE, dd MMM yyyyHH:mm:ss 'GMT'")
                .withZone(ZoneId.of("GMT"))
            return formatter.format(instant)
        }

        private fun formatTimestamp(timestamp: Long): String {
            val instant = Instant.ofEpochMilli(timestamp)
            val formatter = DateTimeFormatter
                .ofPattern("yyyyMMddHHmmss")
                .withZone(ZoneId.systemDefault())
            return formatter.format(instant)
        }

        private fun formatYMD_HMS(timestamp: Long): String {
            val instant = Instant.ofEpochMilli(timestamp)
            val formatter = DateTimeFormatter
                .ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
            return formatter.format(instant)
        }
    }
}
