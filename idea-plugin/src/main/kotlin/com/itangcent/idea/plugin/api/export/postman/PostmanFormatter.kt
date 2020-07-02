package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.itangcent.common.kit.getAs
import com.itangcent.common.model.Request
import com.itangcent.common.model.URL
import com.itangcent.common.model.getContentType
import com.itangcent.common.utils.*
import com.itangcent.http.RequestUtils
import com.itangcent.idea.plugin.api.export.ClassExportRuleKeys
import com.itangcent.idea.plugin.api.export.Folder
import com.itangcent.idea.plugin.api.export.FormatFolderHelper
import com.itangcent.idea.plugin.api.export.ResolveMultiPath
import com.itangcent.idea.psi.ResourceHelper
import com.itangcent.idea.psi.resource
import com.itangcent.idea.psi.resourceClass
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.util.ActionUtils
import org.apache.commons.lang3.RandomUtils
import java.util.Date
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.set

@Singleton
open class PostmanFormatter {

    @Inject
    private val moduleHelper: ModuleHelper? = null

    @Inject
    private val resourceHelper: ResourceHelper? = null

    @Inject
    private val actionContext: ActionContext? = null

    @Inject
    private val formatFolderHelper: FormatFolderHelper? = null

    @Inject
    private val ruleComputer: RuleComputer? = null

    fun request2Items(request: Request): List<HashMap<String, Any?>> {

        val item = formatRequest2Item(request)

        val url: HashMap<String, Any?> = item.getAs("request", "url")!!

        val pathInRequest = request.path ?: URL.nil()
        if (pathInRequest.single()) {
            val path = pathInRequest.url() ?: ""
            url["path"] = path.trim().trim('/').split("/")
            url["raw"] = RequestUtils.concatPath(url.getAs("host"), path)
            return listOf(item)
        }

        val pathMultiResolve = ruleComputer!!.computer(ClassExportRuleKeys.PATH_MULTI, request.resource()!!)?.let {
            ResolveMultiPath.valueOf(it.toUpperCase())
        } ?: ResolveMultiPath.FIRST

        if (pathMultiResolve == ResolveMultiPath.ALL) {
            val host = item.getAs<String>("request", "url", "host") ?: ""
            return pathInRequest.urls().map {
                val copyItem = copyItem(item)
                val copyUrl: HashMap<String, Any?> = copyItem.getAs("request", "url")!!
                copyUrl["path"] = it.trim().trim('/').split("/")
                copyUrl["raw"] = RequestUtils.concatPath(host, it)
                return@map copyItem
            }
        } else {
            val path: String? = when (pathMultiResolve) {
                ResolveMultiPath.FIRST -> {
                    pathInRequest.urls().firstOrNull()
                }
                ResolveMultiPath.LAST -> {
                    pathInRequest.urls().lastOrNull()
                }
                ResolveMultiPath.LONGEST -> {
                    pathInRequest.urls().longest()
                }
                ResolveMultiPath.SHORTEST -> {
                    pathInRequest.urls().shortest()
                }
                else -> ""
            }

            url["path"] = (path ?: "").trim().trim('/').split("/")
            url["raw"] = RequestUtils.concatPath(url.getAs("host"), path)
            return listOf(item)
        }
    }

    protected open fun copyItem(item: HashMap<String, Any?>): HashMap<String, Any?> {
        val copyItem = KV.create<String, Any?>()
        copyItem.putAll(item)

        val request = HashMap(item.getAs<HashMap<String, Any?>>("request"))
        copyItem["request"] = request

        val url = HashMap(request.getAs<HashMap<String, Any?>>("url"))
        request["url"] = url

        return copyItem
    }

    fun request2Item(request: Request): HashMap<String, Any?> {

        val item = formatRequest2Item(request)

        val url: HashMap<String, Any?> = item.getAs("request", "url")!!

        val pathInRequest = request.path ?: URL.nil()

        val path = pathInRequest.url() ?: ""
        url["path"] = path.trim().trim('/').split("/")
        url["raw"] = RequestUtils.concatPath(url.getAs("host"), path)
        return item
    }

    protected open fun formatRequest2Item(request: Request): HashMap<String, Any?> {

        var host = "{{host}}"

        val hostByRule = request.resourceClass()
                ?.let { ruleComputer!!.computer(ClassExportRuleKeys.POST_MAN_HOST, it) }

        if (hostByRule == null) {
            val module = request.resource?.let { resource ->
                actionContext!!.callInReadUI { moduleHelper!!.findModule(resource) }
            }
            if (module != null) {
                host = "{{$module}}"
            }
        } else {
            host = hostByRule
        }

        val item: HashMap<String, Any?> = HashMap()

        item["name"] = request.name

        if (request.hasAnyExt(ClassExportRuleKeys.POST_PREREQUEST.name(), ClassExportRuleKeys.POST_TEST.name())) {
            val events = ArrayList<Any>()
            val preRequest = request.getExt<String>(ClassExportRuleKeys.POST_PREREQUEST.name())
            if (preRequest.notNullOrBlank()) {
                events.add(KV.any().set("listen", "prerequest")
                        .set("script", KV.any()
                                .set("exec", preRequest!!.lines())
                                .set("type", "text/javascript")
                        ))
            }
            val test = request.getExt<String>(ClassExportRuleKeys.POST_TEST.name())
            if (test.notNullOrBlank()) {
                events.add(KV.any().set("listen", "test")
                        .set("script", KV.any()
                                .set("exec", test!!.lines())
                                .set("type", "text/javascript")
                        ))
            }
            item["event"] = events
        }

        val requestInfo: HashMap<String, Any?> = HashMap()
        item["request"] = requestInfo

        requestInfo["method"] = request.method
        requestInfo["description"] = request.desc

        val url: HashMap<String, Any?> = HashMap()
        requestInfo["url"] = url

        url["host"] = host

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
            body["options"] = KV.by("raw", KV.by("language", "json"))
        }

        if (body.isNotEmpty()) {
            requestInfo["body"] = body
        }


        if (request.response.notNullOrEmpty()) {

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
                            .set("value", Date().formatDate("EEE, dd MMM yyyyHH:mm:ss 'GMT'"))
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
        info["name"] = "${info["name"]}-${Date().formatDate("yyyyMMddHHmmss")}"
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

    fun parseRequests(requests: MutableList<Request>): HashMap<String, Any?> {


        //parse [request...] ->
        //                      {
        //                          "module":{
        //                              "folder":[request...]
        //                          }
        //                      }

        val moduleFolderApiMap: HashMap<String, HashMap<Folder, ArrayList<HashMap<String, Any?>>>> = HashMap()

        //group by module
        val moduleGroupedMap: HashMap<String, MutableList<Request>> = HashMap()
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
                    return wrapRootInfo(it.key, it.value)
                }
            } else {
                moduleFolderApiMap.entries.first().let { moduleAndFolders ->
                    val items: ArrayList<HashMap<String, Any?>> = ArrayList()
                    moduleAndFolders.value.forEach { items.add(wrapInfo(it.key, it.value)) }
                    return wrapRootInfo(moduleAndFolders.key, items)
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
        return wrapRootInfo(rootModule, modules)
    }

    private fun parseRequestsToFolder(requests: MutableList<Request>): HashMap<Folder, ArrayList<HashMap<String, Any?>>> {
        //parse [request...] ->
        //                      {
        //                          "folder":[request...]
        //                      }

        //group by folder into: {folder:requests}
        val folderGroupedMap: HashMap<Folder, ArrayList<HashMap<String, Any?>>> = HashMap()
        requests.forEach { request ->
            val folder = formatFolderHelper!!.resolveFolder(request.resource ?: NULL_RESOURCE)
            folderGroupedMap.computeIfAbsent(folder) { ArrayList() }
                    .addAll(request2Items(request))
        }

        return folderGroupedMap
    }

    companion object {
        const val NULL_RESOURCE = "unknown"

        const val POSTMAN_SCHEMA_V2_1_0 = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
    }
}
