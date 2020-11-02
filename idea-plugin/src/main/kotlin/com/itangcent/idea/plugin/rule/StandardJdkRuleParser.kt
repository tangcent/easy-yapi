package com.itangcent.idea.plugin.rule

import com.google.inject.Inject
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMember
import com.itangcent.annotation.script.ScriptReturn
import com.itangcent.annotation.script.ScriptTypeName
import com.itangcent.idea.plugin.utils.LocalStorage
import com.itangcent.idea.plugin.utils.RegexUtils
import com.itangcent.idea.plugin.utils.SessionStorage
import com.itangcent.idea.utils.Charsets
import com.itangcent.idea.utils.FileSaveHelper
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.rule.RuleContext
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.LinkExtractor
import com.itangcent.intellij.jvm.LinkResolver
import com.itangcent.intellij.util.FileUtils
import com.itangcent.suv.http.HttpClientProvider
import java.nio.charset.Charset
import javax.script.*

abstract class StandardJdkRuleParser : ScriptRuleParser() {

    @Inject
    private var httpClientProvider: HttpClientProvider? = null

    @Inject
    protected val localStorage: LocalStorage? = null

    @Inject
    protected val sessionStorage: SessionStorage? = null

    @Inject
    protected val actionContext: ActionContext? = null

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
        }
        if (scriptEngine == null) {
            unsupported = true
            throw UnsupportedScriptException(scriptType())
        }
        initScripEngine(scriptEngine!!)
        return scriptEngine!!
    }

    open fun initScripEngine(scriptEngine: ScriptEngine) {
        scriptEngine.setBindings(SimpleBindings(toolBindings), ScriptContext.GLOBAL_SCOPE)
    }

    override fun initScriptContext(scriptContext: ScriptContext, context: RuleContext) {
        val engineBindings = scriptContext.getBindings(ScriptContext.ENGINE_SCOPE)
        engineBindings.putAll(toolBindings)
        engineBindings["logger"] = logger
        engineBindings["localStorage"] = localStorage
        engineBindings["session"] = sessionStorage
        engineBindings["helper"] = Helper(context.getPsiContext())
        engineBindings["httpClient"] = httpClientProvider!!.getHttpClient()
        engineBindings["files"] = actionContext!!.instance(Files::class)
        engineBindings["config"] = actionContext.instance(Config::class)
    }

    @Inject
    private val linkExtractor: LinkExtractor? = null

    @ScriptTypeName("helper")
    inner class Helper(val context: PsiElement?) {

        fun findClass(canonicalText: String): ScriptPsiTypeContext? {
            return context?.let { duckTypeHelper!!.findType(canonicalText, it)?.let { type -> ScriptPsiTypeContext(type) } }
        }

        @ScriptReturn("array<class/method/field>")
        fun resolveLinks(canonicalText: String): List<RuleContext>? {
            val psiMember = context as? PsiMember ?: return null
            var linkTargets: ArrayList<Any>? = null
            linkExtractor!!.extract(canonicalText, psiMember, object : LinkResolver {
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
            return linkTargets!!.map { contextOf(it, psiMember) }
        }

        @ScriptReturn("class/method/field")
        fun resolveLink(canonicalText: String): RuleContext? {
            val psiMember = context as? PsiMember ?: return null
            var linkTarget: Any? = null
            linkExtractor!!.extract(canonicalText, psiMember, object : LinkResolver {
                override fun linkToPsiElement(plainText: String, linkTo: Any?): String? {
                    if (linkTarget == null && linkTo != null) {
                        linkTarget = linkTo
                    }
                    return null
                }
            })
            return linkTarget?.let { contextOf(it, psiMember) }
        }

    }

    @ScriptTypeName("config")
    class Config {

        @Inject
        private val configReader: ConfigReader? = null

        fun get(name: String): String? {
            return configReader!!.first(name)
        }

        @ScriptReturn("array<string>")
        fun getValues(name: String): Collection<String>? {
            return configReader!!.read(name)
        }

        fun resolveProperty(property: String): String {
            return configReader!!.resolveProperty(property)
        }
    }

    @ScriptTypeName("files")
    class Files {

        @Inject
        private val fileSaveHelper: FileSaveHelper? = null

        fun saveWithUI(content: (String) -> String,
                       defaultFileName: String?,
                       onSaveSuccess: () -> Unit,
                       onSaveFailed: (String?) -> Unit,
                       onSaveCancel: () -> Unit) {
            saveWithUI(content,
                    kotlin.text.Charsets.UTF_8,
                    defaultFileName,
                    onSaveSuccess,
                    onSaveFailed,
                    onSaveCancel)
        }

        fun saveWithUI(content: (String) -> String,
                       charset: String,
                       defaultFileName: String?,
                       onSaveSuccess: () -> Unit,
                       onSaveFailed: (String?) -> Unit,
                       onSaveCancel: () -> Unit) {
            saveWithUI(content,
                    Charsets.forName(charset)!!.charset(),
                    defaultFileName,
                    onSaveSuccess,
                    onSaveFailed,
                    onSaveCancel)

        }

        private fun saveWithUI(content: (String) -> String,
                               charset: Charset,
                               defaultFileName: String?,
                               onSaveSuccess: () -> Unit,
                               onSaveFailed: (String?) -> Unit,
                               onSaveCancel: () -> Unit) {
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

        fun save(content: String,
                 charset: String,
                 path: String) {
            save(content, Charsets.forName(charset)!!.charset(), path)
        }

        private fun save(content: String,
                         charset: Charset,
                         path: String) {
            FileUtils.forceSave(path, content.toByteArray(charset))
        }

    }

    companion object {
        private val toolBindings: Bindings

        init {
            val bindings: Bindings = SimpleBindings()
            bindings["tool"] = RuleToolUtils()
            bindings["regex"] = RegexUtils()
            toolBindings = bindings
        }
    }
}