package com.itangcent.idea.plugin.api.export.postman

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.itangcent.common.model.*
import com.itangcent.common.utils.*
import com.itangcent.http.RequestUtils
import com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys
import com.itangcent.idea.plugin.api.export.core.Folder
import com.itangcent.idea.plugin.api.export.core.FormatFolderHelper
import com.itangcent.idea.plugin.api.export.core.ResolveMultiPath
import com.itangcent.idea.plugin.format.Json5Formatter
import com.itangcent.idea.plugin.format.MessageFormatter
import com.itangcent.idea.plugin.format.SimpleJsonFormatter
import com.itangcent.idea.plugin.rule.SuvRuleContext
import com.itangcent.idea.plugin.settings.helper.PostmanSettingsHelper
import com.itangcent.idea.psi.UltimateDocHelper
import com.itangcent.idea.psi.resource
import com.itangcent.idea.psi.resourceClass
import com.itangcent.idea.utils.GsonExUtils
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.idea.utils.SystemProvider
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.takeIfNotOriginal
import com.itangcent.intellij.extend.takeIfSpecial
import com.itangcent.intellij.util.ActionUtils
import kotlin.collections.set

@Singleton
open class PostmanFormatter {

    @Inject
    private val actionContext: ActionContext? = null

    @Inject
    private val moduleHelper: ModuleHelper? = null

    @Inject
    private val ultimateDocHelper: UltimateDocHelper? = null

    @Inject
    private val formatFolderHelper: FormatFolderHelper? = null

    @Inject
    private val ruleComputer: RuleComputer? = null

    @Inject
    private lateinit var postmanSettingsHelper: PostmanSettingsHelper

    @Inject
    protected lateinit var systemProvider: SystemProvider

    fun request2Items(request: Request): List<HashMap<String, Any?>> {

        val item = formatRequest2Item(request)

        val url: HashMap<String, Any?> = item.getAs("request", "url")!!

        val pathInRequest = request.path ?: URL.nil()
        if (pathInRequest.single()) {
            val path = pathInRequest.url() ?: ""
            url["path"] = parsePath(path)
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
                copyUrl["path"] = parsePath(it)
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

            url["path"] = parsePath(path ?: "")
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
        url["path"] = parsePath(path)
        url["raw"] = RequestUtils.concatPath(url.getAs("host"), path)
        return item
    }

    @Suppress("UNCHECKED_CAST")
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

        item[NAME] = request.name

        parseScripts(request, item)

        val requestInfo: HashMap<String, Any?> = HashMap()
        item["request"] = requestInfo

        requestInfo["method"] = request.method
        requestInfo[DESCRIPTION] = request.desc

        val url: HashMap<String, Any?> = HashMap()
        requestInfo["url"] = url

        url["host"] = host

        val headers: ArrayList<HashMap<String, Any?>> = ArrayList()
        requestInfo["header"] = headers
        request.headers?.forEach {
            headers.add(
                KV.create<String, Any?>()
                    .set(KEY, it.name)
                    .set(VALUE, it.value)
                    .set(TYPE, "text")
                    .set(DESCRIPTION, it.desc ?: "")
            )
        }

        val queryList: ArrayList<HashMap<String, Any?>> = ArrayList()
        url["query"] = queryList
        request.querys?.forEach {
            queryList.add(
                KV.create<String, Any?>()
                    .set(KEY, it.name)
                    .set(VALUE, it.value?.takeIfNotOriginal()?.toString() ?: "")
                    .set("equals", true)
                    .set(DESCRIPTION, it.desc)
            )
        }

        val body: HashMap<String, Any?> = HashMap()
        if (request.formParams != null) {
            val contentType = request.getContentType()
            if (contentType?.contains("form-data") == true) {
                body[MODE] = "formdata"
                val formdatas: ArrayList<HashMap<String, Any?>> = ArrayList()
                request.formParams!!.forEach {
                    formdatas.add(
                        KV.create<String, Any?>()
                            .set(KEY, it.name)
                            .set(VALUE, it.value.takeIfSpecial())
                            .set(TYPE, it.type)
                            .set(DESCRIPTION, it.desc)
                    )
                }
                body["formdata"] = formdatas

            } else {
                body[MODE] = "urlencoded"
                val urlEncodeds: ArrayList<HashMap<String, Any?>> = ArrayList()
                request.formParams!!.forEach {
                    urlEncodeds.add(
                        KV.create<String, Any?>()
                            .set(KEY, it.name)
                            .set(VALUE, it.value.takeIfSpecial())
                            .set(TYPE, it.type)
                            .set(DESCRIPTION, it.desc)
                    )
                }
                body["urlencoded"] = urlEncodeds
            }
        }

        if (request.body != null) {
            body[MODE] = "raw"
            body["raw"] = getBodyFormatter(1).format(request.body)
            body["options"] = KV.by("raw", KV.by("language", "json"))
        }

        if (body.isNotEmpty()) {
            requestInfo["body"] = body
        }

        if (postmanSettingsHelper.buildExample() && request.response.notNullOrEmpty()) {

            val responses: ArrayList<HashMap<String, Any?>> = ArrayList()
            val exampleName = request.name + "-Example"
            request.response!!.forEachIndexed { index, response ->
                val responseInfo: HashMap<String, Any?> = HashMap()
                if (index > 0) {
                    responseInfo[NAME] = exampleName + (index + 1)
                } else {
                    responseInfo[NAME] = exampleName
                }

                if (request.body != null) {
                    val copyRequestInfo = GsonUtils.copy(requestInfo) as HashMap<String, Any?>
                    val copyBody: HashMap<String, Any?> = copyRequestInfo.getAs("body")!!
                    copyBody["raw"] = getBodyFormatter(4).format(request.body)
                    responseInfo["originalRequest"] = copyRequestInfo
                } else {
                    responseInfo["originalRequest"] = requestInfo//need clone?request.clone()?
                }
                responseInfo["code"] = 200
                responseInfo["_postman_previewlanguage"] = "json"
                val responseHeader = ArrayList<Map<String, Any?>>()
                responseInfo["header"] = responseHeader

                if (response.headers?.any { it.name.equals("content-type", true) } == false) {
                    responseHeader.add(
                        KV.create<String, Any?>()
                            .set(NAME, "content-type")
                            .set(KEY, "content-type")
                            .set(VALUE, "application/json;charset=UTF-8")
                            .set(DESCRIPTION, "The mime type of this content")
                    )
                }

                if (response.headers?.any { it.name.equals("date", true) } == false) {

                    responseHeader.add(
                        KV.create<String, Any?>()
                            .set(NAME, "date")
                            .set(KEY, "date")
                            .set(
                                VALUE,
                                systemProvider.currentTimeMillis().asDate().formatDate("EEE, dd MMM yyyyHH:mm:ss 'GMT'")
                            )
                            .set(DESCRIPTION, "The date and time that the message was sent")
                    )
                }

                if (response.headers?.any { it.name.equals("server", true) } == false) {
                    responseHeader.add(
                        KV.create<String, Any?>()
                            .set(NAME, "server")
                            .set(KEY, "server")
                            .set(VALUE, "Apache-Coyote/1.1")
                            .set(DESCRIPTION, "A name for the server")
                    )
                }

                if (response.headers?.any { it.name.equals("transfer-encoding", true) } == false) {

                    responseHeader.add(
                        KV.create<String, Any?>()
                            .set(NAME, "transfer-encoding")
                            .set(KEY, "transfer-encoding")
                            .set(VALUE, "chunked")
                            .set(
                                DESCRIPTION,
                                "The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity."
                            )
                    )
                }

                response.headers?.forEach {
                    responseHeader.add(
                        KV.create<String, Any?>()
                            .set(NAME, it.name)
                            .set(KEY, it.name)
                            .set(VALUE, it.value.takeIfSpecial())
                            .set(DESCRIPTION, it.desc)
                    )
                }

                responseInfo["body"] = getBodyFormatter(8).format(response.body)

                responses.add(responseInfo)
            }
            item["response"] = responses
        }

        return item
    }

    fun item2Request(item: HashMap<String, Any?>): Request? {
        val request = Request()
        request.name = item.getAs("name")

        val requestInfo: HashMap<String, Any?> = item.getAs("request") ?: return null
        request.path = URL.of(requestInfo.getAs<List<String>>("url", "path")?.joinToString(separator = "/"))
        request.method = requestInfo.getAs("method")
        request.desc = requestInfo.getAs(DESCRIPTION)

        val headers: ArrayList<HashMap<String, Any?>>? = requestInfo.getAs("header")
        if (headers.notNullOrEmpty()) {
            val requestHeaders = arrayListOf<Header>()
            request.headers = requestHeaders
            headers!!.forEach {
                val header = Header()
                header.name = it.getAs(KEY)
                header.value = it.getAs(VALUE)
                header.desc = it.getAs(DESCRIPTION)
                requestHeaders.add(header)
            }
        }

        val queryList: ArrayList<HashMap<String, Any?>>? = requestInfo.getAs("url", "query")
        if (queryList.notNullOrEmpty()) {
            val params = arrayListOf<Param>()
            queryList!!.forEach {
                val param = Param()
                param.name = it.getAs(KEY)
                param.value = it.getAs(VALUE)
                param.desc = it.getAs(DESCRIPTION)
                params.add(param)
            }
            request.querys = params
        }

        val body: HashMap<String, Any?>? = requestInfo.getAs("body")
        if (body != null) {
            val mode = body.getAs<String>(MODE)
            when (mode) {
                "raw" -> {//json
                    val jsonBody = body.getAs<String>("raw")
                    if (jsonBody.notNullOrBlank()) {
                        request.body = GsonUtils.fromJson(jsonBody!!)
                        request.bodyType = "json"
                    }
                }
                "formdata" -> {
                    val formdatas: ArrayList<HashMap<String, Any?>>? = body.getAs("formdata")
                    if (formdatas != null) {
                        val formParams = arrayListOf<FormParam>()
                        formdatas.forEach {
                            val formParam = FormParam()
                            formParam.name = it.getAs(KEY)
                            formParam.value = it.getAs(VALUE)
                            formParam.desc = it.getAs(DESCRIPTION)
                            formParam.type = it.getAs(TYPE)
                            formParams.add(formParam)
                        }
                        request.formParams = formParams
                    }
                }
                "urlencoded" -> {
                    val urlEncodeds: ArrayList<HashMap<String, Any?>>? = body.getAs("urlencoded")
                    if (urlEncodeds != null) {
                        val formParams = arrayListOf<FormParam>()
                        urlEncodeds.forEach {
                            val formParam = FormParam()
                            formParam.name = it.getAs(KEY)
                            formParam.value = it.getAs(VALUE)
                            formParam.desc = it.getAs(DESCRIPTION)
                            formParam.type = it.getAs(TYPE)
                            formParams.add(formParam)
                        }
                        request.formParams = formParams
                    }
                }
            }
        }
        return request
    }

    private fun parseScripts(extensible: Extensible, item: HashMap<String, Any?>) {
        if (extensible.hasAnyExt(ClassExportRuleKeys.POST_PRE_REQUEST.name(), ClassExportRuleKeys.POST_TEST.name())) {
            addScriptsToItem(item,
                { extensible.getExt<String>(ClassExportRuleKeys.POST_PRE_REQUEST.name()) },
                { extensible.getExt<String>(ClassExportRuleKeys.POST_TEST.name()) }
            )
        }
    }

    private fun addScriptsToItem(item: HashMap<String, Any?>, preRequest: () -> String?, test: () -> String?) {
        var events: ArrayList<Any>? = null

        preRequest()?.takeIf { it.notNullOrBlank() }?.let {
            events = ArrayList()
            events!!.add(
                KV.any().set("listen", "prerequest")
                    .set(
                        "script", KV.any()
                            .set("exec", it.lines())
                            .set(TYPE, "text/javascript")
                    )
            )
        }

        test()?.takeIf { it.notNullOrBlank() }?.let {
            events = events ?: ArrayList()
            events!!.add(
                KV.any().set("listen", "test")
                    .set(
                        "script", KV.any()
                            .set("exec", it.lines())
                            .set(TYPE, "text/javascript")
                    )
            )
        }

        if (events.notNullOrEmpty()) {
            item["event"] = events
        }
    }

    fun wrapRootInfo(resource: Any, items: ArrayList<HashMap<String, Any?>>): HashMap<String, Any?> {

        val postman: HashMap<String, Any?> = HashMap()
        val info: HashMap<String, Any?> = HashMap()
        postman["info"] = info
        parseNameAndDesc(resource, info)
        val context = SuvRuleContext()
        context.setExt("postman", postman)
        if (resource is Extensible) {
            addScriptsToItem(postman,
                {
                    ruleComputer!!.computer(
                        ClassExportRuleKeys.COLLECTION_POST_PRE_REQUEST,
                        context, null
                    )
                        .append(resource.getExt<String>(ClassExportRuleKeys.POST_PRE_REQUEST.name()), "\n")
                },
                {
                    ruleComputer!!.computer(
                        ClassExportRuleKeys.COLLECTION_POST_TEST,
                        context, null
                    )
                        .append(resource.getExt<String>(ClassExportRuleKeys.POST_TEST.name()), "\n")
                }
            )
        } else {
            addScriptsToItem(postman,
                {
                    ruleComputer!!.computer(
                        ClassExportRuleKeys.COLLECTION_POST_PRE_REQUEST,
                        context, null
                    )
                },
                {
                    ruleComputer!!.computer(
                        ClassExportRuleKeys.COLLECTION_POST_TEST,
                        context, null
                    )
                }
            )
        }

        info[NAME] = "${info[NAME]}-${systemProvider.currentTimeMillis().asDate().formatDate("yyyyMMddHHmmss")}"
        info["schema"] = POSTMAN_SCHEMA_V2_1_0
        postman[ITEM] = items
        return postman
    }

    fun wrapInfo(resource: Any, items: ArrayList<HashMap<String, Any?>>): HashMap<String, Any?> {
        val postman: HashMap<String, Any?> = HashMap()
        parseNameAndDesc(resource, postman)
        (resource as? Extensible)?.let {
            parseScripts(it, postman)
        }
        postman[ITEM] = items
        return postman
    }

    fun parseNameAndDesc(resource: Any, info: HashMap<String, Any?>) {
        if (resource is PsiClass) {
            val attr = ultimateDocHelper!!.findUltimateDescOfClass(resource)
            if (attr.isNullOrBlank()) {
                info[NAME] = resource.name!!
                info[DESCRIPTION] = "exported from:${actionContext!!.callInReadUI { resource.qualifiedName }}"
            } else {
                val lines = attr.lines()
                if (lines.size == 1) {
                    info[NAME] = attr
                    info[DESCRIPTION] = "exported from:${actionContext!!.callInReadUI { resource.qualifiedName }}"
                } else {
                    info[NAME] = lines[0]
                    info[DESCRIPTION] = attr
                }
            }
        } else if (resource is Folder) {
            info[NAME] = resource.name
            info[DESCRIPTION] = resource.attr
        } else if (resource is Pair<*, *>) {
            info[NAME] = resource.first
            info[DESCRIPTION] = resource.second
        } else {
            info[NAME] = resource.toString()
            info[DESCRIPTION] = "exported at ${DateUtils.formatYMD_HMS(systemProvider.currentTimeMillis().asDate())}"
        }
    }

    fun parseRequests(requests: List<Request>): HashMap<String, Any?> {
        val postmanCollection = doParseRequests(requests)
        if (postmanSettingsHelper.autoMergeScript()) {
            autoMerge(postmanCollection)
        }
        return postmanCollection
    }

    @Suppress("UNCHECKED_CAST")
    private fun autoMerge(item: HashMap<String, Any?>) {
        val items = item[ITEM] as? List<*>
        if (items.isNullOrEmpty()) {
            return
        }

        items.forEach { autoMerge(it as HashMap<String, Any?>) }

        val events: List<*>? = findCommonEvents(items)
        if (events.isNullOrEmpty()) {
            return
        }

        items.forEach { (it as HashMap<String, Any?>).remove(EVENT) }

        mergeEvents(item, events)

        return
    }

    @Suppress("UNCHECKED_CAST")
    private fun mergeEvents(item: HashMap<String, Any?>, events: List<*>) {
        val existedEvents = item[EVENT] as? List<*>
        if (existedEvents == null) {
            item[EVENT] = events
            return
        }

        for (event in events) {
            val listenType = (event as HashMap<*, *>)["listen"] as String
            val existedEvent = existedEvents.firstOrNull { (it as HashMap<*, *>)["listen"] == listenType }
            if (existedEvent == null) {
                (existedEvents as MutableList<Any?>).add(event)
            } else {
                existedEvent as HashMap<*, *>
                val script = (existedEvent["script"] as HashMap<String, Any?>)
                val newLines = ArrayList(script["exec"] as List<*>)
                event.getAs<List<Any?>>("script", "exec")?.let { newLines.addAll(it) }
                script["exec"] = newLines
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun findCommonEvents(items: List<*>): List<*>? {
        if (items.isNullOrEmpty()) {
            return null
        }

        if (items.size == 1) {
            return (items[0] as HashMap<String, Any?>)[EVENT] as? List<*>
        }

        val events = (items[0] as HashMap<String, Any?>)[EVENT] as? List<*>
        var i = 1;
        while (i < items.size) {
            if ((items[i] as HashMap<String, Any?>)[EVENT] as? List<*> != events) {
                return null
            }
            ++i;
        }

        return events
    }

    private fun doParseRequests(requests: List<Request>): HashMap<String, Any?> {


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
            moduleGroupedMap.safeComputeIfAbsent(module) { ArrayList() }!!
                .add(request)
        }

        moduleGroupedMap.forEach { (module, requestsInModule) ->
            moduleFolderApiMap[module] = parseRequestsToFolder(requestsInModule)
        }

        if (moduleFolderApiMap.size == 1) {
            val wrapCollection = postmanSettingsHelper.wrapCollection()

            //single module
            val folderApiMap = moduleFolderApiMap.values.first()
            if (folderApiMap.size == 1) {
                //single folder
                folderApiMap.entries.first().let {
                    return if (wrapCollection) {
                        wrapCollection(arrayListOf(wrapInfo(it.key, it.value)))
                    } else {
                        wrapRootInfo(it.key, it.value)
                    }
                }
            } else {
                moduleFolderApiMap.entries.first().let { moduleAndFolders ->
                    val items: ArrayList<HashMap<String, Any?>> = ArrayList()
                    moduleAndFolders.value.forEach { items.add(wrapInfo(it.key, it.value)) }
                    return if (wrapCollection) {
                        wrapCollection(arrayListOf(wrapInfo(moduleAndFolders.key, items)))
                    } else {
                        wrapRootInfo(moduleAndFolders.key, items)
                    }
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

        return wrapCollection(modules)
    }

    private fun wrapCollection(modules: ArrayList<HashMap<String, Any?>>): HashMap<String, Any?> {
        val rootModule = moduleHelper!!.findModuleByPath(ActionUtils.findCurrentPath()) ?: "easy-api"
        return wrapRootInfo(rootModule, modules)
    }

    private fun parseRequestsToFolder(requests: List<Request>): HashMap<Folder, ArrayList<HashMap<String, Any?>>> {
        //parse [request...] ->
        //                      {
        //                          "folder":[request...]
        //                      }

        //group by folder into: {folder:requests}
        val folderGroupedMap: HashMap<Folder, ArrayList<HashMap<String, Any?>>> = HashMap()
        requests.forEach { request ->
            val folder = formatFolderHelper!!.resolveFolder(request.resource ?: NULL_RESOURCE)
            folderGroupedMap.safeComputeIfAbsent(folder) { ArrayList() }!!
                .addAll(request2Items(request))
        }

        return folderGroupedMap
    }

    fun parseRequestsToCollection(collectionInfo: HashMap<String, Any?>, requests: List<Request>) {
        val folderGroupedMap = parseRequestsToFolder(requests)
        val folders = collectionInfo.getEditableItem()
        folderGroupedMap.entries.forEach { (folder, items) ->
            val targetFolder = folders.firstOrNull { it.isCollection() && it["name"] == folder.name }
            if (targetFolder == null) {
                folders.add(wrapInfo(folder, items))
                return@forEach
            }

            val apis = targetFolder.getEditableItem()
            for (item in items) {
                val method = item.method()
                val url = item.rawUrl()
                val sameApi = apis.firstOrNull {
                    it.method() == method && it.rawUrl() == url
                }
                if (sameApi == null) {
                    apis.add(item)
                } else {
                    sameApi.putAll(item)
                }
            }
        }
    }

    private fun HashMap<String, Any?>.method(): String? {
        return this.getAs("request", "method")
    }

    private fun HashMap<String, Any?>.rawUrl(): String? {
        val url = this.sub("request").sub("url")
        var rawUrl = url.getAs<List<String>>("path")?.joinToString("/")

        val queryList: ArrayList<HashMap<String, Any?>>? = url.getAs("query")
        if (queryList.notNullOrEmpty()) {
            rawUrl = rawUrl ?: ""
            queryList!!.forEach {
                rawUrl = if (rawUrl!!.contains('?')) {
                    rawUrl + "&" + it.getAs(KEY) + "=" + it.getAs(VALUE)
                } else {
                    rawUrl + "?" + it.getAs(KEY) + "=" + it.getAs(VALUE)
                }
            }
        }
        return rawUrl
    }

    private fun parsePath(path: String): List<String> {
        val paths = path.trim().trim('/').split("/")
        return paths.map {
            if (it.contains('{')) {
                val p = if (it.contains(':'))
                    it.substring(0, it.indexOf(':')) else
                    it
                return@map p
                    .replace("{", ":")
                    .replace("}", "")
            } else {
                return@map it
            }
        }
    }

    private fun getBodyFormatter(type: Int): MessageFormatter {
        val useJson5 = postmanSettingsHelper.postmanJson5FormatType().needUseJson5(type)
        return if (useJson5) {
            actionContext!!.instance(Json5Formatter::class)
        } else {
            actionContext!!.instance(SimpleJsonFormatter::class)
        }
    }

    companion object {
        const val NULL_RESOURCE = "unknown"

        const val POSTMAN_SCHEMA_V2_1_0 = "https://schema.getpostman.com/json/collection/v2.1.0/collection.json"
    }
}

private const val NAME = "name"
private const val KEY = "key"
private const val VALUE = "value"
private const val TYPE = "type"
private const val MODE = "mode"
private const val DESCRIPTION = "description"
private const val EVENT = "event"
private const val ITEM = "item"