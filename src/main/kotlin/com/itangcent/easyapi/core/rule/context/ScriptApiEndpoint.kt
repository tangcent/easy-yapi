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

    fun setParam(name: String?, defaultValue: String?, required: Boolean, desc: String?) {
        endpoint.setParam(name, defaultValue, required, desc)
    }

    fun setFormParam(name: String?, defaultValue: String?, required: Boolean, desc: String?) {
        endpoint.setFormParam(name, defaultValue, required, desc)
    }

    fun setPathParam(name: String?, defaultValue: String?, desc: String?) {
        endpoint.setPathParam(name, defaultValue, desc)
    }

    fun setHeader(name: String?, defaultValue: String?, required: Boolean, desc: String?) {
        endpoint.setHeader(name, defaultValue, required, desc)
    }

    fun setResponseCode(code: Int) {
        endpoint.setResponseCode(code)
    }

    fun appendResponseBodyDesc(desc: String?) {
        endpoint.appendResponseBodyDesc(desc)
    }

    fun setResponseHeader(name: String?, defaultValue: String?, required: Boolean, desc: String?) {
        endpoint.setResponseHeader(name, defaultValue, required, desc)
    }

    fun setResponseBodyClass(className: String?) {
        endpoint.setResponseBodyClass(className)
    }

    fun appendDesc(desc: String?) {
        endpoint.appendDesc(desc)
    }

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
     * ## SPI indirection (Decision CO8)
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
     * @param host Target host; defaults to [CurlRenderer.DEFAULT_HOST] when blank.
     * @param runPreScripts When true and [project] is set, run folder+class
     *  pre-request scripts before formatting. Default false.
     * @return The formatted cURL command string.
     */
    @JvmOverloads
    fun toCurl(host: String = CurlRenderer.DEFAULT_HOST, runPreScripts: Boolean = false): String {
        val renderer = CurlRenderer.getInstance()
        val p = project ?: return renderer.format(endpoint, host)
        return renderer.buildSync(p, endpoint, host, runPreScripts)
    }

    override fun toString(): String = endpoint.toString()
}