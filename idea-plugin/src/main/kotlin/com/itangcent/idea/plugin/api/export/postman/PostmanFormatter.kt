package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.itangcent.common.model.Request
import com.itangcent.common.model.getContentType
import com.itangcent.common.utils.DateUtils
import com.itangcent.idea.plugin.api.ResourceHelper
import com.itangcent.idea.plugin.api.export.DefaultDocParseHelper
import com.itangcent.idea.plugin.api.export.RequestHelper
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.idea.utils.RequestUtils
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.util.ActionUtils
import com.itangcent.intellij.util.KV
import org.apache.commons.lang3.RandomUtils
import java.util.Date
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.set

@Singleton
class PostmanFormatter {

    @Inject
    private val moduleHelper: ModuleHelper? = null

    @Inject
    private val resourceHelper: ResourceHelper? = null

    @Inject
    private val actionContext: ActionContext? = null

    @Inject
    private val docParseHelper: DefaultDocParseHelper? = null

    @Inject
    private val requestHelper: RequestHelper? = null

    fun request2Item(request: Request): HashMap<String, Any?> {

        val module = request.resource?.let { resource ->
            actionContext!!.callInReadUI { moduleHelper!!.findModule(resource) }
        }
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
                    .set("type", "text")
                    .set("description", it.desc ?: "")
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
            val contentType = request.getContentType()
            if (contentType?.contains("form-data") == true) {
                body["mode"] = "formdata"
                val formdatas: ArrayList<HashMap<String, Any?>> = ArrayList()
                request.formParams!!.forEach {
                    formdatas.add(KV.create<String, Any?>()
                            .set("key", it.name)
                            .set("value", it.value)
                            .set("type", it.type)
                            .set("description", it.desc)
                    )
                }
                body["formdata"] = formdatas

            } else {
                body["mode"] = "urlencoded"
                val urlEncodeds: ArrayList<HashMap<String, Any?>> = ArrayList()
                request.formParams!!.forEach {
                    urlEncodeds.add(KV.create<String, Any?>()
                            .set("key", it.name)
                            .set("value", it.value)
                            .set("type", it.type)
                            .set("description", it.desc)
                    )
                }
                body["urlencoded"] = urlEncodeds
            }
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

                if (response.headers?.any { it.name.equals("content-type", true) } == false) {
                    responseHeader.add(KV.create<String, Any?>()
                            .set("name", "content-type")
                            .set("key", "content-type")
                            .set("value", "application/json;charset=UTF-8")
                            .set("description", "The mime type of this content")
                    )
                }

                if (response.headers?.any { it.name.equals("date", true) } == false) {

                    responseHeader.add(KV.create<String, Any?>()
                            .set("name", "date")
                            .set("key", "date")
                            .set("value", DateUtils.format(Date(), "EEE, dd MMM yyyyHH:mm:ss 'GMT'"))
                            .set("description", "The date and time that the message was sent")
                    )
                }

                if (response.headers?.any { it.name.equals("server", true) } == false) {
                    responseHeader.add(KV.create<String, Any?>()
                            .set("name", "server")
                            .set("key", "server")
                            .set("value", "Apache-Coyote/1.1")
                            .set("description", "A name for the server")
                    )
                }

                if (response.headers?.any { it.name.equals("transfer-encoding", true) } == false) {

                    responseHeader.add(KV.create<String, Any?>()
                            .set("name", "transfer-encoding")
                            .set("key", "transfer-encoding")
                            .set("value", "chunked")
                            .set("description", "The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.")
                    )
                }

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

    fun wrapRootInfo(resource: Any, items: ArrayList<HashMap<String, Any?>>): HashMap<String, Any?> {

        val postman: HashMap<String, Any?> = HashMap()
        val info: HashMap<String, Any?> = HashMap()
        postman["info"] = info
        parseNameAndDesc(resource, info)
        info["schema"] = POSTMAN_SCHEMA_V2_1_0
        postman["item"] = items
        return postman
    }

    fun wrapInfo(resource: Any, items: ArrayList<HashMap<String, Any?>>): HashMap<String, Any?> {
        val postman: HashMap<String, Any?> = HashMap()
        parseNameAndDesc(resource, postman)
        postman["item"] = items
        return postman
    }

    fun parseNameAndDesc(resource: Any, info: HashMap<String, Any?>) {
        if (resource is PsiClass) {
            val attr = findAttrOfClass(resource)
            if (attr.isNullOrBlank()) {
                info["name"] = resource.name!!
                info["description"] = "exported from:${resource.qualifiedName}"
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
        } else {
            info["name"] = "$resource-${DateUtils.format(DateUtils.now(), "yyyyMMddHHmmss")}"
            info["description"] = "exported at ${DateUtils.formatYMD_HMS(DateUtils.now())}"
        }
    }

    private fun findAttrOfClass(cls: PsiClass): String? {
        val docText = resourceHelper!!.findAttrOfClass(cls)
        return when {
            docText.isNullOrBlank() -> cls.name
            else -> docParseHelper!!.resolveLinkInAttr(docText, cls)
        }
    }

    fun parseRequests(requests: MutableList<Request>): HashMap<String, Any?> {

        //group by class into: {class:requests}
        val clsGroupedMap: HashMap<Any, ArrayList<HashMap<String, Any?>>> = HashMap()
        requests.forEach { request ->
            val resource = request.resource?.let { resourceHelper!!.findResourceClass(it) } ?: NULL_RESOURCE
            clsGroupedMap.computeIfAbsent(resource) { ArrayList() }
                    .add(request2Item(request))
        }

        //only one class
        if (clsGroupedMap.size == 1) {
            clsGroupedMap.entries.first()
                    .let {
                        val module = moduleHelper!!.findModule(it.key) ?: "easy-api"
                        return wrapRootInfo(module, arrayListOf(wrapInfo(it.key, it.value)))
                    }
        }

        //group by module
        val moduleGroupedMap: HashMap<Any, ArrayList<HashMap<String, Any?>>> = HashMap()
        clsGroupedMap.forEach { cls, items ->
            val module = moduleHelper!!.findModule(cls) ?: "easy-api"
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
                .map { wrapInfo(it.key, it.value) }
                .forEach { modules.add(it) }

        val rootModule = moduleHelper!!.findModuleByPath(ActionUtils.findCurrentPath()) ?: "easy-api"
        return wrapRootInfo("$rootModule-${DateUtils.format(DateUtils.now(), "yyyyMMddHHmmss")}", modules)
    }

    companion object {
        val NULL_RESOURCE = Any()

        const val POSTMAN_SCHEMA_V2_1_0 = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
    }
}