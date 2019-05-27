package com.itangcent.idea.plugin.api.export.yapi

import com.google.inject.Inject
import com.intellij.psi.PsiMethod
import com.itangcent.common.model.Request
import com.itangcent.common.utils.GsonUtils
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.SimpleRuleParse
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.util.DocCommentUtils
import com.itangcent.intellij.util.KV
import java.util.*
import java.util.stream.Collectors

class YapiFormatter {
    @Inject
    private val logger: Logger? = null

    @Inject
    private val simpleRuleParse: SimpleRuleParse? = null

    @Inject
    private val configReader: ConfigReader? = null

    fun request2Item(request: Request): HashMap<String, Any?> {

        val item: HashMap<String, Any?> = HashMap()

        item["edit_uid"] = 0
        item["status"] = "done"
        item["type"] = "static"
        item["req_body_is_json_schema"] = false
        item["res_body_is_json_schema"] = true
        item["api_opened"] = false
        item["index"] = 0
        item["tag"] = EMPTY_TAGS

        item["title"] = request.name

        appendDescToApiItem(item, request.desc)

        val queryPath: HashMap<String, Any?> = HashMap()
        item["query_path"] = queryPath
        queryPath["params"] = EMPTY_PARAMS

        queryPath["path"] = formatPath(request.path)
        item["path"] = formatPath(request.path)

        addTimeAttr(item)
        item["__v"] = 0

        item["method"] = request.method

        val headers: ArrayList<HashMap<String, Any?>> = ArrayList()
        item["req_headers"] = headers
        request.headers?.forEach {
            headers.add(KV.create<String, Any?>()
                    .set("name", it.name)
                    .set("value", it.value)
                    .set("desc", it.desc)
                    .set("example", it.example)
                    .set("required", 1)
            )
        }

        val queryList: ArrayList<HashMap<String, Any?>> = ArrayList()
        item["req_query"] = queryList
        request.querys?.forEach {
            queryList.add(KV.create<String, Any?>()
                    .set("name", it.name)
                    .set("value", it.value)
                    .set("desc", it.desc)
                    .set("required", it.required.toInt())
            )
        }

        if (request.formParams != null) {
            item["req_body_type"] = "form"
            val urlencodeds: ArrayList<HashMap<String, Any?>> = ArrayList()
            item["req_body_form"] = urlencodeds
            request.formParams!!.forEach {
                urlencodeds.add(KV.create<String, Any?>()
                        .set("name", it.name)
                        .set("value", it.value)
                        .set("type", it.type)
                        .set("required", it.required.toInt())
                        .set("desc", it.desc)
                )
            }
        }

        if (request.paths != null) {
            val pathParmas: ArrayList<HashMap<String, Any?>> = ArrayList()
            item["req_params"] = pathParmas
            request.paths!!.forEach {
                pathParmas.add(KV.create<String, Any?>()
                        .set("name", it.name)
                        .set("desc", it.desc)
                )
            }
        }

        if (request.body != null) {
            item["req_body_is_json_schema"] = true
            item["req_body_type"] = "json"
            item["req_body_form"] = EMPTY_ARR

            //todo:need desc of body
            item["req_body_other"] = parseBySchema(request.body, "")
        }

        if (!request.response.isNullOrEmpty()) {

            val response = request.response!![0]
            item["res_body_type"] = "json"

            if (request.resource is PsiMethod) {
                item["res_body"] = parseBySchema(response.body, findReturnOfMethod(request.resource as PsiMethod))
            } else {
                item["res_body"] = parseBySchema(response.body, null)
            }
        } else {
            item["res_body_type"] = "json"

            item["res_body"] = ""
        }

        return item
    }

    /**
     * make sure the path prefix with "/"
     */
    private fun formatPath(path: String?): String {
        return when {
            path.isNullOrEmpty() -> "/"
            path.startsWith("/") -> path
            else -> "/$path"
        }
    }

    protected fun findReturnOfMethod(method: PsiMethod): String? {
        return DocCommentUtils.findDocsByTag(method.docComment, "return")
    }

    private fun parseBySchema(typedObject: Any?, rootDesc: String?): String? {
        if (typedObject == null) return null
        val result = parseObject("", typedObject)
        result["\$schema"] = "http://json-schema.org/draft-04/schema#"
        if (rootDesc != null) {
            result["description"] = rootDesc
        }
        return GsonUtils.toJson(result)
    }

    @Suppress("UNCHECKED_CAST")
    private fun parseObject(path: String?, typedObject: Any?): HashMap<String, Any?> {
        if (typedObject == null) return nullObject()
        val item: HashMap<String, Any?> = HashMap()
        if (typedObject is String) {
            item["type"] = "string"
        } else if (typedObject is Number) {
            if (typedObject is Int || typedObject is Long) {
                item["type"] = "integer"
            } else {
                item["type"] = "number"
            }
        } else if (typedObject is Boolean) {
            item["type"] = "boolean"
        } else if (typedObject is Array<*>) {
            item["type"] = "array"
            if (typedObject.size > 0) {
                item["items"] = parseObject(contactPath(path, "[]"), typedObject[0])
            } else {
                item["items"] = unknownObject()
            }
        } else if (typedObject is List<*>) {
            item["type"] = "array"
            if (typedObject.size > 0) {
                item["items"] = parseObject(contactPath(path, "[]"), typedObject[0])
            } else {
                item["items"] = unknownObject()
            }
        } else if (typedObject is Map<*, *>) {
            item["type"] = "object"
            val properties: HashMap<String, Any?> = HashMap()
            var comment: HashMap<String, Any?>? = null
            try {
                comment = typedObject[COMMENT_ATTR] as HashMap<String, Any?>?
            } catch (e: Throwable) {
            }
            typedObject.forEach { k, v ->
                if (k != COMMENT_ATTR) {
                    val propertyInfo = parseObject(contactPath(path, k.toString()), v)
                    if (comment != null) {
                        val desc = comment[k]
                        if (desc != null) {
                            propertyInfo["description"] = desc.toString()
                        }
                        val options = comment["$k@options"]
                        if (options != null) {
                            val optionList = options as List<Map<String, Any?>>

                            val optionVals = optionList.stream()
                                    .map { it["value"] }
                                    .collect(Collectors.toList())

                            val optionDesc = getOptionDesc(optionList)
                            propertyInfo["enum"] = optionVals
                            if (optionDesc != null) {
                                propertyInfo["enumDesc"] = optionDesc
                            }

                            addMock(propertyInfo, "@pick(${GsonUtils.toJson(optionVals)})")
                        }
                    }
                    properties[k.toString()] = propertyInfo
                }
            }
            item["properties"] = properties
        }

        //try read mock rules
        val mockRules = readMockRules()
        if (mockRules.isNotEmpty()) {
            for (mockRule in mockRules) {
                if (mockRule.pathPredict(path) &&
                        mockRule.typePredict(item["type"] as String?)) {
                    addMock(item, mockRule.mockStr)
                    break
                }
            }
        }

        return item
    }

    private fun contactPath(path: String?, subPath: String): String {
        return when {
            path.isNullOrBlank() -> subPath
            subPath == "[]" -> "$path$subPath"
            else -> "$path.$subPath"
        }
    }

    /**
     * get description of options
     */
    private fun getOptionDesc(options: List<Map<String, Any?>>): String? {
        return options.stream()
                .map { it["value"].toString() + " :" + it["desc"] }
                .filter { it != null }
                .reduce { s1, s2 -> s1 + "\n" + s2 }
                .orElse(null)
    }

    private fun nullObject(): HashMap<String, Any?> {
        val item: HashMap<String, Any?> = HashMap()
        item["type"] = "null"
//        item["properties"] = HashMap<String, Any?>()
        return item
    }

    private fun addMock(item: HashMap<String, Any?>, mockStr: Any) {
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
        item["add_time"] = System.currentTimeMillis() / 1000
        item["up_time"] = System.currentTimeMillis() / 1000
    }

    private fun appendDescToApiItem(item: HashMap<String, Any?>, desc: String?) {
        if (desc == null) return
        val existedDesc = item["markdown"]
        if (existedDesc == null) {
            item["markdown"] = desc
            item["desc"] = "<p>$desc</p>"
        } else {
            item["markdown"] = "$existedDesc\n$desc"
            item["desc"] = "<p>$existedDesc\n$desc</p>"
        }
    }

    //region mock rules---------------------------------------------------------
    private var mockRules: ArrayList<MockRule>? = null

    private fun readMockRules(): List<MockRule> {
        if (mockRules != null) return mockRules!!
        mockRules = ArrayList()

        configReader!!.foreach({ key ->
            key.startsWith("mock.")
        }, { key, value ->
            try {
                mockRules!!.add(parseMockRule(key.removePrefix("mock."), value))
            } catch (e: Exception) {
                logger!!.error("error to parse mock rule:$key=$value")
            }
        })

        return mockRules!!
    }

    private fun parseMockRule(key: String, value: String): MockRule {

        val tinyKey = key
                .removePrefix("[")
                .removeSuffix("]")
        val pathStr = tinyKey.substringBefore("|")
        val typeStr = tinyKey.substringAfter("|", "*")
        return MockRule(simpleRuleParse!!.parseRegexOrConstant(pathStr),
                simpleRuleParse.parseRegexOrConstant(typeStr), value)

    }

    class MockRule(val pathPredict: (String?) -> Boolean,
                   val typePredict: (String?) -> Boolean,
                   val mockStr: String)

    //endregion mock rules---------------------------------------------------------

    companion object {
        val NO_METHOD = "ALL"
        val COMMENT_ATTR = "@comment"
        val SPRING_REQUEST_RESPONSE: Array<String> = arrayOf("HttpServletRequest", "HttpServletResponse")

        val EMPTY_ARR: List<String> = Collections.emptyList<String>()!!
        val EMPTY_PARAMS: List<String> = Collections.emptyList<String>()
        val EMPTY_TAGS: List<String> = Collections.emptyList<String>()
    }

}

fun Boolean?.toInt(): Int {
    return when {
        this != null && this -> 1
        else -> 0
    }
}