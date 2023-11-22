package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.model.Request
import com.itangcent.common.model.URL
import com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys
import com.itangcent.idea.plugin.api.export.core.ResolveMultiPath
import com.itangcent.idea.psi.resource
import com.itangcent.intellij.config.rule.RuleComputer

/**
 * The `UrlSelector` class is responsible for selecting a single URL from a given request.
 * When a request contains a single URL, it is returned as-is. However, when the request contains
 * multiple URLs, a resolution strategy is determined based on specified rules, and this strategy is
 * used to resolve the multiple URLs to a single URL.
 *
 * The resolution strategy is determined based on [ClassExportRuleKeys.PATH_MULTI], and the default
 * strategy is [ResolveMultiPath.FIRST].
 */
@Singleton
class UrlSelector {

    @Inject
    private val ruleComputer: RuleComputer? = null

    /**
     * Selects a URL from a given request
     *
     * @param request The request containing the path (URL or URLs) to be resolved.
     * @return The selected URL after applying the resolution strategy.
     */
    fun selectUrls(request: Request): URL {
        val pathInRequest = request.path ?: return URL.nil()

        // If there's only one URL, return it
        if (pathInRequest.single()) {
            return pathInRequest
        }

        // Determine the resolution strategy based on a rule, defaulting to FIRST if the rule is not set
        val pathMultiResolve = ruleComputer!!.computer(ClassExportRuleKeys.PATH_MULTI, request.resource()!!)?.let {
            ResolveMultiPath.valueOf(it.uppercase())
        } ?: ResolveMultiPath.FIRST

        // Resolve and return the URL based on the determined strategy
        return pathMultiResolve.resolve(pathInRequest)
    }
}