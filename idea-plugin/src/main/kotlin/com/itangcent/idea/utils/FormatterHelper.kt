package com.itangcent.idea.utils

import com.google.inject.Inject
import com.google.inject.Singleton
import com.google.inject.name.Named
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.codeStyle.CodeStyleManager
import com.itangcent.common.logger.traceError
import com.itangcent.common.utils.IDUtils
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.logger
import com.itangcent.intellij.file.LocalFileRepository
import java.io.File
import java.nio.charset.StandardCharsets

/**
 * Provides utilities for formatting text in various formats (e.g., JSON, XML, HTML)
 * by leveraging IntelliJ Platform's code style management capabilities.
 *
 * @author tangcent
 * @date 2024/03/31
 */
@Singleton
class FormatterHelper {

    @Inject
    private lateinit var actionContext: ActionContext

    @Inject
    private lateinit var project: Project

    @Inject
    @Named("projectCacheRepository")
    private lateinit var localFileRepository: LocalFileRepository

    private val localFileSystem by lazy { LocalFileSystem.getInstance() }

    /**
     * Formats the given text according to the specified type (e.g., "json", "xml", "html"),
     * utilizing the code style settings of the current project.
     *
     * @param text The text to format.
     * @param type The type of the text (e.g., "json", "xml", "html").
     * @return The formatted text.
     */
    fun formatText(text: String, type: String): String {
        try {
            return actionContext.callInWriteUI {
                val file: File = localFileRepository.getOrCreateFile("temp${IDUtils.shortUUID()}.${type}")
                try {
                    file.writeText(text, StandardCharsets.UTF_8)
                    val virtualFile = localFileSystem.refreshAndFindFileByPath(file.absolutePath)!!
                    val psiFile: PsiFile = PsiManager.getInstance(project).findFile(virtualFile)!!
                    CodeStyleManager.getInstance(project).reformat(psiFile)
                    psiFile.text
                } finally {
                    file.delete()
                }
            }!!
        } catch (e: Exception) {
            actionContext.logger().traceError("format text failed", e)
            return text
        }
    }
}

fun FormatterHelper.formatJson(json: String): String {
    return formatText(json, "json")
}

fun FormatterHelper.formatXml(xml: String): String {
    return formatText(xml, "xml")
}

fun FormatterHelper.formatHtml(html: String): String {
    return formatText(html, "html")
}