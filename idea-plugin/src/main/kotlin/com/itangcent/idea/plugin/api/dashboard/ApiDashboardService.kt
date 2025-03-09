package com.itangcent.idea.plugin.api.dashboard

import com.google.inject.Inject
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.project.ProjectManagerListener
import com.intellij.openapi.project.rootManager
import com.intellij.openapi.wm.ToolWindowManager
import com.intellij.psi.*
import com.itangcent.cache.CacheSwitcher
import com.itangcent.cache.HttpContextCacheHelper
import com.itangcent.cache.withoutCache
import com.itangcent.common.constant.HttpMethod
import com.itangcent.common.kit.headLine
import com.itangcent.common.logger.Log
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.*
import com.itangcent.common.spi.Setup
import com.itangcent.common.utils.safe
import com.itangcent.http.CookieStore
import com.itangcent.http.HttpResponse
import com.itangcent.http.RequestUtils
import com.itangcent.http.contentType
import com.itangcent.idea.binder.DbBeanBinderFactory
import com.itangcent.idea.icons.EasyIcons
import com.itangcent.idea.plugin.api.cache.CachedRequestClassExporter
import com.itangcent.idea.plugin.api.cache.ProjectCacheRepository
import com.itangcent.idea.plugin.api.export.ExportDoc
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.CustomizedMethodFilter
import com.itangcent.idea.plugin.api.export.core.MethodFilter
import com.itangcent.idea.plugin.api.export.core.SpecialMethodFilter
import com.itangcent.idea.plugin.api.export.curl.CurlExporter
import com.itangcent.idea.plugin.api.export.http.HttpClientExporter
import com.itangcent.idea.plugin.api.export.markdown.MarkdownApiExporter
import com.itangcent.idea.plugin.api.export.postman.PostmanApiExporter
import com.itangcent.idea.plugin.api.export.yapi.YapiApiExporter
import com.itangcent.idea.plugin.config.EnhancedConfigReader
import com.itangcent.idea.plugin.rule.SuvRuleParser
import com.itangcent.idea.plugin.settings.helper.MarkdownSettingsHelper
import com.itangcent.idea.plugin.utils.NotificationUtils
import com.itangcent.idea.psi.UltimateDocHelper
import com.itangcent.idea.psi.resourceClass
import com.itangcent.idea.psi.resourceMethod
import com.itangcent.idea.swing.IconCustomized
import com.itangcent.idea.swing.ToolTipAble
import com.itangcent.idea.utils.*
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.rule.RuleParser
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.extend.withBoundary
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.PsiClassUtils
import com.itangcent.intellij.util.FileType
import com.itangcent.suv.http.CookiePersistenceHelper
import com.itangcent.suv.http.HttpClientProvider
import org.apache.http.entity.ContentType
import java.util.*
import javax.swing.Icon
import javax.swing.tree.DefaultMutableTreeNode

@Service
class ApiDashboardService(private val project: Project) {

    companion object : Log() {
        fun getInstance(project: Project): ApiDashboardService = project.service()
    }

    private var dashboardPanel: ApiDashboardPanel? = null

    @Inject
    private lateinit var logger: Logger

    @Inject
    private lateinit var httpClientProvider: HttpClientProvider

    @Inject
    private lateinit var classExporter: ClassExporter

    @Inject
    private lateinit var cacheSwitcher: CacheSwitcher

    @Inject
    private lateinit var ultimateDocHelper: UltimateDocHelper

    @Inject
    private lateinit var customizedMethodFilter: CustomizedMethodFilter

    @Inject
    private lateinit var projectCacheRepository: ProjectCacheRepository

    @Inject
    private lateinit var httpContextCacheHelper: HttpContextCacheHelper

    @Inject
    private lateinit var cookiePersistenceHelper: CookiePersistenceHelper

    lateinit var actionContext: ActionContext
        private set

    private val cookieStore: CookieStore
        get() = httpClientProvider.getHttpClient().cookieStore()

    private val requestRawInfoBinderFactory: DbBeanBinderFactory<RequestRawInfo> by lazy {
        DbBeanBinderFactory(projectCacheRepository.getOrCreateFile(".api.dashboard.v1.0.db").path) { RequestRawInfo() }
    }

    private var currentRequest: RequestRawInfo? = null

    fun setCurrentRequest(request: RequestRawInfo) {
        currentRequest = request
    }

    data class RequestRawInfo(
        var key: String? = null,
        var name: String? = null,
        var path: String? = null,
        var method: String? = null,
        var headers: String? = null,
        var querys: String? = null,
        var formParams: MutableList<FormParam>? = null,
        var bodyType: String? = null,
        var body: String? = null,
        var bodyAttr: String? = null
    ) {
        fun cacheKey(): String = key ?: ""

        fun contentType(): String? {
            if (headers.isNullOrBlank()) return null
            return headers!!.lines()
                .find { it.trim().startsWith("Content-Type:", ignoreCase = true) }
                ?.substringAfter(":", "")?.trim()
        }

        fun hasForm(): Boolean {
            if (method == "GET" || method == "ALL") return false
            val contentType = contentType() ?: return false
            return !contentType.contains("application/json")
        }

        fun hasBodyOrForm(): Boolean = method != null && method != HttpMethod.GET
    }

    init {
        Setup.load(ApiDashboardService::class.java.classLoader)
        createNewActionContext()

        // Add project dispose listener
        project.messageBus.connect().subscribe(ProjectManager.TOPIC, object : ProjectManagerListener {
            override fun projectClosing(project: Project) {
                if (project == this@ApiDashboardService.project) {
                    actionContext.stop()
                }
            }
        })
    }

    fun createNewActionContext() {
        val builder = ActionContext.builder()
        builder.bindInstance(Project::class, project)
        builder.bind(ClassExporter::class) { it.with(CachedRequestClassExporter::class).singleton() }
        builder.bind(LocalFileRepository::class) { it.with(ProjectCacheRepository::class).singleton() }
        builder.bindInstance(ExportDoc::class, ExportDoc.of("request"))
        builder.bind(PsiClassHelper::class) { it.with(CustomizedPsiClassHelper::class).singleton() }
        builder.bind(MethodFilter::class) { it.with(CustomizedMethodFilter::class).singleton() }
        builder.bind(ConfigReader::class) { it.with(EnhancedConfigReader::class).singleton() }
        builder.bind(RuleParser::class) { it.with(SuvRuleParser::class).singleton() }

        actionContext = builder.build()
        actionContext.init(this)
        actionContext.runAsync {
            cookiePersistenceHelper.loadCookiesInto(cookieStore)
        }
    }

    fun ensureActionContextActive() {
        if (!::actionContext.isInitialized || actionContext.isStopped()) {
            createNewActionContext()
        }
    }

    fun setDashboardPanel(panel: ApiDashboardPanel) {
        this.dashboardPanel = panel
    }

    fun navigateToClass(psiClass: PsiClass) {
        // First ensure the dashboard panel is visible
        actionContext.runInSwingUI {
            val toolWindow = ToolWindowManager.getInstance(project).getToolWindow("API Dashboard")
            toolWindow?.show {
                // After the tool window is shown, delegate to panel to find and select the class node
                dashboardPanel?.navigateToClass(psiClass)
            }
        }
    }

    fun refreshApis(useCache: Boolean = false) {
        actionContext.runAsync {
            if (!useCache) {
                cacheSwitcher.withoutCache {
                    val boundary = actionContext.createBoundary()
                    doRefreshApis()
                    boundary.waitComplete()
                }
            } else {
                doRefreshApis()
            }
        }
    }

    fun refreshApis(projectNodeData: ProjectNodeData): ProjectNodeData? {
        return cacheSwitcher.withoutCache {
            if (projectNodeData is ModuleNodeData) {
                val module = actionContext.callInReadUI {
                    ModuleManager.getInstance(project).findModuleByName(projectNodeData.moduleName)
                }
                if (module != null) {
                    val requests = mutableListOf<Request>()
                    actionContext.withBoundary {
                        val sourceRoots = module.rootManager.getSourceRoots(false)
                        if (sourceRoots.isNotEmpty()) {
                            for (contentRoot in sourceRoots) {
                                actionContext.runInReadUI {
                                    val rootDirectory =
                                        PsiManager.getInstance(project).findDirectory(contentRoot) ?: return@runInReadUI
                                    traversal(
                                        rootDirectory,
                                        { FileType.acceptable(it.name) && (it is PsiClassOwner) }) { psiFile ->
                                        actionContext.checkStatus()
                                        for (psiClass in (psiFile as PsiClassOwner).classes) {
                                            actionContext.checkStatus()
                                            classExporter.export(psiClass) { doc ->
                                                if (doc is Request) {
                                                    requests.add(doc)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    return@withoutCache if (requests.isEmpty()) {
                        null
                    } else {
                        buildModuleTreeData(projectNodeData.moduleName, requests)
                    }
                }
            } else if (projectNodeData is ClassNodeData) {
                val requests = mutableListOf<Request>()
                actionContext.withBoundary {
                    actionContext.runInReadUI {
                        classExporter.export(projectNodeData.psiClass) { doc ->
                            if (doc is Request) {
                                requests.add(doc)
                            }
                        }
                    }
                }
                return@withoutCache if (requests.isEmpty()) {
                    null
                } else {
                    buildClassTreeData(projectNodeData.psiClass, requests)
                }
            } else if (projectNodeData is ApiNodeData) {
                val psiMethod = projectNodeData.request.resourceMethod() ?: return@withoutCache null
                val psiClass = psiMethod.containingClass ?: return@withoutCache null
                customizedMethodFilter.addMethodFilter(SpecialMethodFilter(psiMethod))
                var request: Request? = null
                actionContext.withBoundary {
                    actionContext.runAsync {
                        actionContext.runInReadUI {
                            classExporter.export(psiClass) { doc ->
                                if (doc is Request) {
                                    request = doc
                                }
                            }
                        }
                    }
                }
                customizedMethodFilter.clearMethodFilters()
                return@withoutCache if (request == null) {
                    null
                } else {
                    ApiNodeData(request!!)
                }
            }
            return@withoutCache projectNodeData
        }
    }

    private fun doRefreshApis() {
        val moduleManager = actionContext.callInReadUI { ModuleManager.getInstance(project) }!!
        val modules = actionContext.callInReadUI { moduleManager.sortedModules.reversed() }!!

        val moduleApisList = mutableListOf<ProjectNodeData>()

        for (module in modules) {
            actionContext.checkStatus()
            val sourceRoots = module.rootManager.getSourceRoots(false)
            if (sourceRoots.isEmpty()) {
                LOG.info("no source files be found in module:${module.name}")
                continue
            }
            val boundary = actionContext.createBoundary()
            val requests = mutableListOf<Request>()
            for (contentRoot in sourceRoots) {
                actionContext.checkStatus()
                actionContext.runInReadUI {
                    val rootDirectory =
                        PsiManager.getInstance(project).findDirectory(contentRoot) ?: return@runInReadUI
                    traversal(
                        rootDirectory,
                        { FileType.acceptable(it.name) && (it is PsiClassOwner) }) { psiFile ->
                        actionContext.checkStatus()
                        for (psiClass in (psiFile as PsiClassOwner).classes) {
                            actionContext.checkStatus()
                            classExporter.export(psiClass) { doc ->
                                if (doc is Request) {
                                    requests.add(doc)
                                }
                            }
                        }
                    }
                }
            }
            boundary.waitComplete()
            if (requests.isNotEmpty()) {
                val moduleNode = buildModuleTreeData(module.name, requests)
                moduleApisList.add(moduleNode)
            }
        }
        dashboardPanel?.updateApis(moduleApisList)
    }

    private fun buildModuleTreeData(moduleName: String, requests: List<Request>): ProjectNodeData {
        val moduleNode = ModuleNodeData(moduleName)

        // Group APIs by class
        val apisByClass = requests.groupBy { it.resourceClass() }

        apisByClass.forEach { (psiClass, apis) ->
            if (psiClass != null) {
                val classTreeData = buildClassTreeData(psiClass, apis)
                moduleNode.addSubNodeData(classTreeData)
            }
        }

        return moduleNode
    }

    private fun buildClassTreeData(
        psiClass: PsiClass,
        apis: List<Request>
    ): ClassNodeData {
        val classDesc = actionContext.callInReadUI { ultimateDocHelper.findUltimateDescOfClass(psiClass) }
        val classNode = ClassNodeData(psiClass, classDesc)

        apis.forEach { api ->
            classNode.addSubNodeData(ApiNodeData(api))
        }
        return classNode
    }

    private fun traversal(
        psiDirectory: PsiDirectory,
        fileFilter: (PsiFile) -> Boolean,
        fileHandle: (PsiFile) -> Unit
    ) {
        val dirStack: Stack<PsiDirectory> = Stack()
        var dir: PsiDirectory? = psiDirectory
        while (dir != null) {
            actionContext.checkStatus()
            for (file in dir.files) {
                actionContext.checkStatus()
                if (fileFilter(file)) {
                    fileHandle(file)
                }

            }

            actionContext.checkStatus()
            for (subdirectory in dir.subdirectories) {
                dirStack.push(subdirectory)
            }

            if (dirStack.isEmpty()) break
            dir = dirStack.pop()
            Thread.yield()
        }
    }

    fun addHost(host: String) {
        httpContextCacheHelper.addHost(host)
    }

    fun getHosts(): List<String> {
        return httpContextCacheHelper.getHosts()
    }

    fun sendRequest(
        host: String,
        path: String,
        method: String,
        headers: List<Header>,
        formParams: List<FormParam>?,
        body: String?
    ): HttpResponse {
        logger.info("send request to $host$path")
        val url = RequestUtils.UrlBuild().host(host).path(path).url()
        val httpResponse = httpClientProvider.getHttpClient()
            .request()
            .method(method)
            .url(url)
            .apply {
                headers.forEach { header(it.name ?: "", it.value) }
                if (formParams?.isNotEmpty() == true) {
                    val contentType = headers.contentType()
                    if (contentType?.startsWith("application/x-www-form-urlencoded") == true) {
                        formParams.forEach { param ->
                            param.name?.let { param(it, param.value) }
                        }
                    } else if (contentType?.startsWith("multipart/form-data") == true) {
                        formParams.forEach { param ->
                            if (param.type == "file") {
                                val filePath = param.value
                                if (!filePath.isNullOrBlank()) {
                                    param.name?.let { fileParam(it, filePath) }
                                }
                            } else {
                                param.name?.let { param(it, param.value) }
                            }
                        }
                    }
                } else if (body != null) {
                    // Handle raw body
                    val contentType = headers.contentType()
                    if (contentType != null) {
                        contentType(contentType)
                    } else {
                        contentType(ContentType.APPLICATION_JSON)
                    }
                    body(body)
                }
            }
            .call()
        logger.info("response status code: ${httpResponse.code()}")
        actionContext.runAsync {
            cookiePersistenceHelper.storeCookiesFrom(cookieStore)
        }
        return httpResponse
    }

    fun formatResponse(response: HttpResponse): String? {
        val rawResult = response.string() ?: return null
        return try {
            val contentType = safe { response.contentType()?.let { ContentType.parse(it) } }
            if (contentType != null) {
                if (contentType.mimeType.startsWith("text/html")) {
                    return actionContext.instance(FormatterHelper::class).formatHtml(rawResult)
                }
                if (contentType.mimeType.startsWith("text/xml")) {
                    return actionContext.instance(FormatterHelper::class).formatXml(rawResult)
                }
                if (contentType.mimeType.startsWith("application/json")) {
                    return actionContext.instance(FormatterHelper::class).formatJson(rawResult)
                }
                return rawResult
            }
            rawResult
        } catch (e: Exception) {
            LOG.traceError("format response error", e)
            rawResult
        }
    }

    fun saveResponse(responseText: String, currentResponse: HttpResponse) {
        if (responseText.isBlank()) {
            NotificationUtils.notifyInfo(project, "No response to save")
            return
        }

        // Set default filename based on content type
        val defaultFileName = when {
            currentResponse.contentType()?.startsWith("application/json") == true -> "response.json"
            currentResponse.contentType()?.startsWith("text/html") == true -> "response.html"
            currentResponse.contentType()?.startsWith("text/xml") == true -> "response.xml"
            else -> "response.txt"
        }

        val fileSaveHelper = actionContext.instance(FileSaveHelper::class)
        val markdownSettingsHelper = actionContext.instance(MarkdownSettingsHelper::class)
        fileSaveHelper.saveOrCopy(responseText, markdownSettingsHelper.outputCharset(), { defaultFileName }, {
            NotificationUtils.notifyInfo(project, "Response copied to clipboard")
        }, { filePath ->
            NotificationUtils.notifyInfo(project, "Response saved successfully to: $filePath")
        }) { errorMessage ->
            LOG.traceError("failed to save response", Exception(errorMessage))
            NotificationUtils.notifyInfo(project, "Failed to save response: $errorMessage")
        }
    }

    fun saveCurrentRequest() {
        currentRequest?.let { request ->
            requestRawInfoBinderFactory.getBeanBinder(request.cacheKey()).save(request)
        }
    }

    fun loadRequestContent(request: Request): RequestRawInfo {
        val key = request.cacheKey()
        val beanBinder = requestRawInfoBinderFactory.getBeanBinder(key)
        val savedRequest = beanBinder.tryRead()

        if (savedRequest != null) {
            return savedRequest
        }

        return RequestRawInfo().apply {
            this.key = key
            this.name = request.name?.trim()
            this.path = request.path?.url()
            this.method = request.method?.trim()
            this.headers = formatRequestHeaders(request)
            this.querys = formatQueryParams(request)
            this.formParams = request.formParams?.toMutableList()
            this.bodyType = request.bodyType?.trim()
            this.body = formatRequestBody(request)
            this.bodyAttr = request.bodyAttr?.trim()
        }
    }

    fun updateCurrentRequest(
        path: String? = null,
        method: String? = null,
        headers: String? = null,
        querys: String? = null,
        formParams: List<FormParam>? = null,
        body: String? = null
    ) {
        currentRequest?.let { request ->
            path?.let { request.path = it }
            method?.let { request.method = it }
            headers?.let { request.headers = it }
            querys?.let { request.querys = it }
            formParams?.let { request.formParams = it.toMutableList() }
            body?.let { request.body = it }
            saveCurrentRequest()
        }
    }

    private fun formatRequestHeaders(request: Request): String {
        if (request.headers.isNullOrEmpty()) return ""
        return request.headers!!.joinToString("\n") { "${it.name}: ${it.value}" }
    }

    private fun formatQueryParams(request: Request): String {
        if (request.querys.isNullOrEmpty()) return ""
        return request.querys!!.joinToString("&") { "${it.name}=${it.value ?: ""}" }
    }

    private fun formatRequestBody(request: Request): String {
        if (request.hasBodyOrForm()) {
            return when {
                request.body != null -> RequestUtils.parseRawBody(request.body!!)
                else -> ""
            }
        }
        return ""
    }

    private fun Request.cacheKey(): String {
        return actionContext.callInReadUI {
            PsiClassUtils.fullNameOfMember(resourceClass(), resourceMethod()!!)
        } ?: path?.url() ?: ""
    }

    // Node data for modules
    class ModuleNodeData(val moduleName: String) : ProjectNodeData(), IconCustomized, ToolTipAble {
        override val icon: Icon
            get() = EasyIcons.ModuleGroup!!

        override val text: String
            get() = moduleName

        override val tooltip: String?
            get() = null

        override fun icon(): Icon = icon

        override fun toolTip(): String? = tooltip

        override fun isNavigatable(): Boolean = false

        override fun navigateToSource() {}

        override fun resetEnable(): Boolean = true

        override fun reset() {
            getSubNodeData()?.forEach { it.reset() }
        }

        override fun toString(): String = text

        override fun resource(): Any? = null

        override fun exportToYapi(actionContext: ActionContext) {
            getSubNodeData()?.forEach { it.exportToYapi(actionContext) }
        }

        override fun exportToPostman(actionContext: ActionContext) {
            val postmanApiExporter = actionContext.instance(PostmanApiExporter::class)
            val requests = collectDocs()
            postmanApiExporter.export(requests)
        }

        override fun exportToMarkdown(actionContext: ActionContext) {
            val markdownApiExporter = actionContext.instance(MarkdownApiExporter::class)
            val requests = collectDocs()
            markdownApiExporter.export(requests)
        }

        override fun exportToCurl(actionContext: ActionContext) {
            val curlExporter = actionContext.instance(CurlExporter::class)
            val requests = collectDocs()
            curlExporter.export(requests)
        }

        override fun exportToHttpClient(actionContext: ActionContext) {
            val httpClientExporter = actionContext.instance(HttpClientExporter::class)
            val requests = collectDocs()
            httpClientExporter.export(requests)
        }
    }

    // Node data for classes
    inner class ClassNodeData(val psiClass: PsiClass, private val classDesc: String?) : ProjectNodeData(),
        IconCustomized, ToolTipAble {

        override val icon: Icon
            get() = EasyIcons.Class!!

        override val text: String by lazy {
            classDesc?.headLine() ?: actionContext.callInReadUI { psiClass.name } ?: "anonymous"
        }

        override val tooltip: String by lazy {
            classDesc ?: actionContext.callInReadUI { psiClass.qualifiedName ?: psiClass.name } ?: "anonymous"
        }

        override fun icon(): Icon = icon

        override fun toolTip(): String = tooltip

        override fun isNavigatable(): Boolean = psiClass.canNavigate()

        override fun navigateToSource() {
            actionContext.runInReadUI {
                psiClass.navigate(true)
            }
        }

        override fun resetEnable(): Boolean = true

        override fun reset() {
            getSubNodeData()?.forEach { it.reset() }
        }

        override fun toString(): String = text

        override fun resource(): Any = psiClass

        override fun exportToYapi(actionContext: ActionContext) {
            getSubNodeData()?.forEach { it.exportToYapi(actionContext) }
        }

        override fun exportToPostman(actionContext: ActionContext) {
            val postmanApiExporter = actionContext.instance(PostmanApiExporter::class)
            val requests = collectDocs()
            postmanApiExporter.export(requests)
        }

        override fun exportToMarkdown(actionContext: ActionContext) {
            val markdownApiExporter = actionContext.instance(MarkdownApiExporter::class)
            val requests = collectDocs()
            markdownApiExporter.export(requests)
        }

        override fun exportToCurl(actionContext: ActionContext) {
            val curlExporter = actionContext.instance(CurlExporter::class)
            val requests = collectDocs()
            curlExporter.export(requests)
        }

        override fun exportToHttpClient(actionContext: ActionContext) {
            val httpClientExporter = actionContext.instance(HttpClientExporter::class)
            val requests = collectDocs()
            httpClientExporter.export(requests)
        }
    }

    // Node data for APIs
    inner class ApiNodeData(val request: Request) : ProjectNodeData(), IconCustomized, ToolTipAble {
        override val icon: Icon
            get() = EasyIcons.Method!!

        override val text: String by lazy {
            buildString {
                append(request.method ?: HttpMethod.GET)
                append(" ")
                append(request.name ?: request.path ?: "-")
                request.path?.let { path ->
                    if (path.url() != request.name) {
                        append(" [")
                        append(path)
                        append("]")
                    }
                }
            }
        }

        override val tooltip: String?
            get() = request.desc

        override fun icon(): Icon = icon

        override fun toolTip(): String? = tooltip

        override fun docs(handle: (Request) -> Unit) {
            handle(request)
        }

        override fun isNavigatable(): Boolean = request.resourceMethod()?.canNavigate() ?: false

        override fun navigateToSource() {
            actionContext.runInReadUI {
                request.resourceMethod()?.navigate(true)
            }
        }

        override fun toString(): String = text

        override fun resetEnable(): Boolean {
            return true
        }

        override fun reset() {
            requestRawInfoBinderFactory.deleteBinder(request.cacheKey())
            currentRequest = null
        }

        override fun resource(): Any? = request.resource

        override fun exportToYapi(actionContext: ActionContext) {
            val yapiApiExporter = actionContext.instance(YapiApiExporter::class)
            yapiApiExporter.exportDoc(request)
        }

        override fun exportToPostman(actionContext: ActionContext) {
            val postmanApiExporter = actionContext.instance(PostmanApiExporter::class)
            postmanApiExporter.export(listOf(request))
        }

        override fun exportToMarkdown(actionContext: ActionContext) {
            val markdownApiExporter = actionContext.instance(MarkdownApiExporter::class)
            markdownApiExporter.export(listOf(request))
        }

        override fun exportToCurl(actionContext: ActionContext) {
            val curlExporter = actionContext.instance(CurlExporter::class)
            curlExporter.export(listOf(request))
        }

        override fun exportToHttpClient(actionContext: ActionContext) {
            val httpClientExporter = actionContext.instance(HttpClientExporter::class)
            httpClientExporter.export(listOf(request))
        }
    }

    fun dispose() {
        if (::actionContext.isInitialized && !actionContext.isStopped()) {
            actionContext.stop()
        }
    }
}

interface DocContainer {

    fun docs(handle: (Request) -> Unit)
}

fun DocContainer.collectDocs(): List<Request> {
    val requests = mutableListOf<Request>()
    docs { requests.add(it) }
    return requests
}

// Base class for all tree nodes
abstract class TreeNodeData<T> {
    private var treeNode: DefaultMutableTreeNode? = null
    private var subNodeData: ArrayList<T>? = null
    var parentNodeData: T? = null
        private set

    val isRoot: Boolean
        get() = parentNodeData == null

    @Suppress("UNCHECKED_CAST")
    open fun addSubNodeData(nodeData: T) {
        if (subNodeData == null) {
            subNodeData = ArrayList()
        }
        subNodeData!!.add(nodeData)
        (nodeData as TreeNodeData<Any>).parentNodeData = this
        this.asTreeNode().add(nodeData.asTreeNode())
    }

    @Suppress("UNCHECKED_CAST")
    open fun replaceSubNodeData(oldNodeData: T, newNodeData: T) {
        val index = subNodeData?.indexOf(oldNodeData) ?: -1
        if (index >= 0) {
            subNodeData?.set(index, newNodeData)
            (newNodeData as TreeNodeData<Any>).parentNodeData = this
            this.asTreeNode().insert(newNodeData.asTreeNode(), index)
            oldNodeData.asTreeNode().removeFromParent()
        }
    }

    open fun getSubNodeData(): ArrayList<T>? {
        return this.subNodeData?.let { ArrayList(it) }
    }

    fun removeSub(nodeData: T) {
        subNodeData?.remove(nodeData)
        nodeData.asTreeNode().removeFromParent()
    }

    @Suppress("UNCHECKED_CAST")
    fun removeFromParent() {
        if (parentNodeData == null) {
            treeNode?.removeFromParent()
        } else {
            parentNodeData?.asNode()?.removeSub(this as T)
            this.parentNodeData = null
        }
    }

    fun removeAllSub() {
        this.asTreeNode().removeAllChildren()
        this.subNodeData?.clear()
    }

    @Suppress("UNCHECKED_CAST")
    private fun T.asNode(): TreeNodeData<T> {
        return (this as TreeNodeData<T>)
    }

    private fun T.asTreeNode(): DefaultMutableTreeNode {
        return (this as TreeNodeData<*>).asTreeNode()
    }

    @Suppress("UNCHECKED_CAST")
    val rootNodeData: T
        get() {
            return when (parentNodeData) {
                null -> this as T
                else -> (parentNodeData as TreeNodeData<T>).rootNodeData
            }
        }

    open fun asTreeNode(): DefaultMutableTreeNode {
        if (treeNode != null) {
            return treeNode!!
        }
        treeNode = DefaultMutableTreeNode(this)
        return treeNode!!
    }

    abstract val icon: Icon?
    abstract val text: String
    abstract val tooltip: String?
}

abstract class ProjectNodeData : DocContainer, TreeNodeData<ProjectNodeData>() {

    override fun docs(handle: (Request) -> Unit) {
        this.getSubNodeData()?.forEach { it.docs(handle) }
    }

    open fun popupEnable(): Boolean {
        return true
    }

    open fun curlEnable(): Boolean {
        return true
    }

    open fun refreshEnable(): Boolean {
        return true
    }

    open fun resetEnable(): Boolean {
        return false
    }

    abstract fun isNavigatable(): Boolean

    abstract fun navigateToSource()

    abstract fun reset()

    abstract fun exportToYapi(actionContext: ActionContext)

    abstract fun exportToPostman(actionContext: ActionContext)

    abstract fun exportToMarkdown(actionContext: ActionContext)

    abstract fun exportToCurl(actionContext: ActionContext)

    abstract fun exportToHttpClient(actionContext: ActionContext)

    protected open fun resource(): Any? = null
}