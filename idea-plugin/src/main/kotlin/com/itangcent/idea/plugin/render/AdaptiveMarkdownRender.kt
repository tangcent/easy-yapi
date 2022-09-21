package com.itangcent.idea.plugin.render

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.common.logger.Log
import com.itangcent.common.logger.traceError
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.ContextSwitchListener

@Singleton
class AdaptiveMarkdownRender : MarkdownRender {

    @Inject
    private lateinit var actionContext: ActionContext

    @Inject
    private val configReader: ConfigReader? = null

    @Inject
    private val contextSwitchListener: ContextSwitchListener? = null

    private var availableRenders: ArrayList<MarkdownRender>? = null

    @Inject
    private lateinit var logger: Logger

    @Volatile
    private var init = false

    @PostConstruct
    fun init() {
        contextSwitchListener!!.onModuleChange {
            synchronized(this) {
                availableRenders = null
                init = false
            }
        }
    }

    private fun tryInit() {

        if (init) {
            return
        }

        synchronized(this) {
            if (init) {
                return
            }
            findAvailableRender()
            init = true
        }
    }

    protected fun findAvailableRender() {
        val availableRender = ArrayList<MarkdownRender>()
        if (!configReader!!.first("markdown.render.shell").isNullOrBlank()) {
            LOG.info("add availableRender: [${ConfigurableShellFileMarkdownRender::class}]")
            availableRender.add(actionContext.instance(ConfigurableShellFileMarkdownRender::class))
        }
        if (!configReader.first("markdown.render.server").isNullOrBlank()) {
            LOG.info("add availableRender: [${RemoteMarkdownRender::class}]")
            availableRender.add(actionContext.instance(RemoteMarkdownRender::class))
        }
        LOG.info("add availableRender: [${BundledMarkdownRender::class}]")
        availableRender.add(actionContext.instance(BundledMarkdownRender::class))
        this.availableRenders = availableRender
    }

    override fun render(markdown: String): String? {
        tryInit()
        val renders = availableRenders ?: return null
        for (markdownRender in renders) {
            try {
                val html = markdownRender.render(markdown)
                if (html != null) {
                    LOG.info("render html with: [$markdownRender]")
                    return html
                }
            } catch (e: Throwable) {
                logger.traceError("failed render markdown with $markdownRender", e)
            }
        }
        return null
    }

    companion object : Log()
}