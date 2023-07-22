package com.itangcent.idea.plugin.rule

import com.google.inject.Inject
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.itangcent.annotation.script.ScriptReturn
import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.common.text.TemplateEvaluator
import com.itangcent.common.text.TemplateUtils
import com.itangcent.common.text.union
import com.itangcent.idea.plugin.api.export.ExportChannel
import com.itangcent.idea.plugin.utils.LocalStorage
import com.itangcent.idea.plugin.utils.RegexUtils
import com.itangcent.idea.plugin.utils.SessionStorage
import com.itangcent.idea.utils.Charsets
import com.itangcent.idea.utils.FileSaveHelper
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.intellij.adaptor.ModuleAdaptor.filePath
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.rule.RuleContext
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.LinkExtractor
import com.itangcent.intellij.jvm.LinkResolver
import com.itangcent.intellij.psi.ContextSwitchListener
import com.itangcent.intellij.util.FileUtils
import com.itangcent.suv.http.HttpClientProvider
import com.itangcent.utils.TemplateKit
import java.nio.charset.Charset
import javax.script.*
import kotlin.collections.set

abstract class StandardJdkRuleParser : ScriptRuleParser() {

    @Inject
    private lateinit var httpClientProvider: HttpClientProvider

    @Inject
    protected lateinit var localStorage: LocalStorage

    @Inject
    protected lateinit var sessionStorage: SessionStorage

    private var scriptEngine: ScriptEngine? = null

    private var unsupported = false

    protected abstract fun scriptType(): String

    private fun buildScriptEngine(): ScriptEngine? {
        val manager = ScriptEngineManager()
        return manager.getEngineByName(scriptType())
    }

    override fun getScriptEngine(): ScriptEngine {
        if (unsupported) {
            throw UnsupportedScriptException(scriptType())
        }
        if (scriptEngine != null) return scriptEngine!!
        synchronized(this) {
            if (scriptEngine != null) return scriptEngine!!
            scriptEngine = buildScriptEngine()
            initScripEngine(scriptEngine!!)
        }
        if (scriptEngine == null) {
            unsupported = true
            throw UnsupportedScriptException(scriptType())
        }
        return scriptEngine!!
    }

    open fun initScripEngine(scriptEngine: ScriptEngine) {
        scriptEngine.setBindings(SimpleBindings(toolBindings), ScriptContext.GLOBAL_SCOPE)
    }

    override fun initScriptContext(scriptContext: ScriptContext, context: RuleContext) {
        val engineBindings = scriptContext.getBindings(ScriptContext.ENGINE_SCOPE)
        engineBindings.putAll(toolBindings)
        engineBindings.set("logger", "LOG", logger!!)
        engineBindings["localStorage"] = localStorage
        engineBindings.set("session", "S", sessionStorage)
        engineBindings.set("helper", "H", Helper(context.getPsiContext()))
        engineBindings["httpClient"] = httpClientProvider.getHttpClient()
        engineBindings.set("files", "F", actionContext.instance(Files::class))
        engineBindings.set("config", "C", actionContext.instance(Config::class))
        engineBindings.set("runtime", "R", Runtime(context))
    }

    @Inject
    private val linkExtractor: LinkExtractor? = null

    @ScriptTypeName("helper")
    inner class Helper(val context: PsiElement?) {

        fun findClass(canonicalText: String): ScriptPsiTypeContext? {
            return context?.let {
                duckTypeHelper!!.findType(canonicalText, it)?.let { type -> ScriptPsiTypeContext(type) }
            }
        }

        @ScriptReturn("array<class/method/field>")
        fun resolveLinks(canonicalText: String): List<RuleContext>? {
            context ?: return null
            var linkTargets: ArrayList<Any>? = null
            linkExtractor!!.extract(canonicalText, context, object : LinkResolver {
                override fun linkToPsiElement(plainText: String, linkTo: Any?): String? {
                    if (linkTo != null) {
                        if (linkTargets == null) {
                            linkTargets = ArrayList()
                        }
                        linkTargets!!.add(linkTo)
                    }
                    return null
                }
            })
            if (linkTargets.isNullOrEmpty()) {
                return emptyList()
            }
            return linkTargets!!.map { contextOf(it, context) }
        }

        @ScriptReturn("class/method/field")
        fun resolveLink(canonicalText: String): RuleContext? {
            context ?: return null
            var linkTarget: Any? = null
            linkExtractor!!.extract(canonicalText, context, object : LinkResolver {
                override fun linkToPsiElement(plainText: String, linkTo: Any?): String? {
                    if (linkTarget == null && linkTo != null) {
                        linkTarget = linkTo
                    }
                    return null
                }
            })
            return linkTarget?.let { contextOf(it, context) }
        }

    }

    @ScriptTypeName("config")
    class Config {

        @Inject
        private lateinit var configReader: ConfigReader

        fun get(name: String): String? {
            return configReader.first(name)
        }

        @ScriptReturn("array<string>")
        fun getValues(name: String): Collection<String>? {
            return configReader.read(name)
        }

        fun resolveProperty(property: String): String {
            return configReader.resolveProperty(property)
        }

        @Suppress("UNCHECKED_CAST")
        fun resolvePropertyWith(str: String?, placeHolder: Any?, context: Map<*, *>?): String? {
            if (str == null) {
                return null
            }
            val templateEvaluator: TemplateEvaluator = if (context == null) {
                TemplateEvaluator.from { configReader.first(it) }
            } else {
                TemplateEvaluator.from(context as Map<String?, *>)
                    .union(TemplateEvaluator.from { configReader.first(it) })
            }
            return TemplateUtils.render(str)
                .placeholder(TemplateKit.resolvePlaceHolder(placeHolder) ?: charArrayOf('$'))
                .templateEvaluator(templateEvaluator)
                .render()
        }
    }

    @ScriptTypeName("files")
    class Files {

        @Inject
        private val fileSaveHelper: FileSaveHelper? = null

        /**
         * @param content provide file content with file path.
         */
        fun saveWithUI(
            content: (String) -> String,
            defaultFileName: String?,
            onSaveSuccess: () -> Unit,
            onSaveFailed: (String?) -> Unit,
            onSaveCancel: () -> Unit,
        ) {
            saveWithUI(
                content,
                kotlin.text.Charsets.UTF_8,
                defaultFileName,
                onSaveSuccess,
                onSaveFailed,
                onSaveCancel
            )
        }

        /**
         * @param content provide file content with file path.
         */
        fun saveWithUI(
            content: (String) -> String,
            charset: String,
            defaultFileName: String?,
            onSaveSuccess: () -> Unit,
            onSaveFailed: (String?) -> Unit,
            onSaveCancel: () -> Unit,
        ) {
            saveWithUI(
                content,
                Charsets.forName(charset)!!.charset(),
                defaultFileName,
                onSaveSuccess,
                onSaveFailed,
                onSaveCancel
            )

        }

        /**
         * @param content provide file content with file path.
         */
        private fun saveWithUI(
            content: (String) -> String,
            charset: Charset,
            defaultFileName: String?,
            onSaveSuccess: () -> Unit,
            onSaveFailed: (String?) -> Unit,
            onSaveCancel: () -> Unit,
        ) {
            fileSaveHelper!!.saveBytes(
                {
                    content(it).toByteArray(charset)
                },
                {
                    defaultFileName
                },
                onSaveSuccess, onSaveFailed, onSaveCancel
            )
        }

        fun save(content: String, path: String) {
            save(content, kotlin.text.Charsets.UTF_8, path)
        }

        fun save(
            content: String,
            charset: String,
            path: String,
        ) {
            save(content, Charsets.forName(charset)!!.charset(), path)
        }

        private fun save(
            content: String,
            charset: Charset,
            path: String,
        ) {
            FileUtils.forceSave(path, content.toByteArray(charset))
        }

    }

    @ScriptTypeName("runtime")
    class Runtime(private val context: RuleContext) {

        private val actionContext by lazy { ActionContext.getContext() }

        fun channel(): String? {
            return actionContext?.instance(ExportChannel::class)?.channel()
        }

        fun projectName(): String? {
            return actionContext?.instance(Project::class)?.name
        }

        fun projectPath(): String? {
            return actionContext?.instance(Project::class)?.basePath
        }

        fun module(): String? {
            return context.getResource()?.let {
                actionContext?.instance(ModuleHelper::class)?.findModule(it)
            }
        }

        fun moduleName(): String? {
            return actionContext?.instance(ContextSwitchListener::class)
                ?.getModule()?.name
        }

        fun modulePath(): String? {
            return actionContext?.instance(ContextSwitchListener::class)
                ?.getModule()?.filePath()
        }

        fun filePath(): String? {
            return context.getResource()?.containingFile?.virtualFile?.path
        }

        fun getBean(className: String): Any? {
            if (actionContext == null) return null
            if (!className.startsWith("com.itangcent")) {
                throw IllegalArgumentException(
                    "permission denied! " +
                            "runtime.getBean only support com.itangcent.*"
                )
            }
            val cls = try {
                Class.forName(className)
            } catch (e: ClassNotFoundException) {
                throw IllegalArgumentException(
                    "class $className not be found"
                )
            }
            return actionContext?.instance(cls.kotlin)
        }

        fun async(runnable: Runnable) {
            actionContext!!.runAsync(runnable)
        }
    }

    companion object {
        private val toolBindings: Bindings

        init {
            val bindings: Bindings = SimpleBindings()
            bindings.set("tool", "T", RuleToolUtils)
            bindings.set("regex", "RE", RegexUtils)
            toolBindings = bindings
        }

        fun Bindings.set(name: String, alias: String, value: Any) {
            this[name] = value
            this[alias] = value
        }
    }
}