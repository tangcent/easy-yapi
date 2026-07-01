package com.itangcent.easyapi.ai.agent

import com.itangcent.easyapi.config.source.RuleFileResolver
import com.itangcent.easyapi.logging.IdeaLog
import com.intellij.openapi.project.Project
import java.nio.file.Paths
import java.util.Locale

/**
 * Captures "free" perception before each reasoning step.
 *
 * The AI assistant runs inside the rule-file edit dialog, so the relevant
 * ambient context is the rule file being edited plus the other rule files
 * that already exist — NOT the main editor's active file/caret (which is
 * meaningless in a Settings dialog).
 *
 * Locale detection  is layered on top: [detectUserLanguage] resolves
 * the user's preferred language from the IDE locale. This is a generic,
 * markdown-agnostic hint — markdown-specific business rules (e.g.
 * `markdown.template.language`) are intentionally NOT consulted here; the
 * agent proposes that rule when the ambient language hint is non-English.
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
        val userLanguage = detectUserLanguage()
        // Trace what the agent "saw" at turn start.
        LOG.info("ambient capture: project=$projectName " +
            "editingRuleFile=${editingRuleFile ?: "<none>"} " +
            "existingRuleFiles=${existingRuleFiles.size} " +
            "userLanguage=${userLanguage ?: "<none>"}")
        return Ambient(projectName, editingRuleFile, existingRuleFiles, userLanguage)
    }

    /**
     * Resolve the user's preferred language for the ambient hint .
     *
     * Priority chain (first non-empty wins):
     *
     * ```
     * 1. IDE default locale (Locale.getDefault(), non-English) → toLanguageTag()
     * 2. null — English or undetermined (no suggestion)
     * ```
     *
     * Pure: no PSI reads, no network, no config access. Reads only the
     * JVM-default locale. This is a generic user-language hint, not a
     * markdown-specific resolution — markdown-specific rules (e.g.
     * `markdown.template.language`) belong to the Markdown resolver, not
     * here. The agent's preamble uses this hint to decide whether to
     * propose `markdown.template.language` for non-English users.
     */
    private fun detectUserLanguage(): String? {
        val locale = Locale.getDefault()
        val lang = locale.language
        if (lang.isNotEmpty() && lang != "en") {
            return locale.toLanguageTag()
        }
        return null
    }
}
