package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.model.Request
import com.itangcent.common.model.URL
import com.itangcent.common.utils.longest
import com.itangcent.common.utils.shortest
import com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys
import com.itangcent.idea.plugin.api.export.core.ResolveMultiPath
import com.itangcent.idea.psi.resource
import com.itangcent.intellij.config.rule.RuleComputer

@Singleton
class UrlSelector {

    @Inject
    private val ruleComputer: RuleComputer? = null

    fun selectUrls(request: Request): URL {
        val pathInRequest = request.path ?: return URL.nil()

        if (pathInRequest.single()) {
            return pathInRequest
        }

        val pathMultiResolve = ruleComputer!!.computer(ClassExportRuleKeys.PATH_MULTI, request.resource()!!)?.let {
            ResolveMultiPath.valueOf(it.toUpperCase())
        } ?: ResolveMultiPath.FIRST

        when (pathMultiResolve) {
            ResolveMultiPath.ALL -> {
                return pathInRequest
            }

            ResolveMultiPath.FIRST -> {
                return URL.of(pathInRequest.urls().firstOrNull())
            }

            ResolveMultiPath.LAST -> {
                return URL.of(pathInRequest.urls().lastOrNull())
            }

            ResolveMultiPath.LONGEST -> {
                return URL.of(pathInRequest.urls().longest())
            }

            ResolveMultiPath.SHORTEST -> {
                return URL.of(pathInRequest.urls().shortest())
            }

            else -> {
                return pathInRequest
            }
        }
    }
}