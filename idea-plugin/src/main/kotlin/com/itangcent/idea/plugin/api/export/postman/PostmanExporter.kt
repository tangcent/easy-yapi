package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.psi.*
import com.intellij.psi.util.PsiUtil
import com.itangcent.common.utils.DateUtils
import com.itangcent.common.utils.GsonUtils
import com.itangcent.idea.plugin.api.export.ApiExporter
import com.itangcent.intellij.psi.JsonOption
import com.itangcent.intellij.spring.MultipartFile
import com.itangcent.intellij.util.ActionUtils
import com.itangcent.intellij.util.KV
import org.apache.commons.lang3.RandomUtils
import org.apache.commons.lang3.StringUtils
import org.apache.commons.lang3.exception.ExceptionUtils
import java.util.*
import java.util.function.Consumer

class PostmanExporter : ApiExporter() {

    @Inject
    var anActionEvent: AnActionEvent? = null

    fun export(): HashMap<String, Any?>? {

        val navigatable = anActionEvent!!.getData(CommonDataKeys.NAVIGATABLE)
        if (navigatable is PsiDirectory) {//select dir
            val itemsMap: HashMap<String, ArrayList<HashMap<String, Any?>>?> = HashMap()
            traversal(navigatable, {
                isJavaFile(it)
            }, Consumer { file ->
                export(file)?.forEach { module, item ->
                    if (!itemsMap.containsKey(module)) {
                        itemsMap[module] = ArrayList()
                    }
                    itemsMap[module]!!.add(item)
                }
            })

            if (itemsMap.isEmpty()) return null

            var rootModule = findModule(navigatable)
            if (rootModule == null) rootModule = ""
            return warpItemsWithInfo(itemsMap, rootModule)
        } else {
            val currentClass = ActionUtils.findCurrentClass() ?: return null
            val module = findModuleOfClass(currentClass) ?: findModule()
            val item = export(currentClass, module)
            return item?.let { warpWithInfo(it, module) }
        }
    }

    private fun export(psiFile: PsiFile): HashMap<String, HashMap<String, Any?>>? {
        if (psiFile !is PsiJavaFile) return null
        var module: String? = null
        var items: HashMap<String, HashMap<String, Any?>>? = null
        for (psiCls in psiFile.classes) {
            if (isCtrl(psiCls)) {
                if (module == null) {
                    module = findModuleOfClass(psiCls) ?: findModule(psiFile)
                }
                val item = export(psiCls, module)
                if (item != null) {
                    if (items == null) {
                        items = HashMap()
                    }
                    items[module!!] = item
                }
            }
        }

        if (items == null || items.isEmpty()) return null

        return items
    }

    private fun warpItemsWithInfo(itemsMap: Map<String, ArrayList<HashMap<String, Any?>>?>, rootModule: String): HashMap<String, Any?>? {
        val postman: HashMap<String, Any?> = HashMap()

        val info: HashMap<String, Any?> = HashMap()
        postman["info"] = info
        info["name"] = "$rootModule-${DateUtils.format(DateUtils.now(), "yyyyMMddHHmmss")}"
        info["description"] = "exported from module:$rootModule at ${DateUtils.formatYMD_HMS(DateUtils.now())}"
        info["schema"] = "https://schema.getpostman.com/json/collection/v2.0.0/collection.json"

        val dirItems: ArrayList<HashMap<String, Any?>> = ArrayList()
        postman["item"] = dirItems

        if (itemsMap.size == 1) {
            val singleModule = itemsMap.keys.first()
            if (singleModule == rootModule) {//todo:required equal?
                val items = itemsMap[singleModule]
                if (items == null) return null
                else {
                    dirItems.addAll(items)
                    return postman
                }
            }

        }

        itemsMap.forEach { module, items ->
            val dirItem: HashMap<String, Any?> = HashMap()
            dirItem["name"] = module
            dirItem["description"] = "exported from:" + module
            dirItem["item"] = items
            dirItems.add(dirItem)
        }
        return postman
    }

    private fun warpWithInfo(item: HashMap<String, Any?>, module: String?): HashMap<String, Any?> {
        val postman: HashMap<String, Any?> = HashMap()

        val info: HashMap<String, Any?> = HashMap()
        postman["info"] = info
        info["name"] = "$module-${DateUtils.format(DateUtils.now(), "yyyyMMddHHmmss")}"
        info["description"] = "exported from module:$module at ${DateUtils.formatYMD_HMS(DateUtils.now())}"
        info["schema"] = "https://schema.getpostman.com/json/collection/v2.0.0/collection.json"

        val dirItems: ArrayList<Map<String, Any?>> = ArrayList()
        postman["item"] = dirItems

        dirItems.add(item)

        return postman
    }

    private fun export(currentClass: PsiClass, module: String?): HashMap<String, Any?>? {

        if (shouldIgnore(currentClass)) {
            return null
        }

        //region use direct module name as host-----------------------------------------------------
        var host = "{{host}}"
        if (module != null) {
            host = "{{$module}}"
        }
        //endregion use direct module name as host-----------------------------------------------------

        val ctrlRequestMappingAnn = findRequestMapping(currentClass)
        val basePath: String = findHttpPath(ctrlRequestMappingAnn) ?: ""

        val ctrlHttpMethod = findHttpMethod(ctrlRequestMappingAnn)

        val dirItem: HashMap<String, Any?> = HashMap()
        dirItem["name"] = StringUtils.left(findAttrOfClass(currentClass), 30)
        dirItem["description"] = "exported from:" + currentClass.name

        val items: ArrayList<Map<String, Any?>> = ArrayList()
        dirItem["item"] = items

        foreachMethod(currentClass, { method ->
            exportMethodApi(method, basePath, ctrlHttpMethod, host)?.let { items.add(it) }
        })

        if (items.isEmpty()) {
            return null
        }

        return dirItem
    }

    private fun exportMethodApi(method: PsiMethod, basePath: String, ctrlHttpMethod: String, host: String): HashMap<String, Any?>? {

        val requestMappingAnn = findRequestMapping(method) ?: return null
        var httpMethod = findHttpMethod(requestMappingAnn)
        if (httpMethod == NO_METHOD && ctrlHttpMethod != NO_METHOD) {
            httpMethod = ctrlHttpMethod
        }

        val httpPath = contractPath(basePath, findHttpPath(requestMappingAnn))!!

        val item: HashMap<String, Any?> = HashMap()

        var attr: String? = null
        val attrOfMethod = findAttrOfMethod(method)!!
        if (attrOfMethod.contains("\n")) {//multi line
            val lines = attrOfMethod.lines()
            for (line in lines) {
                if (line.isNotBlank()) {
                    attr = line
                    break
                }
            }
            item["description"] = attrOfMethod
        } else {
            attr = attrOfMethod
        }

        item["name"] = attr

        val request: HashMap<String, Any?> = HashMap()
        item["request"] = request

        val url: HashMap<String, Any?> = HashMap()
        request["url"] = url

        url["host"] = host
        url["path"] = httpPath.trim().trim('/').split("/")
        url["raw"] = contractPath(host, httpPath)

        val itemInfo = PostmanItemInfo(item)
        itemInfo.setHttpMethod(httpMethod)
        processMethodParameters(method, itemInfo)

        val returnType = method.returnType
        if (returnType != null) {
            try {
                val response: HashMap<String, Any?> = HashMap()
                response["name"] = attr + "-Example"
                response["originalRequest"] = request//need clone?request.clone()?
                response["status"] = "OK"
                response["code"] = 200
                response["_postman_previewlanguage"] = "json"
                response["_postman_previewtype"] = "text"
                val responseHeader = ArrayList<Map<String, Any?>>()
                response["header"] = responseHeader
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
                response["responseTime"] = RandomUtils.nextInt(1, 10 * (1 + method.parameters.size))

                val typedResponse = psiClassHelper!!.getTypeObject(returnType, method)

                response["body"] = GsonUtils.prettyJson(typedResponse)

                //add response to item
                val responses: ArrayList<HashMap<String, Any?>> = ArrayList()
                responses.add(response)
                item["response"] = responses

            } catch (e: Throwable) {
                logger!!.error("error to parse body:" + ExceptionUtils.getStackTrace(e))
            }

        }
        return item

    }

    override fun parseRequestBody(psiType: PsiType?, context: PsiElement): Any? {
        return psiClassHelper!!.getTypeObject(psiType, context, JsonOption.NONE)
    }

    @Suppress("UNCHECKED_CAST")
    private inner class PostmanItemInfo(private val item: HashMap<String, Any?>) : ItemInfo {
        override fun getHttpPath(): String {

            val request = getRequest()
            val url = request["url"] as HashMap<String, Any?>

            val host = url["host"] as String?
            val raw = url["raw"] as String? ?: return ""

            return if (host != null) {
                raw.removePrefix(host)
            } else {
                raw
            }
        }

        override fun addPathVal(name: String, desc: String) {
            //ignore
        }

        override fun setJsonBody(body: Any?, desc: String?) {
            val request = getRequest()
            val bodyOfRequest: HashMap<String, Any?> = HashMap()
            request["body"] = bodyOfRequest

            bodyOfRequest["mode"] = "raw"

            bodyOfRequest["raw"] = GsonUtils.prettyJson(body)
        }

        override fun setHttpMethod(httpMethod: String) {
            getRequest()["method"] = httpMethod
        }

        override fun getHttpMethod(): String {
            val method = getRequest()["method"]
            if (method != null) return method as String
            setHttpMethod(NO_METHOD)
            return NO_METHOD
        }

        override fun getData(): Any {
            return item
        }

        @Suppress("UNCHECKED_CAST")
        fun getRequest(): HashMap<String, Any?> {
            var request = item["request"]
            if (request == null) {
                request = HashMap<String, Any?>()
                item["request"] = request
            }
            return request as HashMap<String, Any?>
        }

        @Suppress("UNCHECKED_CAST")
        fun getUrl(): HashMap<String, Any?> {
            val request = getRequest()
            var url = request["url"]
            if (url == null) {
                url = HashMap<String, Any?>()
                request["url"] = url
            }
            return url as HashMap<String, Any?>
        }

        @Suppress("UNCHECKED_CAST")
        fun getUrlencodeds(): ArrayList<HashMap<String, Any?>> {

            setHeader("Content-Type", "application/x-www-form-urlencoded")
            val request = getRequest()
            val body: HashMap<String, Any?> = request.computeIfAbsent("body",
                    { _ -> HashMap<String, Any?>() }) as HashMap<String, Any?>

            body["mode"] = "urlencoded"
            val urlencodeds: ArrayList<HashMap<String, Any?>> = body.computeIfAbsent("urlencoded",
                    { _ -> ArrayList<HashMap<String, Any?>>() }) as ArrayList<HashMap<String, Any?>>
            return urlencodeds
        }

        override fun addModelAttr(parameter: PsiParameter) {

            val urlencodeds: ArrayList<HashMap<String, Any?>> = getUrlencodeds()

            val paramType = parameter.type
            try {
                val paramCls = PsiUtil.resolveClassInClassTypeOnly(psiClassHelper!!.unboxArrayOrList(paramType))
                val fields = psiClassHelper.getFields(paramCls, JsonOption.READ_COMMENT)
                val comment: KV<String, Any>? = fields.getAs(COMMENT_ATTR)
                fields.forEach { filedName, fieldVal ->
                    if (filedName != COMMENT_ATTR) {
                        val urlencodedParam: HashMap<String, Any?> = HashMap()
                        urlencodeds.add(buildFormParam(filedName, fieldVal))
                        if (comment != null) {
                            val fieldComment = comment.getAs<String?>(filedName)
                            urlencodedParam["description"] = fieldComment
                        }
                    }
                }
            } catch (e: Exception) {
                logger!!.error("error to parse[" + paramType.canonicalText + "] as ModelAttribute")
            }
        }

        @Suppress("UNCHECKED_CAST")
        override fun addQuery2Url(paramName: String, defaultVal: Any?, required: Boolean, attr: String?) {
            val url = getUrl()
            val queryInUrl = url["query"]
            val queryList: ArrayList<HashMap<String, Any?>>
            if (queryInUrl == null) {
                queryList = ArrayList()
                url["query"] = queryList
            } else {
                queryList = queryInUrl as ArrayList<HashMap<String, Any?>>
            }

            val query: HashMap<String, Any?> = HashMap()
            queryList.add(query)

            query["key"] = paramName
            query["value"] = tinyQueryParam(defaultVal?.toString())
            query["equals"] = true
            query["description"] = attr
        }

        override fun addModelAttr(name: String, value: Any?, attr: String?) {
            val urlencodeds: ArrayList<HashMap<String, Any?>> = getUrlencodeds()
            urlencodeds.add(buildFormParam(name, value))
        }

        override fun addModelAttrAsQuery(parameter: PsiParameter) {
            val paramType = parameter.type
            try {
                val paramCls = PsiUtil.resolveClassInClassTypeOnly(psiClassHelper!!.unboxArrayOrList(paramType))
                val fields = psiClassHelper.getFields(paramCls, JsonOption.READ_COMMENT)
                val comment: KV<String, Any>? = fields.getAs(COMMENT_ATTR)
                fields.forEach { filedName, fieldVal ->
                    if (filedName != COMMENT_ATTR) {
                        addQuery2Url(filedName, fieldVal?.toString(), comment?.get("filedName") as String?)
                    }
                }
            } catch (e: Exception) {
                logger!!.error("error to parse[" + paramType.canonicalText + "] as ModelAttribute")
            }
        }

        override fun appendDesc(desc: String?) {
            //ignore
        }

        @Suppress("UNCHECKED_CAST")
        override fun setHeader(name: String, value: String) {

            val request = getRequest()

            val headers: ArrayList<HashMap<String, Any?>> = request.computeIfAbsent("header",
                    { _ -> ArrayList<HashMap<String, Any?>>() }) as ArrayList<HashMap<String, Any?>>

            var contentHeader: java.util.HashMap<String, Any?>? = headers.firstOrNull { it["key"] == name }
            if (contentHeader == null) {
                contentHeader = HashMap()
                headers.add(contentHeader)
                contentHeader["key"] = name
            }
            contentHeader["value"] = value
        }

        private fun buildFormParam(name: String, value: Any?): HashMap<String, Any?> {
            val urlencodedParam: HashMap<String, Any?> = HashMap()
            urlencodedParam["key"] = name
            val dv = deepComponent(value)
            if (dv != null && dv is MultipartFile) {
                urlencodedParam["value"] = null
                urlencodedParam["type"] = "file"
            } else {
                urlencodedParam["value"] = dv?.toString()
                urlencodedParam["type"] = "text"
            }
            return urlencodedParam
        }
    }
}
