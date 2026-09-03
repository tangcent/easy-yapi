package com.itangcent.easyapi.core.rule.context

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.channel.spi.CurlRenderer
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.HttpMetadata
import com.itangcent.easyapi.core.export.HttpMethod

/**
 * Script-facing wrapper around [ApiEndpoint], exposed to rule scripts as `it` / `api`.
 *
 * Constructed by [RuleContext.wrapExt] (single construction site). The optional
 * [project] is carried so [toCurl] can read cURL settings and run pre-scripts
 * via the [CurlRenderer] SPI; rules that don't call [toCurl] pay no
 * Project-coupling cost.
 *
 * @param endpoint The underlying endpoint. Rule mutations (e.g. [setPath],
 *  [appendDesc]) write through to this instance.
 * @param project The current IntelliJ project, or null when constructed outside
 *  a rule context (e.g. in unit tests). Used only by [toCurl].
 */
class ScriptApiEndpoint(
    val endpoint: ApiEndpoint,
    private val project: Project? = null,
) {

    private val http: HttpMetadata? get() = endpoint.metadata as? HttpMetadata

    fun name(): String? = endpoint.name

    fun path(): String? = http?.path

    fun setPath(path: String) {
        val meta = http ?: return
        meta.path = path
    }

    fun method(): String? = http?.method?.name

    fun setMethod(method: String) {
        val meta = http ?: return
        HttpMethod.values().find { it.name.equals(method, ignoreCase = true) }?.let {
            meta.method = it
        }
    }

    fun description(): String? = endpoint.description

    fun setDescription(desc: String?) {
        endpoint.description = desc
    }

    // ── Parameter/header setters ────────────────────────────────────────────
    //
    // The trailing [example] arg is threaded through to the underlying
    // [ApiEndpoint] so Swagger `@Parameter#example` / `@Header#example` can be
    // carried as a parameter/header example. Each setter is declared twice:
    // the full-arity form is the current API, and a `@Deprecated` short-arity
    // overload (`example` = null) keeps historical rule scripts running.
    //
    // Why explicit overloads instead of Kotlin default args + @JvmOverloads:
    // Groovy's MOP dispatches on real arity, so "omit example" must exist as a
    // real method. Declaring it explicitly (rather than letting @JvmOverloads
    // generate it) puts it in @Metadata with true parameter names, and allows
    // marking it @Deprecated so RuleKeyScriptProfiler can filter it out of the
    // AI-facing catalog — new rules only ever see the full-arity form.
    //
    // Rule key → field name note: `example` is fed by the `param.demo` rule
    // (see swagger3.config); the name differs for historical reasons.

    /** Adds a query parameter. @param example optional example value; pass null when absent. */
    fun setParam(name: String?, defaultValue: String?, required: Boolean, desc: String?, example: String?) {
        endpoint.setParam(name, defaultValue, required, desc, example)
    }

    /**
     * Legacy 4-arg form kept for rule scripts written before `example` support
     * (commit 7127d9d2). New rules must use the full [setParam] overload,
     * passing `null` for [example] when absent.
     *
     * @deprecated compatibility shim for historical `.rules` — filtered out of
     *  the AI script-context catalog by [RuleKeyScriptProfiler].
     */
    @Deprecated(
        message = "Legacy overload for historical rules; pass example explicitly (use null when absent).",
        replaceWith = ReplaceWith("setParam(name, defaultValue, required, desc, null)")
    )
    fun setParam(name: String?, defaultValue: String?, required: Boolean, desc: String?) {
        setParam(name, defaultValue, required, desc, null)
    }

    /** Adds a form parameter. @param example optional example value; pass null when absent. */
    fun setFormParam(name: String?, defaultValue: String?, required: Boolean, desc: String?, example: String?) {
        endpoint.setFormParam(name, defaultValue, required, desc, example)
    }

    /**
     * Legacy 4-arg form kept for rule scripts written before `example` support
     * (commit 7127d9d2). New rules must use the full [setFormParam] overload,
     * passing `null` for [example] when absent.
     *
     * @deprecated compatibility shim for historical `.rules` — filtered out of
     *  the AI script-context catalog by [RuleKeyScriptProfiler].
     */
    @Deprecated(
        message = "Legacy overload for historical rules; pass example explicitly (use null when absent).",
        replaceWith = ReplaceWith("setFormParam(name, defaultValue, required, desc, null)")
    )
    fun setFormParam(name: String?, defaultValue: String?, required: Boolean, desc: String?) {
        setFormParam(name, defaultValue, required, desc, null)
    }

    /**
     * Adds a path parameter. `required` is hard-coded to `true` (path params
     * are always required by HTTP semantics), so this setter takes no
     * [required] argument, unlike the other setters.
     * @param example optional example value; pass null when absent.
     */
    fun setPathParam(name: String?, defaultValue: String?, desc: String?, example: String?) {
        endpoint.setPathParam(name, defaultValue, desc, example)
    }

    /**
     * Legacy 3-arg form kept for rule scripts written before `example` support
     * (commit 7127d9d2). New rules must use the full [setPathParam] overload,
     * passing `null` for [example] when absent.
     *
     * @deprecated compatibility shim for historical `.rules` — filtered out of
     *  the AI script-context catalog by [RuleKeyScriptProfiler].
     */
    @Deprecated(
        message = "Legacy overload for historical rules; pass example explicitly (use null when absent).",
        replaceWith = ReplaceWith("setPathParam(name, defaultValue, desc, null)")
    )
    fun setPathParam(name: String?, defaultValue: String?, desc: String?) {
        setPathParam(name, defaultValue, desc, null)
    }

    /** Adds a request header. @param example optional example value; pass null when absent. */
    fun setHeader(name: String?, defaultValue: String?, required: Boolean, desc: String?, example: String?) {
        endpoint.setHeader(name, defaultValue, required, desc, example)
    }

    /**
     * Legacy 4-arg form kept for rule scripts written before `example` support
     * (commit 7127d9d2). New rules must use the full [setHeader] overload,
     * passing `null` for [example] when absent.
     *
     * @deprecated compatibility shim for historical `.rules` — filtered out of
     *  the AI script-context catalog by [RuleKeyScriptProfiler].
     */
    @Deprecated(
        message = "Legacy overload for historical rules; pass example explicitly (use null when absent).",
        replaceWith = ReplaceWith("setHeader(name, defaultValue, required, desc, null)")
    )
    fun setHeader(name: String?, defaultValue: String?, required: Boolean, desc: String?) {
        setHeader(name, defaultValue, required, desc, null)
    }

    fun setResponseCode(code: Int) {
        endpoint.setResponseCode(code)
    }

    fun appendResponseBodyDesc(desc: String?) {
        endpoint.appendResponseBodyDesc(desc)
    }

    /** Adds a response header. @param example optional example value; pass null when absent. */
    fun setResponseHeader(name: String?, defaultValue: String?, required: Boolean, desc: String?, example: String?) {
        endpoint.setResponseHeader(name, defaultValue, required, desc, example)
    }

    /**
     * Legacy 4-arg form kept for rule scripts written before `example` support
     * (commit 7127d9d2). New rules must use the full [setResponseHeader]
     * overload, passing `null` for [example] when absent.
     *
     * @deprecated compatibility shim for historical `.rules` — filtered out of
     *  the AI script-context catalog by [RuleKeyScriptProfiler].
     */
    @Deprecated(
        message = "Legacy overload for historical rules; pass example explicitly (use null when absent).",
        replaceWith = ReplaceWith("setResponseHeader(name, defaultValue, required, desc, null)")
    )
    fun setResponseHeader(name: String?, defaultValue: String?, required: Boolean, desc: String?) {
        setResponseHeader(name, defaultValue, required, desc, null)
    }

    fun setResponseBodyClass(className: String?) {
        endpoint.setResponseBodyClass(className)
    }

    fun appendDesc(desc: String?) {
        endpoint.appendDesc(desc)
    }

    /** Same as [toCurl] with the default host and pre-request scripts disabled. */
    fun toCurl(): String = toCurl(CurlRenderer.DEFAULT_HOST, false)

    /** Same as [toCurl] with pre-request scripts disabled for the given [host]. */
    fun toCurl(host: String): String = toCurl(host, false)

    /**
     * Builds a cURL command for this endpoint. Available in rule scripts as
     * `api.toCurl()`.
     *
     * ## Example
     *
     * ```config
     * export.after=groovy:api.appendDesc("\n\n```\n" + api.toCurl() + "\n```\n")
     * ```
     *
     * ## Behavior
     *
     * - [host] defaults to [CurlRenderer.DEFAULT_HOST] (`"{{host}}"`) so rule authors
     *   can resolve it later via environment/config. Pass an explicit host (e.g.
     *   `api.toCurl("https://api.example.com")`) to bake it in.
     * - Format options (long flags, pretty-print, etc.) flow from the persisted
     *   cURL settings, so the user's cURL settings tab controls rule-generated
     *   cURL too.
     * - When [runPreScripts] is `true` AND [project] is non-null, folder+class
     *   pre-request scripts are applied to a deep copy before formatting
     *   (original endpoint untouched). Endpoint-scope scripts are
     *   intentionally NOT included here because `export.after` fires post-build and
     *   the endpoint-key scope semantics differ from the copy/export path.
     * - When [project] is null (e.g. unit tests, headless rule eval), falls back to
     *   the pure format path — no settings, no scripts.
     *
     * ## SPI indirection
     *
     * Delegates to [CurlRenderer] (application-scoped SPI in `channel.spi`),
     * never to concrete `channel.curl.*` types — keeps the CO3 DAG rule
     * (`core.*` MUST NOT import concrete `channel.<id>.*`). The SPI
     * implementation (`CurlRendererService`) is registered in `plugin.xml`.
     *
     * ## Threading
     *
     * `export.after` rules fire synchronously inside the rule engine's script
     * thread (a background worker). This method is non-suspend and delegates to
     * [CurlRenderer.buildSync], which `runBlocking`s the suspend builder internally.
     * That is safe because `PreScriptApplier.applyScripts` is EDT-free (no `swing`
     * hops) — `runBlocking` on a background thread never deadlocks with EDT.
     *
     * @param host Target host; the no-arg overload passes [CurlRenderer.DEFAULT_HOST].
     * @param runPreScripts When true and [project] is set, run folder+class
     *  pre-request scripts before formatting. The no-arg/one-arg overloads pass false.
     * @return The formatted cURL command string.
     */
    fun toCurl(host: String, runPreScripts: Boolean): String {
        val renderer = CurlRenderer.getInstance()
        val p = project ?: return renderer.format(endpoint, host)
        return renderer.buildSync(p, endpoint, host, runPreScripts)
    }

    override fun toString(): String = endpoint.toString()
}