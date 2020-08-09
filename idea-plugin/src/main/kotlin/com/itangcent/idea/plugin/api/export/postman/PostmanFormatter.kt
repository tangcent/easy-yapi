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
import com.itangcent.idea.plugin.rule.SuvRuleContext
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.psi.ResourceHelper
import com.itangcent.idea.psi.resource
import com.itangcent.idea.psi.resourceClass
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.util.ActionUtils
import org.apache.commons.lang3.RandomUtils
import java.util.*
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

    @Inject
    private val settingBinder: SettingBinder? = null

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
            headers.add(KV.create<String, Any?>()
                    .set(KEY, it.name)
                    .set(VALUE, it.value)
                    .set(TYPE, "text")
                    .set(DESCRIPTION, it.desc ?: "")
            )
        }

        val queryList: ArrayList<HashMap<String, Any?>> = ArrayList()
        url["query"] = queryList
        request.querys?.forEach {
            queryList.add(KV.create<String, Any?>()
                    .set(KEY, it.name)
                    .set(VALUE, it.value)
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
                    formdatas.add(KV.create<String, Any?>()
                            .set(KEY, it.name)
                            .set(VALUE, it.value)
                            .set(TYPE, it.type)
                            .set(DESCRIPTION, it.desc)
                    )
                }
                body["formdata"] = formdatas

            } else {
                body[MODE] = "urlencoded"
                val urlEncodeds: ArrayList<HashMap<String, Any?>> = ArrayList()
                request.formParams!!.forEach {
                    urlEncodeds.add(KV.create<String, Any?>()
                            .set(KEY, it.name)
                            .set(VALUE, it.value)
                            .set(TYPE, it.type)
                            .set(DESCRIPTION, it.desc)
                    )
                }
                body["urlencoded"] = urlEncodeds
            }
        }

        if (request.body != null) {
            body[MODE] = "raw"
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
                    responseInfo[NAME] = exampleName + (index + 1)
                } else {
                    responseInfo[NAME] = exampleName
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
                            .set(NAME, "content-type")
                            .set(KEY, "content-type")
                            .set(VALUE, "application/json;charset=UTF-8")
                            .set(DESCRIPTION, "The mime type of this content")
                    )
                }

                if (response.headers?.any { it.name.equals("date", true) } == false) {

                    responseHeader.add(KV.create<String, Any?>()
                            .set(NAME, "date")
                            .set(KEY, "date")
                            .set(VALUE, Date().formatDate("EEE, dd MMM yyyyHH:mm:ss 'GMT'"))
                            .set(DESCRIPTION, "The date and time that the message was sent")
                    )
                }

                if (response.headers?.any { it.name.equals("server", true) } == false) {
                    responseHeader.add(KV.create<String, Any?>()
                            .set(NAME, "server")
                            .set(KEY, "server")
                            .set(VALUE, "Apache-Coyote/1.1")
                            .set(DESCRIPTION, "A name for the server")
                    )
                }

                if (response.headers?.any { it.name.equals("transfer-encoding", true) } == false) {

                    responseHeader.add(KV.create<String, Any?>()
                            .set(NAME, "transfer-encoding")
                            .set(KEY, "transfer-encoding")
                            .set(VALUE, "chunked")
                            .set(DESCRIPTION, "The form of encoding used to safely transfer the entity to the user. Currently defined methods are: chunked, compress, deflate, gzip, identity.")
                    )
                }

                response.headers?.forEach {
                    responseHeader.add(KV.create<String, Any?>()
                            .set(NAME, it.name)
                            .set(KEY, it.name)
                            .set(VALUE, it.value)
                            .set(DESCRIPTION, it.desc)
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
            events!!.add(KV.any().set("listen", "prerequest")
                    .set("script", KV.any()
                            .set("exec", it.lines())
                            .set(TYPE, "text/javascript")
                    ))
        }

        test()?.takeIf { it.notNullOrBlank() }?.let {
            events = events ?: ArrayList()
            events!!.add(KV.any().set("listen", "test")
                    .set("script", KV.any()
                            .set("exec", it.lines())
                            .set(TYPE, "text/javascript")
                    ))
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
                        ruleComputer!!.computer(ClassExportRuleKeys.COLLECTION_POST_PRE_REQUEST,
                                context, null)
                                .append(resource.getExt<String>(ClassExportRuleKeys.POST_PRE_REQUEST.name()), "\n")
                    },
                    {
                        ruleComputer!!.computer(ClassExportRuleKeys.COLLECTION_POST_TEST,
                                context, null)
                                .append(resource.getExt<String>(ClassExportRuleKeys.POST_TEST.name()), "\n")
                    }
            )
        } else {
            addScriptsToItem(postman,
                    {
                        ruleComputer!!.computer(ClassExportRuleKeys.COLLECTION_POST_PRE_REQUEST,
                                context, null)
                    },
                    {
                        ruleComputer!!.computer(ClassExportRuleKeys.COLLECTION_POST_TEST,
                                context, null)
                    }
            )
        }

        info[NAME] = "${info[NAME]}-${Date().formatDate("yyyyMMddHHmmss")}"
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
            val attr = resourceHelper!!.findAttrOfClass(resource)
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
            info[DESCRIPTION] = "exported at ${DateUtils.formatYMD_HMS(DateUtils.now())}"
        }
    }

    fun parseRequests(requests: MutableList<Request>): HashMap<String, Any?> {
        val postmanCollection = doParseRequests(requests)
        if (settingBinder!!.read().autoMergeScript) {
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
    private fun mergeEvents(item: java.util.HashMap<String, Any?>, events: List<*>) {
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

    private fun doParseRequests(requests: MutableList<Request>): HashMap<String, Any?> {


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

        moduleGroupedMap.forEach { (module, requestsInModule) ->
            moduleFolderApiMap[module] = parseRequestsToFolder(requestsInModule)
        }

        if (moduleFolderApiMap.size == 1) {
            val wrapCollection = settingBinder!!.read().wrapCollection

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
