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
import com.itangcent.idea.plugin.settings.MarkdownFormatType
import com.itangcent.idea.plugin.settings.helper.MarkdownSettingsHelper
import com.itangcent.idea.psi.ResourceHelper
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.toPrettyString
import com.itangcent.intellij.util.ActionUtils
import com.itangcent.intellij.util.forEachValid

/**
 * format [com.itangcent.common.model.Doc] to `markdown`.
 */
@Singleton
class MarkdownFormatter {

    @Inject
    private val actionContext: ActionContext? = null

    @Inject
    private val moduleHelper: ModuleHelper? = null

    @Inject
    protected lateinit var markdownSettingsHelper: MarkdownSettingsHelper

    @Inject
    protected val resourceHelper: ResourceHelper? = null

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

        val rootModule = moduleHelper!!.findModuleByPath(ActionUtils.findCurrentPath()) ?: "easy-api"
        return wrapInfo(rootModule, modules)
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

    private fun parseApi(info: Any, deep: Int, handle: (String) -> Unit) {
        when (info) {
            is Request -> parseRequest(info, deep, handle)
            is MethodDoc -> parseMethodDoc(info, deep, handle)
            is Map<*, *> -> parseInfo(info, deep, handle)
        }
    }

    private fun parseInfo(info: Map<*, *>, deep: Int, handle: (String) -> Unit) {
        handle(hN(deep))
        handle(" ")
        handle(info[NAME].toString())
        handle("\n\n")
        info[DESC]?.let {
            handle(it.toString())
            handle("\n\n")
        }
        (info[ITEMS] as List<*>)
            .filterNotNull()
            .forEach {
                parseApi(it, deep + 1, handle)
                handle("\n\n")
            }
    }

    private fun parseMethodDoc(methodDoc: MethodDoc, deep: Int, handle: (String) -> Unit) {

        val objectFormatter = getObjectFormatter(handle)

        handle("\n---\n")
        handle("${hN(deep)} ${methodDoc.name}\n\n")

        if (methodDoc.desc.notNullOrBlank()) {
            handle("**Desc：**\n\n")
            handle("${methodDoc.desc}\n\n")
        }

        handle("\n**Params：**\n\n")
        if (methodDoc.params.isNullOrEmpty()) {
            handle("Non-Parameter\n")
        } else {
            objectFormatter.transaction {
                methodDoc.params?.forEach {
                    objectFormatter.writeObject(it.value, it.name ?: "", it.desc ?: "")
                }
            }
        }

        handle("\n**Return：**\n\n")
        if (methodDoc.ret == null) {
            handle("Non-Return\n")
        } else {
            methodDoc.ret?.let {
                objectFormatter.writeObject(it, methodDoc.retDesc ?: "")
            }
        }
    }

    private fun parseRequest(request: Request, deep: Int, handle: (String) -> Unit) {

        val objectFormatter = getObjectFormatter(handle)

        handle("\n---\n")
        handle("${hN(deep)} ${request.name}\n\n")

        //region basic info
        handle("${hN(deep + 1)} BASIC\n\n")
        handle("**Path：** ${request.path}\n\n")
        handle("**Method：** ${request.method}\n\n")
        if (request.desc.notNullOrBlank()) {
            handle("**Desc：**\n\n")
            handle("${request.desc}\n\n")
        }
        //endregion

        handle("${hN(deep + 1)} REQUEST\n\n")

        //path
        if (request.paths.notNullOrEmpty()) {
            handle("\n**Path Params：**\n\n")
            handle("| name  |  value   | desc  |\n")
            handle("| ------------ | ------------ | ------------ |\n")
            request.paths!!.forEach {
                handle(
                    "| ${it.name} | ${it.value ?: ""} |" +
                            " ${escape(it.desc)} |\n"
                )
            }
        }

        //header
        if (request.headers.notNullOrEmpty()) {
            handle("\n**Headers：**\n\n")
            handle("| name  |  value  |  required  | desc  |\n")
            handle("| ------------ | ------------ | ------------ | ------------ |\n")
            request.headers!!.forEach {
                handle(
                    "| ${it.name} | ${it.value ?: ""} | ${
                        KitUtils.fromBool(
                            it.required
                                ?: false, "YES", "NO"
                        )
                    } | ${escape(it.desc)} |\n"
                )
            }
        }

        //query
        if (request.querys.notNullOrEmpty()) {
            handle("\n**Query：**\n\n")
            handle("| name  |  value  |  required | desc  |\n")
            handle("| ------------ | ------------ | ------------ | ------------ |\n")
            request.querys!!.forEach {
                handle(
                    "| ${it.name} | ${it.value ?: ""} | ${KitUtils.fromBool(it.required ?: false, "YES", "NO")} |" +
                            " ${escape(it.desc)} |\n"
                )
            }
        }

        if (request.body != null) {

            handle("\n**RequestBody**\n\n")
            objectFormatter.writeObject(request.body, request.bodyAttr ?: "")

            if (markdownSettingsHelper.outputDemo()) {
                handle("\n**Request Demo：**\n\n")
                parseToJson(handle, request.body)
            }

        }

        if (request.formParams.notNullOrEmpty()) {
            handle("\n**Form：**\n\n")
            handle("| name  |  value  | required |  type  |  desc  |\n")
            handle("| ------------ | ------------ | ------------ | ------------ | ------------ |\n")
            request.formParams!!.forEach {
                handle(
                    "| ${it.name} | ${it.value ?: ""} | ${KitUtils.fromBool(it.required ?: false, "YES", "NO")} |" +
                            " ${it.type} | ${escape(it.desc)} |\n"
                )
            }
        }

        if (request.response.notNullOrEmpty()) {

            val response = request.response!!.firstOrNull { it.body != null }
            //todo:support multiple response
            if (response != null) {
                handle("\n\n")
                handle("${hN(deep + 1)} RESPONSE\n\n")
                handle("**Header：**\n\n")
                handle("| name  |  value  |  required  | desc  |\n")
                handle("| ------------ | ------------ | ------------ | ------------ | ------------ |\n")
                response.headers!!.forEach {
                    handle(
                        "| ${it.name} | ${it.value ?: ""} | ${
                            it.required.or("YES", "NO")
                        } |  ${escape(it.desc)} |\n"
                    )
                }

                handle("\n**Body：**\n\n")
                response.body?.let {
                    objectFormatter.writeObject(it, response.bodyDesc ?: "")
                }

                // handler json example
                if (markdownSettingsHelper.outputDemo()) {
                    handle("\n**Response Demo：**\n\n")
                    parseToJson(handle, response.body)
                }
            }

        }
    }

    private fun parseToJson(handle: (String) -> Unit, body: Any?) {
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
            val attr = resourceHelper!!.findAttrOfClass(resource)
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

    companion object {
        private const val NAME = "name"
        private const val DESC = "desc"
        private const val ITEMS = "items"
    }

    private fun getObjectFormatter(handle: (String) -> Unit): ObjectFormatter {
        val markdownFormatType = markdownSettingsHelper.markdownFormatType()
        return if (markdownFormatType == MarkdownFormatType.ULTIMATE) {
            UltimateObjectFormatter(handle)
        } else {
            SimpleObjectFormatter(handle)
        }
    }

}

private interface ObjectFormatter {

    fun writeObject(obj: Any?, desc: String) {
        writeObject(obj, "", desc)
    }

    fun writeObject(obj: Any?, name: String, desc: String)

    fun transaction(action: (ObjectFormatter) -> Unit)
}

private abstract class AbstractObjectFormatter(val handle: (String) -> Unit) : ObjectFormatter {

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

private class SimpleObjectFormatter(handle: (String) -> Unit) : AbstractObjectFormatter(handle) {

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

private class UltimateObjectFormatter(handle: (String) -> Unit) : AbstractObjectFormatter(handle) {

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
}