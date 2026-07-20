package com.itangcent.easyapi.core.ai.agent

import com.itangcent.easyapi.core.config.source.RuleFileResolver
import com.itangcent.easyapi.core.internal.threading.readSync
import com.itangcent.easyapi.core.export.recognizer.CompositeApiClassRecognizer
import com.itangcent.easyapi.core.logging.IdeaLog
import com.intellij.openapi.module.ModuleUtilCore
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.AnnotatedElementsSearch
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
 *
 * Env-var *names* are NOT captured here. The source of truth for "which
 * env var should a workflow rule reference?" is the existing rule files
 * (a `method.additional.header=...${Authorization}` line reveals the
 * name) plus the source code (the token field the producer returns). The
 * agent resolves names via the `get_existing_rules_for_key` tool, not by
 * peeking at the Environments panel — runtime env-var *values* are out of
 * scope for a rule-authoring agent, and their *keys* can leak
 * vendor/infrastructure hints (e.g. `STRIPE_SECRET_KEY`) into the
 * transcript sent to the external LLM provider.
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
        val perception = runCatching { captureApiPerception(project) }
            .onFailure { LOG.warn("Ambient perception capture failed", it) }
            .getOrDefault(ApiPerception(emptyList(), emptyList()))
        // Trace what the agent "saw" at turn start.
        LOG.info("ambient capture: project=$projectName " +
            "editingRuleFile=${editingRuleFile ?: "<none>"} " +
            "existingRuleFiles=${existingRuleFiles.size} " +
            "userLanguage=${userLanguage ?: "<none>"} " +
            "moduleNames=${perception.moduleNames.size} " +
            "frameworkHints=${perception.frameworkHints.size}")
        LOG.info("Ambient captured ${perception.moduleNames.size} API-bearing module(s), " +
            "${perception.frameworkHints.size} framework(s)")
        return Ambient(
            projectName,
            editingRuleFile,
            existingRuleFiles,
            userLanguage,
            perception.moduleNames,
            perception.frameworkHints
        )
    }

    /**
     * Single PSI scan that captures both the API-bearing module names and the
     * detected web-framework labels, computed once per [capture] so the agent
     * can detect multi-app workspaces and active frameworks cheaply without an
     * `list_project_endpoints` round-trip on every turn.
     *
     * Reuses [CompositeApiClassRecognizer.allTargetAnnotations] — the same
     * aggregated annotation set `ApiScanner` searches on — and the indexed
     * [AnnotatedElementsSearch] primitive, so no new PSI tool is introduced
     * (cached fields on `Ambient`, not new tools). For each controller class
     * found, its containing module is resolved via
     * [ModuleUtilCore.findModuleForPsiElement]. When an annotation FQN produces
     * ≥1 hit, its owning recognizer's framework label is collected into
     * [ApiPerception.frameworkHints] (deduped).
     *
     * Settings gates (Req 9.3) are respected automatically: disabled
     * recognizers are excluded from [CompositeApiClassRecognizer.allTargetAnnotations],
     * so their annotations are never searched and their framework labels never
     * surface.
     *
     * Decision CO5: the annotation→framework map is built per call from
     * [CompositeApiClassRecognizer.recognizers] — the EP-respecting service
     * seam — instead of hard-coding concrete framework recognizer imports.
     * The map covers exactly the enabled recognizers (the same set whose
     * annotations appear in `allTargetAnnotations`), so every looked-up FQN
     * has a matching framework label. The map is small and cheap; `capture()`
     * is invoked once per agent turn.
     *
     * PSI read runs inside `readSync`. Best-effort: a per-annotation search
     * failure is logged and skipped so ambient capture never blocks the agent.
     *
     * Privacy: collects module **names** and short framework **labels** only —
     * never env-var keys or values from the Environments panel.
     */
    private fun captureApiPerception(project: Project): ApiPerception = readSync {
        val composite = CompositeApiClassRecognizer.getInstance(project)
        val annotationFqns = composite.allTargetAnnotations
        if (annotationFqns.isEmpty()) return@readSync ApiPerception(emptyList(), emptyList())
        // Decision CO5: build the annotation→framework map per call from the
        // EP-respecting seam rather than hard-coding 5 concrete recognizer
        // imports. The map covers exactly the enabled recognizers, which is
        // the same set whose annotations appear in `annotationFqns` above.
        val annotationFrameworkLookup: Map<String, String> = buildMap {
            for (recognizer in composite.recognizers()) {
                for (annotationFqn in recognizer.targetAnnotations) {
                    put(annotationFqn, recognizer.frameworkName)
                }
            }
        }
        val projectScope = GlobalSearchScope.projectScope(project)
        val allScope = GlobalSearchScope.allScope(project)
        val javaFacade = JavaPsiFacade.getInstance(project)
        val moduleNames = LinkedHashSet<String>()
        val frameworkHints = LinkedHashSet<String>()
        for (annotationFqn in annotationFqns) {
            try {
                val annotationClass = javaFacade.findClass(annotationFqn, allScope) ?: continue
                if (!annotationClass.isAnnotationType) continue
                val annotated = AnnotatedElementsSearch.searchPsiClasses(annotationClass, projectScope).findAll()
                var hasHit = false
                for (psiClass in annotated) {
                    if (psiClass.isAnnotationType) continue
                    hasHit = true
                    val module = ModuleUtilCore.findModuleForPsiElement(psiClass)
                    if (module != null && module.name.isNotBlank()) {
                        moduleNames.add(module.name)
                    }
                }
                if (hasHit) {
                    annotationFrameworkLookup[annotationFqn]?.let { frameworkHints.add(it) }
                }
            } catch (e: Exception) {
                LOG.warn("Ambient perception capture: error searching annotation $annotationFqn", e)
            }
        }
        ApiPerception(moduleNames.toList(), frameworkHints.toList())
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

/**
 * Result of the single API-perception PSI scan: the API-bearing module names
 * and the detected web-framework labels, computed together in one pass by
 * [AmbientPerception.captureApiPerception].
 */
private data class ApiPerception(
    val moduleNames: List<String>,
    val frameworkHints: List<String>
)
