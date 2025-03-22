package com.itangcent.idea.plugin.api.export.suv

import com.google.inject.Inject
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiMethod
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.Doc
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.model.Request
import com.itangcent.common.utils.filterAs
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.debug.LoggerCollector
import com.itangcent.idea.config.CachedResourceResolver
import com.itangcent.idea.plugin.api.ClassApiExporterHelper
import com.itangcent.idea.plugin.api.cache.DefaultFileApiCacheRepository
import com.itangcent.idea.plugin.api.cache.FileApiCacheRepository
import com.itangcent.idea.plugin.api.export.ExportChannel
import com.itangcent.idea.plugin.api.export.ExportDoc
import com.itangcent.idea.plugin.api.export.core.*
import com.itangcent.idea.plugin.api.export.curl.CurlExporter
import com.itangcent.idea.plugin.api.export.http.HttpClientExporter
import com.itangcent.idea.plugin.api.export.markdown.MarkdownFormatter
import com.itangcent.idea.plugin.api.export.postman.PostmanApiExporter
import com.itangcent.idea.plugin.api.export.postman.PostmanApiHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanCachedApiHelper
import com.itangcent.idea.plugin.api.export.postman.PostmanFormatFolderHelper
import com.itangcent.idea.plugin.api.export.yapi.*
import com.itangcent.idea.plugin.config.EnhancedConfigReader
import com.itangcent.idea.plugin.dialog.SuvApiExportDialog
import com.itangcent.idea.plugin.rule.SuvRuleParser
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.settings.helper.IntelligentSettingsHelper
import com.itangcent.idea.plugin.settings.helper.MarkdownSettingsHelper
import com.itangcent.idea.plugin.settings.helper.YapiSettingsHelper
import com.itangcent.idea.plugin.settings.helper.YapiTokenChecker
import com.itangcent.idea.psi.PsiResource
import com.itangcent.idea.utils.CustomizedPsiClassHelper
import com.itangcent.idea.utils.FileSaveHelper
import com.itangcent.idea.utils.RuleComputeListenerRegistry
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.resource.ResourceResolver
import com.itangcent.intellij.config.rule.RuleComputeListener
import com.itangcent.intellij.config.rule.RuleParser
import com.itangcent.intellij.constant.EventKey
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.findCurrentMethod
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.extend.logger
import com.itangcent.intellij.extend.withBoundary
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.tip.TipsHelper
import com.itangcent.intellij.util.UIUtils
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance

open class SuvApiExporter {

    @Inject
    private lateinit var logger: Logger

    @Inject
    private lateinit var actionContext: ActionContext

    @Inject
    private lateinit var classApiExporterHelper: ClassApiExporterHelper

    @Inject
    private lateinit var intelligentSettingsHelper: IntelligentSettingsHelper

    @Suppress("UNCHECKED_CAST")
    fun showExportWindow() {
        LoggerCollector.getLog().takeIf { it.isNotBlank() }?.let { logger.debug(it) }

        try {
            val docs = classApiExporterHelper.export().map { DocWrapper(it) }

            if (docs.isEmpty()) {
                logger.info("No API found in the selected files")
                return
            }

            actionContext.runInSwingUI {

                val multipleApiExportDialog = actionContext.instance { SuvApiExportDialog() }

                UIUtils.show(multipleApiExportDialog)

                multipleApiExportDialog.setOnChannelChanged { channel ->
                    if (channel == null) {
                        multipleApiExportDialog.updateRequestListToUI(docs)
                        return@setOnChannelChanged
                    }
                    val apiExporterAdapter = channel as ApiExporterWrapper
                    multipleApiExportDialog.updateRequestListToUI(
                        docs
                            .filter { apiExporterAdapter.support(it.docType) }
                            .toList())
                }

                multipleApiExportDialog.setChannels(EXPORTER_CHANNELS)

                multipleApiExportDialog.setApisHandle { channel, requests ->
                    doExport(channel as ApiExporterWrapper, requests as List<DocWrapper>)
                }
            }
        } catch (e: Exception) {
            logger.traceError("Apis exported failed", e)
        }
    }

    private fun SuvApiExportDialog.updateRequestListToUI(docs: List<DocWrapper>) {
        this.updateRequestList(docs)
        if (intelligentSettingsHelper.selectedOnly()) {
            val currentMethod = actionContext.findCurrentMethod()
            if (currentMethod != null) {
                docs.firstOrNull { it.resourceMethod() == currentMethod }
                    ?.let {
                        this.selectMethod(it)
                        return
                    }
            }
        }
        this.selectAll()
    }

    class DocWrapper {

        var resource: Any?
        var name: String?
        var docType: KClass<*>

        constructor(doc: Doc) {
            this.resource = doc.resource
            this.name = doc.name
            this.docType = doc::class
        }

        constructor(resource: Any?, name: String?, docType: KClass<*>) {
            this.resource = resource
            this.name = name
            this.docType = docType
        }

        override fun toString(): String {
            return name ?: ""
        }
    }

    private fun DocWrapper.resourceMethod(): PsiMethod? {
        return (this.resource as? PsiResource)?.resource() as? PsiMethod
    }

    abstract class ApiExporterAdapter {

        @Inject
        protected lateinit var logger: Logger

        @Inject
        protected lateinit var classExporter: ClassExporter

        @Inject
        protected lateinit var actionContext: ActionContext

        private var suvApiExporter: SuvApiExporter? = null

        fun setSuvApiExporter(suvApiExporter: SuvApiExporter) {
            this.suvApiExporter = suvApiExporter
        }

        fun exportApisFromMethod(actionContext: ActionContext, requests: List<DocWrapper>) {

            this.logger = actionContext.logger()

            val actionContextBuilder = ActionContext.builder()
            actionContextBuilder.setParentContext(actionContext)
            actionContextBuilder.bindInstance(Project::class, actionContext.instance(Project::class))
            actionContextBuilder.bindInstance(AnActionEvent::class, actionContext.instance(AnActionEvent::class))
            actionContextBuilder.bindInstance(DataContext::class, actionContext.instance(DataContext::class))

            val resources = requests
                .asSequence()
                .map { it.resource }
                .filter { it != null }
                .map { it as PsiResource }
                .map { it.resource() }
                .filter { it is PsiMethod }
                .map { it as PsiMethod }
                .toList()

            actionContextBuilder.bindInstance(MethodFilter::class, ExplicitMethodFilter(resources))

            onBuildActionContext(actionContext, actionContextBuilder)

            val newActionContext = actionContextBuilder.build()

            newActionContext.runAsync {
                try {
                    newActionContext.init(this)
                    beforeExport {
                        newActionContext.runInReadUI {
                            try {
                                doExportApisFromMethod(requests)
                            } catch (e: Exception) {
                                logger.traceError("Failed to export APIs: " + e.message, e)
                            }
                        }
                    }
                } catch (e: Throwable) {
                    logger.traceError("Failed to export APIs: " + e.message, e)
                }
            }

            actionContext.hold()

            newActionContext.on(EventKey.ON_COMPLETED) {
                actionContext.unHold()
            }

            newActionContext.waitCompleteAsync()
        }

        protected open fun beforeExport(next: () -> Unit) {
            next()
        }

        protected open fun onBuildActionContext(
            actionContext: ActionContext,
            builder: ActionContextBuilder,
        ) {

            builder.inheritFrom(actionContext, SettingBinder::class)

            builder.inheritFrom(actionContext, Logger::class)

            builder.inheritFrom(actionContext, TipsHelper::class)

//            builder.bindInstance(Logger::class, BeanWrapperProxies.wrap(Logger::class, actionContext.logger()))

//            builder.bind(Logger::class) { it.with(ConfigurableLogger::class).singleton() }
//            builder.bind(Logger::class, "delegate.logger") { it.with(IdeaConsoleLogger::class).singleton() }

            builder.bind(RuleParser::class) { it.with(SuvRuleParser::class).singleton() }
            builder.bind(RuleComputeListener::class) { it.with(RuleComputeListenerRegistry::class).singleton() }
            builder.bind(PsiClassHelper::class) { it.with(CustomizedPsiClassHelper::class).singleton() }

            builder.bind(ResourceResolver::class) { it.with(CachedResourceResolver::class).singleton() }
            builder.bind(FileApiCacheRepository::class) { it.with(DefaultFileApiCacheRepository::class).singleton() }

            builder.bind(ConfigReader::class) { it.with(EnhancedConfigReader::class).singleton() }

            afterBuildActionContext(actionContext, builder)
        }

        protected open fun actionName(): String {
            return "Basic"
        }

        protected open fun afterBuildActionContext(
            actionContext: ActionContext,
            builder: ActionContextBuilder,
        ) {

        }

        private fun doExportApisFromMethod(requestWrappers: List<DocWrapper>) {

            val classes = requestWrappers
                .asSequence()
                .map { it.resource }
                .filter { it is PsiResource }
                .map { it as PsiResource }
                .map { it.resourceClass() }
                .filter { it != null }
                .distinct()
                .toList()


            actionContext.runAsync {
                val docs: MutableList<Doc> = Collections.synchronizedList(ArrayList())

                actionContext.withBoundary {
                    for (cls in classes) {
                        classExporter.export(cls!!) { doc ->
                            docs.add(doc)
                        }
                    }
                }

                if (docs.isEmpty()) {
                    logger.info("No APIs found")
                }

                doExportDocs(docs)
            }
        }

        abstract fun doExportDocs(docs: MutableList<Doc>)
    }

    class ApiExporterWrapper(val adapter: KClass<*>, val name: String, vararg supportedDocTypes: KClass<*>) {
        private val supportedDocType: Array<KClass<*>> = arrayOf(*supportedDocTypes)

        fun support(docType: KClass<*>): Boolean {
            return this.supportedDocType.contains(docType)
        }

        override fun toString(): String {
            return name
        }
    }

    class ExplicitMethodFilter(private var methods: List<PsiMethod>) : MethodFilter {

        override fun checkMethod(method: PsiMethod): Boolean {
            return this.methods.contains(method)
        }
    }

    class PostmanApiExporterAdapter : ApiExporterAdapter() {
        override fun actionName(): String {
            return "PostmanExportAction"
        }

        override fun afterBuildActionContext(
            actionContext: ActionContext,
            builder: ActionContextBuilder,
        ) {
            super.afterBuildActionContext(actionContext, builder)

            builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }

            builder.bind(PostmanApiHelper::class) { it.with(PostmanCachedApiHelper::class).singleton() }

            builder.bind(FormatFolderHelper::class) { it.with(PostmanFormatFolderHelper::class).singleton() }

            builder.bindInstance(ExportChannel::class, ExportChannel.of("postman"))
            builder.bindInstance(ExportDoc::class, ExportDoc.of("request"))

            builder.bindInstance("file.save.default", "postman.json")
            builder.bindInstance("file.save.last.location.key", "com.itangcent.postman.export.path")

        }

        override fun doExportDocs(docs: MutableList<Doc>) {
            actionContext.instance(PostmanApiExporter::class)
                .export(docs.filterAs())
        }
    }

    class YapiApiExporterAdapter : ApiExporterAdapter() {

        @Inject
        protected lateinit var yapiSettingsHelper: YapiSettingsHelper

        override fun actionName(): String {
            return "YapiExportAction"
        }

        override fun afterBuildActionContext(
            actionContext: ActionContext,
            builder: ActionContextBuilder,
        ) {
            super.afterBuildActionContext(actionContext, builder)

            builder.inheritFrom(actionContext, Project::class)

            builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }

            builder.bind(YapiApiHelper::class) { it.with(CachedYapiApiHelper::class).singleton() }

            builder.bind(LinkResolver::class) { it.with(YapiLinkResolver::class).singleton() }

            builder.bindInstance(ExportChannel::class, ExportChannel.of("yapi"))
            builder.bindInstance(ExportDoc::class, ExportDoc.of("request", "methodDoc"))

            builder.bindInstance("file.save.default", "api.json")
            builder.bindInstance("file.save.last.location.key", "com.itangcent.api.export.path")

            builder.bind(PsiClassHelper::class) { it.with(YapiPsiClassHelper::class).singleton() }

            builder.bind(YapiTokenChecker::class) { it.with(YapiTokenCheckerSupport::class).singleton() }

            builder.bind(AdditionalParseHelper::class) { it.with(YapiAdditionalParseHelper::class).singleton() }
        }

        override fun beforeExport(next: () -> Unit) {
            val serverFound = yapiSettingsHelper.getServer(false).notNullOrBlank()
            if (serverFound) {
                next()
            }
        }

        override fun doExportDocs(docs: MutableList<Doc>) {
            val suvYapiApiExporter = actionContext.init(SuvYapiApiExporter())

            try {
                docs.forEach { suvYapiApiExporter.exportDoc(it) }
            } catch (e: Exception) {
                logger.error("Failed to export APIs to YAPI")
                logger.traceError(e)
            }
        }

        class SuvYapiApiExporter : AbstractYapiApiExporter() {

            //privateToken+folderName -> CartInfo
            private val folderNameCartMap: HashMap<String, CartInfo> = HashMap()

            @Synchronized
            override fun getCartForFolder(folder: Folder, privateToken: String): CartInfo? {
                var cartInfo = folderNameCartMap["$privateToken${folder.name}"]
                if (cartInfo != null) return cartInfo

                cartInfo = super.getCartForFolder(folder, privateToken)
                if (cartInfo != null) {
                    folderNameCartMap["$privateToken${folder.name}"] = cartInfo
                }
                return cartInfo
            }

            private val successExportedCarts: MutableSet<String> = HashSet()

            override fun exportDoc(doc: Doc, privateToken: String, cartId: String): Boolean {
                if (super.exportDoc(doc, privateToken, cartId)) {
                    synchronized(successExportedCarts) {
                        if (successExportedCarts.add(cartId)) {
                            logger.info(
                                "Export to ${
                                    yapiApiHelper.getCartWeb(
                                        yapiApiHelper.getProjectIdByToken(
                                            privateToken
                                        )!!, cartId
                                    )
                                } success"
                            )
                        }
                    }
                    return true
                }
                return false
            }
        }
    }

    class MarkdownApiExporterAdapter : ApiExporterAdapter() {

        @Inject
        private lateinit var fileSaveHelper: FileSaveHelper

        @Inject
        private lateinit var markdownFormatter: MarkdownFormatter

        @Inject
        private lateinit var markdownSettingsHelper: MarkdownSettingsHelper

        override fun actionName(): String {
            return "MarkdownExportAction"
        }

        override fun afterBuildActionContext(
            actionContext: ActionContext,
            builder: ActionContextBuilder,
        ) {
            super.afterBuildActionContext(actionContext, builder)

            builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }

            builder.bindInstance(ExportChannel::class, ExportChannel.of("markdown"))
            builder.bindInstance(ExportDoc::class, ExportDoc.of("request", "methodDoc"))

            builder.bindInstance("file.save.default", "easy-api.md")
            builder.bindInstance("file.save.last.location.key", "com.itangcent.markdown.export.path")
        }

        override fun doExportDocs(docs: MutableList<Doc>) {
            try {
                if (docs.isEmpty()) {
                    logger.info("No API found in the selected scope")
                    return
                }
                logger.info("Start parse apis")
                val apiInfo = markdownFormatter.parseDocs(docs)
                docs.clear()
                actionContext.runAsync {
                    try {
                        fileSaveHelper.saveOrCopy(apiInfo, markdownSettingsHelper.outputCharset(), {
                            logger.info("Exported data are copied to clipboard,you can paste to a md file now")
                        }, {
                            logger.info("Apis save success: $it")
                        }) {
                            logger.info("Apis save failed")
                        }
                    } catch (e: Exception) {
                        logger.traceError("Apis save failed", e)

                    }
                }
            } catch (e: Exception) {
                logger.traceError("Apis save failed", e)

            }
        }
    }

    class CurlApiExporterAdapter : ApiExporterAdapter() {

        @Inject
        private lateinit var curlExporter: CurlExporter

        override fun actionName(): String {
            return "CurlExportAction"
        }

        override fun afterBuildActionContext(
            actionContext: ActionContext,
            builder: ActionContextBuilder,
        ) {
            super.afterBuildActionContext(actionContext, builder)

            builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }

            builder.bindInstance(ExportDoc::class, ExportDoc.of("request"))

            builder.bindInstance("file.save.default", "easy-api-curl.md")
            builder.bindInstance("file.save.last.location.key", "com.itangcent.curl.export.path")
        }

        override fun doExportDocs(docs: MutableList<Doc>) {
            val requests = docs.filterAs(Request::class)
            try {
                if (docs.isEmpty()) {
                    logger.info("No API found in the selected scope")
                    return
                }
                curlExporter.export(requests)
            } catch (e: Exception) {
                logger.traceError("Apis save failed", e)
            }
        }
    }

    class HttpClientApiExporterAdapter : ApiExporterAdapter() {

        @Inject
        private lateinit var httpClientExporter: HttpClientExporter

        override fun actionName(): String {
            return "HttpClientExportAction"
        }

        override fun afterBuildActionContext(
            actionContext: ActionContext,
            builder: ActionContextBuilder,
        ) {
            super.afterBuildActionContext(actionContext, builder)

            builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }

            builder.bindInstance(ExportDoc::class, ExportDoc.of("request"))

            builder.bindInstance("file.save.default", "easy-api-httpClient.http")
            builder.bindInstance("file.save.last.location.key", "com.itangcent.httpClient.export.path")
        }

        override fun doExportDocs(docs: MutableList<Doc>) {
            val requests = docs.filterAs(Request::class)
            try {
                if (docs.isEmpty()) {
                    logger.info("No API found in the selected scope")
                    return
                }
                httpClientExporter.export(requests)
            } catch (e: Exception) {
                logger.traceError("Apis save failed", e)
            }
        }
    }

    private fun doExport(channel: ApiExporterWrapper, requests: List<DocWrapper>) {
        if (requests.isEmpty()) {
            logger.info("No API found in the selected scope")
            return
        }
        val adapter = channel.adapter.createInstance() as ApiExporterAdapter
        adapter.setSuvApiExporter(this)
        adapter.exportApisFromMethod(actionContext, requests)
    }

    companion object {

        private val EXPORTER_CHANNELS: List<*> = listOf(
            ApiExporterWrapper(YapiApiExporterAdapter::class, "Yapi", Request::class, MethodDoc::class),
            ApiExporterWrapper(PostmanApiExporterAdapter::class, "Postman", Request::class),
            ApiExporterWrapper(MarkdownApiExporterAdapter::class, "Markdown", Request::class, MethodDoc::class),
            ApiExporterWrapper(CurlApiExporterAdapter::class, "Curl", Request::class),
            ApiExporterWrapper(HttpClientApiExporterAdapter::class, "HttpClient", Request::class)
        )
    }
}