package com.itangcent.idea.plugin.render

import com.google.inject.Inject
import com.google.inject.Singleton
import com.itangcent.intellij.config.ConfigReader

/**
 *
 * render `markdown` from local shell
 *
 * config example:
 * <p>
 * markdown.render.shell=/usr/local/bin/node render.js
 * markdown.render.work.dir=/fake_path/yapi-markdown-render
 * markdown.render.timeout=3000
 * <p>
 *
 * see https://github.com/easyyapi/yapi-markdown-render
 */
@Singleton
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