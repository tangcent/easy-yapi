package com.itangcent.idea.plugin.render

import com.google.inject.Inject
import com.itangcent.common.utils.FileUtils
import com.itangcent.intellij.file.LocalFileRepository
import java.io.File
import java.util.concurrent.atomic.AtomicInteger

abstract class FileMarkdownRender : MarkdownRender {

    @Inject
    private val localFileRepository: LocalFileRepository? = null

    protected val idx = AtomicInteger()

    override fun render(markdown: String): String? {
        if (markdown.isBlank()) {
            return null
        }
        var tempFile: File? = null
        var htmlFile: File? = null
        try {
            val tempFileName = tempFileName()
            tempFile = localFileRepository!!.getOrCreateFile("$tempFileName.md")
            FileUtils.write(tempFile, markdown)
            if (!renderFile(tempFile.canonicalPath)) {
                return null
            }
            htmlFile = localFileRepository.getFile("$tempFileName.html")
            return htmlFile?.let { FileUtils.read(it) }
        } finally {
            tempFile?.let { FileUtils.remove(it) }
            htmlFile?.let { FileUtils.remove(it) }
        }
    }

    open protected fun tempFileName(): String {
        return ".temp${System.currentTimeMillis()}${idx.getAndIncrement()}"
    }

    abstract fun renderFile(tempFile: String): Boolean

}