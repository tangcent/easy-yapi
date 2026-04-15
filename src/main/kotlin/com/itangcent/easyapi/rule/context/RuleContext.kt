package com.itangcent.easyapi.rule.context

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.logging.IdeaConsole
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.logging.IdeaConsoleProvider
import com.itangcent.easyapi.psi.helper.AnnotationHelper
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.util.file.FileSaveHelper
import com.itangcent.easyapi.util.file.FileSelectHelper
import com.itangcent.easyapi.util.storage.LocalStorage
import com.itangcent.easyapi.util.storage.SessionStorage

/**
 * Context for rule evaluation containing all necessary dependencies.
 *
 * Provides access to:
 * - The PSI element being evaluated
 * - Documentation and annotation helpers
 * - Configuration and storage
 * - Logging and extension points
 *
 * ## Creation
 * ```kotlin
 * // From a project and element
 * val context = RuleContext.from(project, psiMethod)
 *
 * // Using builder pattern
 * val context = RuleContext.builder()
 *     .project(project)
 *     .element(psiClass)
 *     .fieldContext("user.name")
 *     .build()
 *
 * // Without an element (for event rules)
 * val context = RuleContext.withoutElement(project)
 * ```
 *
 * @property project The IntelliJ project
 * @property element The PSI element being evaluated (may be null for event rules)
 * @property fieldContext The field path context for nested evaluation
 * @see RuleParser for rule evaluation
 */
class RuleContext private constructor(
    val project: Project,
    val element: PsiElement?,
    val fieldContext: String?,
    private val sessionStorageInstance: SessionStorage,
    private val extensions: MutableMap<String, Any?> = mutableMapOf(),
    private val typeTextOverride: String? = null,
    /** The PsiType being evaluated (for json.rule.convert and type-based rules). */
    val psiType: com.intellij.psi.PsiType? = null
) {
    val typeText: String? get() = typeTextOverride ?: psiType?.canonicalText ?: element?.text

    /** Captured regex groups from the most recent filter match. */
    var regexGroups: List<String>? = null

    val docHelper: DocHelper by lazy {
        StandardDocHelper.getInstance(project)
    }

    val annotationHelper: AnnotationHelper by lazy {
        UnifiedAnnotationHelper()
    }

    val session: SessionStorage get() = sessionStorageInstance

    val config: ConfigReader by lazy {
        ConfigReader.getInstance(project)
    }

    val localStorage: LocalStorage by lazy {
        LocalStorage.getInstance(project)
    }

    val console: IdeaConsole by lazy {
        IdeaConsoleProvider.getInstance(project).getConsole()
    }

    fun getExt(name: String): Any? = extensions[name]

    fun setExt(name: String, value: Any?) {
        extensions[name] = value
    }

    fun exts(): Map<String, Any?> = extensions

    /**
     * Wraps an extension value for script binding.
     *
     * - `"fieldContext"` strings are wrapped as [ScriptFieldContext] so scripts can call `.path()` / `.property(name)`.
     * - [PsiElement] values are wrapped as their corresponding script context.
     * - All other values are returned as-is.
     */
    fun wrapExt(key: String, value: Any?): Any? {
        if (value == null) return null
        if (key == "fieldContext" && value is String) return ScriptFieldContext(value)
        if (value is PsiElement) return withElement(value).asScriptIt()
        if (value is ApiEndpoint) return ScriptApiEndpoint(value)
        return value
    }

    fun withElement(element: PsiElement, fieldContext: String? = this.fieldContext): RuleContext {
        return RuleContext(
            project,
            element,
            fieldContext,
            sessionStorageInstance,
            extensions,
            typeTextOverride,
            psiType
        )
    }

    companion object : IdeaLog {
        fun from(project: Project, element: PsiElement, fieldContext: String? = null): RuleContext {
            val sessionStorage = SessionStorage.getInstance(project)
            return RuleContext(project, element, fieldContext, sessionStorage)
        }

        fun withPsiType(
            project: Project,
            psiType: com.intellij.psi.PsiType,
            contextElement: PsiElement? = null
        ): RuleContext {
            val sessionStorage = SessionStorage.getInstance(project)
            return RuleContext(project, contextElement, null, sessionStorage, psiType = psiType)
        }

        /**
         * Create a [RuleContext] without a [PsiElement].
         * Used for event rules that don't operate on PSI (e.g. http.call.before/after).
         */
        fun withoutElement(project: Project): RuleContext {
            val sessionStorage = SessionStorage.getInstance(project)
            return RuleContext(project, null, null, sessionStorage)
        }
    }
}

data class FilesContext(
    val fileSaveHelper: FileSaveHelper?,
    val fileSelectHelper: FileSelectHelper?
)
