package com.itangcent.idea.plugin.api.export.http

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.model.Request
import com.itangcent.common.model.rawContentType
import com.itangcent.common.utils.IDUtils
import com.itangcent.http.RequestUtils
import com.itangcent.idea.psi.resource
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.psi.PsiClassUtil 

/**
 * `HttpClientFormatter` is a utility class responsible for formatting and parsing HTTP client requests.
 * This class also provides functionality to update existing documentation with new or altered requests.
 *
 * @author tangcent
 */
@Singleton
class HttpClientFormatter {

    @Inject
    private lateinit var actionContext: ActionContext

    companion object {
        // Reference prefix used in documentation
        const val REF = "### ref: "
    }

    /**
     * Parses and formats a list of `Request` objects into a string representation.
     *
     * @param host The host URL.
     * @param requests The list of requests to be processed.
     * @return A string representation of the formatted requests.
     */
    fun parseRequests(
        host: String,
        requests: List<Request>
    ): String {
        val sb = StringBuilder()
        for (request in requests) {
            parseRequest(host, request, sb)
        }
        return sb.toString()
    }

    /**
     * Parses and formats a list of `Request` objects, updating an existing documentation string.
     *
     * @param existedDoc The existing documentation string.
     * @param host The host URL.
     * @param requests The list of requests to be processed.
     * @return A string representation of the updated and formatted requests.
     */
    fun parseRequests(
        existedDoc: String,
        host: String,
        requests: List<Request>
    ): String {
        val docs = splitDoc(existedDoc)
        val requestMap = requests.associateBy {
            it.ref()
        }
        val sb = StringBuilder()

        // Process and update existing entries
        for (doc in docs) {
            val request = requestMap[doc.first]
            if (request != null) {
                parseRequest(host, request, sb)
            } else {
                sb.append(REF).append(doc.first).append("\n")
                    .append(doc.second)
            }
        }

        // Process new requests
        val processedRefs = docs.map { it.first }.toSet()
        requests.filter { request ->
            request.ref() !in processedRefs
        }.forEach { request ->
            parseRequest(host, request, sb)
        }

        return sb.toString().trimEnd('\n', '#', ' ')
    }

    /**
     * Splits an existing documentation string into a list of `RefDoc` objects for easy processing.
     *
     * @param doc The existing documentation string.
     * @return A list of `RefDoc` objects representing the split documentation.
     */
    private fun splitDoc(doc: String): List<RefDoc> {
        val refDocs = mutableListOf<RefDoc>()
        val lines = doc.lines()
        var ref: String? = null
        val sb = StringBuilder()
        for (line in lines) {
            if (line.startsWith(REF)) {
                if (ref != null) {
                    refDocs.add(RefDoc(ref, sb.toString()))
                    sb.clear()
                }
                ref = line.substring(REF.length)
            } else {
                sb.append(line).append("\n")
            }
        }
        if (ref != null) {
            refDocs.add(RefDoc(ref, sb.toString()))
        }
        return refDocs
    }

    /**
     * Parses and formats a single `Request` object, appending the formatted string to a `StringBuilder`.
     *
     * @param host The host URL.
     * @param request The request to be processed.
     * @param sb The `StringBuilder` to which the formatted string is appended.
     */
    private fun parseRequest(
        host: String,
        request: Request,
        sb: StringBuilder
    ) {
        sb.appendRef(request)
        val apiName = request.name ?: (request.method + ":" + request.path?.url())
        sb.append("### $apiName\n\n")
        if (!request.desc.isNullOrEmpty()) {
            request.desc!!.lines().forEach {
                sb.append("// ").append(it).append("\n")
            }
        }

        sb.append(request.method).append(" ")
            .append(RequestUtils.concatPath(host, request.path?.url() ?: ""))
        if (!request.querys.isNullOrEmpty()) {
            val query = request.querys!!.joinToString("&") { "${it.name}=${it.value ?: ""}" }
            if (query.isNotEmpty()) {
                sb.append("?").append(query)
            }
        }

        sb.append("\n")

        request.headers?.forEach { header ->
            sb.appendHeader(header.name ?: "", header.value)
        }

        val contentType = request.rawContentType()
        when {
            contentType?.contains("application/json") == true -> {
                request.body?.let { body ->
                    sb.append("\n")
                    sb.append(RequestUtils.parseRawBody(body))
                }
            }

            contentType?.contains("application/x-www-form-urlencoded") == true -> {
                request.formParams?.let { formParams ->
                    val formData = formParams.joinToString("&") { "${it.name}=${it.value ?: ""}" }
                    sb.append("\n")
                    sb.append(formData)
                }
            }

            contentType?.contains("multipart/form-data") == true -> {
                request.formParams?.let { formParams ->
                    sb.append("\n")
                    sb.append("Content-Type: multipart/form-data; boundary=WebAppBoundary\n")
                    for (param in formParams) {
                        sb.append("\n--WebAppBoundary\n")
                        if (param.type == "file") {
                            sb.append("Content-Disposition: form-data; name=\"${param.name}\"; filename=\"${param.value ?: "file"}\"\n")
                            sb.append("\n< ./relative/path/to/${param.value ?: "file"}\n")
                        } else {
                            sb.append("Content-Disposition: form-data; name=\"${param.name}\"\n")
                            sb.append("\n${param.value ?: "[${param.name}]"}\n")
                        }
                        sb.append("--WebAppBoundary--\n")
                    }
                }
            }
        }

        sb.appendEnd()
    }

    private fun StringBuilder.appendEnd() {
        append("\n\n###\n\n")
    }

    private fun StringBuilder.appendHeader(name: String, value: String?) =
        append(name).append(": ").append(value ?: "").append("\n")

    private fun StringBuilder.appendRef(request: Request) =
        append(REF).append(request.ref())
            .append("\n")

    private fun Request.ref(): String = resource()?.let {
        actionContext.callInReadUI { PsiClassUtil.fullNameOfMember(it) }
    } ?: IDUtils.shortUUID()
}

/**
 * Type alias for a pair representing a reference and its documentation
 */
typealias RefDoc = Pair<String, String>
