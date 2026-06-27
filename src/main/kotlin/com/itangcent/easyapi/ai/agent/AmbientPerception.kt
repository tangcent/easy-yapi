package com.itangcent.easyapi.ai.agent

import com.intellij.openapi.project.Project
import com.itangcent.easyapi.config.source.RuleFileResolver
import com.itangcent.easyapi.logging.IdeaLog
import java.nio.file.Paths

/**
 * Captures "free" perception before each reasoning step.
 *
 * The AI assistant runs inside the rule-file edit dialog, so the relevant
 * ambient context is the rule file being edited plus the other rule files
 * that already exist — NOT the main editor's active file/caret (which is
 * meaningless in a Settings dialog).
 */
object AmbientPerception : IdeaLog {

    /**
     * Capture the ambient context for [project], scoped to the rule file
     * [editingFilePath] being edited in the dialog (may be `null` when the
     * panel is invoked outside a file-edit context, e.g. tests).
     */
    fun capture(project: Project, editingFilePath: String? = null): Ambient {
        val projectName = project.name
        val editingRuleFile = editingFilePath
            ?.let { runCatching { Paths.get(it).fileName.toString() }.getOrNull() }
        val existingRuleFiles = runCatching {
            RuleFileResolver(project).listRuleFiles()
        }.getOrDefault(emptyList())
        // Trace what the agent "saw" at turn start.
        LOG.info("ambient capture: project=$projectName " +
            "editingRuleFile=${editingRuleFile ?: "<none>"} " +
            "existingRuleFiles=${existingRuleFiles.size}")
        return Ambient(projectName, editingRuleFile, existingRuleFiles)
    }
}
