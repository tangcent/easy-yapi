package com.itangcent.easyapi.exporter.channel.curl

import com.itangcent.easyapi.script.ScriptScope

/**
 * Composite options for [CurlBuilder]. Composes the existing [CurlFormatOptions]
 * (5 format flags) with the pre-script controls.
 *
 * Default [CurlBuildOptions] yields format-default output with NO script
 * execution — preserving byte-for-byte output (no script machinery is invoked).
 *
 * ## Script gate semantics
 *
 * Pre-script execution is gated by `runPreScripts && scopes.isNotEmpty()`.
 * A caller that enables [runPreScripts] but supplies an empty [scopes] list
 * incurs no script machinery. This keeps the
 * [CurlBuilder.format] step pure and plain-JUnit-testable when scripts
 * are off, and lets [CurlBuilder.build] short-circuit before touching [PreScriptApplier].
 *
 * @property format The 5 formatting flags forwarded to [CurlFormatter].
 * @property runPreScripts When `true` AND [scopes] is non-empty AND a `Project` is
 *   available, the builder runs the resolved pre-request scripts against the
 *   endpoint (deep copy) before formatting. When `false` (default), no scripts run.
 * @property scopes Script scopes to resolve (folder → class → endpoint inline, in
 *   outer→inner order matching [com.itangcent.easyapi.dashboard.RequestExecutor.resolveScripts]).
 *   Ignored when [runPreScripts] is `false`.
 */
data class CurlBuildOptions(
    val format: CurlFormatOptions = CurlFormatOptions(),
    val runPreScripts: Boolean = false,
    val scopes: List<ScriptScope> = emptyList(),
)
