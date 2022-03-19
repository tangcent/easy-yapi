package com.itangcent.idea.plugin.api.export.markdown

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.itangcent.common.constant.Attrs
import com.itangcent.common.kit.KVUtils
import com.itangcent.common.kit.KitUtils
import com.itangcent.common.kit.or
import com.itangcent.common.model.Doc
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Request
import com.itangcent.common.utils.DateUtils
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.common.utils.safeComputeIfAbsent
import com.itangcent.http.RequestUtils
import com.itangcent.idea.plugin.api.export.core.Folder
import com.itangcent.idea.plugin.api.export.core.FormatFolderHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanFormatter
import com.itangcent.idea.plugin.rule.SuvRuleContext
import com.itangcent.idea.plugin.settings.MarkdownFormatType
import com.itangcent.idea.plugin.settings.helper.MarkdownSettingsHelper
import com.itangcent.idea.psi.UltimateDocHelper
import com.itangcent.idea.psi.resource
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.toPrettyString
import com.itangcent.intellij.util.forEachValid

/**
 * format [com.itangcent.common.model.Doc] to `markdown`.
 *
 * @author tangcent
 */
@Singleton
class MarkdownFormatter {

    @Inject
    private val actionContext: ActionContext? = null

    @Inject
    private val moduleHelper: ModuleHelper? = null

    @Inject
    private lateinit var ruleComputer: RuleComputer

    @Inject
    protected lateinit var markdownSettingsHelper: MarkdownSettingsHelper

    @Inject
    protected val ultimateDocHelper: UltimateDocHelper? = null

    @Inject
    private val formatFolderHelper: FormatFolderHelper? = null

    fun parseRequests(requests: List<Doc>): String {
        val sb = StringBuilder()
        val groupedRequest = groupRequests(requests)
        parseApi(groupedRequest, 1) { sb.append(it) }
        return sb.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun groupRequests(requests: List<Doc>): Any {


        //parse [request...] ->
        //                      {
        //                          "module":{
        //                              "folder":[request...]
        //                          }
        //                      }

        val moduleFolderApiMap: HashMap<String, HashMap<Folder, ArrayList<Doc>>> = HashMap()

        //group by module
        val moduleGroupedMap: HashMap<String, MutableList<Doc>> = HashMap()
        requests.forEach { request ->
            val module = request.resource?.let { moduleHelper!!.findModule(it) } ?: "easy-api"
            moduleGroupedMap.safeComputeIfAbsent(module) { ArrayList() }!!
                .add(request)
        }

        moduleGroupedMap.forEach { (module, requestsInModule) ->
            moduleFolderApiMap[module] = parseRequestsToFolder(requestsInModule)
        }


        if (moduleFolderApiMap.size == 1) {
            //single module
            val folderApiMap = moduleFolderApiMap.values.first()
            if (folderApiMap.size == 1) {
                //single folder
                folderApiMap.entries.first().let {
                    return wrapInfo(it.key, it.value)
                }
            } else {
                moduleFolderApiMap.entries.first().let { moduleAndFolders ->
                    val items: ArrayList<HashMap<String, Any?>> = ArrayList()
                    moduleAndFolders.value.forEach { items.add(wrapInfo(it.key, it.value)) }
                    return wrapInfo(moduleAndFolders.key, items)
                }
            }
        }

        val modules: ArrayList<HashMap<String, Any?>> = ArrayList()
        moduleFolderApiMap.entries
            .map { moduleAndFolders ->
                val items: ArrayList<HashMap<String, Any?>> = ArrayList()
                moduleAndFolders.value.forEach { items.add(wrapInfo(it.key, it.value)) }
                return@map wrapInfo(moduleAndFolders.key, items)
            }
            .forEach { modules.add(it) }

        return modules
    }

    private fun parseRequestsToFolder(requests: MutableList<Doc>): HashMap<Folder, ArrayList<Doc>> {
        //parse [request...] ->
        //                      {
        //                          "folder":[request...]
        //                      }

        //group by folder into: {folder:requests}
        val folderGroupedMap: HashMap<Folder, ArrayList<Doc>> = HashMap()
        requests.forEach { request ->
            val folder = formatFolderHelper!!.resolveFolder(request.resource ?: PostmanFormatter.NULL_RESOURCE)
            folderGroupedMap.safeComputeIfAbsent(folder) { ArrayList() }!!
                .add(request)
        }

        return folderGroupedMap
    }

    private fun parseApi(info: Any, deep: Int, handle: Handle) {
        when (info) {
            is Request -> parseRequest(info, deep, handle)
            is MethodDoc -> parseMethodDoc(info, deep, handle)
            is Map<*, *> -> parseInfo(info, deep, handle)
            is List<*> -> info.filterNotNull()
                .forEach {
                    parseApi(it, deep, handle)
                    handle.doubleLine()
                }
        }
    }

    private fun parseInfo(info: Map<*, *>, deep: Int, handle: Handle) {
        val title = info[NAME].toString()
        handle(ruleComputer.computer(MarkdownExportRuleKeys.HN_TITLE,
            SuvRuleContext().also {
                it.setExt("title", title)
                it.setExt("deep", deep)
            }, null)
            ?: "${hN(deep)} $title")
        handle.doubleLine()
        info[DESC]?.let {
            handle(it.toString())
            handle.doubleLine()
        }
        info[ITEMS]?.let { parseApi(it, deep + 1, handle) }
    }

    private fun parseMethodDoc(methodDoc: MethodDoc, deep: Int, handle: Handle) {

        val objectFormatter = getObjectFormatter(handle)

        val suvRuleContext = SuvRuleContext()
        suvRuleContext.setExt("type", "methodDoc")
        suvRuleContext.setExt("doc", methodDoc)
        suvRuleContext.setExt("deep", deep)
        suvRuleContext.setExt("title", methodDoc.name)

        handle("\n---\n")
        handle(ruleComputer.computer(MarkdownExportRuleKeys.HN_TITLE, suvRuleContext, methodDoc.resource())
            ?: "${hN(deep)} ${methodDoc.name}")
        handle.doubleLine()

        if (methodDoc.desc.notNullOrBlank()) {
            handle(ruleComputer.computer(MarkdownExportRuleKeys.METHOD_DOC_DESC, suvRuleContext, methodDoc.resource())
                ?: "**Desc:**\n\n ${methodDoc.desc}")
            handle.doubleLine()
        }

        handle(ruleComputer.computer(MarkdownExportRuleKeys.METHOD_DOC_PARAMS, suvRuleContext, methodDoc.resource())
            ?: "**Params:**")
        handle.doubleLine()
        if (methodDoc.params.isNullOrEmpty()) {
            handle("Non-Parameter\n")
        } else {
            objectFormatter.transaction {
                methodDoc.params?.forEach {
                    objectFormatter.writeObject(it.value, it.name ?: "", it.desc ?: "")
                }
            }
            handle.nextLine()
        }

        handle(ruleComputer.computer(MarkdownExportRuleKeys.METHOD_DOC_RETURN, suvRuleContext, methodDoc.resource())
            ?: "**Return:**")
        handle.doubleLine()
        if (methodDoc.ret == null) {
            handle("Non-Return\n")
        } else {
            methodDoc.ret?.let {
                objectFormatter.writeObject(it, methodDoc.retDesc ?: "")
            }
            handle.nextLine()
        }
    }

    private fun parseRequest(request: Request, deep: Int, handle: Handle) {

        val objectFormatter = getObjectFormatter(handle)

        val suvRuleContext = SuvRuleContext()
        suvRuleContext.setExt("type", "request")
        suvRuleContext.setExt("doc", request)
        suvRuleContext.setExt("deep", deep)
        suvRuleContext.setExt("title", request.name)

        handle("\n---\n")

        handle(ruleComputer.computer(MarkdownExportRuleKeys.HN_TITLE, suvRuleContext, request.resource())
            ?: "${hN(deep)} ${request.name}")
        handle.doubleLine()

        //region basic info

        handle(ruleComputer.computer(MarkdownExportRuleKeys.BASIC, suvRuleContext, request.resource())
            ?: "> BASIC")
        handle.doubleLine()

        handle(ruleComputer.computer(MarkdownExportRuleKeys.BASIC_PATH, suvRuleContext, request.resource())
            ?: "**Path:** ${request.path}")
        handle.doubleLine()

        handle(ruleComputer.computer(MarkdownExportRuleKeys.BASIC_METHOD, suvRuleContext, request.resource())
            ?: "**Method:** ${request.method}")
        handle.doubleLine()

        if (request.desc.notNullOrBlank()) {
            handle(ruleComputer.computer(MarkdownExportRuleKeys.BASIC_DESC, suvRuleContext, request.resource())
                ?: "**Desc:**\n\n ${request.desc}")
            handle.doubleLine()
        }

        //endregion

        //region request

        handle(ruleComputer.computer(MarkdownExportRuleKeys.REQUEST, suvRuleContext, request.resource())
            ?: "> REQUEST")
        handle.doubleLine()

        //path
        if (request.paths.notNullOrEmpty()) {
            handle(ruleComputer.computer(MarkdownExportRuleKeys.REQUEST_PATH, suvRuleContext, request.resource())
                ?: "**Path Params:**")
            handle.doubleLine()
            handle("| name  |  value   | desc  |\n")
            handle("| ------------ | ------------ | ------------ |\n")
            request.paths!!.forEach {
                handle(
                    "| ${it.name} | ${escape(it.value)} |" +
                            " ${escape(it.desc)} |\n"
                )
            }
            handle.nextLine()
        }

        //header
        if (request.headers.notNullOrEmpty()) {
            handle(ruleComputer.computer(MarkdownExportRuleKeys.REQUEST_HEADERS, suvRuleContext, request.resource())
                ?: "**Headers:**")
            handle.doubleLine()
            handle("| name  |  value  |  required  | desc  |\n")
            handle("| ------------ | ------------ | ------------ | ------------ |\n")
            request.headers!!.forEach {
                handle(
                    "| ${it.name} | ${escape(it.value)} | ${
                        KitUtils.fromBool(
                            it.required
                                ?: false, "YES", "NO"
                        )
                    } | ${escape(it.desc)} |\n"
                )
            }
            handle.nextLine()
        }

        //query
        if (request.querys.notNullOrEmpty()) {
            handle(ruleComputer.computer(MarkdownExportRuleKeys.REQUEST_QUERY, suvRuleContext, request.resource())
                ?: "**Query:**")
            handle.doubleLine()
            handle("| name  |  value  |  required | desc  |\n")
            handle("| ------------ | ------------ | ------------ | ------------ |\n")
            request.querys!!.forEach {
                handle(
                    "| ${it.name} | ${escape(it.value?.toString())} | ${
                        KitUtils.fromBool(it.required ?: false,
                            "YES",
                            "NO")
                    } |" +
                            " ${escape(it.desc)} |\n"
                )
            }
            handle.nextLine()
        }

        if (request.body != null) {
            handle(ruleComputer.computer(MarkdownExportRuleKeys.REQUEST_BODY, suvRuleContext, request.resource())
                ?: "**Request Body:**")
            handle.doubleLine()

            objectFormatter.writeObject(request.body, request.bodyAttr ?: "")

            if (markdownSettingsHelper.outputDemo()) {
                handle("\n")
                handle(ruleComputer.computer(MarkdownExportRuleKeys.REQUEST_BODY_DEMO,
                    suvRuleContext,
                    request.resource())
                    ?: "**Request Demo:**")
                handle.doubleLine()

                parseToJson(handle, request.body)
            }
            handle.nextLine()
        }

        if (request.formParams.notNullOrEmpty()) {
            handle(ruleComputer.computer(MarkdownExportRuleKeys.REQUEST_FORM, suvRuleContext, request.resource())
                ?: "**Form:**")
            handle.doubleLine()

            handle("| name  |  value  | required |  type  |  desc  |\n")
            handle("| ------------ | ------------ | ------------ | ------------ | ------------ |\n")
            request.formParams!!.forEach {
                handle(
                    "| ${it.name} | ${escape(it.value)} | ${KitUtils.fromBool(it.required ?: false, "YES", "NO")} |" +
                            " ${it.type} | ${escape(it.desc)} |\n"
                )
            }
            handle.nextLine()
        }

        //endregion

        if (request.response.notNullOrEmpty()) {

            val response = request.response!!.firstOrNull { it.body != null }
            //todo:support multiple response
            if (response != null) {
                handle.doubleLine()
                handle(ruleComputer.computer(MarkdownExportRuleKeys.RESPONSE, suvRuleContext, request.resource())
                    ?: "> RESPONSE")
                handle.doubleLine()

                //response headers
                val responseHeaders = response.headers
                if (responseHeaders.notNullOrEmpty()) {
                    handle(ruleComputer.computer(MarkdownExportRuleKeys.RESPONSE_HEADERS,
                        suvRuleContext,
                        request.resource())
                        ?: "**Headers:**")
                    handle.doubleLine()
                    handle("| name  |  value  |  required  | desc  |\n")
                    handle("| ------------ | ------------ | ------------ | ------------ |\n")
                    responseHeaders!!.forEach {
                        handle(
                            "| ${it.name} | ${escape(it.value)} | ${
                                it.required.or("YES", "NO")
                            } |  ${escape(it.desc)} |\n"
                        )
                    }
                    handle.nextLine()
                }

                //response body
                response.body?.let {
                    handle(ruleComputer.computer(MarkdownExportRuleKeys.RESPONSE_BODY,
                        suvRuleContext,
                        request.resource())
                        ?: "**Body:**")
                    handle.doubleLine()
                    objectFormatter.writeObject(it, response.bodyDesc ?: "")
                    handle.nextLine()
                }

                // handler json example
                if (markdownSettingsHelper.outputDemo()) {
                    handle(ruleComputer.computer(MarkdownExportRuleKeys.RESPONSE_BODY_DEMO,
                        suvRuleContext,
                        request.resource())
                        ?: "**Response Demo:**")
                    handle.doubleLine()
                    parseToJson(handle, response.body)
                    handle.nextLine()
                }
            }
        }
    }

    private fun parseToJson(handle: Handle, body: Any?) {
        handle("```json\n")
        body?.let {
            if (it != 0) {
                handle(RequestUtils.parseRawBody(it))
            }
        }
        handle("\n```\n")
    }

    private fun hN(n: Int): String {
        return "#".repeat(n)
    }

    private fun wrapInfo(resource: Any, items: List<Any?>): HashMap<String, Any?> {
        val info: HashMap<String, Any?> = HashMap()
        parseNameAndDesc(resource, info)
        info[ITEMS] = items
        return info
    }

    fun parseNameAndDesc(resource: Any, info: HashMap<String, Any?>) {
        if (resource is PsiClass) {
            val attr = ultimateDocHelper!!.findUltimateDescOfClass(resource)
            if (attr.isNullOrBlank()) {
                info["name"] = resource.name!!
                info["description"] = "exported from:${actionContext!!.callInReadUI { resource.qualifiedName }}"
            } else {
                val lines = attr.lines()
                if (lines.size == 1) {
                    info["name"] = attr
                    info["description"] = "exported from:${actionContext!!.callInReadUI { resource.qualifiedName }}"
                } else {
                    info["name"] = lines[0]
                    info["description"] = attr
                }
            }
        } else if (resource is Folder) {
            info["name"] = resource.name
            info["description"] = resource.attr
        } else if (resource is Pair<*, *>) {
            info["name"] = resource.first
            info["description"] = resource.second
        } else {
            info["name"] = resource.toString()
            info["description"] = "exported at ${DateUtils.formatYMD_HMS(DateUtils.now())}"
        }
    }

    private fun getObjectFormatter(handle: Handle): ObjectFormatter {
        val markdownFormatType = markdownSettingsHelper.markdownFormatType()
        return if (markdownFormatType == MarkdownFormatType.ULTIMATE) {
            UltimateObjectFormatter(handle)
        } else {
            SimpleObjectFormatter(handle)
        }
    }

    companion object {
        private const val NAME = "name"
        private const val DESC = "desc"
        private const val ITEMS = "items"
    }

}

private typealias Handle = (String) -> Unit

private fun Handle.nextLine() {
    this("\n")
}

private fun Handle.doubleLine() {
    this("\n\n")
}

private interface ObjectFormatter {

    fun writeObject(obj: Any?, desc: String) {
        writeObject(obj, "", desc)
    }

    fun writeObject(obj: Any?, name: String, desc: String)

    fun transaction(action: (ObjectFormatter) -> Unit)
}

private abstract class AbstractObjectFormatter(val handle: Handle) : ObjectFormatter {

    /**
     * -1: always writeHeader, -> -1
     * 0: writeHeader once, -> 1
     * >0: not writeHeader
     */
    private var inStream = -1

    override fun writeObject(obj: Any?, name: String, desc: String) {
        if (inStream == -1 || inStream++ == 0) {
            writeHeader()
        }
        writeBody(obj, name, desc)
    }

    abstract fun writeHeader()

    abstract fun writeBody(obj: Any?, name: String, desc: String)

    protected fun writeHeaders(vararg headers: String) {
        headers.forEach { handle("| $it ") }
        handle("|\n")
        repeat(headers.size) { handle("| ------------ ") }
        handle("|\n")
    }

    protected fun addBodyProperty(deep: Int, vararg columns: Any?) {
        handle("| ")
        if (deep > 1) {
            handle("&ensp;&ensp;".repeat(deep - 1))
            handle("&#124;─")
        }
        columns.forEach { handle("${format(it)} | ") }
        handle("\n")
    }

    fun format(any: Any?): String {
        if (any == null) {
            return ""
        }
        if (any is Boolean) {
            return if (any) "YES" else "NO"
        }

        return escape(any.toPrettyString())
    }

    override fun transaction(action: (ObjectFormatter) -> Unit) {
        inStream = 0
        try {
            action(this)
        } finally {
            inStream = -1
        }
    }
}

private class SimpleObjectFormatter(handle: Handle) : AbstractObjectFormatter(handle) {

    override fun writeHeader() {
        writeHeaders("name", "type", "desc")
    }

    override fun writeBody(obj: Any?, name: String, desc: String) {
        writeBody(obj, name, desc, 0)
    }

    @Suppress("UNCHECKED_CAST")
    fun writeBody(obj: Any?, name: String, desc: String, deep: Int) {

        var type: String? = null
        when (obj) {
            null -> type = "object"
            is String -> type = "string"
            is Number -> type = if (obj is Int || obj is Long) {
                "integer"
            } else {
                "number"
            }
            is Boolean -> type = "boolean"
        }
        if (type != null) {
            addBodyProperty(deep, name, type, desc)
            return
        }

        if (obj is Array<*>) {
            addBodyProperty(deep, name, "array", desc)

            if (obj.size > 0) {
                obj.forEach {
                    writeBody(it, "", "", deep + 1)
                }
            } else {
                writeBody(null, "", "", deep + 1)
            }
        } else if (obj is Collection<*>) {
            addBodyProperty(deep, name, "array", desc)
            if (obj.size > 0) {
                obj.forEach {
                    writeBody(it, "", "", deep + 1)
                }
            } else {
                writeBody(null, "", "", deep + 1)
            }
        } else if (obj is Map<*, *>) {
            if (deep > 0) {
                addBodyProperty(deep, name, "object", desc)
            }
            var comment: Map<String, Any?>? = null
            try {
                comment = obj[Attrs.COMMENT_ATTR] as Map<String, Any?>?
            } catch (e: Throwable) {
            }
            obj.forEachValid { k, v ->
                val propertyDesc = KVUtils.getUltimateComment(comment, k)
                writeBody(v, k.toString(), propertyDesc, deep + 1)
            }
        } else {
            addBodyProperty(deep, name, "object", desc)
        }
    }

}

private class UltimateObjectFormatter(handle: Handle) : AbstractObjectFormatter(handle) {

    override fun writeHeader() {
        writeHeaders("name", "type", "required", "default", "desc")
    }

    override fun writeBody(obj: Any?, name: String, desc: String) {
        writeBody(obj, name, null, null, desc, 0)
    }

    @Suppress("UNCHECKED_CAST")
    fun writeBody(obj: Any?, name: String, required: Boolean?, default: String?, desc: String, deep: Int) {

        var type: String? = null
        when (obj) {
            null -> type = "object"
            is String -> type = "string"
            is Number -> type = if (obj is Int || obj is Long) {
                "integer"
            } else {
                "number"
            }
            is Boolean -> type = "boolean"
        }
        if (type != null) {
            addBodyProperty(deep, name, type, required, default, desc)
            return
        }

        if (obj is Array<*>) {
            addBodyProperty(deep, name, "array", required, default, desc)
            if (obj.size > 0) {
                obj.forEach {
                    writeBody(it, "", null, null, "", deep + 1)
                }
            } else {
                writeBody(null, "", null, null, "", deep + 1)
            }
        } else if (obj is Collection<*>) {
            addBodyProperty(deep, name, "array", desc)
            if (obj.size > 0) {
                obj.forEach {
                    writeBody(it, "", null, null, "", deep + 1)
                }
            } else {
                writeBody(null, "", null, null, "", deep + 1)
            }
        } else if (obj is Map<*, *>) {
            if (deep > 0) {
                addBodyProperty(deep, name, "object", required, default, desc)
            }
            val comments: HashMap<String, Any?>? = obj[Attrs.COMMENT_ATTR] as? HashMap<String, Any?>?
            val requireds: HashMap<String, Any?>? = obj[Attrs.REQUIRED_ATTR] as? HashMap<String, Any?>?
            val defaults: HashMap<String, Any?>? = obj[Attrs.DEFAULT_VALUE_ATTR] as? HashMap<String, Any?>?
            obj.forEachValid { k, v ->
                val key = k.toString()
                val propertyDesc: String? = KVUtils.getUltimateComment(comments, k)
                writeBody(
                    v, key,
                    requireds?.get(key) as? Boolean,
                    defaults?.get(key) as? String,
                    propertyDesc ?: "",
                    deep + 1
                )
            }
        } else {
            addBodyProperty(deep, name, "object", required, default, desc)
        }
    }

}

private fun escape(str: String?): String {
    if (str.isNullOrBlank()) return ""
    return str.replace("\n", "<br>")
        .replace("|", "\\|")
}