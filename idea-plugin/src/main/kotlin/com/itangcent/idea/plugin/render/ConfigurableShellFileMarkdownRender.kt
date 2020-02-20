package com.itangcent.idea.plugin.render

import com.google.inject.Inject
import com.itangcent.intellij.config.ConfigReader

class ConfigurableShellFileMarkdownRender : ShellFileMarkdownRender() {

    @Inject
    private val configReader: ConfigReader? = null

    override fun getRenderShell(): String? {
        return configReader!!.first("markdown.render.shell")
    }

    override fun getWorkDir(): String? {
        return configReader!!.first("markdown.render.work.dir")
    }

    override fun getTimeOut(): Long? {
        return configReader!!.first("markdown.render.timeout")?.toLong()
    }
}