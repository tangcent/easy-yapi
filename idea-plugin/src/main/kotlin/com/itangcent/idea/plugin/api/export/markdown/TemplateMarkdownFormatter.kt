package com.itangcent.idea.plugin.api.export.markdown

import com.itangcent.common.model.Doc
import com.itangcent.common.model.Request
import com.itangcent.common.utils.safeComputeIfAbsent
import com.itangcent.idea.plugin.api.export.core.Folder
import com.itangcent.idea.plugin.api.export.core.FormatFolderHelper
import com.itangcent.intellij.config.resource.Resource
import com.itangcent.intellij.extend.lazyBean

/**
 * A template-based implementation of [MarkdownFormatter] that uses a markdown template file
 * to format API documentation.
 */
class TemplateMarkdownFormatter(
    private val templateFile: Resource
) : MarkdownFormatter {

    private val formatFolderHelper by lazyBean<FormatFolderHelper>()

    private val template: TemplateExpression by lazy {
        if (!templateFile.reachable) {
            throw IllegalStateException("Template file not found: $templateFile")
        }
        val template = templateFile.content!!
        TemplateExpression.parseTemplate(template)
    }

    override fun parseDocs(requests: List<Doc>): String {
        val mdText = StringBuilder()

        // Group requests by folder
        val folderGroupedMap: HashMap<Folder, ArrayList<Doc>> = HashMap()
        requests.forEach { doc ->
            if (doc is Request) {
                val folder = formatFolderHelper.resolveFolder(doc.resource ?: "unknown")
                folderGroupedMap.safeComputeIfAbsent(folder) { ArrayList() }!!
                    .add(doc)
            }
        }

        // Process each folder group
        folderGroupedMap.forEach { (folder, docs) ->
            mdText.writeFolder(folder)

            // Write APIs in this folder
            docs.forEach { doc ->
                if (doc is Request) {
                    template.eval(doc, mdText)
                    mdText.append("\n\n")
                }
            }
        }

        return mdText.toString()
    }

    private fun StringBuilder.writeFolder(folder: Folder) {
        // Write folder name and description
        this.append("# ${folder.name}\n\n")
        if (!folder.attr.isNullOrEmpty()) {
            this.append("${folder.attr}\n\n")
        }
    }
}