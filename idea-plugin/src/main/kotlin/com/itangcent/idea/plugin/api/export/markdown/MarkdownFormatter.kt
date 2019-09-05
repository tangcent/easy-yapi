package com.itangcent.idea.plugin.api.export.markdown

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.common.constant.Attrs
import com.itangcent.common.model.Doc
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Request
import com.itangcent.common.utils.DateUtils
import com.itangcent.common.utils.KVUtils
import com.itangcent.common.utils.KitUtils
import com.itangcent.idea.plugin.api.export.DefaultDocParseHelper
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.util.ActionUtils
import com.itangcent.intellij.util.DocCommentUtils
import com.itangcent.intellij.util.forEachValid
import org.apache.commons.lang3.StringUtils
import java.util.*

@Singleton
class MarkdownFormatter {

    @Inject
    private val logger: Logger? = null

    @Inject
    private val actionContext: ActionContext? = null

    @Inject
    private val docParseHelper: DefaultDocParseHelper? = null

    @Inject
    private val moduleHelper: ModuleHelper? = null

    fun parseRequests(requests: MutableList<Doc>): String {
        val sb = StringBuilder()
        val groupedRequest = groupRequests(requests)
        parseApi(groupedRequest, 1) { sb.append(it) }
        return sb.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun groupRequests(docs: MutableList<Doc>): Any {

        //group by class into: {class:requests}
        val clsGroupedMap: HashMap<Any, ArrayList<Any?>> = HashMap()
        docs.forEach { request ->
            val resource = request.resource?.let { findResourceClass(it) } ?: NULL_RESOURCE
            clsGroupedMap.computeIfAbsent(resource) { ArrayList() }
                    .add(request)
        }

        //only one class
        if (clsGroupedMap.size == 1) {
            clsGroupedMap.entries.first()
                    .let {
                        val module = moduleHelper!!.findModule(it.key) ?: "easy-api"
                        return wrapInfo(module, arrayListOf(wrapInfo(it.key, it.value)))
                    }
        }

        //group by module
        val moduleGroupedMap: HashMap<Any, ArrayList<Any?>> = HashMap()
        clsGroupedMap.forEach { cls, items ->
            val module = moduleHelper!!.findModule(cls) ?: "easy-api"
            moduleGroupedMap.computeIfAbsent(module) { ArrayList() }
                    .add(wrapInfo(cls, items))
        }

        //only one module
        if (moduleGroupedMap.size == 1) {
            moduleGroupedMap.entries.first()
                    .let {
                        return wrapInfo(it.key, arrayListOf(wrapInfo(it.key, it.value)))
                    }
        }

        val modules: ArrayList<HashMap<String, Any?>> = ArrayList()
        moduleGroupedMap.entries
                .map { wrapInfo(it.key, arrayListOf(wrapInfo(it.key, it.value))) }
                .forEach { modules.add(it) }

        val rootModule = moduleHelper!!.findModuleByPath(ActionUtils.findCurrentPath()) ?: "easy-api"
        return wrapInfo("$rootModule-${DateUtils.format(DateUtils.now(), "yyyyMMddHHmmss")}", modules as ArrayList<Any?>)
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

        handle("---\n")
        handle("${hN(deep)} ${methodDoc.name}\n\n")
        handle("<a id=${methodDoc.name}> </a>\n\n")

        if (!methodDoc.desc.isNullOrBlank()) {
            handle("**Desc：**\n\n")
            handle("<p>${methodDoc.desc}</p>\n\n")
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
            methodDoc.ret?.let { parseBody(0, "", "", it, handle) }
        }

    }

    private fun parseRequest(request: Request, deep: Int, handle: (String) -> Unit) {

        handle("---\n")
        handle("${hN(deep)} ${request.name}\n\n")
        handle("<a id=${request.name}> </a>\n\n")

        //region basic info
        handle("${hN(deep + 1)} BASIC\n\n")
        handle("**Path：** ${request.path}\n\n")
        handle("**Method：** ${request.method}\n\n")
        if (!request.desc.isNullOrBlank()) {
            handle("**Desc：**\n\n")
            handle("<p>${request.desc}</p>\n\n")
        }
        //endregion

        handle("${hN(deep + 1)} REQUEST\n\n")

        //path
        if (!request.paths.isNullOrEmpty()) {
            handle("\n**Path：**\n\n")
            handle("| name  |  value   | desc  |\n")
            handle("| ------------ | ------------ | ------------ |\n")
            request.paths!!.forEach {
                handle("| ${it.name} | ${it.value ?: ""} |" +
                        " ${escape(it.desc)} |\n")
            }
        }

        //header
        if (!request.headers.isNullOrEmpty()) {
            handle("\n**Headers：**\n\n")
            handle("| name  |  value  |  required | example  | desc  |\n")
            handle("| ------------ | ------------ | ------------ | ------------ | ------------ |\n")
            request.headers!!.forEach {
                handle("| ${it.name} | ${it.value ?: ""} | ${KitUtils.fromBool(it.required ?: false, "YES", "NO")} |" +
                        " ${it.example ?: ""} | ${escape(it.desc)} |\n")
            }
        }

        //query
        if (!request.querys.isNullOrEmpty()) {
            handle("\n**Query：**\n\n")
            handle("| name  |  value  |  required | desc  |\n")
            handle("| ------------ | ------------ | ------------ | ------------ |\n")
            request.querys!!.forEach {
                handle("| ${it.name} | ${it.value ?: ""} | ${KitUtils.fromBool(it.required ?: false, "YES", "NO")} |" +
                        " ${escape(it.desc)} |\n")
            }
        }

        if (request.method != "GET") {

            if (request.body != null) {

                handle("\n**RequestBody**\n\n")
                handle("| name  |  type  |  desc  |\n")
                handle("| ------------ | ------------ | ------------ |\n")
                parseBody(0, "", "", request.body, handle)

            } else if (!request.formParams.isNullOrEmpty()) {
                handle("\n**Form：**\n\n")
                handle("| name  |  value  | required |  type  |  desc  |\n")
                handle("| ------------ | ------------ | ------------ | ------------ | ------------ |\n")
                request.formParams!!.forEach {
                    handle("| ${it.name} | ${it.value} | ${KitUtils.fromBool(it.required ?: false, "YES", "NO")} |" +
                            " ${it.type} | ${escape(it.desc)} |\n")
                }
            }

        }

        if (!request.response.isNullOrEmpty()) {

            val response = request.response!!.firstOrNull { it.body != null }
            //todo:support multiple response
            if (response != null) {
                handle("${hN(deep + 1)} RESPONSE\n\n")
                handle("**Header：**\n\n")
                handle("| name  |  value  |  required | example  | desc  |\n")
                handle("| ------------ | ------------ | ------------ | ------------ | ------------ |\n")
                response.headers!!.forEach {
                    handle("| ${it.name} | ${it.value ?: ""} | ${KitUtils.fromBool(it.required
                            ?: false, "YES", "NO")} |" +
                            " ${it.example ?: ""} | ${escape(it.desc)} |\n")
                }

                handle("\n**Body：**\n\n")
                handle("| name  |  type  |  desc  |\n")
                handle("| ------------ | ------------ | ------------ |\n")
                response.body?.let { parseBody(0, "", "", it, handle) }
            }

        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseBody(deep: Int, name: String, desc: String, obj: Any?, handle: (String) -> Unit) {

        var type: String? = null
        when (obj) {
            obj == null -> type = "object"
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

    private fun wrapInfo(resource: Any, items: ArrayList<Any?>): HashMap<String, Any?> {
        val info: HashMap<String, Any?> = HashMap()
        parseNameAndDesc(resource, info)
        info[ITEMS] = items
        return info
    }

    private fun parseNameAndDesc(resource: Any, info: HashMap<String, Any?>) {
        if (resource is PsiClass) {
            val attr = findAttrOfClass(resource)
            if (attr.isNullOrBlank()) {
                info[NAME] = resource.name!!
                info[DESC] = "exported from module:${resource.qualifiedName}"
            } else {
                val lines = attr.lines()
                if (lines.size == 1) {
                    info[NAME] = attr
                    info[DESC] = "exported from module:${actionContext!!.callInReadUI { resource.qualifiedName }}"
                } else {
                    info[NAME] = lines[0]
                    info[DESC] = attr
                }
            }
        } else {
            info[NAME] = "$resource-${DateUtils.format(DateUtils.now(), "yyyyMMddHHmmss")}"
            info[DESC] = "exported at ${DateUtils.formatYMD_HMS(DateUtils.now())}"
        }
    }

    private fun findResourceClass(resource: Any): PsiClass? {
        return when (resource) {
            is PsiMethod -> actionContext!!.callInReadUI { resource.containingClass }
            is PsiClass -> resource
            else -> null
        }
    }

    private fun findAttrOfClass(cls: PsiClass): String? {
        val docComment = actionContext!!.callInReadUI { cls.docComment }
        val docText = DocCommentUtils.getAttrOfDocComment(docComment)
        return when {
            StringUtils.isBlank(docText) -> cls.name
            else -> docParseHelper!!.resolveLinkInAttr(docText, cls)
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