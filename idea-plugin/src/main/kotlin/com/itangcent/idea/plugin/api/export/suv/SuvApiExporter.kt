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
import com.itangcent.common.utils.GsonUtils
import com.itangcent.common.utils.filterAs
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.common.utils.notNullOrEmpty
import com.itangcent.debug.LoggerCollector
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.api.cache.DefaultFileApiCacheRepository
import com.itangcent.idea.plugin.api.cache.FileApiCacheRepository
import com.itangcent.idea.plugin.api.cache.ProjectCacheRepository
import com.itangcent.idea.plugin.api.export.*
import com.itangcent.idea.plugin.api.export.markdown.MarkdownFormatter
import com.itangcent.idea.plugin.api.export.postman.*
import com.itangcent.idea.plugin.config.RecommendConfigReader
import com.itangcent.idea.plugin.dialog.SuvApiExportDialog
import com.itangcent.idea.plugin.rule.SuvRuleParser
import com.itangcent.idea.plugin.script.GroovyActionExtLoader
import com.itangcent.idea.plugin.script.LoggerBuffer
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.psi.PsiResource
import com.itangcent.idea.utils.Charsets
import com.itangcent.idea.utils.CustomizedPsiClassHelper
import com.itangcent.idea.utils.FileSaveHelper
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.rule.RuleParser
import com.itangcent.intellij.constant.EventKey
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.DefaultLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.intellij.jvm.PsiResolver
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.SelectedHelper
import com.itangcent.intellij.tip.TipsHelper
import com.itangcent.intellij.util.FileType
import com.itangcent.intellij.util.UIUtils
import com.itangcent.suv.http.ConfigurableHttpClientProvider
import com.itangcent.suv.http.HttpClientProvider
import org.apache.commons.lang3.exception.ExceptionUtils
import java.util.*
import kotlin.reflect.KClass
import kotlin.reflect.full.createInstance
import kotlin.streams.toList

class SuvApiExporter {

    @Inject
    private val logger: Logger? = null

    @Inject
    private val actionContext: ActionContext? = null

    @Inject
    private val classExporter: ClassExporter? = null

    @Suppress("UNCHECKED_CAST")
    fun showExportWindow() {

        logger!!.info("Start find apis...")

        LoggerCollector.getLog().takeIf { it.isNotBlank() }?.let { logger.debug(it) }

        val docs: MutableList<DocWrapper> = Collections.synchronizedList(ArrayList<DocWrapper>())

        SelectedHelper.Builder()
                .fileFilter { FileType.acceptable(it.name) }
                .classHandle {
                    actionContext!!.checkStatus()
                    classExporter!!.export(it) { doc ->
                        docs.add(DocWrapper(doc))
                    }
                }
                .onCompleted {
                    try {
                        if (classExporter is Worker) {
                            classExporter.waitCompleted()
                        }
                        if (docs.isEmpty()) {
                            logger.info("No api be found!")
                            return@onCompleted
                        }

                        val multipleApiExportDialog = actionContext!!.instance { SuvApiExportDialog() }

                        UIUtils.show(multipleApiExportDialog)

                        actionContext.runInSwingUI {

                            multipleApiExportDialog.setOnChannelChanged { channel ->
                                if (channel == null) {
                                    multipleApiExportDialog.updateRequestList(docs)
                                    return@setOnChannelChanged
                                }
                                val apiExporterAdapter = channel as ApiExporterWrapper
                                multipleApiExportDialog.updateRequestList(docs
                                        .filter { apiExporterAdapter.support(it.docType) }
                                        .toList())
                            }

//                            multipleApiExportDialog.updateRequestList(docs)

                            multipleApiExportDialog.setChannels(EXPORTER_CHANNELS)

                            multipleApiExportDialog.setApisHandle { channel, requests ->
                                doExport(channel as ApiExporterWrapper, requests as List<DocWrapper>)
                            }


                        }
                    } catch (e: Exception) {
                        logger.error("Apis find failed" + ExceptionUtils.getStackTrace(e))
                    }
                }
                .traversal()
    }

    private var customActionExtLoader: ((String, ActionContext.ActionContextBuilder) -> Unit)? = null

    fun setCustomActionExtLoader(customActionExtLoader: (String, ActionContext.ActionContextBuilder) -> Unit) {
        this.customActionExtLoader = customActionExtLoader
    }

    protected fun loadCustomActionExt(actionName: String, builder: ActionContext.ActionContextBuilder) {
        customActionExtLoader?.let { it(actionName, builder) }
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

    abstract class ApiExporterAdapter {

        @Inject(optional = true)
        protected var logger: Logger? = null

        @Inject
        protected val classExporter: ClassExporter? = null

        @Inject
        protected val actionContext: ActionContext? = null

        @Inject
        private val psiResolver: PsiResolver? = null

        private var suvApiExporter: SuvApiExporter? = null

        fun setSuvApiExporter(suvApiExporter: SuvApiExporter) {
            this.suvApiExporter = suvApiExporter
        }

        fun exportApisFromMethod(actionContext: ActionContext, requests: List<DocWrapper>) {

            this.logger = actionContext.instance(Logger::class)

            val actionContextBuilder = ActionContext.builder()
            actionContextBuilder.setParentContext(actionContext)
            actionContextBuilder.bindInstance(Project::class, actionContext.instance(Project::class))
            actionContextBuilder.bindInstance(AnActionEvent::class, actionContext.instance(AnActionEvent::class))
            actionContextBuilder.bindInstance(DataContext::class, actionContext.instance(DataContext::class))

            val resources = requests
                    .stream()
                    .filter { it != null }
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

            actionPerformed(newActionContext)

            newActionContext.hold()
            Thread {
                try {
                    newActionContext.runAsync {
                        try {
                            newActionContext.init(this)
                            beforeExport {
                                newActionContext.runInReadUI {
                                    try {
                                        doExportApisFromMethod(requests)
                                    } catch (e: Exception) {
                                        logger!!.error("error to export apis:" + e.message)
                                        logger!!.traceError(e)
                                    }
                                }
                            }
                        } catch (e: Throwable) {
                            logger!!.error("error to export apis:" + e.message)
                            logger!!.traceError(e)
                        }
                    }
                } catch (e: Throwable) {
                    logger!!.error("error to export apis:" + e.message)
                    logger!!.traceError(e)
                } finally {
                    newActionContext.unHold()
                }
            }.start()

            actionContext.hold()

            newActionContext.on(EventKey.ON_COMPLETED) {
                actionContext.unHold()
            }

            newActionContext.waitCompleteAsync()
        }

        protected open fun actionPerformed(actionContext: ActionContext) {
            val loggerBuffer: LoggerBuffer? = actionContext.getCache<LoggerBuffer>("LOGGER_BUF")
            loggerBuffer?.drainTo(actionContext.instance(Logger::class))
            val actionExtLoader: GroovyActionExtLoader? = actionContext.getCache<GroovyActionExtLoader>("GROOVY_ACTION_EXT_LOADER")
            actionExtLoader?.let { extLoader ->
                actionContext.on(EventKey.ON_COMPLETED) {
                    extLoader.close()
                }
            }
        }

        protected open fun beforeExport(next: () -> Unit) {
            next()
        }

        protected open fun onBuildActionContext(actionContext: ActionContext, builder: ActionContext.ActionContextBuilder) {

            builder.bindInstance("plugin.name", "easy_api")

            builder.inheritFrom(actionContext, SettingBinder::class)

            builder.inheritFrom(actionContext, Logger::class)

            builder.inheritFrom(actionContext, TipsHelper::class)

//            builder.bindInstance(Logger::class, BeanWrapperProxies.wrap(Logger::class, actionContext.instance(Logger::class)))

//            builder.bind(Logger::class) { it.with(ConfigurableLogger::class).singleton() }
//            builder.bind(Logger::class, "delegate.logger") { it.with(ConsoleRunnerLogger::class).singleton() }

            builder.bind(RuleParser::class) { it.with(SuvRuleParser::class).singleton() }
            builder.bind(PsiClassHelper::class) { it.with(CustomizedPsiClassHelper::class).singleton() }

            builder.bind(FileApiCacheRepository::class) { it.with(DefaultFileApiCacheRepository::class).singleton() }
            builder.bind(LocalFileRepository::class, "projectCacheRepository") {
                it.with(ProjectCacheRepository::class).singleton()
            }

            afterBuildActionContext(actionContext, builder)

            suvApiExporter?.loadCustomActionExt(actionName(), builder)
        }

        protected open fun actionName(): String {
            return "Basic"
        }

        protected open fun afterBuildActionContext(actionContext: ActionContext, builder: ActionContext.ActionContextBuilder) {

        }

        private fun doExportApisFromMethod(requestWrappers: List<DocWrapper>) {

            val classes = requestWrappers
                    .stream()
                    .filter { it != null }
                    .map { it.resource }
                    .filter { it is PsiResource }
                    .map { it as PsiResource }
                    .map { it.resourceClass() }
                    .filter { it != null }
                    .distinct()
                    .toList()

            val docs: MutableList<Doc> = ArrayList()
            for (cls in classes) {
                classExporter!!.export(cls!!) { doc ->
                    docs.add(doc)
                }
            }


            actionContext!!.runAsync {

                if (classExporter is Worker) {
                    classExporter.waitCompleted()
                }

                if (docs.isNullOrEmpty()) {
                    logger!!.info("no api has be found")
                }

                doExportDocs(docs)
            }
        }

        abstract fun doExportDocs(docs: MutableList<Doc>)
    }

    class ApiExporterWrapper {
        val adapter: KClass<*>
        val name: String
        private val supportedDocType: Array<KClass<*>>

        constructor(adapter: KClass<*>, name: String, vararg supportedDocTypes: KClass<*>) {
            this.adapter = adapter
            this.name = name
            this.supportedDocType = arrayOf(*supportedDocTypes)
        }

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

        @Inject
        private val postmanApiHelper: PostmanApiHelper? = null

        @Inject
        private val fileSaveHelper: FileSaveHelper? = null

        @Inject
        private val postmanFormatter: PostmanFormatter? = null

        override fun actionName(): String {
            return "PostmanExportAction"
        }

        override fun afterBuildActionContext(actionContext: ActionContext, builder: ActionContext.ActionContextBuilder) {
            super.afterBuildActionContext(actionContext, builder)

            builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }

            builder.bind(PostmanApiHelper::class) { it.with(PostmanCachedApiHelper::class).singleton() }
            builder.bind(HttpClientProvider::class) { it.with(ConfigurableHttpClientProvider::class).singleton() }

            builder.bind(ClassExporter::class) { it.with(PostmanSpringRequestClassExporter::class).singleton() }

            builder.bind(ConfigReader::class, "delegate_config_reader") { it.with(PostmanConfigReader::class).singleton() }
            builder.bind(ConfigReader::class) { it.with(RecommendConfigReader::class).singleton() }

            //always not read api from cache
            builder.bindInstance("class.exporter.read.cache", false)

            builder.bindInstance("file.save.default", "postman.json")
            builder.bindInstance("file.save.last.location.key", "com.itangcent.postman.export.path")

        }

        override fun doExportDocs(docs: MutableList<Doc>) {

            try {
                val postman = postmanFormatter!!.parseRequests(docs.filterAs())
                docs.clear()
                if (postmanApiHelper!!.hasPrivateToken()) {
                    logger!!.info("PrivateToken of postman be found")
                    val createdCollection = postmanApiHelper.createCollection(postman)

                    if (createdCollection.notNullOrEmpty()) {
                        val collectionName = createdCollection!!["name"]?.toString()
                        if (collectionName.notNullOrBlank()) {
                            logger!!.info("Imported as collection:$collectionName")
                            return
                        }
                    }

                    logger!!.error("Export to postman failed,You could check below:" +
                            "1.the network " +
                            "2.the privateToken")

                } else {
                    logger!!.info("PrivateToken of postman not be setting")
                    logger!!.info("To enable automatically import to postman you could set privateToken of postman" +
                            "in \"Preference -> Other Setting -> EasyApi\"")
                    logger!!.info("If you do not have a privateToken of postman, you can easily generate one by heading over to the" +
                            " Postman Integrations Dashboard [https://go.postman.co/integrations/services/pm_pro_api].")
                }
                fileSaveHelper!!.saveOrCopy(GsonUtils.prettyJson(postman), {
                    logger!!.info("Exported data are copied to clipboard,you can paste to postman now")
                }, {
                    logger!!.info("Apis save success: $it")
                }) {
                    logger!!.info("Apis save failed")
                }
            } catch (e: Exception) {
                logger!!.traceError("Apis save failed", e)

            }

        }
    }

    class MarkdownApiExporterAdapter : ApiExporterAdapter() {

        @Inject
        private val fileSaveHelper: FileSaveHelper? = null

        @Inject
        private val markdownFormatter: MarkdownFormatter? = null

        @Inject
        private val settingBinder: SettingBinder? = null

        override fun actionName(): String {
            return "MarkdownExportAction"
        }

        override fun afterBuildActionContext(actionContext: ActionContext, builder: ActionContext.ActionContextBuilder) {
            super.afterBuildActionContext(actionContext, builder)

            builder.bind(LocalFileRepository::class) { it.with(DefaultLocalFileRepository::class).singleton() }

            builder.bind(ClassExporter::class) { it.with(ComboClassExporter::class).singleton() }
            builder.bindInstance("AVAILABLE_CLASS_EXPORTER", arrayOf<Any>(SpringRequestClassExporter::class, DefaultMethodDocClassExporter::class))


            builder.bind(ConfigReader::class, "delegate_config_reader") { it.with(EasyApiConfigReader::class).singleton() }
            builder.bind(ConfigReader::class) { it.with(RecommendConfigReader::class).singleton() }

            //always not read api from cache
            builder.bindInstance("class.exporter.read.cache", false)

            builder.bindInstance("file.save.default", "easy-api.md")
            builder.bindInstance("file.save.last.location.key", "com.itangcent.markdown.export.path")
        }

        override fun doExportDocs(docs: MutableList<Doc>) {
            try {
                if (docs.isEmpty()) {
                    logger!!.info("No api be found to export!")
                    return
                }
                logger!!.info("Start parse apis")
                val apiInfo = markdownFormatter!!.parseRequests(docs.toMutableList())
                docs.clear()
                actionContext!!.runAsync {
                    try {
                        fileSaveHelper!!.saveOrCopy(apiInfo, Charsets.forName(settingBinder!!.read().outputCharset)?.charset()
                                ?: kotlin.text.Charsets.UTF_8, {
                            logger!!.info("Exported data are copied to clipboard,you can paste to a md file now")
                        }, {
                            logger!!.info("Apis save success: $it")
                        }) {
                            logger!!.info("Apis save failed")
                        }
                    } catch (e: Exception) {
                        logger!!.traceError("Apis save failed", e)

                    }
                }
            } catch (e: Exception) {
                logger!!.traceError("Apis save failed", e)

            }
        }
    }

    private fun doExport(channel: ApiExporterWrapper, requests: List<DocWrapper>) {
        if (requests.isNullOrEmpty()) {
            logger!!.info("no api has be selected")
            return
        }
        val adapter = channel.adapter.createInstance() as ApiExporterAdapter
        adapter.setSuvApiExporter(this)
        adapter.exportApisFromMethod(actionContext!!, requests)
    }

    companion object {

        private val EXPORTER_CHANNELS: List<*> = listOf(
                ApiExporterWrapper(PostmanApiExporterAdapter::class, "Postman", Request::class),
                ApiExporterWrapper(MarkdownApiExporterAdapter::class, "Markdown", Request::class, MethodDoc::class)
        )

    }
}