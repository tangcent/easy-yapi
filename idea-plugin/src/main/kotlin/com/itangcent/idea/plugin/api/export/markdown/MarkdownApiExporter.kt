package com.itangcent.idea.plugin.api.export.markdown

import com.google.inject.Inject
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.common.constant.Attrs
import com.itangcent.common.exporter.ClassExporter
import com.itangcent.common.exporter.ParseHandle
import com.itangcent.common.model.Request
import com.itangcent.common.utils.DateUtils
import com.itangcent.common.utils.KitUtils
import com.itangcent.idea.plugin.api.export.DocParseHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanApiExporter
import com.itangcent.idea.plugin.utils.FileSaveHelper
import com.itangcent.idea.plugin.utils.ModuleHelper
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.SelectedHelper
import com.itangcent.intellij.util.ActionUtils
import com.itangcent.intellij.util.DocCommentUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import java.util.*

class MarkdownApiExporter {

    @Inject
    private val logger: Logger? = null

    @Inject
    private val actionContext: ActionContext? = null

    @Inject
    private val classExporter: ClassExporter? = null

    @Inject
    private val parseHandle: ParseHandle? = null

    @Inject
    private val docParseHelper: DocParseHelper? = null

    @Inject
    private val fileSaveHelper: FileSaveHelper? = null

    @Inject
    private val moduleHelper: ModuleHelper? = null

    fun export() {

        logger!!.info("Start find apis...")
        val requests: MutableList<Request> = Collections.synchronizedList(ArrayList<Request>())

        SelectedHelper.Builder()
                .dirHandle { dir, callBack ->
                    actionContext!!.runInSwingUI {
                        try {
                            val project = actionContext.instance(Project::class)
                            val yes = Messages.showYesNoDialog(project,
                                    "Export the model in directory [${ActionUtils.findCurrentPath(dir)}]?",
                                    "Are you sure",
                                    Messages.getQuestionIcon())
                            if (yes == Messages.YES) {
                                callBack(true)
                            } else {
                                logger.info("Cancel the operation export api from [${ActionUtils.findCurrentPath(dir)}]!")
                                callBack(false)
                            }
                        } catch (e: Exception) {
                            callBack(false)
                        }
                    }
                }
                .classHandle {
                    classExporter!!.export(it, parseHandle!!) { request -> requests.add(request) }
                }
                .onCompleted {
                    try {
                        if (requests.isEmpty()) {
                            logger.info("No api be found to export!")
                            return@onCompleted
                        }
                        val apiInfo = parseRequests(requests)
                        requests.clear()
                        actionContext!!.runAsync {
                            try {
                                fileSaveHelper!!.saveOrCopy(apiInfo, {
                                    logger.info("Exported data are copied to clipboard,you can paste to a md file now")
                                }, {
                                    logger.info("Apis save success")
                                }) {
                                    logger.info("Apis save failed")
                                }
                            } catch (e: Exception) {
                                logger.info("Apis save failed" + ExceptionUtils.getStackTrace(e))
                            }
                        }
                    } catch (e: Exception) {
                        logger.info("Apis save failed" + ExceptionUtils.getStackTrace(e))
                    }
                }
                .traversal()
    }

    private fun parseRequests(requests: MutableList<Request>): String {
        val sb = StringBuilder()
        val groupedRequest = groupRequests(requests)
        parseApi(groupedRequest, 1) { sb.append(it) }
        return sb.toString()

    }

    private fun groupRequests(requests: MutableList<Request>): Any {

        //group by class into: {class:requests}
        val clsGroupedMap: HashMap<Any, ArrayList<Any?>> = HashMap()
        requests.forEach { request ->
            val resource = request.resource?.let { findResourceClass(it) } ?: PostmanApiExporter.NULL_RESOURCE
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
        if (info is Request) {
            parseRequest(info, deep, handle)
        } else if (info is Map<*, *>) {
            parseInfo(info, deep, handle)
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

        //header
        if (!request.headers.isNullOrEmpty()) {
            handle("\n**Headers：**\n\n")
            handle("| name  |  value  |  required | example  | desc  |\n")
            handle("| ------------ | ------------ | ------------ | ------------ | ------------ |\n")
            request.headers!!.forEach {
                handle("| ${it.name} | ${it.value ?: ""} | ${KitUtils.fromBool(it.required ?: false, "YES", "NO")} |" +
                        " ${it.example ?: ""} | ${it.desc ?: ""} |\n")
            }
        }

        //query
        if (!request.querys.isNullOrEmpty()) {
            handle("\n**Query：**\n\n")
            handle("| name  |  value  |  required | desc  |\n")
            handle("| ------------ | ------------ | ------------ | ------------ |\n")
            request.querys!!.forEach {
                handle("| ${it.name} | ${it.value ?: ""} | ${KitUtils.fromBool(it.required ?: false, "YES", "NO")} |" +
                        " ${it.desc ?: ""} |\n")
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
                            " ${it.type} | ${it.desc} |\n")
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
                            " ${it.example ?: ""} | ${it.desc ?: ""} |\n")
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
            obj.forEach { k, v ->
                if (k != Attrs.COMMENT_ATTR) {
                    var propertyDesc: String? = null
                    if (comment != null) {
                        val descInComment = comment[k]
                        if (descInComment != null) {
                            propertyDesc = descInComment.toString()
                        }
                        val options = comment["$k@options"]
                        if (options != null) {
                            val optionList = options as List<Map<String, Any?>>

                            val optionDesc = getOptionDesc(optionList)
                            if (optionDesc != null) {
                                if (propertyDesc == null) {
                                    propertyDesc = optionDesc
                                } else {
                                    propertyDesc = "$propertyDesc\n$optionDesc"
                                }
                            }
                        }
                    }
                    parseBody(deep + 1, k.toString(), propertyDesc ?: "", v, handle)
                }
            }
        } else {
            addBodyProperty(deep, name, "object", desc, handle)
        }
    }

    private fun getOptionDesc(options: List<Map<String, Any?>>): String? {
        return options.stream()
                .map { it["value"].toString() + " :" + it["desc"] }
                .filter { it != null }
                .reduce { s1, s2 -> s1 + "\n" + s2 }
                .orElse(null)
    }

    private fun addBodyProperty(deep: Int, name: String, type: String, desc: String, handle: (String) -> Unit) {
        handle("| ")
        if (deep > 1) {
            handle("&ensp;&ensp;".repeat(deep - 1))
            handle("&#124;─")
        }
        handle("$name | $type | $desc |\n")
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
            val attr = findAttrOfClass(resource, parseHandle!!)
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

    private fun findAttrOfClass(cls: PsiClass, parseHandle: ParseHandle): String? {
        val docComment = actionContext!!.callInReadUI { cls.docComment }
        val docText = DocCommentUtils.getAttrOfDocComment(docComment)
        return when {
            StringUtils.isBlank(docText) -> cls.name
            else -> docParseHelper!!.resolveLinkInAttr(docText, cls, parseHandle)
        }
    }

    companion object {
        private const val NAME = "name"
        private const val DESC = "desc"
        private const val ITEMS = "items"
    }
}