package com.itangcent.easyapi.core.rule.parser

import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.itangcent.easyapi.core.internal.threading.IdeDispatchers
import com.itangcent.easyapi.core.internal.threading.backgroundAsync
import com.itangcent.easyapi.core.internal.threading.readSync
import com.itangcent.easyapi.core.http.HttpClientProvider
import com.itangcent.easyapi.core.http.ScriptHttpClient
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.psi.LinkResolver
import com.itangcent.easyapi.core.psi.helper.AnnotatedElementsHelper
import com.itangcent.easyapi.core.psi.helper.SourceHelper
import com.itangcent.easyapi.core.psi.type.JsonType
import com.itangcent.easyapi.core.rule.RuleKey
import com.itangcent.easyapi.core.rule.context.RuleContext
import com.itangcent.easyapi.core.rule.context.ScriptPsiClassContext
import com.itangcent.easyapi.core.rule.context.ScriptPsiFieldContext
import com.itangcent.easyapi.core.rule.context.ScriptPsiMethodContext
import com.itangcent.easyapi.core.rule.context.asScriptIt
import com.itangcent.easyapi.core.util.text.RegexUtils
import com.itangcent.easyapi.core.util.RuleToolUtils
import com.itangcent.easyapi.core.util.ide.ModuleHelper
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

    /**
     * Bounds the number of concurrently executing scripts. Groovy evaluation is
     * a blocking CPU-bound call; without this bound, a concurrent class scan
     * (up to `MAX_CONCURRENT_SCANS`) can spawn one engine per in-flight class
     * and spike CPU. This semaphore keeps script execution — the actual
     * contention point — under a fixed ceiling regardless of how many exporters
     * happen to evaluate rules concurrently.
     */
    private val scriptSemaphore = Semaphore(SCRIPT_CONCURRENCY)

    /**
     * Compiled-script cache keyed by script source. Groovy scripts are compiled
     * once and reused across every class evaluation, avoiding the per-call
     * recompilation that dominates the cost of `groovy:` rules in large scans.
     *
     * Bounded (like [com.itangcent.easyapi.core.export.recognizer.MetaAnnotationResolver])
     * so stale script sources are evicted naturally after rule edits, without an
     * explicit invalidation path.
     */
    private val compiledCache: com.google.common.cache.Cache<String, javax.script.CompiledScript> =
        com.google.common.cache.CacheBuilder.newBuilder()
            .maximumSize(COMPILED_CACHE_MAX_SIZE)
            .expireAfterAccess(COMPILED_CACHE_EXPIRE_MINUTES, java.util.concurrent.TimeUnit.MINUTES)
            .build()

    override fun canParse(expression: String): Boolean = expression.startsWith(prefix)

    /**
     * Drops every cached [javax.script.CompiledScript].
     *
     * Called on configuration reload: rule sources changed, so previously
     * compiled scripts are dead weight. They are more than memory — each
     * `CompiledScript` keeps its Groovy classloader reachable, so an unbounded
     * cache of stale scripts shows up as Metaspace growth. Time-based expiry
     * alone would leave them around for up to [COMPILED_CACHE_EXPIRE_MINUTES].
     */
    fun invalidateCompiledCache() {
        compiledCache.invalidateAll()
    }

    override suspend fun parse(expression: String, context: RuleContext, ruleKey: RuleKey<*>?): Any? {
        val script = expression.removePrefix(prefix)
        if (script.isBlank()) return null
        LOG.info("Jsr223ScriptParser: Starting to parse script (engine=${enginePool.engineName}, script length=${script.length})")
        return withContext(IdeDispatchers.Background) {
            scriptSemaphore.withPermit {
                LOG.info("Jsr223ScriptParser: Running on Background thread=${Thread.currentThread().name}")
                enginePool.withEngine { engine ->
                    val bindings = engine.createBindings()
                    bind(bindings, context)
                    LOG.info("Jsr223ScriptParser: Executing script...")
                    val compiled = compiledScript(engine, script)
                    val result = if (compiled != null) {
                        compiled.eval(bindings)
                    } else {
                        engine.eval(script, bindings)
                    }
                    LOG.info("Jsr223ScriptParser: Script execution completed, result type=${result?.javaClass?.simpleName}")
                    result
                }
            }
        }
    }

    /**
     * Returns a cached [javax.script.CompiledScript] for [script], compiling it
     * via the engine's [javax.script.Compilable] interface on first use.
     *
     * Falls back to returning `null` (compilation unsupported) — the caller
     * then evaluates the raw source directly.
     */
    private fun compiledScript(
        engine: javax.script.ScriptEngine,
        script: String
    ): javax.script.CompiledScript? {
        val compilable = engine as? javax.script.Compilable ?: return null
        return try {
            compiledCache.get(script) { compilable.compile(script) }
        } catch (e: Exception) {
            LOG.warn("Jsr223ScriptParser: failed to compile script (will eval raw source)", e)
            null
        }
    }

    companion object : IdeaLog {
        private const val COMPILED_CACHE_MAX_SIZE: Long = 500
        private const val COMPILED_CACHE_EXPIRE_MINUTES: Long = 10

        /** Upper bound on concurrently executing scripts (see [scriptSemaphore]). */
        private const val SCRIPT_CONCURRENCY = 4
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

        // extensions from rule context — fieldContext strings are auto-wrapped as ScriptFieldPathContext
        context.exts().forEach { (key, value) ->
            bindings[key] = context.wrapExt(key, value)
        }

        // fieldContext fallback — use context.fieldContext if not set via extensions
        if (!bindings.containsKey("fieldContext")) {
            bindings["fieldContext"] = context.wrapExt("fieldContext", context.fieldContext)
        }

        // httpClient — wrapped in ScriptHttpClient so `groovy:` scripts can make
        // sub-requests: `httpClient.newRequest(url).post().form(k, v).execute()`
        // (preferred), or `executeSync(request)` for a pre-built HttpRequest. The
        // suspend HttpClient.execute is not callable from the blocking JSR-223
        // boundary. Scope: bound ONLY here (groovy: rule values +
        // http.call.before/after events), NOT in PmScriptExecutor (postman.* scripts
        // use pm.sendRequest for sub-requests if ever needed).
        val rawHttpClient = runCatching {
            HttpClientProvider.getInstance(context.project).getClient()
        }.onFailure { LOG.warn("Jsr223ScriptParser: failed to get httpClient", it) }
            .getOrNull()
        bindings["httpClient"] = rawHttpClient?.let { ScriptHttpClient(it) }

        // helper + alias
        bindings["helper"] = ScriptHelper(context)
        bindings["H"] = bindings["helper"]

        // runtime + alias
        bindings["runtime"] = ScriptRuntime(context)
        bindings["R"] = bindings["runtime"]
    }
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
class ScriptHelper(private val context: RuleContext) : IdeaLog {

    private val linkResolver: LinkResolver? by lazy {
        LinkResolver.getInstance(context.project)
    }

    /**
     * Converts a JSON type string to a JSON Schema data type string.
     *
     * Mapping:
     * - string/date/datetime/file → "string"
     * - short/int/long → "integer"
     * - float/double → "number"
     * - boolean → "boolean"
     * - array → "array"
     * - object → "object"
     * - null/unknown → "string"
     *
     * ## Usage in Scripts
     * ```
     * helper.jsonTypeToSchemaType("int")    // "integer"
     * helper.jsonTypeToSchemaType("string") // "string"
     * H.jsonTypeToSchemaType("long")        // "integer"
     * ```
     */
    fun jsonTypeToSchemaType(jsonType: String?): String {
        if (jsonType.isNullOrBlank()) return "string"
        return JsonType.toSchemaType(jsonType)
    }

    /**
     * Find a class by its fully qualified name.
     * Returns a script-friendly class context, or null if not found.
     */
    fun findClass(canonicalText: String): Any? {
        val project = context.project
        val sourceHelper = SourceHelper.getInstance(project)
        val psiClass = readSync {
            JavaPsiFacade.getInstance(project)
                .findClass(canonicalText, GlobalSearchScope.allScope(project))
        }?.let { sourceHelper.getSourceClassSync(it) } ?: return null
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

    /**
     * Finds the first class annotated with the given annotation FQN.
     *
     * Singular counterpart of [findClassesByAnnotation]. Returns a
     * [ScriptPsiClassContext] wrapper for the first match, or `null` when no
     * class is annotated or the annotation is not resolvable. Never throws —
     * any error is logged at `warn` and `null` is returned.
     *
     * This is the idiomatic form for document-level annotations (e.g.
     * `@SwaggerDefinition`, `@OpenAPIDefinition`) that live on a single config
     * class — the rule scripts previously wrote
     * `findClassesByAnnotation(...)[0]` with a redundant `isEmpty()` guard,
     * which still paid for the full scan.
     *
     * The actual PSI lookup lives in
     * [AnnotatedElementsHelper.findClassByAnnotation]; this wrapper is a thin
     * context adapter that wraps the raw [com.intellij.psi.PsiClass] result in
     * a [ScriptPsiClassContext] for the `helper` / `H` script binding.
     *
     * ## Usage in Scripts
     * ```
     * // Find the single @SwaggerDefinition config class
     * def cls = helper.findClassByAnnotation("io.swagger.annotations.SwaggerDefinition")
     * if (cls == null) return null
     * return cls.annMap("io.swagger.annotations.SwaggerDefinition")?.info?.title
     *
     * // Short alias
     * def cls = H.findClassByAnnotation("io.swagger.annotations.SwaggerDefinition")
     * ```
     *
     * @param annotationFqn fully-qualified annotation name (e.g.
     *   `"io.swagger.annotations.SwaggerDefinition"`). Simple names are NOT
     *   supported — pass the FQN.
     * @return a [ScriptPsiClassContext] for the first match, or `null`.
     * @see findClassesByAnnotation for the all-matches variant
     */
    fun findClassByAnnotation(annotationFqn: String): Any? {
        if (annotationFqn.isBlank()) return null
        val hit = AnnotatedElementsHelper.getInstance(context.project)
            .findClassByAnnotation(annotationFqn) ?: return null
        return ScriptPsiClassContext(context.withElement(hit))
    }

    /**
     * Finds all classes annotated with the given annotation FQN.
     *
     * Returns a list of [ScriptPsiClassContext] wrappers (empty list if no
     * matches or the annotation is not resolvable). Never throws — any error
     * is logged at `warn` and an empty list is returned.
     *
     * When only the first match is needed, prefer [findClassByAnnotation],
     * which stops the search early and returns `null` instead of an empty list.
     *
     * The actual PSI lookup was
     * extracted to [AnnotatedElementsHelper] so it can be reused by non-script
     * callers and unit-tested in isolation (see
     * `AnnotatedElementsHelperTest`). This wrapper is now a thin context
     * adapter — it delegates the lookup to the helper and wraps each raw
     * [com.intellij.psi.PsiClass] result in a [ScriptPsiClassContext] for the
     * `helper` / `H` script binding.
     *
     * ## Usage in Scripts
     * ```
     * // Find all classes annotated with @Service
     * def services = helper.findClassesByAnnotation("org.springframework.stereotype.Service")
     *
     * // Short alias
     * def services = H.findClassesByAnnotation("org.springframework.stereotype.Service")
     * ```
     *
     * Added for the `springfox-openapi.config`
     * Springfox `Docket` extractor and any rule script that needs to locate
     * annotated code.
     *
     * @param annotationFqn fully-qualified annotation name (e.g.
     *   `"org.springframework.context.annotation.Bean"`). Simple names are NOT
     *   supported — pass the FQN.
     */
    fun findClassesByAnnotation(annotationFqn: String): List<Any> {
        if (annotationFqn.isBlank()) return emptyList()
        val hits = AnnotatedElementsHelper.getInstance(context.project)
            .findClassesByAnnotation(annotationFqn)
        return hits.map { ScriptPsiClassContext(context.withElement(it)) }
    }

    /**
     * Finds all methods annotated with the given annotation FQN.
     *
     * Returns a list of [ScriptPsiMethodContext] wrappers (empty list if no
     * matches or the annotation is not resolvable). Never throws — any error
     * is logged at `warn` and an empty list is returned.
     *
     * The actual PSI lookup was
     * extracted to [AnnotatedElementsHelper] (see `findClassesByAnnotation`
     * above). This wrapper delegates the lookup to the helper and wraps each
     * raw [com.intellij.psi.PsiMethod] result in a [ScriptPsiMethodContext]
     * for the `helper` / `H` script binding.
     *
     * ## Usage in Scripts
     * ```
     * // Find all @Bean methods (Springfox Docket beans)
     * def beanMethods = helper.findMethodsByAnnotation("org.springframework.context.annotation.Bean")
     *
     * // Short alias
     * def beanMethods = H.findMethodsByAnnotation("org.springframework.context.annotation.Bean")
     * ```
     *
     * Added for the `springfox-openapi.config`
     * Springfox `Docket` extractor (locates `@Bean` methods returning
     * `springfox.documentation.spring.web.plugins.Docket`).
     *
     * @param annotationFqn fully-qualified annotation name (e.g.
     *   `"org.springframework.context.annotation.Bean"`). Simple names are NOT
     *   supported — pass the FQN.
     */
    fun findMethodsByAnnotation(annotationFqn: String): List<Any> {
        if (annotationFqn.isBlank()) return emptyList()
        val hits = AnnotatedElementsHelper.getInstance(context.project)
            .findMethodsByAnnotation(annotationFqn)
        return hits.map { ScriptPsiMethodContext(context.withElement(it)) }
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
