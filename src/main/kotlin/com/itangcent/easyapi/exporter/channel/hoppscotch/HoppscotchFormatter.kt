package com.itangcent.easyapi.exporter.channel.hoppscotch

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.exporter.channel.PlaceholderSyntaxConverter
import com.itangcent.easyapi.exporter.channel.PlaceholderTargetSyntax
import com.itangcent.easyapi.exporter.channel.hoppscotch.model.*
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.exporter.model.httpMetadata
import com.itangcent.easyapi.exporter.model.isHttp
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.model.ObjectModelJsonConverter
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.util.json.GsonUtils

/**
 * Options for customizing the Hoppscotch collection output.
 *
 * @property defaultHost the default host URL used when no `hopp.host` rule is defined
 * @property appendTimestamp whether to append a timestamp to the collection name
 */
data class HoppscotchFormatOptions(
    val defaultHost: String = "https://<<host>>",
    val appendTimestamp: Boolean = true
)

/**
 * Converts [ApiEndpoint]s into a Hoppscotch [HoppCollection].
 *
 * ## Conversion logic
 * - Endpoints are grouped by folder name into nested [HoppCollection] sub-collections
 * - Each endpoint becomes a [HoppRESTRequest] with method, URL, params, headers, and body
 * - Request body format is determined by Content-Type: JSON, form-urlencoded, or multipart/form-data
 * - Pre-request and test scripts are resolved from rule keys ([HoppscotchRuleKeys])
 * - The host URL is resolved from `hopp.host` rule, falling back to [HoppscotchFormatOptions.defaultHost]
 * - Collection-level scripts are aggregated from `hopp.collection.prerequest` / `hopp.collection.test` rules
 *
 * @param project the IntelliJ project (required for [RuleEngine] access)
 * @param options formatting options
 * @param systemTimeProvider injectable time source (for testing)
 * @see HoppscotchChannel for the channel that uses this formatter
 * @see HoppscotchRuleKeys for available rule keys
 */
class HoppscotchFormatter(
    private val project: Project,
    private val options: HoppscotchFormatOptions = HoppscotchFormatOptions(),
    private val systemTimeProvider: () -> Long = { System.currentTimeMillis() }
) {
    private val ruleEngine: RuleEngine by lazy { RuleEngine.getInstance(project) }

    suspend fun format(endpoints: List<ApiEndpoint>, moduleName: String): HoppCollection {
        val httpEndpoints = endpoints.filter { it.isHttp }
        val grouped = httpEndpoints.groupBy { it.folder ?: "" }

        val folders = mutableListOf<HoppCollection>()
        val rootRequests = mutableListOf<HoppRESTRequest>()

        for ((folder, list) in grouped) {
            val requests = list.map { toRequest(it) }
            if (folder.isBlank()) {
                rootRequests.addAll(requests)
            } else {
                folders.add(
                    HoppCollection(
                        name = folder,
                        requests = requests
                    )
                )
            }
        }

        val collectionPreRequest = resolveCollectionScript(
            HoppscotchRuleKeys.HOPP_COLLECTION_PREREQUEST,
            HoppscotchRuleKeys.HOPP_CLASS_PREREQUEST,
            HoppscotchRuleKeys.HOPP_PREREQUEST,
            httpEndpoints
        )
        val collectionTest = resolveCollectionScript(
            HoppscotchRuleKeys.HOPP_COLLECTION_TEST,
            HoppscotchRuleKeys.HOPP_CLASS_TEST,
            HoppscotchRuleKeys.HOPP_TEST,
            httpEndpoints
        )

        val collectionName = if (options.appendTimestamp) {
            "$moduleName-${formatTimestamp(systemTimeProvider())}"
        } else {
            moduleName
        }

        return HoppCollection(
            name = collectionName,
            folders = folders,
            requests = rootRequests,
            preRequestScript = collectionPreRequest,
            testScript = collectionTest
        )
    }

    private suspend fun toRequest(endpoint: ApiEndpoint): HoppRESTRequest {
        val meta = endpoint.httpMetadata
            ?: throw IllegalArgumentException("HoppscotchFormatter only supports HTTP endpoints")

        val host = resolveHost(endpoint)
        val endpointUrl = buildEndpointUrl(meta, host)
        val params = buildParams(meta)
        val headers = buildHeaders(meta)
        val body = buildBody(meta)
        val preRequestScript = resolveScript(HoppscotchRuleKeys.HOPP_PREREQUEST, endpoint)
        val testScript = resolveScript(HoppscotchRuleKeys.HOPP_TEST, endpoint)

        fireEvent(endpoint)

        return HoppRESTRequest(
            name = endpoint.name ?: "${meta.method.name} ${meta.path}",
            method = meta.method.name,
            endpoint = endpointUrl,
            params = params,
            headers = headers,
            body = body,
            preRequestScript = preRequestScript,
            testScript = testScript,
            description = endpoint.description
        )
    }

    private suspend fun resolveHost(endpoint: ApiEndpoint): String {
        val psiClass = endpoint.sourceClass
        val hostByRule = if (psiClass != null) {
            ruleEngine.evaluate(HoppscotchRuleKeys.HOPP_HOST, psiClass)
        } else {
            ruleEngine.evaluate(HoppscotchRuleKeys.HOPP_HOST)
        }
        if (!hostByRule.isNullOrBlank()) {
            return convertToHoppscotchVarSyntax(hostByRule)
        }
        return options.defaultHost
    }

    private fun convertToHoppscotchVarSyntax(value: String): String {
        return value.trim()
            .trim('`')
            .replace("{{", "<<")
            .replace("}}", ">>")
    }

    private fun buildEndpointUrl(meta: HttpMetadata, host: String): String {
        val path = meta.path.trim().let { if (it.startsWith("/")) it else "/$it" }
        return (host.trimEnd('/') + path)
            .trim('`')
            .replace("{{", "<<")
            .replace("}}", ">>")
    }

    private fun buildParams(meta: HttpMetadata): List<HoppKeyValue> {
        return meta.parameters
            .filter { it.binding == ParameterBinding.Query || it.binding == ParameterBinding.Path }
            .map { param ->
                HoppKeyValue(
                    key = param.name,
                    value = param.example ?: param.defaultValue ?: "",
                    active = true,
                    description = param.description
                )
            }
    }

    private fun buildHeaders(meta: HttpMetadata): List<HoppKeyValue> {
        return meta.headers.map { header ->
            HoppKeyValue(
                key = header.name,
                value = PlaceholderSyntaxConverter.convert(
                    header.value ?: header.example ?: "",
                    PlaceholderTargetSyntax.HOPPSCOTCH
                ) { name -> !ConfigReader.getInstance(project).getFirst(name).isNullOrEmpty() },
                active = true,
                description = header.description
            )
        }
    }

    private fun buildBody(meta: HttpMetadata): HoppRequestBody {
        val contentType = meta.contentType?.lowercase().orEmpty()
        val formParams = meta.parameters.filter { it.binding == ParameterBinding.Form }

        if (contentType.contains("json")) {
            val raw = when {
                meta.body != null -> ObjectModelJsonConverter.toJson(meta.body)
                else -> "{}"
            }
            return HoppRequestBody(
                contentType = "application/json",
                body = raw
            )
        }

        if (contentType.contains("x-www-form-urlencoded")) {
            return HoppRequestBody(
                contentType = "application/x-www-form-urlencoded",
                body = formParams.joinToString("&") {
                    "${java.net.URLEncoder.encode(it.name, "UTF-8")}=${java.net.URLEncoder.encode(it.example ?: it.defaultValue ?: "", "UTF-8")}"
                }
            )
        }

        if (contentType.contains("multipart") || contentType.contains("form-data")) {
            return HoppRequestBody(
                contentType = "multipart/form-data",
                body = formParams.map {
                    HoppFormDataEntry(
                        key = it.name,
                        value = it.example ?: it.defaultValue ?: "",
                        active = true,
                        isFile = false
                    )
                }
            )
        }

        if (meta.body != null) {
            return HoppRequestBody(
                contentType = "application/json",
                body = ObjectModelJsonConverter.toJson(meta.body)
            )
        }

        return HoppRequestBody()
    }

    private suspend fun resolveScript(ruleKey: com.itangcent.easyapi.rule.RuleKey.StringKey, endpoint: ApiEndpoint): String {
        val scripts = mutableListOf<String>()

        val psiMethod = endpoint.sourceMethod
        if (psiMethod != null) {
            ruleEngine.evaluate(ruleKey, psiMethod)
                ?.takeIf { it.isNotBlank() }?.let { scripts.add(it) }
        }

        val psiClass = endpoint.sourceClass
        if (psiClass != null) {
            ruleEngine.evaluate(HoppscotchRuleKeys.HOPP_CLASS_PREREQUEST.let {
                if (ruleKey == HoppscotchRuleKeys.HOPP_TEST) HoppscotchRuleKeys.HOPP_CLASS_TEST else it
            }, psiClass)
                ?.takeIf { it.isNotBlank() }?.let { scripts.add(it) }
        }

        return scripts.flatMap { it.lines().filter { l -> l.isNotBlank() } }.joinToString("\n")
    }

    private suspend fun resolveCollectionScript(
        collectionKey: com.itangcent.easyapi.rule.RuleKey.EventKey,
        classKey: com.itangcent.easyapi.rule.RuleKey.StringKey,
        methodKey: com.itangcent.easyapi.rule.RuleKey.StringKey,
        endpoints: List<ApiEndpoint>
    ): String {
        val scripts = mutableListOf<String>()

        ruleEngine.evaluate(collectionKey) { ctx ->
            ctx.setExt("collection", endpoints)
        }

        for (endpoint in endpoints) {
            val psiClass = endpoint.sourceClass
            if (psiClass != null) {
                ruleEngine.evaluate(classKey, psiClass)
                    ?.takeIf { it.isNotBlank() }?.let { scripts.add(it) }
            }
            val psiMethod = endpoint.sourceMethod
            if (psiMethod != null) {
                ruleEngine.evaluate(methodKey, psiMethod)
                    ?.takeIf { it.isNotBlank() }?.let { scripts.add(it) }
            }
        }

        return scripts.flatMap { it.lines().filter { l -> l.isNotBlank() } }.joinToString("\n")
    }

    private suspend fun fireEvent(endpoint: ApiEndpoint) {
        endpoint.sourceMethod?.let { element ->
            ruleEngine.evaluate(HoppscotchRuleKeys.HOPP_FORMAT_AFTER, element) { ctx ->
                ctx.setExt("endpoint", endpoint)
            }
        }
    }

    companion object {
        fun parsePath(path: String): List<String> {
            return path.trim().trim('/').split("/").filter { it.isNotEmpty() }
        }

        private fun formatTimestamp(millis: Long): String {
            val instant = java.time.Instant.ofEpochMilli(millis)
            val formatter = java.time.format.DateTimeFormatter
                .ofPattern("yyyyMMddHHmmss")
                .withZone(java.time.ZoneId.systemDefault())
            return formatter.format(instant)
        }
    }
}
