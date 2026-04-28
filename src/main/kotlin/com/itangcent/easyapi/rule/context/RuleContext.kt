package com.itangcent.easyapi.rule.context

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.core.threading.readSync
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.logging.IdeaConsole
import com.itangcent.easyapi.logging.IdeaLog
import com.itangcent.easyapi.logging.IdeaConsoleProvider
import com.itangcent.easyapi.psi.helper.AnnotationHelper
import com.itangcent.easyapi.psi.helper.DocHelper
import com.itangcent.easyapi.psi.helper.UnifiedDocHelper
import com.itangcent.easyapi.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.psi.type.ResolvedField
import com.itangcent.easyapi.psi.type.ResolvedMethod
import com.itangcent.easyapi.psi.type.ResolvedParam
import com.itangcent.easyapi.psi.type.ResolvedType
import com.itangcent.easyapi.util.file.FileSaveHelper
import com.itangcent.easyapi.util.file.FileSelectHelper
import com.itangcent.easyapi.util.storage.LocalStorage
import com.itangcent.easyapi.util.storage.SessionStorage

/**
 * Context for rule evaluation containing all necessary dependencies.
 *
 * Follows the dual-element pattern (inspired by the legacy plugin's `getCore()` / `getResource()`):
 * - [element] — the underlying PSI element, always available for annotation/doc/modifier access
 * - [core] — the "rich" element being evaluated: either a PsiElement or a resolved element
 *   ([ResolvedMethod], [ResolvedField], [ResolvedParam], [ResolvedType.ClassType])
 *
 * When [core] is a resolved element, [typeText] returns the resolved canonical text
 * (e.g., `reactor.core.publisher.Mono<com.itangcent.model.UserInfo>`) for regex matching.
 * Script contexts use [core] for type-aware operations (e.g., `returnType()` returns
 * `Result<String>` instead of `Result<T>`).
 *
 * ## Creation
 * ```kotlin
 * // From a PsiElement (core == element)
 * val context = RuleContext.from(project, psiMethod)
 *
 * // From a resolved element (core = resolvedMethod, element = resolvedMethod.psiMethod)
 * val context = RuleContext.from(project, resolvedMethod)
 *
 * // From a resolved type (for json.rule.convert evaluation)
 * val context = RuleContext.withResolvedType(project, resolvedType)
 *
 * // Without an element (for event rules)
 * val context = RuleContext.withoutElement(project)
 * ```
 */
class RuleContext private constructor(
    val project: Project,
    /** The underlying PSI element — always available for annotation/doc/modifier access. */
    val element: PsiElement?,
    /** The "rich" element being evaluated. When this is a ResolvedMethod/ResolvedField/ResolvedParam/ResolvedType,
     *  [typeText] returns the resolved canonical text and script contexts use resolved types. */
    val core: Any?,
    val fieldContext: String?,
    private val sessionStorageInstance: SessionStorage,
    private val extensions: MutableMap<String, Any?> = mutableMapOf(),
    /** The PsiType being evaluated (for json.rule.convert and type-based rules). */
    val psiType: com.intellij.psi.PsiType? = null
) {
    /**
     * Canonical text representation of this context's element.
     *
     * - For types/resolved types → fully-qualified name with type args (e.g., `Mono<UserInfo>`)
     * - For PsiType → canonical text (e.g., `reactor.core.publisher.Mono<UserInfo>`)
     * - For PsiClass → qualified name (e.g., `com.itangcent.UserCtrl`)
     * - For PsiMethod → `class#method` (e.g., `com.itangcent.UserCtrl#greeting`)
     * - For PsiField → `class#field` (e.g., `com.itangcent.UserInfo#name`)
     * - For PsiParameter → `class#method.param` (e.g., `com.itangcent.UserCtrl#greeting.id`)
     */
    val canonicalText: String?
        get() = resolvedCanonicalText()
            ?: readSync { psiType?.canonicalText ?: psiCanonicalText() }

    private fun resolvedCanonicalText(): String? = when (core) {
        is ResolvedMethod -> {
            val cls = readSync { core.psiMethod.containingClass?.qualifiedName } ?: ""
            "$cls#${core.psiMethod.name}"
        }
        is ResolvedField -> {
            val cls = readSync { core.psiField.containingClass?.qualifiedName } ?: ""
            "$cls#${core.psiField.name}"
        }
        is ResolvedParam -> {
            val method = readSync { core.psiParameter.declarationScope }
            if (method is com.intellij.psi.PsiMethod) {
                val cls = readSync { method.containingClass?.qualifiedName } ?: ""
                "$cls#${method.name}.${core.name}"
            } else core.name
        }
        is ResolvedType -> core.qualifiedName()
        else -> null
    }

    private fun psiCanonicalText(): String? = when (val el = element) {
        is com.intellij.psi.PsiClass -> el.qualifiedName
        is com.intellij.psi.PsiMethod -> {
            val cls = el.containingClass?.qualifiedName ?: ""
            "$cls#${el.name}"
        }
        is com.intellij.psi.PsiField -> {
            val cls = el.containingClass?.qualifiedName ?: ""
            "$cls#${el.name}"
        }
        is com.intellij.psi.PsiParameter -> {
            val method = el.declarationScope
            if (method is com.intellij.psi.PsiMethod) {
                val cls = method.containingClass?.qualifiedName ?: ""
                "$cls#${method.name}.${el.name}"
            } else el.name
        }
        else -> el?.text
    }

    /**
     * Type text for regex filter matching (used by json.rule.convert).
     *
     * Returns the type's canonical text when the context is type-based.
     * For resolved types, returns the fully-qualified name with resolved type args.
     */
    val matchText: String?
        get() = resolvedTypeText()
            ?: readSync { psiType?.canonicalText ?: element?.text }

    private fun resolvedTypeText(): String? = when (core) {
        is ResolvedMethod -> core.returnType.qualifiedName()
        is ResolvedField -> core.type.qualifiedName()
        is ResolvedParam -> core.type.qualifiedName()
        is ResolvedType -> core.qualifiedName()
        else -> null
    }

    override fun toString(): String = canonicalText ?: "anonymous"

    /** Captured regex groups from the most recent filter match. */
    var regexGroups: List<String>? = null

    val docHelper: DocHelper by lazy { UnifiedDocHelper.getInstance(project) }
    val annotationHelper: AnnotationHelper by lazy { UnifiedAnnotationHelper() }
    val session: SessionStorage get() = sessionStorageInstance
    val config: ConfigReader get() = ConfigReader.getInstance(project)
    val localStorage: LocalStorage by lazy { LocalStorage.getInstance(project) }
    val console: IdeaConsole by lazy { IdeaConsoleProvider.getInstance(project).getConsole() }

    fun getExt(name: String): Any? = extensions[name]
    fun setExt(name: String, value: Any?) { extensions[name] = value }
    fun exts(): Map<String, Any?> = extensions

    fun wrapExt(key: String, value: Any?): Any? {
        if (value == null) return null
        if (key == "fieldContext" && value is String) return ScriptFieldContext(value)
        if (value is PsiElement) return withElement(value).asScriptIt()
        if (value is ApiEndpoint) return ScriptApiEndpoint(value)
        return value
    }

    fun withElement(element: PsiElement, fieldContext: String? = this.fieldContext): RuleContext {
        return RuleContext(project, element, element, fieldContext, sessionStorageInstance, extensions, psiType)
    }

    companion object : IdeaLog {
        fun from(project: Project, element: PsiElement, fieldContext: String? = null): RuleContext {
            val ss = SessionStorage.getInstance(project)
            return RuleContext(project, element, element, fieldContext, ss)
        }

        fun from(project: Project, method: ResolvedMethod, fieldContext: String? = null): RuleContext {
            val ss = SessionStorage.getInstance(project)
            return RuleContext(project, method.psiMethod, method, fieldContext, ss)
        }

        fun from(project: Project, field: ResolvedField, fieldContext: String? = null): RuleContext {
            val ss = SessionStorage.getInstance(project)
            return RuleContext(project, field.psiField, field, fieldContext, ss)
        }

        fun from(project: Project, param: ResolvedParam): RuleContext {
            val ss = SessionStorage.getInstance(project)
            return RuleContext(project, param.psiParameter, param, null, ss)
        }

        fun from(project: Project, classType: ResolvedType.ClassType): RuleContext {
            val ss = SessionStorage.getInstance(project)
            return RuleContext(project, classType.psiClass, classType, null, ss)
        }

        fun withPsiType(
            project: Project,
            psiType: com.intellij.psi.PsiType,
            contextElement: PsiElement? = null
        ): RuleContext {
            val ss = SessionStorage.getInstance(project)
            return RuleContext(project, contextElement, contextElement, null, ss, psiType = psiType)
        }

        /**
         * Creates a context for evaluating type-based rules (e.g., json.rule.convert)
         * with a resolved type as [core].
         *
         * [typeText] returns [resolvedType]'s [ResolvedType.qualifiedName] via [resolvedTypeText],
         * ensuring regex rules like `#regex:Mono<(.*?)>` match the fully-resolved type text.
         */
        fun withResolvedType(
            project: Project,
            resolvedType: ResolvedType,
            psiType: com.intellij.psi.PsiType? = null,
            contextElement: PsiElement? = null
        ): RuleContext {
            val ss = SessionStorage.getInstance(project)
            return RuleContext(project, contextElement, resolvedType, null, ss, psiType = psiType)
        }

        fun withoutElement(project: Project): RuleContext {
            val ss = SessionStorage.getInstance(project)
            return RuleContext(project, null, null, null, ss)
        }
    }
}

data class FilesContext(
    val fileSaveHelper: FileSaveHelper?,
    val fileSelectHelper: FileSelectHelper?
)
