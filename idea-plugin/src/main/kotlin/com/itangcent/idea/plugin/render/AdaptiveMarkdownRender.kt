package com.itangcent.idea.plugin.render

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.psi.ContextSwitchListener

@Singleton
class AdaptiveMarkdownRender : MarkdownRender {

    @Inject
    private val configurableShellFileMarkdownRender: ConfigurableShellFileMarkdownRender? = null

    @Inject
    private val remoteMarkdownRender: RemoteMarkdownRender? = null

    @Inject
    private val configReader: ConfigReader? = null

    @Inject
    private val contextSwitchListener: ContextSwitchListener? = null

    private var availableRender: ArrayList<MarkdownRender>? = null

    @Volatile
    private var init = false

    @PostConstruct
    fun init() {
        contextSwitchListener!!.onModuleChange {
            synchronized(this) {
                availableRender = null
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
            availableRender.add(configurableShellFileMarkdownRender!!)
        }
        if (!configReader.first("markdown.render.server").isNullOrBlank()) {
            availableRender.add(remoteMarkdownRender!!)
        }
        this.availableRender = availableRender
    }

    override fun render(markdown: String): String? {
        tryInit()
        val renders = availableRender ?: return null
        for (markdownRender in renders) {
            val html = markdownRender.render(markdown)
            if (html != null) {
                return html
            }
        }
        return null
    }
}