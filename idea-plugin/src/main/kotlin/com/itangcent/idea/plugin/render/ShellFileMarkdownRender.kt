package com.itangcent.idea.plugin.render

import com.google.inject.Inject
import com.itangcent.common.logger.traceError
import com.itangcent.idea.utils.SystemProvider
import com.itangcent.intellij.logger.Logger
import java.io.File
import java.util.concurrent.TimeUnit

abstract class ShellFileMarkdownRender : FileMarkdownRender() {

    @Inject
    private lateinit var logger: Logger

    @Inject
    private lateinit var systemProvider: SystemProvider

    override fun renderFile(tempFile: String): Boolean {
        var renderShell = getRenderShell()
        if (renderShell.isNullOrBlank()) return false
        if (renderShell.contains("#target")) {
            renderShell = renderShell.replace(
                    "#target",
                    tempFile.removeSuffix(".md") + ".html"
            )
        }
        renderShell = if (renderShell.contains("#fileName")) {
            renderShell.replace("#fileName", tempFile)
        } else {
            "$renderShell $tempFile"
        }
        logger.debug("exec shell:$renderShell")
        val workDir = getWorkDir()
        try {
            val process: Process = if (workDir == null) {
                systemProvider.runtime().exec(renderShell)
            } else {
                systemProvider.runtime().exec(renderShell, emptyArray(), File(workDir))
            }
            process.waitFor(getTimeOut() ?: 3000, TimeUnit.MILLISECONDS)
        } catch (e: Exception) {
            logger.traceError("failed exec shell:$renderShell", e)
            return false
        }
        return true
    }

    abstract fun getRenderShell(): String?

    /**
     * the working directory of the subprocess to exec the shell
     */
    abstract fun getWorkDir(): String?

    abstract fun getTimeOut(): Long?
}