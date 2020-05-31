package com.itangcent.idea.plugin.api.export.markdown

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.itangcent.common.constant.Attrs
import com.itangcent.common.kit.KVUtils
import com.itangcent.common.kit.KitUtils
import com.itangcent.common.model.Doc
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Request
import com.itangcent.common.utils.DateUtils
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.http.RequestUtils
import com.itangcent.idea.plugin.api.export.Folder
import com.itangcent.idea.plugin.api.export.FormatFolderHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanFormatter
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.psi.ResourceHelper
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.util.ActionUtils
import com.itangcent.intellij.util.forEachValid
import java.util.*

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
    protected val settingBinder: SettingBinder? = null

    @Inject
    protected val resourceHelper: ResourceHelper? = null

    @Inject
    private val formatFolderHelper: FormatFolderHelper? = null

    fun parseRequests(requests: MutableList<Doc>): String {
        val sb = StringBuilder()
        val groupedRequest = groupRequests(requests)
        parseApi(groupedRequest, 1) { sb.append(it) }
        return sb.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun groupRequests(requests: MutableList<Doc>): Any {


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
            moduleGroupedMap.computeIfAbsent(module) { ArrayList() }
                    .add(request)
        }

        moduleGroupedMap.forEach { module, requestsInModule ->
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
            folderGroupedMap.computeIfAbsent(folder) { ArrayList() }
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
            handle("| name  |  type  |  desc  |\n")
            handle("| ------------ | ------------ | ------------ |\n")
            methodDoc.params?.forEach { parseBody(1, it.name ?: "", it.desc ?: "", it.value, handle) }
        }

        handle("\n**Return：**\n\n")
        if (methodDoc.ret == null) {
            handle("Non-Return\n")
        } else {
            handle("| name  |  type  |  desc  |\n")
            handle("| ------------ | ------------ | ------------ |\n")
            methodDoc.ret?.let { parseBody(0, "", methodDoc.retDesc ?: "", it, handle) }
        }

    }

    private fun parseRequest(request: Request, deep: Int, handle: (String) -> Unit) {

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
            handle("\n**Path：**\n\n")
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
                        "| ${it.name} | ${it.value ?: ""} | ${KitUtils.fromBool(it.required
                                ?: false, "YES", "NO")} | ${escape(it.desc)} |\n"
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
            handle("| name  |  type  |  desc  |\n")
            handle("| ------------ | ------------ | ------------ |\n")
            parseBody(0, "", request.bodyAttr ?: "", request.body, handle)

            if (settingBinder!!.read().outputDemo) {
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
                        "| ${it.name} | ${it.value} | ${KitUtils.fromBool(it.required ?: false, "YES", "NO")} |" +
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
                            "| ${it.name} | ${it.value ?: ""} | ${KitUtils.fromBool(
                                    it.required
                                            ?: false, "YES", "NO"
                            )} |  ${escape(it.desc)} |\n"
                    )
                }

                handle("\n**Body：**\n\n")
                handle("| name  |  type  |  desc  |\n")
                handle("| ------------ | ------------ | ------------ |\n")
                response.body?.let { parseBody(0, "", response.bodyDesc ?: "", it, handle) }

                // handler json example
                if (settingBinder!!.read().outputDemo) {
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

    @Suppress("UNCHECKED_CAST")
    private fun parseBody(deep: Int, name: String, desc: String, obj: Any?, handle: (String) -> Unit) {

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
            addBodyProperty(deep, name, type, desc, handle)
            return
        }

        if (obj is Array<*>) {
            addBodyProperty(deep, name, "array", desc, handle)

            if (obj.size > 0) {
                parseBody(deep + 1, "", "", obj[0], handle)
            } else {
                parseBody(deep + 1, "", "", null, handle)
            }
        } else if (obj is List<*>) {
            addBodyProperty(deep, name, "array", desc, handle)
            if (obj.size > 0) {
                parseBody(deep + 1, "", "", obj[0], handle)
            } else {
                parseBody(deep + 1, "", "", null, handle)
            }
        } else if (obj is Map<*, *>) {
            if (deep > 0) {
                addBodyProperty(deep, name, "object", desc, handle)
            }
            var comment: HashMap<String, Any?>? = null
            try {
                comment = obj[Attrs.COMMENT_ATTR] as HashMap<String, Any?>?
            } catch (e: Throwable) {
            }
            obj.forEachValid { k, v ->
                val propertyDesc: String? = KVUtils.getUltimateComment(comment, k)
                parseBody(deep + 1, k.toString(), propertyDesc ?: "", v, handle)
            }
        } else {
            addBodyProperty(deep, name, "object", desc, handle)
        }
    }

    private fun addBodyProperty(deep: Int, name: String, type: String, desc: String, handle: (String) -> Unit) {
        handle("| ")
        if (deep > 1) {
            handle("&ensp;&ensp;".repeat(deep - 1))
            handle("&#124;─")
        }
        handle("$name | $type | ${escape(desc)} |\n")
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
        } else if (resource is Pair<*, *>) {
            info["name"] = resource.first
            info["description"] = resource.second
        } else {
            info["name"] = resource.toString()
            info["description"] = "exported at ${DateUtils.formatYMD_HMS(DateUtils.now())}"
        }
    }

    private fun escape(str: String?): String {
        if (str.isNullOrBlank()) return ""
        return str.replace("\n", "<br>")
    }

    companion object {
        private const val NAME = "name"
        private const val DESC = "desc"
        private const val ITEMS = "items"
        private val NULL_RESOURCE = Any()
    }
}
