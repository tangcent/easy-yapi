package com.itangcent.easyapi.rule.parser

import com.itangcent.easyapi.core.threading.IdeDispatchers
import com.itangcent.easyapi.core.threading.backgroundAsync
import com.itangcent.easyapi.core.threading.readSync
import com.itangcent.easyapi.http.HttpClientProvider
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.rule.RuleKey
import com.itangcent.easyapi.rule.context.*
import com.itangcent.easyapi.util.RegexUtils
import com.itangcent.easyapi.util.RuleToolUtils
import com.itangcent.easyapi.util.ide.ModuleHelper
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import javax.script.Bindings

/**
 * Base class for JSR-223 script-based rule parsers.
 *
 * Provides script execution capabilities for rule evaluation using
 * the javax.script API. Supports Groovy through JSR-223 compatible engines.
 *
 * ## Supported Languages
 * - **Groovy** (`groovy:` prefix) - Uses Groovy engine
 *
 * ## Script Context
 * Scripts have access to:
 * - `it` - The current element context
 * - `logger` / `LOG` - Logging utilities
 * - `session` / `S` - Session storage
 * - `tool` / `T` - Utility functions ([RuleToolUtils])
 * - `regex` / `RE` - Regex utilities ([RegexUtils])
 * - `files` / `F` - File operations
 * - `config` / `C` - Configuration access
 * - `helper` / `H` - Class lookup utilities
 * - `runtime` / `R` - Project/module metadata
 * - `httpClient` - HTTP client for API calls
 *
 * ## Usage
 * ```
 * # Groovy example
 * groovy: it.ann("org.springframework.web.bind.annotation.RequestMapping")?.path()
 * ```
 *
 * @param prefix The expression prefix (e.g., "groovy:")
 * @param engineName The JSR-223 engine name
 * @see RuleParser for the interface
 */
abstract class Jsr223ScriptParser(
    private val prefix: String,
    engineName: String
) : RuleParser {

    private val enginePool = EnginePool(engineName)

    override fun canParse(expression: String): Boolean = expression.startsWith(prefix)

    override suspend fun parse(expression: String, context: RuleContext, ruleKey: RuleKey<*>?): Any? {
        val script = expression.removePrefix(prefix)
        if (script.isBlank()) return null
        LOG.debug("Jsr223ScriptParser: Starting to parse script (engine=${enginePool.engineName}, script length=${script.length})")
        return withContext(IdeDispatchers.Background) {
            LOG.debug("Jsr223ScriptParser: Running on Background thread=${Thread.currentThread().name}")
            enginePool.withEngine { engine ->
                val bindings = engine.createBindings()
                bind(bindings, context)
                LOG.debug("Jsr223ScriptParser: Executing script...")
                engine.eval(script, bindings).also { result ->
                    LOG.debug("Jsr223ScriptParser: Script execution completed, result type=${result?.javaClass?.simpleName}")
                }
            }
        }
    }

    private fun bind(bindings: Bindings, context: RuleContext) {
        bindings["it"] = context.asScriptIt()

        // logger + alias
        bindings["logger"] = context.console
        bindings["LOG"] = context.console

        // session + alias (wrapped to match legacy Storage API: get/set/pop/push/peek with groups)
        val sessionWrapper = ScriptStorageWrapper(context.session)
        bindings["session"] = sessionWrapper
        bindings["S"] = sessionWrapper
        bindings["sessionStorage"] = sessionWrapper

        // tool + alias
        bindings["tool"] = RuleToolUtils
        bindings["T"] = RuleToolUtils

        // regex + alias
        bindings["regex"] = RegexUtils
        bindings["RE"] = RegexUtils

        // files + alias (wrapped to match legacy Files API: save, saveWithUI)
        bindings["files"] = ScriptFilesWrapper
        bindings["F"] = ScriptFilesWrapper

        // config + alias (wrapped to match legacy Config API: get, getValues, resolveProperty)
        val configWrapper = ScriptConfigWrapper(context.config)
        bindings["config"] = configWrapper
        bindings["C"] = configWrapper

        // localStorage (wrapped to match legacy Storage API)
        bindings["localStorage"] = ScriptStorageWrapper(context.localStorage)

        // extensions from rule context — fieldContext strings are auto-wrapped as ScriptFieldContext
        context.exts().forEach { (key, value) ->
            bindings[key] = context.wrapExt(key, value)
        }

        // fieldContext fallback — use context.fieldContext if not set via extensions
        if (!bindings.containsKey("fieldContext")) {
            bindings["fieldContext"] = context.wrapExt("fieldContext", context.fieldContext)
        }

        // httpClient
        val httpClient = runCatching {
            HttpClientProvider.getInstance(context.project).getClient()
        }.getOrNull()
        bindings["httpClient"] = httpClient

        // helper + alias
        bindings["helper"] = ScriptHelper(context)
        bindings["H"] = bindings["helper"]

        // runtime + alias
        bindings["runtime"] = ScriptRuntime(context)
        bindings["R"] = bindings["runtime"]
    }

    companion object : IdeaLog
}

/**
 * Script helper providing class lookup utilities.
 *
 * Mirrors the legacy `StandardJdkRuleParser.Helper` class.
 * Provides `findClass` and link resolution utilities to scripts.
 *
 * ## Usage in Scripts
 * ```
 * // Find a class by name
 * helper.findClass("com.example.User")
 *
 * // Short alias
 * H.findClass("java.lang.String")
 * ```
 *
 * @param context The rule context
 */
class ScriptHelper(private val context: RuleContext) {

    private val linkResolver: com.itangcent.easyapi.psi.LinkResolver? by lazy {
        com.itangcent.easyapi.psi.LinkResolver.getInstance(context.project)
    }

    /**
     * Find a class by its fully qualified name.
     * Returns a script-friendly class context, or null if not found.
     */
    fun findClass(canonicalText: String): Any? {
        val project = context.project
        val psiClass = readSync {
            com.intellij.psi.JavaPsiFacade.getInstance(project)
                .findClass(canonicalText, com.intellij.psi.search.GlobalSearchScope.allScope(project))
        } ?: return null
        return ScriptPsiClassContext(context.withElement(psiClass))
    }

    fun resolveLink(canonicalText: String): Any? = readSync {
        val element = context.element ?: return@readSync null
        val resolver = linkResolver ?: return@readSync null
        val resolved = resolver.resolveLink(canonicalText, element) ?: return@readSync null
        when (resolved) {
            is com.intellij.psi.PsiClass -> ScriptPsiClassContext(
                context.withElement(resolved)
            )

            is com.intellij.psi.PsiMethod -> ScriptPsiMethodContext(
                context.withElement(resolved)
            )

            is com.intellij.psi.PsiField -> ScriptPsiFieldContext(
                context.withElement(resolved)
            )

            else -> null
        }
    }

    fun resolveLinks(canonicalText: String): List<Any> = readSync {
        val element = context.element ?: return@readSync emptyList()
        val resolver = linkResolver ?: return@readSync emptyList()
        val resolved = resolver.resolveAllLinks(canonicalText, element)
        resolved.mapNotNull { target ->
            when (target) {
                is com.intellij.psi.PsiClass -> ScriptPsiClassContext(
                    context.withElement(target)
                )

                is com.intellij.psi.PsiMethod -> ScriptPsiMethodContext(
                    context.withElement(target)
                )

                is com.intellij.psi.PsiField -> ScriptPsiFieldContext(
                    context.withElement(target)
                )

                else -> null
            }
        }
    }
}

/**
 * Script runtime providing project/module metadata.
 *
 * Mirrors the legacy `StandardJdkRuleParser.Runtime` class.
 * Provides project/module metadata to scripts.
 *
 * ## Usage in Scripts
 * ```
 * // Get project name
 * runtime.projectName()
 *
 * // Get module name
 * runtime.moduleName()
 *
 * // Get file path
 * runtime.filePath()
 *
 * // Short aliases
 * R.projectName()
 * R.module()
 * ```
 *
 * @param context The rule context
 */
class ScriptRuntime(private val context: RuleContext) {

    fun projectName(): String? {
        return context.project.name
    }

    fun projectPath(): String? {
        return context.project.basePath
    }

    fun module(): String? {
        return context.element?.let {
            runBlocking { ModuleHelper.resolveModuleName(it) }
        }
    }

    fun moduleName(): String? = module()

    fun modulePath(): String? {
        return context.element?.let {
            runBlocking { ModuleHelper.resolveModulePath(it) }
        }
    }

    fun filePath(): String? = readSync {
        context.element?.containingFile?.virtualFile?.path
    }

    fun async(runnable: Runnable) {
        backgroundAsync { runnable.run() }
    }
}

/**
 * Groovy-based rule parser using the JSR-223 Groovy engine.
 *
 * Parses expressions prefixed with `groovy:`.
 *
 * ## Example
 * ```
 * groovy: it.ann("org.springframework.web.bind.annotation.RequestMapping")?.path()
 * ```
 */
class GroovyScriptParser : Jsr223ScriptParser(prefix = "groovy:", engineName = "groovy")
