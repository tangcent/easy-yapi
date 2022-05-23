package com.itangcent.idea.plugin.api.export.markdown

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.itangcent.common.model.Doc
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Request
import com.itangcent.common.utils.*
import com.itangcent.http.RequestUtils
import com.itangcent.idea.plugin.api.export.core.ClassExportContext
import com.itangcent.idea.plugin.api.export.core.Folder
import com.itangcent.idea.plugin.api.export.core.FormatFolderHelper
import com.itangcent.idea.plugin.api.export.core.MethodExportContext
import com.itangcent.idea.plugin.api.export.rule.MethodDocRuleWrap
import com.itangcent.idea.plugin.api.export.rule.RequestRuleWrap
import com.itangcent.idea.plugin.rule.SuvRuleContext
import com.itangcent.idea.plugin.settings.helper.MarkdownSettingsHelper
import com.itangcent.idea.psi.UltimateDocHelper
import com.itangcent.idea.psi.resource
import com.itangcent.idea.psi.resourceClass
import com.itangcent.idea.psi.resourceMethod
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.idea.utils.SystemProvider
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.takeIfNotOriginal
import com.itangcent.intellij.extend.takeIfSpecial
import com.itangcent.intellij.jvm.DuckTypeHelper
import com.itangcent.intellij.jvm.element.ExplicitMethod
import java.util.concurrent.ConcurrentHashMap

/**
 * format [com.itangcent.common.model.Doc] to `markdown`.
 *
 * @author tangcent
 */
@Singleton
class MarkdownFormatter {

    @Inject
    private val actionContext: ActionContext? = null

    @Inject
    private val moduleHelper: ModuleHelper? = null

    @Inject
    protected lateinit var systemProvider: SystemProvider

    @Inject
    private lateinit var duckTypeHelper: DuckTypeHelper

    @Inject
    private lateinit var ruleComputer: RuleComputer

    @Inject
    protected lateinit var markdownSettingsHelper: MarkdownSettingsHelper

    @Inject
    protected val ultimateDocHelper: UltimateDocHelper? = null

    @Inject
    private val formatFolderHelper: FormatFolderHelper? = null

    @Inject
    private lateinit var tableWriterBuilder: TableWriterBuilder

    @Inject
    private lateinit var objectWriterBuilder: ObjectWriterBuilder

    @Inject
    private lateinit var configReader: ConfigReader

    fun parseRequests(requests: List<Doc>): String {
        val sb = StringBuilder()
        val writer: Writer = { sb.append(it) }
        val groupedRequest = groupRequests(requests)
        val suvRuleContext = SuvRuleContext()
        ruleComputer.computer(MarkdownExportRuleKeys.HEADER, suvRuleContext, null)
            ?.let {
                writer(it)
                writer.doubleLine()
            }
        parseApi(groupedRequest, 1, writer)
        ruleComputer.computer(MarkdownExportRuleKeys.FOOTER, suvRuleContext, null)
            ?.let {
                writer(it)
                writer.nextLine()
            }
        return sb.toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun groupRequests(requests: List<Doc>): Any {


        //parse [request...] ->
        //                      {
        //                          "module":{
        //                              "folder":[request...]
        //                          }
        //                      }

        val moduleFolderApiMap: HashMap<String, HashMap<Folder, ArrayList<Doc>>> = HashMap()

        //group by module
        val moduleGroupedMap: HashMap<String, MutableList<Doc>> = HashMap()
        requests.forEach { request ->
            val module = request.resource?.let { moduleHelper!!.findModule(it) } ?: "easy-api"
            moduleGroupedMap.safeComputeIfAbsent(module) { ArrayList() }!!
                .add(request)
        }

        moduleGroupedMap.forEach { (module, requestsInModule) ->
            moduleFolderApiMap[module] = parseRequestsToFolder(requestsInModule)
        }


        if (moduleFolderApiMap.size == 1) {
            //single module
            val folderApiMap = moduleFolderApiMap.values.first()
            if (folderApiMap.size == 1) {
                //single folder
                folderApiMap.entries.first().let {
                    return wrapInfo(it.key, it.value)
                }
            } else {
                moduleFolderApiMap.entries.first().let { moduleAndFolders ->
                    val items: ArrayList<HashMap<String, Any?>> = ArrayList()
                    moduleAndFolders.value.forEach { items.add(wrapInfo(it.key, it.value)) }
                    return wrapInfo(moduleAndFolders.key, items)
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

        return modules
    }

    private fun parseRequestsToFolder(requests: MutableList<Doc>): HashMap<Folder, ArrayList<Doc>> {
        //parse [request...] ->
        //                      {
        //                          "folder":[request...]
        //                      }

        //group by folder into: {folder:requests}
        val folderGroupedMap: HashMap<Folder, ArrayList<Doc>> = HashMap()
        requests.forEach { request ->
            val folder = formatFolderHelper!!.resolveFolder(request.resource ?: NULL_RESOURCE)
            folderGroupedMap.safeComputeIfAbsent(folder) { ArrayList() }!!
                .add(request)
        }

        return folderGroupedMap
    }

    private fun parseApi(info: Any, deep: Int, writer: Writer) {
        when (info) {
            is Request -> parseRequest(info, deep, writer)
            is MethodDoc -> parseMethodDoc(info, deep, writer)
            is Map<*, *> -> parseInfo(info, deep, writer)
            is List<*> -> info.filterNotNull()
                .forEach {
                    parseApi(it, deep, writer)
                    writer.doubleLine()
                }
        }
    }

    private fun parseInfo(info: Map<*, *>, deep: Int, writer: Writer) {
        val title = info[NAME].toString()
        writer(ruleComputer.computer(MarkdownExportRuleKeys.HN_TITLE,
            SuvRuleContext().also {
                it.setExt("title", title)
                it.setExt("deep", deep)
            }, null)
            ?: "${hN(deep)} $title")
        writer.doubleLine()
        info[DESC]?.let {
            writer(it.toString())
            writer.doubleLine()
        }
        info[ITEMS]?.let { parseApi(it, deep + 1, writer) }
    }

    private fun parseMethodDoc(methodDoc: MethodDoc, deep: Int, writer: Writer) {

        val suvRuleContext = SuvRuleContext()
        suvRuleContext.setExt("type", "methodDoc")
        suvRuleContext.setExt("doc", methodDoc)//for compatible
        suvRuleContext.setExt("methodDoc", MethodDocRuleWrap(methodDoc.createMethodExportContext(), methodDoc))
        suvRuleContext.setExt("deep", deep)
        suvRuleContext.setExt("title", methodDoc.name)

        writer("\n---\n")
        writer(ruleComputer.computer(MarkdownExportRuleKeys.HN_TITLE, suvRuleContext, methodDoc.resource())
            ?: "${hN(deep)} ${methodDoc.name}")
        writer.doubleLine()

        if (methodDoc.desc.notNullOrBlank()) {
            writer(ruleComputer.computer(MarkdownExportRuleKeys.METHOD_DOC_DESC, suvRuleContext, methodDoc.resource())
                ?: "**Desc:**\n\n ${methodDoc.desc}")
            writer.doubleLine()
        }

        writer(ruleComputer.computer(MarkdownExportRuleKeys.METHOD_DOC_PARAMS, suvRuleContext, methodDoc.resource())
            ?: "**Params:**")
        writer.doubleLine()
        if (methodDoc.params.isNullOrEmpty()) {
            writer("Non-Parameter\n")
        } else {
            val objectWriter = objectWriterBuilder.build("methodDoc.params", writer)
            objectWriter.writeHeader()
            methodDoc.params?.forEach {
                objectWriter.writeObject(it.value, it.name ?: "", it.desc ?: "")
            }
            writer.nextLine()
        }

        writer(ruleComputer.computer(MarkdownExportRuleKeys.METHOD_DOC_RETURN, suvRuleContext, methodDoc.resource())
            ?: "**Return:**")
        writer.doubleLine()
        if (methodDoc.ret == null) {
            writer("Non-Return\n")
        } else {
            methodDoc.ret?.let {
                val objectWriter = objectWriterBuilder.build("methodDoc.return", writer)
                objectWriter.writeHeader()
                objectWriter.writeObject(it, methodDoc.retDesc ?: "")
            }
            writer.nextLine()
        }
    }

    private fun parseRequest(request: Request, deep: Int, writer: Writer) {

        val suvRuleContext = SuvRuleContext()
        suvRuleContext.setExt("type", "request")
        suvRuleContext.setExt("doc", request)//for compatible
        suvRuleContext.setExt("api", RequestRuleWrap(request.createMethodExportContext(), request))
        suvRuleContext.setExt("deep", deep)
        suvRuleContext.setExt("title", request.name)

        writer("\n---\n")

        writer(ruleComputer.computer(MarkdownExportRuleKeys.HN_TITLE, suvRuleContext, request.resource())
            ?: "${hN(deep)} ${request.name}")
        writer.doubleLine()

        //region basic info

        writer(ruleComputer.computer(MarkdownExportRuleKeys.BASIC, suvRuleContext, request.resource())
            ?: "> BASIC")
        writer.doubleLine()

        writer(ruleComputer.computer(MarkdownExportRuleKeys.BASIC_PATH, suvRuleContext, request.resource())
            ?: "**Path:** ${request.path}")
        writer.doubleLine()

        writer(ruleComputer.computer(MarkdownExportRuleKeys.BASIC_METHOD, suvRuleContext, request.resource())
            ?: "**Method:** ${request.method}")
        writer.doubleLine()

        if (request.desc.notNullOrBlank()) {
            writer(ruleComputer.computer(MarkdownExportRuleKeys.BASIC_DESC, suvRuleContext, request.resource())
                ?: "**Desc:**\n\n ${request.desc}")
            writer.doubleLine()
        }

        //endregion

        //region request

        writer(ruleComputer.computer(MarkdownExportRuleKeys.REQUEST, suvRuleContext, request.resource())
            ?: "> REQUEST")
        writer.doubleLine()

        //path
        if (request.paths.notNullOrEmpty()) {
            writer(ruleComputer.computer(MarkdownExportRuleKeys.REQUEST_PATH, suvRuleContext, request.resource())
                ?: "**Path Params:**")
            writer.doubleLine()
            val tableRender = tableWriterBuilder.build(writer, "request.pathParams", arrayOf("name", "value", "desc"))
            tableRender.writeHeaders()
            tableRender.addRows(request.paths, { it.name }, { it.value.takeIfNotOriginal() }, { it.desc })
            writer.nextLine()
        }

        //header
        if (request.headers.notNullOrEmpty()) {
            writer(ruleComputer.computer(MarkdownExportRuleKeys.REQUEST_HEADERS, suvRuleContext, request.resource())
                ?: "**Headers:**")
            writer.doubleLine()
            val tableRender =
                tableWriterBuilder.build(writer, "request.headers", arrayOf("name", "value", "required", "desc"))
            tableRender.writeHeaders()
            tableRender.addRows(request.headers, { it.name }, { it.value.takeIfSpecial() }, { it.required ?: false }, { it.desc })
            writer.nextLine()
        }

        //query
        if (request.querys.notNullOrEmpty()) {
            writer(ruleComputer.computer(MarkdownExportRuleKeys.REQUEST_QUERY, suvRuleContext, request.resource())
                ?: "**Query:**")
            writer.doubleLine()
            val tableRender =
                tableWriterBuilder.build(writer, "request.querys", arrayOf("name", "value", "required", "desc"))
            tableRender.writeHeaders()
            tableRender.addRows(request.querys, { it.name }, { it.value.takeIfNotOriginal() }, { it.required ?: false }, { it.desc })
            writer.nextLine()
        }

        if (request.body != null) {
            writer(ruleComputer.computer(MarkdownExportRuleKeys.REQUEST_BODY, suvRuleContext, request.resource())
                ?: "**Request Body:**")
            writer.doubleLine()

            val objectWriter = objectWriterBuilder.build("request.body", writer)
            objectWriter.writeHeader()
            objectWriter.writeObject(request.body, request.bodyAttr ?: "")

            if (markdownSettingsHelper.outputDemo()) {
                writer("\n")
                writer(ruleComputer.computer(MarkdownExportRuleKeys.REQUEST_BODY_DEMO,
                    suvRuleContext,
                    request.resource())
                    ?: "**Request Demo:**")
                writer.doubleLine()

                parseToJson(writer, request.body)
            }
            writer.nextLine()
        }

        if (request.formParams.notNullOrEmpty()) {
            writer(ruleComputer.computer(MarkdownExportRuleKeys.REQUEST_FORM, suvRuleContext, request.resource())
                ?: "**Form:**")
            writer.doubleLine()

            val tableRender =
                tableWriterBuilder.build(writer, "request.form", arrayOf("name", "value", "required", "type", "desc"))
            tableRender.writeHeaders()
            tableRender.addRows(request.formParams,
                { it.name },
                { it.value.takeIfSpecial() },
                { it.required ?: false },
                { it.type },
                { it.desc })

            writer.nextLine()
        }

        //endregion

        if (request.response.notNullOrEmpty()) {

            val response = request.response!!.firstOrNull { it.body != null }
            //todo:support multiple response
            if (response != null) {
                writer.doubleLine()
                writer(ruleComputer.computer(MarkdownExportRuleKeys.RESPONSE, suvRuleContext, request.resource())
                    ?: "> RESPONSE")
                writer.doubleLine()

                //response headers
                val responseHeaders = response.headers
                if (responseHeaders.notNullOrEmpty()) {
                    writer(ruleComputer.computer(MarkdownExportRuleKeys.RESPONSE_HEADERS,
                        suvRuleContext,
                        request.resource())
                        ?: "**Headers:**")
                    writer.doubleLine()

                    val tableRender =
                        tableWriterBuilder.build(writer,
                            "response.headers",
                            arrayOf("name", "value", "required", "desc"))
                    tableRender.writeHeaders()
                    tableRender.addRows(responseHeaders,
                        { it.name },
                        { it.value.takeIfNotOriginal() },
                        { it.required ?: false },
                        { it.desc })

                    writer.nextLine()
                }

                //response body
                response.body?.let {
                    writer(ruleComputer.computer(MarkdownExportRuleKeys.RESPONSE_BODY,
                        suvRuleContext,
                        request.resource())
                        ?: "**Body:**")
                    writer.doubleLine()
                    val objectWriter = objectWriterBuilder.build("response.body", writer)
                    objectWriter.writeHeader()
                    objectWriter.writeObject(it, response.bodyDesc ?: "")
                    writer.nextLine()
                }

                // handler json example
                if (markdownSettingsHelper.outputDemo()) {
                    writer(ruleComputer.computer(MarkdownExportRuleKeys.RESPONSE_BODY_DEMO,
                        suvRuleContext,
                        request.resource())
                        ?: "**Response Demo:**")
                    writer.doubleLine()
                    parseToJson(writer, response.body)
                    writer.nextLine()
                }
            }
        }
    }

    private fun parseToJson(writer: Writer, body: Any?) {
        writer("```json\n")
        body?.let {
            if (it != 0) {
                writer(RequestUtils.parseRawBody(it))
            }
        }
        writer("\n```\n")
    }

    private fun hN(n: Int): String {
        return "#".repeat(n)
    }

    private fun wrapInfo(resource: Any, items: List<Any?>): HashMap<String, Any?> {
        val info: HashMap<String, Any?> = HashMap()
        parseNameAndDesc(resource, info)
        info[ITEMS] = items
        return info
    }

    private fun parseNameAndDesc(resource: Any, info: HashMap<String, Any?>) {
        if (resource is PsiClass) {
            val attr = ultimateDocHelper!!.findUltimateDescOfClass(resource)
            if (attr.isNullOrBlank()) {
                info["name"] = resource.name!!
                info[DESC] = "exported from:${actionContext!!.callInReadUI { resource.qualifiedName }}"
            } else {
                val lines = attr.lines()
                if (lines.size == 1) {
                    info["name"] = attr
                    info[DESC] = "exported from:${actionContext!!.callInReadUI { resource.qualifiedName }}"
                } else {
                    info["name"] = lines[0]
                    info[DESC] = attr
                }
            }
        } else if (resource is Folder) {
            info["name"] = resource.name
            info[DESC] = resource.attr
        } else if (resource is Pair<*, *>) {
            info["name"] = resource.first
            info[DESC] = resource.second
        } else {
            info["name"] = resource.toString()
            info[DESC] = "exported at ${DateUtils.formatYMD_HMS(systemProvider.currentTimeMillis().asDate())}"
        }
    }

    private val explicitCache = ConcurrentHashMap<PsiClass, ArrayList<ExplicitMethod>>()

    private fun Doc.createMethodExportContext(): MethodExportContext? {
        val resourceClass = this.resourceClass() ?: return null
        val resourceMethod = this.resourceMethod() ?: return null
        val explicitMethods = explicitCache.computeIfAbsent(resourceClass) {
            return@computeIfAbsent actionContext!!.callInReadUI { duckTypeHelper.explicit(resourceClass).methods() }!!
        }
        val explicitMethod = explicitMethods.find { it.psi() == resourceMethod } ?: return null
        val classExportContext = ClassExportContext(resourceClass)
        return MethodExportContext(classExportContext, explicitMethod)
    }

    companion object {
        private const val NAME = "name"
        private const val DESC = "desc"
        private const val ITEMS = "items"
        private const val NULL_RESOURCE = "unknown"
    }
}