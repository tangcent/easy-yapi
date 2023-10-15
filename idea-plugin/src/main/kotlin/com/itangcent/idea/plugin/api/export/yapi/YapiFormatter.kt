package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiMethod
import com.itangcent.common.constant.Attrs
import com.itangcent.common.kit.KVUtils
import com.itangcent.common.logger.traceWarn
import com.itangcent.common.model.*
import com.itangcent.common.utils.*
import com.itangcent.idea.plugin.api.export.UrlSelector
import com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys
import com.itangcent.idea.plugin.format.Json5Formatter
import com.itangcent.idea.plugin.render.MarkdownRender
import com.itangcent.idea.plugin.rule.SuvRuleContext
import com.itangcent.idea.plugin.rule.setDoc
import com.itangcent.idea.plugin.settings.helper.YapiSettingsHelper
import com.itangcent.idea.psi.resource
import com.itangcent.idea.psi.resourceMethod
import com.itangcent.idea.utils.SystemProvider
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.config.rule.SimpleRuleParser
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.takeIfNotOriginal
import com.itangcent.intellij.extend.takeIfSpecial
import com.itangcent.intellij.jvm.DocHelper
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.PsiClassUtils
import com.itangcent.intellij.tip.OnlyOnceInContextTip
import com.itangcent.intellij.tip.TipsHelper
import com.itangcent.intellij.util.forEachValid
import java.util.*
import java.util.regex.Pattern

@Singleton
open class YapiFormatter {

    @Inject
    private lateinit var logger: Logger

    @Inject
    private lateinit var ruleComputer: RuleComputer

    @Inject
    private lateinit var configReader: ConfigReader

    @Inject
    private lateinit var actionContext: ActionContext

    @Inject
    protected lateinit var docHelper: DocHelper

    @Inject
    protected lateinit var markdownRender: MarkdownRender

    @Inject
    protected lateinit var systemProvider: SystemProvider

    @Inject
    protected lateinit var yapiSettingsHelper: YapiSettingsHelper

    @Inject
    protected lateinit var urlSelector: UrlSelector

    protected val json5Formatter: Json5Formatter by lazy {
        actionContext.instance(Json5Formatter::class)
    }

    fun doc2Items(doc: Doc): List<HashMap<String, Any?>> {
        if (doc is Request) {
            return request2Items(doc)
        } else if (doc is MethodDoc) {
            return listOf(methodDoc2Item(doc))
        }
        throw IllegalArgumentException("unknown doc")
    }

    //region methodDoc----------------------------------------------------------

    fun methodDoc2Item(methodDoc: MethodDoc): HashMap<String, Any?> {

        val item: HashMap<String, Any?> = HashMap()

        item["edit_uid"] = 0
        item["status"] = methodDoc.getStatus()
        item["type"] = "static"
        item["req_body_is_json_schema"] = false
        item["res_body_is_json_schema"] = true
        item["api_opened"] = methodDoc.isOpen()
        item["index"] = 0
        item["tag"] = methodDoc.getTags()

        item["title"] = methodDoc.name

        appendDescToApiItem(item, methodDoc.desc)

        val queryPath: HashMap<String, Any?> = HashMap()
        item["query_path"] = queryPath
        queryPath["params"] = EMPTY_PARAMS

        val path = actionContext.callInReadUI { formatPath(getPathOfMethodDoc(methodDoc)) }
        queryPath["path"] = path
        item["path"] = path

        addTimeAttr(item)
        item["__v"] = 0

        val httpMethod = actionContext.callInReadUI { getHttpMethodOfMethodDoc(methodDoc) }
        item["method"] = httpMethod

        val headers: ArrayList<HashMap<String, Any?>> = ArrayList()
        item["req_headers"] = headers


        item["req_query"] = emptyArray<Any>()

        if (!methodDoc.params.isNullOrEmpty()) {
            if (httpMethod == "GET") {
                val queryList: MutableList<HashMap<String, Any?>> = LinkedList()
                item["req_query"] = queryList
                methodDoc.params?.forEach {
                    queryList.add(
                        linkedMapOf(
                            "name" to it.name,
                            "value" to parseQueryValueAsJson5(it.value),
                            "example" to parseQueryValueAsJson5(it.getExample() ?: it.value.takeIfNotOriginal()),
                            "desc" to it.desc,
                            "required" to it.required.asInt()
                        )
                    )
                }
            } else {
                item["req_body_is_json_schema"] = true
                item["req_body_type"] = "json"
                item["req_body_form"] = EMPTY_ARR

                //todo:need desc of body
                item["req_body_other"] = parseParamsBySchema(methodDoc.params, "")
            }
        }

        if (methodDoc.ret != null) {
            item["res_body_type"] = "json"
            if (yapiSettingsHelper.yapiResBodyJson5()) {
                item["res_body_is_json_schema"] = false
                item["res_body"] = parseByJson5(methodDoc.ret,
                    methodDoc.resourceMethod()?.let {
                        findReturnOfMethod(it)
                    })
            } else {
                item["res_body"] = parseBySchema(methodDoc.ret,
                    methodDoc.resourceMethod()?.let {
                        findReturnOfMethod(it)
                    })
            }
        } else {
            item["res_body_type"] = "json"
            item["res_body"] = ""
        }

        return item
    }

    private fun parseQueryValueAsJson5(value: Any?): String? {
        when (value) {
            is Array<*> -> {
                return json5Formatter.format(value)
            }

            is Collection<*> -> {
                return json5Formatter.format(value)
            }

            is Map<*, *> -> {
                return json5Formatter.format(value)
            }

            else -> {
                return value?.toString()
            }
        }
    }

    private fun getPathOfMethodDoc(methodDoc: MethodDoc): String {
        val path = ruleComputer.computer(ClassExportRuleKeys.METHOD_DOC_PATH, methodDoc.resource()!!)

        if (path.notNullOrEmpty()) {
            return path!!
        }

        return formatPath(PsiClassUtils.fullNameOfMethod(methodDoc.resourceMethod()!!))
    }

    private fun getHttpMethodOfMethodDoc(methodDoc: MethodDoc): String {
        return ruleComputer.computer(ClassExportRuleKeys.METHOD_DOC_METHOD, methodDoc.resource()!!)
            ?: "POST"
    }

    private fun parseParamsBySchema(params: MutableList<Param>?, rootDesc: String?): String? {
        if (params == null) return null

        val result: HashMap<String, Any?> = LinkedHashMap()

        result["type"] = "object"
        val properties: HashMap<String, Any?> = LinkedHashMap()
        var requireds: LinkedList<String>? = null
        for (param in params) {

            val propertyInfo = parseObject(param.name, param.value)

            if (param.desc != null) {
                propertyInfo["description"] = param.desc
            }
            if (param.required == true) {
                if (requireds == null) {
                    requireds = LinkedList()
                }
                requireds.add(param.name!!)
            }
            properties[param.name!!] = propertyInfo
        }

        result["properties"] = properties
        if (requireds.notNullOrEmpty()) {
            result["required"] = requireds!!.toTypedArray()
        }

        return toJsonWithSchema(result, rootDesc)
    }

    //endregion methodDoc----------------------------------------------------------

    fun request2Items(request: Request): List<HashMap<String, Any?>> {

        val item = request2Item(request)

        val urls = urlSelector.selectUrls(request)

        val suvRuleContext = SuvRuleContext(request.resource())
        suvRuleContext.setDoc(request)

        if (urls.single()) {
            val selectedUrl = urls.url() ?: ""
            val path = formatPath(selectedUrl)
            val queryPath: HashMap<String, Any?> = item.getAs("query_path")!!
            queryPath["path"] = path
            item["path"] = path

            suvRuleContext.setExt("url", selectedUrl)
            suvRuleContext.setExt("item", item)
            ruleComputer.computer(YapiClassExportRuleKeys.AFTER_FORMAT, suvRuleContext, request.resource())

            return listOf(item)
        } else {
            return urls.urls().map { selectedUrl ->
                val copyItem = copyItem(item)
                val path = formatPath(selectedUrl)
                val queryPath: HashMap<String, Any?> = copyItem.getAs("query_path")!!
                copyItem["path"] = path
                queryPath["path"] = path

                suvRuleContext.setExt("url", selectedUrl)
                suvRuleContext.setExt("item", copyItem)
                ruleComputer.computer(YapiClassExportRuleKeys.AFTER_FORMAT, suvRuleContext, request.resource())
                return@map copyItem
            }
        }
    }

    protected open fun copyItem(item: HashMap<String, Any?>): HashMap<String, Any?> {
        val copyItem = linkedMapOf<String, Any?>()
        copyItem.putAll(item)

        val queryPath = HashMap(item.getAs<HashMap<String, Any?>>("query_path"))
        copyItem["queryPath"] = queryPath

        return copyItem
    }

    fun request2Item(request: Request): HashMap<String, Any?> {

        val item: HashMap<String, Any?> = HashMap()

        item["edit_uid"] = 0
        item["status"] = request.getStatus()
        item["type"] = "static"
        item["req_body_is_json_schema"] = false
        item["res_body_is_json_schema"] = true
        item["api_opened"] = request.isOpen()
        item["index"] = 0
        item["tag"] = request.getTags()

        item["title"] = request.name

        appendDescToApiItem(item, request.desc)

        val queryPath: HashMap<String, Any?> = HashMap()
        item["query_path"] = queryPath
        queryPath["params"] = EMPTY_PARAMS

//        queryPath["path"] = formatPath(request.path)
//        item["path"] = formatPath(request.path)

        addTimeAttr(item)
        item["__v"] = 0

        item["method"] = request.method

        val headers: MutableList<HashMap<String, Any?>> = LinkedList()
        item["req_headers"] = headers
        request.headers?.forEach {
            headers.add(
                linkedMapOf(
                    "name" to it.name,
                    "value" to it.value,
                    "desc" to it.desc,
                    "example" to (it.getExample() ?: it.value.takeIfSpecial()),
                    "required" to it.required.asInt()
                )
            )
        }

        val queryList: MutableList<HashMap<String, Any?>> = LinkedList()
        item["req_query"] = queryList
        request.querys?.forEach {
            queryList.add(
                linkedMapOf(
                    "name" to it.name,
                    "value" to it.value,
                    "example" to (it.getExample() ?: it.value.takeIfNotOriginal()?.toString()),
                    "desc" to it.desc,
                    "required" to it.required.asInt()
                )
            )
        }

        if (request.formParams != null) {
            item["req_body_type"] = "form"
            val urlencodeds: ArrayList<HashMap<String, Any?>> = ArrayList()
            item["req_body_form"] = urlencodeds
            request.formParams!!.forEach {
                urlencodeds.add(
                    linkedMapOf(
                        "name" to it.name,
                        "example" to (it.getExample() ?: it.value.takeIfSpecial()),
                        "type" to it.type,
                        "required" to it.required.asInt(),
                        "desc" to it.desc
                    )
                )
            }
        }

        if (request.paths != null) {
            val pathParmas: ArrayList<HashMap<String, Any?>> = ArrayList()
            item["req_params"] = pathParmas
            request.paths!!.forEach {
                pathParmas.add(
                    linkedMapOf(
                        "name" to it.name,
                        "example" to (it.getExample() ?: it.value.takeIfSpecial()),
                        "desc" to it.desc
                    )
                )
            }
        }

        if (request.body != null) {
            item["req_body_type"] = "json"
            item["req_body_form"] = EMPTY_ARR

            if (yapiSettingsHelper.yapiReqBodyJson5()) {
                item["req_body_is_json_schema"] = false
                item["req_body_other"] = parseByJson5(request.body, request.bodyAttr)
            } else {
                item["req_body_is_json_schema"] = true
                item["req_body_other"] = parseBySchema(request.body, request.bodyAttr)
            }
        }

        if (!request.response.isNullOrEmpty()) {

            val response = request.response!![0]
            item["res_body_type"] = "json"
            if (yapiSettingsHelper.yapiResBodyJson5()) {
                item["res_body_is_json_schema"] = false
                item["res_body"] = parseByJson5(response.body, request.resourceMethod()?.let {
                    findReturnOfMethod(it)
                })
            } else {
                item["res_body"] = parseBySchema(response.body, request.resourceMethod()?.let {
                    findReturnOfMethod(it)
                })
            }
        } else {
            item["res_body_type"] = "json"

            item["res_body"] = ""
        }

        return item
    }

    fun item2Request(item: HashMap<String, Any?>): Request {

        val request = Request()

        request.name = item.getAs("title")
        request.desc = item.getAs("markdown")
        request.path = URL.of(item.getAs<String>("path"))
        request.method = item.getAs("method")

        val headers: MutableList<HashMap<String, Any?>>? = item.getAs("req_headers")
        if (headers.notNullOrEmpty()) {
            val requestHeaders = arrayListOf<Header>()
            request.headers = requestHeaders
            headers!!.forEach {
                val header = Header()
                header.name = it.getAs("name")
                header.value = it.getAs("value")
                header.desc = it.getAs("desc")
                header.required = it.getAs<Int>("required") == 1
                requestHeaders.add(header)
            }
        }

        val queryList: List<Map<String, Any?>>? = item.getAs("req_query")
        if (queryList.notNullOrEmpty()) {
            val params = arrayListOf<Param>()
            queryList!!.forEach {
                val param = Param()
                param.name = it.getAs("name")
                param.value = it.getAs("value")
                param.desc = it.getAs("desc")
                param.required = it.getAs<Int>("required") == 1
                params.add(param)
            }
            request.querys = params
        }

        val formParams: List<Map<String, Any?>>? = item.getAs("req_body_form")
        if (formParams != null) {
            val requestFormParams = arrayListOf<FormParam>()
            formParams.forEach {
                val formParam = FormParam()
                formParam.name = it.getAs("name")
                formParam.value = it.getAs("value")
                formParam.desc = it.getAs("desc")
                formParam.required = it.getAs<Int>("required") == 1
                requestFormParams.add(formParam)
            }
            request.formParams = requestFormParams
        }

        val pathParmas: List<Map<String, Any?>>? = item.getAs("req_params")
        if (pathParmas != null) {
            val requestPathParams = arrayListOf<PathParam>()
            pathParmas.forEach {
                val pathParam = PathParam()
                pathParam.name = it.getAs("name")
                pathParam.value = it.getAs("value")
                pathParam.desc = it.getAs("desc")
                requestPathParams.add(pathParam)
            }
            request.paths = requestPathParams
        }

        ActionContext.getContext()?.instance(TipsHelper::class)?.showTips(YAPI_BODY_PARSE_TIP)

        return request
    }

    /**
     * Make sure the path prefix with `/`
     * Path is only allowed to consist of alphanumeric or `-/_:.{}=`
     */
    private fun formatPath(path: String?): String {
        if (!autoFormatUrl()) {
            return path ?: ""
        }
        return when {
            path.isNullOrEmpty() -> "/"
            path.startsWith("/") -> path
            else -> "/$path"
        }.let {
            REGEX_URL_CONSIST.replace(it, "/")
        }.let {
            REGEX_URL_REDUNDANT_SLASH.replace(it, "/")
        }
    }

    protected fun findReturnOfMethod(method: PsiMethod): String? {
        return docHelper.findDocByTag(method, "return")
    }

    private fun jsonTypeOf(typedObject: Any?): String {
        when (typedObject) {
            null -> {
                return "null"
            }

            is String -> {
                return "string"
            }

            is Number -> {
                return if (typedObject is Int || typedObject is Long) {
                    "integer"
                } else {
                    "number"
                }
            }

            is Boolean -> {
                return "boolean"
            }

            is Array<*> -> {
                return "array"
            }

            is List<*> -> {
                return "array"
            }

            is Map<*, *> -> {
                return "object"
            }

            else -> return "object"
        }
    }

    //region parse-schema
    private fun parseBySchema(typedObject: Any?, rootDesc: String?): String? {
        if (typedObject == null) return null
        val result = parseObject("", typedObject)
        return toJsonWithSchema(result, rootDesc)
    }

    private fun toJsonWithSchema(result: HashMap<String, Any?>, rootDesc: String?): String {
        result["\$schema"] = "http://json-schema.org/draft-04/schema#"
        if (rootDesc != null) {
            result["description"] = rootDesc
        }
        return GsonUtils.toJson(result)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseObject(path: String?, typedObject: Any?): HashMap<String, Any?> {
        typedObject ?: return nullObject()
        val item: HashMap<String, Any?> = LinkedHashMap()
        item["type"] = jsonTypeOf(typedObject)
        if (typedObject is Array<*>) {
            if (typedObject.size > 0) {
                item["items"] = parseObject(contactPath(path, "[]"), typedObject[0])
            } else {
                item["items"] = unknownObject()
            }
        } else if (typedObject is Collection<*>) {
            if (typedObject.size > 0) {
                item["items"] = parseObject(contactPath(path, "[]"), typedObject.first())
            } else {
                item["items"] = unknownObject()
            }
        } else if (typedObject is Map<*, *>) {
            val properties: HashMap<String, Any?> = LinkedHashMap()
            val comment: Map<String, Any?>? = typedObject[Attrs.COMMENT_ATTR] as? Map<String, Any?>?
            val required: Map<String, Any?>? = typedObject[Attrs.REQUIRED_ATTR] as? Map<String, Any?>?
            val default: Map<String, Any?>? = typedObject[Attrs.DEFAULT_VALUE_ATTR] as? Map<String, Any?>?

            var requireds: LinkedList<String>? = null
            if (!required.isNullOrEmpty()) {
                requireds = LinkedList()
            }
            val mocks: Map<String, Any?>? = typedObject[Attrs.MOCK_ATTR] as? Map<String, Any?>?
            val advancedInfo: Map<String, Any?>? = typedObject[Attrs.ADVANCED_ATTR] as? Map<String, Any?>?
            typedObject.forEachValid { k, v ->
                try {
                    val key = k.toString()
                    val propertyInfo = parseObject(contactPath(path, key), v)

                    if (comment != null) {
                        val desc = comment[k]
                        if (desc != null) {
                            propertyInfo["description"] = desc.toString()
                        }
                        val options = comment["$k@options"]
                        if (options != null) {
                            var mockPropertyInfo = propertyInfo
                            try {
                                while (mockPropertyInfo["type"] == "array") {
                                    mockPropertyInfo = mockPropertyInfo["items"] as HashMap<String, Any?>
                                }
                            } catch (ignore: Exception) {
                            }

                            val optionList = options as List<Map<String, Any?>>

                            val optionVals = optionList.map { it["value"] }

                            val optionDesc = KVUtils.getOptionDesc(optionList)
                            mockPropertyInfo["enum"] = optionVals
                            if (optionDesc != null) {
                                mockPropertyInfo["enumDesc"] = optionDesc
                            }

                            addMock(mockPropertyInfo, "@pick(${GsonUtils.toJson(optionVals)})", true)
                        }
                    }
                    if (required?.get(k) == true) {
                        requireds?.add(key)
                    }
                    mocks?.get(key)?.let { addMock(propertyInfo, it) }
                    advancedInfo?.get(key)?.let { addAdvanced(propertyInfo, it) }
                    default?.get(k)?.takeIf { !it.anyIsNullOrBlank() }
                        ?.let { propertyInfo["default"] = it }

                    properties[key] = propertyInfo
                } catch (e: Exception) {
                    logger.traceWarn("failed to mock for $path.$k", e)
                }
            }
            item["properties"] = properties
            if (requireds.notNullOrEmpty()) {
                item["required"] = requireds!!.toTypedArray()
            }
        }

        //try read mock rules
        if (mockRules.isNotEmpty()) {
            for (mockRule in mockRules) {
                if (mockRule.pathPredict(path) &&
                    mockRule.typePredict(item["type"] as String?)
                ) {
                    addMock(item, mockRule.mockStr)
                    break
                }
            }
        }

        return item
    }

    //endregion

    //region parse-json5
    private fun parseByJson5(typedObject: Any?, rootDesc: String?): String {
        addMockAsProperty("", typedObject)
        val json5Formatter = json5Formatter
        return json5Formatter.format(typedObject, rootDesc)
    }

    private fun addMockAsProperty(path: String, typedObject: Any?): Any? {
        var ret = typedObject
        addMockAsProperty(path, typedObject) {
            ret = it
        }
        return ret
    }

    @Suppress("UNCHECKED_CAST")
    private fun addMockAsProperty(path: String, typedObject: Any?, retSet: (Any?) -> Unit) {
        when (typedObject) {
            null -> {
                return
            }

            is MutableMap<*, *> -> {
                typedObject as MutableMap<Any?, Any?>
                val mocks: Map<String, Any?>? = typedObject[Attrs.MOCK_ATTR] as? Map<String, Any?>?
                val comment: Map<String, Any?>? = typedObject[Attrs.COMMENT_ATTR] as? Map<String, Any?>?
                val keys = ArrayList(typedObject.keys)
                for (key in keys) {
                    if (key is String && key.startsWith("@")) {
                        continue
                    }
                    val subPath = contactPath(path, key.toString())

                    val mockStr = mocks?.get(key)
                    if (mockStr != null) {
                        setMock(typedObject, key, mockStr.asMockString())
                        continue
                    }
                    val options = comment?.get("$key@options")
                    if (options != null) {
                        val optionList = options as List<Map<String, Any?>>
                        val optionVals = optionList.map { it["value"] }
                        setMock(typedObject, key, "@pick(${GsonUtils.toJson(optionVals).asMockString()})")
                        continue
                    }
                    val value = typedObject[key]
                    addMockAsProperty(subPath, value) {
                        typedObject[key] = it
                    }
                }
            }

            is List<*> -> {
                for ((index, item) in typedObject.withIndex()) {
                    addMockAsProperty(contactPath(path, "[]"), item) {
                        (typedObject as MutableList<Any?>)[index] = it
                    }
                }
            }

            is Collection<*> -> {
                val ret = typedObject::class.newInstance() as? MutableCollection<Any?> ?: return
                typedObject.forEach {
                    ret.add(addMockAsProperty(contactPath(path, "[]"), it))
                }
                retSet(ret)
            }

            is Array<*> -> {
                for ((index, item) in typedObject.withIndex()) {
                    addMockAsProperty(contactPath(path, "[]"), item) {
                        (typedObject as Array<Any?>)[index] = it
                    }
                }
            }

            else -> {
                val jsonType = jsonTypeOf(typedObject)
                if (jsonType != "object" && jsonType != "array") {
                    for (mockRule in mockRules) {
                        if (mockRule.pathPredict(path) &&
                            mockRule.typePredict(jsonType)
                        ) {
                            retSet(mockRule.mockStr.asMockString())
                            break
                        }
                    }
                }
            }
        }
    }

    private fun Any?.asMockString(): Any? {
        return (this as? String)?.replace("\"", "\\\"") ?: this
    }

    private fun setMock(typedObject: MutableMap<Any?, Any?>, key: Any?, mockStr: Any?) {
        when (val value = typedObject[key]) {
            is Map<*, *> -> {
                logger.warn("should not set mock to map!!")
                return
            }

            is Array<*> -> {
                setMock(value, mockStr) {
                    typedObject[key] = it
                }
            }

            is Collection<*> -> {
                setMock(value, mockStr) {
                    typedObject[key] = it
                }
            }

            else -> {
                typedObject[key] = mockStr
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun setMock(typedObject: Any?, mockStr: Any?, retSet: (Any?) -> Unit) {
        when (typedObject) {
            is Array<*> -> {
                if (typedObject.size == 0) {
                    retSet(arrayOf(mockStr))
                    return
                }
                (typedObject as? Array<Any?>)?.set(0, mockStr)
            }

            is Collection<*> -> {
                if (typedObject.size == 0) {
                    retSet(arrayListOf(mockStr))
                    return
                }
                if (typedObject is MutableList<*>) {
                    (typedObject as? MutableList<Any?>)?.set(0, mockStr)
                } else {
                    retSet(arrayListOf(mockStr))
                }
            }

            else -> {
                retSet(mockStr)
            }
        }
    }

    private fun addAdvanced(propertyInfo: HashMap<String, Any?>, it: Any?) {
        when (it) {
            null -> {
                return
            }

            is Array<*> -> {
                it.forEach { advanced -> addAdvanced(propertyInfo, advanced) }
            }

            is Collection<*> -> {
                it.forEach { advanced -> addAdvanced(propertyInfo, advanced) }
            }

            is String -> {
                val advancedMap = try {
                    GsonUtils.fromJson(it, Map::class)
                } catch (e: Exception) {
                    logger.warn("failed process advanced info: $it")
                    return
                }
                advancedMap.forEach { (key, value) -> propertyInfo.putIfAbsent(key.toString(), value) }
            }

            is Map<*, *> -> {
                it.forEach { (key, value) -> propertyInfo.putIfAbsent(key.toString(), value) }
            }
        }
    }

    //endregion

    private fun contactPath(path: String?, subPath: String): String {
        return when {
            path.isNullOrBlank() -> subPath
            subPath == "[]" -> "$path$subPath"
            else -> "$path.$subPath"
        }
    }

    private fun nullObject(): HashMap<String, Any?> {
        val item: HashMap<String, Any?> = HashMap()
        item["type"] = "null"
//        item["properties"] = HashMap<String, Any?>()
        return item
    }

    private fun addMock(item: HashMap<String, Any?>, mockStr: Any, force: Boolean = false) {
        if (!force && item.containsKey("mock")) {
            return
        }
        val mock: HashMap<String, Any?> = HashMap()
        mock["mock"] = mockStr
        item["mock"] = mock
    }

    private fun unknownObject(): HashMap<String, Any?> {
        val item: HashMap<String, Any?> = HashMap()
        item["type"] = "object"
        item["properties"] = HashMap<String, Any?>()
        return item
    }

    private fun addTimeAttr(item: HashMap<String, Any?>) {
        item["add_time"] = systemProvider.currentTimeMillis() / 1000
        item["up_time"] = systemProvider.currentTimeMillis() / 1000
    }

    private fun appendDescToApiItem(item: HashMap<String, Any?>, desc: String?) {
        if (desc == null) return
        val existedDesc = item["markdown"]
        if (existedDesc == null) {
            item["markdown"] = desc
            item["desc"] = markdownRender.render(desc) ?: "<p>$desc</p>"
        } else {
            item["markdown"] = "$existedDesc\n$desc"
            item["desc"] = markdownRender.render(desc) ?: "<p>$existedDesc\n$desc</p>"
        }
    }

    //region mock rules---------------------------------------------------------
    private val mockRules: List<MockRule> by lazy {
        readMockRules()
    }

    private fun readMockRules(): List<MockRule> {
        val mockRules = arrayListOf<MockRule>()

        configReader.foreach({ key ->
            key.startsWith("mock.")
        }, { key, value ->
            try {
                mockRules.add(parseMockRule(key.removePrefix("mock."), value))
            } catch (e: Exception) {
                logger.error("error to parse mock rule:$key=$value")
            }
        })

        return mockRules
    }

    private fun parseMockRule(key: String, value: String): MockRule {

        val tinyKey = key
            .removePrefix("[")
            .removeSuffix("]")
        val pathStr = tinyKey.substringBefore("|")
        val typeStr = tinyKey.substringAfter("|", "*")
        return MockRule(
            parseRegexOrConstant(pathStr),
            parseRegexOrConstant(typeStr), value
        )

    }

    private val regexParseCache: HashMap<String, (String?) -> Boolean> = HashMap()

    private fun parseRegexOrConstant(str: String): (String?) -> Boolean {
        return regexParseCache.safeComputeIfAbsent(str) {
            if (str.isBlank()) {
                return@safeComputeIfAbsent { true }
            }
            val tinyStr = str.trim()
            if (tinyStr == "*") {
                return@safeComputeIfAbsent { true }
            }

            if (tinyStr.contains("*")) {
                val pattern = Pattern.compile(
                    "^${
                        tinyStr.replace("*.", SimpleRuleParser.STAR_DOT)
                            .replace("*", SimpleRuleParser.STAR)
                            .replace(SimpleRuleParser.STAR_DOT, ".*?(?<=^|\\.)")
                            .replace(SimpleRuleParser.STAR, ".*?")
                            .replace("[", "\\[")
                            .replace("]", "\\]")

                    }$"
                )

                return@safeComputeIfAbsent {
                    pattern.matcher(it ?: "").matches()
                }
            }

            return@safeComputeIfAbsent {
                str == it
            }
        }!!
    }

    class MockRule(
        val pathPredict: (String?) -> Boolean,
        val typePredict: (String?) -> Boolean,
        val mockStr: String,
    )

    //endregion mock rules---------------------------------------------------------

    protected fun autoFormatUrl(): Boolean {
        return configReader.first("auto.format.url")?.toBool() ?: true
    }

    companion object {
        val EMPTY_ARR: List<String> = emptyList()
        val EMPTY_PARAMS: List<String> = emptyList()

        val REGEX_URL_CONSIST = Regex("[^a-zA-Z0-9-/_:.{}?=!]")
        val REGEX_URL_REDUNDANT_SLASH = Regex("//+")

        // TODO: support parse yapi body
        private val YAPI_BODY_PARSE_TIP = OnlyOnceInContextTip(
            "Yapi body not be parsed. It will be supported in the future"
        )
    }
}