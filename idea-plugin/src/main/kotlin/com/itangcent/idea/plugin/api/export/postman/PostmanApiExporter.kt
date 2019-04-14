package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.itangcent.common.exporter.ClassExporter
import com.itangcent.common.exporter.ParseHandle
import com.itangcent.common.model.Request
import com.itangcent.common.utils.DateUtils
import com.itangcent.common.utils.GsonUtils
import com.itangcent.idea.plugin.api.export.CommonRules
import com.itangcent.idea.plugin.api.export.DocParseHelper
import com.itangcent.idea.plugin.utils.FileSaveHelper
import com.itangcent.idea.plugin.utils.RequestUtils
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.SelectedHelper
import com.itangcent.intellij.util.ActionUtils
import com.itangcent.intellij.util.DocCommentUtils
import com.itangcent.intellij.util.KV
import org.apache.commons.lang3.RandomUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap

class PostmanApiExporter {

    @Inject
    private val logger: Logger? = null

    @Inject
    private val postmanApiHelper: PostmanApiHelper? = null

    @Inject
    private val actionContext: ActionContext? = null

    @Inject
    private val classExporter: ClassExporter? = null

    @Inject
    private val parseHandle: ParseHandle? = null

    @Inject
    private val commonRules: CommonRules? = null

    @Inject
    private val docParseHelper: DocParseHelper? = null

    @Inject
    private val fileSaveHelper: FileSaveHelper? = null

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
                        val postman = parseRequests(requests)
                        requests.clear()
                        actionContext!!.runAsync {
                            try {
                                if (postmanApiHelper!!.hasPrivateToken()) {
                                    logger.info("PrivateToken of postman be found")
                                    if (postmanApiHelper.importApiInfo(postman)) {
                                        return@runAsync
                                    } else {
                                        logger.error("Export to postman failed,You could check below:" +
                                                "1.the network " +
                                                "2.the privateToken")
                                    }
                                } else {
                                    logger.info("PrivateToken of postman not be setting")
                                    logger.info("To enable automatically import to postman you could set privateToken" +
                                            " of host [https://api.getpostman.com] in \"File -> Other Setting -> EasyApiSetting\"")
                                    logger.info("If you do not have a privateToken of postman, you can easily generate one by heading over to the" +
                                            " Postman Integrations Dashboard [https://go.postman.co/integrations/services/pm_pro_api].")
                                }
                                fileSaveHelper!!.saveOrCopy(GsonUtils.prettyJson(postman), {
                                    logger.info("Exported data are copied to clipboard,you can paste to postman now")
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

    private fun parseRequests(requests: MutableList<Request>): HashMap<String, Any?> {

        //group by class into: {class:requests}
        val clsGroupedMap: HashMap<Any, ArrayList<HashMap<String, Any?>>> = HashMap()
        requests.forEach { request ->
            val resource = request.resource?.let { findResourceClass(it) } ?: NULL_RESOURCE
            clsGroupedMap.computeIfAbsent(resource) { ArrayList() }
                    .add(request2Item(request))
        }

        //only one class
        if (clsGroupedMap.size == 1) {
            clsGroupedMap.entries.first()
                    .let {
                        val module = findModule(it.key) ?: "easy-api"
                        return wrapRootInfo(module, arrayListOf(wrapInfo(it.key, it.value)))
                    }
        }

        //group by module
        val moduleGroupedMap: HashMap<Any, ArrayList<HashMap<String, Any?>>> = HashMap()
        clsGroupedMap.forEach { cls, items ->
            val module = findModule(cls) ?: "easy-api"
            moduleGroupedMap.computeIfAbsent(module) { ArrayList() }
                    .add(wrapInfo(cls, items))
        }


        //only one module
        if (moduleGroupedMap.size == 1) {
            moduleGroupedMap.entries.first()
                    .let {
                        return wrapRootInfo(it.key, arrayListOf(wrapInfo(it.key, it.value)))
                    }
        }

        val modules: ArrayList<HashMap<String, Any?>> = ArrayList()
        moduleGroupedMap.entries
                .map { wrapInfo(it.key, arrayListOf(wrapRootInfo(it.key, it.value))) }
                .forEach { modules.add(it) }

        val rootModule = findModuleByPath(ActionUtils.findCurrentPath()) ?: "easy-api"
        return wrapRootInfo("$rootModule-${DateUtils.format(DateUtils.now(), "yyyyMMddHHmmss")}", modules)
    }

    private fun wrapRootInfo(resource: Any, items: ArrayList<HashMap<String, Any?>>): HashMap<String, Any?> {

        val postman: HashMap<String, Any?> = HashMap()
        val info: HashMap<String, Any?> = HashMap()
        postman["info"] = info
        parseNameAndDesc(resource, info)
        info["schema"] = "https://schema.getpostman.com/json/collection/v2.0.0/collection.json"
        postman["item"] = items
        return postman
    }

    private fun wrapInfo(resource: Any, items: ArrayList<HashMap<String, Any?>>): HashMap<String, Any?> {
        val postman: HashMap<String, Any?> = HashMap()
        parseNameAndDesc(resource, postman)
        postman["item"] = items
        return postman
    }

    private fun parseNameAndDesc(resource: Any, info: HashMap<String, Any?>) {
        if (resource is PsiClass) {
            val attr = findAttrOfClass(resource, parseHandle!!)
            if (attr.isNullOrBlank()) {
                info["name"] = resource.name!!
                info["description"] = "exported from module:${resource.qualifiedName}"
            } else {
                val lines = attr.lines()
                if (lines.size == 1) {
                    info["name"] = attr
                    info["description"] = "exported from module:${actionContext!!.callInReadUI { resource.qualifiedName }}"
                } else {
                    info["name"] = lines[0]
                    info["description"] = attr
                }
            }
        } else {
            info["name"] = "$resource-${DateUtils.format(DateUtils.now(), "yyyyMMddHHmmss")}"
            info["description"] = "exported at ${DateUtils.formatYMD_HMS(DateUtils.now())}"
        }
    }

    private fun request2Item(request: Request): HashMap<String, Any?> {

        val module = request.resource?.let { findModule(it) }
        var host = "{{host}}"
        if (module != null) {
            host = "{{$module}}"
        }

        val item: HashMap<String, Any?> = HashMap()

        item["name"] = request.name

        val requestInfo: HashMap<String, Any?> = HashMap()
        item["request"] = requestInfo

        requestInfo["method"] = request.method
        requestInfo["description"] = request.desc

        val url: HashMap<String, Any?> = HashMap()
        requestInfo["url"] = url

        url["host"] = host
        url["path"] = request.path!!.trim().trim('/').split("/")
        url["raw"] = RequestUtils.contractPath(host, request.path)


        val headers: ArrayList<HashMap<String, Any?>> = ArrayList()
        requestInfo["header"] = headers
        request.headers?.forEach {
            headers.add(KV.create<String, Any?>()
                    .set("key", it.name)
                    .set("value", it.value)
            )
        }

        val queryList: ArrayList<HashMap<String, Any?>> = ArrayList()
        url["query"] = queryList
        request.querys?.forEach {
            queryList.add(KV.create<String, Any?>()
                    .set("key", it.name)
                    .set("value", it.value)
                    .set("equals", true)
                    .set("description", it.desc)
            )
        }

        val body: HashMap<String, Any?> = HashMap()
        if (request.formParams != null) {
            body["mode"] = "urlencoded"
            val urlencodeds: ArrayList<HashMap<String, Any?>> = ArrayList()
            request.formParams!!.forEach {
                urlencodeds.add(KV.create<String, Any?>()
                        .set("key", it.name)
                        .set("value", it.value)
                        .set("type", it.type)
                        .set("description", it.desc)
                )
            }
            body["urlencoded"] = urlencodeds
        }

        if (request.body != null) {
            body["mode"] = "raw"
            body["raw"] = RequestUtils.parseRawBody(request.body!!)
        }

        if (body.isNotEmpty()) {
            requestInfo["body"] = body
        }


        if (!request.response.isNullOrEmpty()) {

            val responses: ArrayList<HashMap<String, Any?>> = ArrayList()
            val exampleName = request.name + "-Example"
            request.response!!.forEachIndexed { index, response ->
                val responseInfo: HashMap<String, Any?> = HashMap()
                if (index > 0) {
                    responseInfo["name"] = exampleName + (index + 1)
                } else {
                    responseInfo["name"] = exampleName
                }
                responseInfo["originalRequest"] = requestInfo//need clone?request.clone()?
                responseInfo["status"] = "OK"
                responseInfo["code"] = 200
                responseInfo["_postman_previewlanguage"] = "json"
                responseInfo["_postman_previewtype"] = "text"
                val responseHeader = ArrayList<Map<String, Any?>>()
                responseInfo["header"] = responseHeader
                responseHeader.add(KV.create<String, Any?>()
                        .set("name", "content-type")
                        .set("key", "content-type")
                        .set("value", "application/json;charset=UTF-8")
                        .set("description", "The mime type of this content")
                )
                responseHeader.add(KV.create<String, Any?>()
                        .set("name", "date")
                        .set("key", "date")
                        .set("value", DateUtils.format(Date(), "EEE, dd MMM yyyyHH:mm:ss 'GMT'"))
                        .set("description", "The date and time that the message was sent")
                )
                responseHeader.add(KV.create<String, Any?>()
                        .set("name", "server")
                        .set("key", "server")
                        .set("value", "Apache-Coyote/1.1")
                        .set("description", "A name for the server")
                )
                responseHeader.add(KV.create<String, Any?>()
                        .set("name", "transfer-encoding")
                        .set("key", "transfer-encoding")
                        .set("value", "chunked")
                        .set("description", "The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.")
                )

                response.headers?.forEach {
                    responseHeader.add(KV.create<String, Any?>()
                            .set("name", it.name)
                            .set("key", it.name)
                            .set("value", it.value)
                            .set("description", it.desc)
                    )
                }

                responseInfo["responseTime"] = RandomUtils.nextInt(10, 100)

                responseInfo["body"] = response.body?.let { RequestUtils.parseRawBody(it) }

                responses.add(responseInfo)
            }
            item["response"] = responses
        }

        return item
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

    //region find module
    private fun findModule(resource: Any): String? {
        return when (resource) {
            is PsiMethod -> findModule(resource)
            is PsiClass -> findModule(resource)
            is PsiFile -> findModule(resource)
            else -> null
        }
    }

    private fun findModule(psiMethod: PsiMethod): String? {
        val containingClass = psiMethod.containingClass
        if (containingClass != null) {
            return findModule(containingClass)
        }
        return null
    }

    private fun findModule(cls: PsiClass): String? {
        val moduleRules = commonRules!!.readModuleRules()
        if (!moduleRules.isEmpty()) {
            val module = moduleRules
                    .map { it(cls, cls, cls) }
                    .firstOrNull { it != null }
            if (!module.isNullOrBlank()) {
                return module
            }
        }
        return findModule(cls.containingFile)
    }

    private fun findModule(psiFile: PsiFile): String? {
        val currentPath = ActionUtils.findCurrentPath(psiFile)
        return findModuleByPath(currentPath)
    }

    private fun findModuleByPath(path: String?): String? {
        if (path == null) return null
        var module: String? = null
        try {
            var currentPath = path
            when {
                currentPath.contains("/src/") -> currentPath = StringUtils.substringBefore(currentPath, "/src/")
                currentPath.contains("/main/") -> currentPath = StringUtils.substringBefore(currentPath, "/main/")
                currentPath.contains("/java/") -> currentPath = StringUtils.substringBefore(currentPath, "/java/")
            }
            module = StringUtils.substringAfterLast(currentPath, "/")
        } catch (e: Exception) {
            logger!!.error("error in findCurrentPath:" + e.toString())
        }
        return module

    }
    //endregion

    companion object {
        val NULL_RESOURCE = Object()
    }
}