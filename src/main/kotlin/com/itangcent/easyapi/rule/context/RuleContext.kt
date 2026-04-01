package com.itangcent.easyapi.rule.context

import com.intellij.psi.PsiElement
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.config.DefaultConfigReader
import com.itangcent.easyapi.core.context.ActionContext
import com.itangcent.easyapi.core.context.projectOrNull
import com.itangcent.easyapi.core.context.registerAutoClear
import com.itangcent.easyapi.logging.IdeaConsole
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.psi.helper.AnnotationHelper
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.StandardDocHelper
import com.itangcent.easyapi.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.util.RegexUtils
import com.itangcent.easyapi.util.RuleToolUtils
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
 * // From an ActionContext and element
 * val context = RuleContext.from(actionContext, psiMethod)
 *
 * // Using builder pattern
 * val context = RuleContext.builder()
 *     .actionContext(actionContext)
 *     .element(psiClass)
 *     .fieldContext("user.name")
 *     .build()
 *
 * // Without an element (for event rules)
 * val context = RuleContext.withoutElement(actionContext)
 * ```
 *
 * @property actionContext The parent action context
 * @property element The PSI element being evaluated (may be null for event rules)
 * @property fieldContext The field path context for nested evaluation
 * @see RuleParser for rule evaluation
 */
class RuleContext private constructor(
    val actionContext: ActionContext,
    val element: PsiElement?,
    val fieldContext: String?,
    private val sessionStorageInstance: SessionStorage,
    private val extensions: MutableMap<String, Any?> = mutableMapOf()
) {
    val docHelper: DocHelper by lazy {
        actionContext.instanceOrNull(DocHelper::class) ?: StandardDocHelper()
    }

    val annotationHelper: AnnotationHelper by lazy {
        actionContext.instanceOrNull(AnnotationHelper::class) ?: UnifiedAnnotationHelper()
    }

    val session: SessionStorage get() = sessionStorageInstance

    val config: ConfigReader by lazy {
        actionContext.projectOrNull()?.let { DefaultConfigReader.getInstance(it) }
            ?: error("No ConfigReader available and no PsiElement or Project to derive one")
    }

    val localStorage: LocalStorage by lazy {
        actionContext.projectOrNull()?.let { LocalStorage.getInstance(it) }
            ?: error("No LocalStorage available and no PsiElement or Project to derive one")
    }

    val console: IdeaConsole get() = actionContext.console

    fun getExt(name: String): Any? = extensions[name]

    fun setExt(name: String, value: Any?) {
        extensions[name] = value
    }

    fun exts(): Map<String, Any?> = extensions

    fun withElement(element: PsiElement, fieldContext: String? = this.fieldContext): RuleContext {
        return RuleContext(actionContext, element, fieldContext, sessionStorageInstance, extensions)
    }

    companion object : IdeaLog {
        fun builder() = Builder()

        fun from(actionContext: ActionContext, element: PsiElement, fieldContext: String? = null): RuleContext {
            val sessionStorage = SessionStorage().also { actionContext.registerAutoClear(it) }
            return RuleContext(actionContext, element, fieldContext, sessionStorage)
        }

        /**
         * Create a [RuleContext] without a [PsiElement].
         * Used for event rules that don't operate on PSI (e.g. http.call.before/after).
         */
        fun withoutElement(actionContext: ActionContext): RuleContext {
            val sessionStorage = SessionStorage().also { actionContext.registerAutoClear(it) }
            return RuleContext(actionContext, null, null, sessionStorage)
        }
    }

/**
 * Builder for constructing [RuleContext] instances.
 *
 * ## Usage
 * ```kotlin
 * val context = RuleContext.builder()
 *     .actionContext(actionContext)
 *     .element(psiClass)
 *     .fieldContext("user.address")
 *     .build()
 * ```
 */
    class Builder {
        private var actionContext: ActionContext? = null
        private var element: PsiElement? = null
        private var fieldContext: String? = null

        fun actionContext(context: ActionContext) = apply { this.actionContext = context }

        fun element(psiElement: PsiElement) = apply { this.element = psiElement }

        fun fieldContext(context: String?) = apply { this.fieldContext = context }

        fun build(): RuleContext {
            val ctx = actionContext ?: error("ActionContext is required")
            val sessionStorage = SessionStorage().also { ctx.registerAutoClear(it) }
            return RuleContext(ctx, element, fieldContext, sessionStorage)
        }
    }
}

/**
 * Context providing file operation helpers.
 *
 * Used for file save and selection operations during rule execution.
 *
 * @property fileSaveHelper Helper for saving files
 * @property fileSelectHelper Helper for selecting files
 */
data class FilesContext(
    val fileSaveHelper: FileSaveHelper?,
    val fileSelectHelper: FileSelectHelper?
)
