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

    private val availableRender = ArrayList<MarkdownRender>()

    @Volatile
    private var init = false

    @PostConstruct
    fun init() {
        contextSwitchListener!!.onModuleChange {
            synchronized(this) {
                availableRender.clear()
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
        if (!configReader!!.first("markdown.render.shell").isNullOrBlank()) {
            availableRender.add(configurableShellFileMarkdownRender!!)
        }
        if (!configReader.first("markdown.render.server").isNullOrBlank()) {
            availableRender.add(remoteMarkdownRender!!)
        }
    }

    override fun render(markdown: String): String? {
        tryInit()
        for (markdownRender in availableRender) {
            val html = markdownRender.render(markdown)
            if (html != null) {
                return html
            }
        }
        return null
    }
}