package com.itangcent.easyapi.core.rule.context

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.itangcent.easyapi.core.config.ConfigReader
import com.itangcent.easyapi.core.internal.threading.readSync
import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.logging.IdeaConsole
import com.itangcent.easyapi.core.logging.IdeaLog
import com.itangcent.easyapi.core.logging.console
import com.itangcent.easyapi.core.psi.helper.AnnotationHelper
import com.itangcent.easyapi.core.psi.helper.DocHelper
import com.itangcent.easyapi.core.psi.helper.UnifiedDocHelper
import com.itangcent.easyapi.core.psi.helper.UnifiedAnnotationHelper
import com.itangcent.easyapi.core.psi.type.ResolvedField
import com.itangcent.easyapi.core.psi.type.ResolvedMethod
import com.itangcent.easyapi.core.psi.type.ResolvedParam
import com.itangcent.easyapi.core.psi.type.ResolvedType
import com.itangcent.easyapi.core.util.file.FileSaveHelper
import com.itangcent.easyapi.core.util.file.FileSelectHelper
import com.itangcent.easyapi.core.util.storage.LocalStorage
import com.itangcent.easyapi.core.util.storage.SessionStorage

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

    /**
     * Canonical text of the resolved [core], built without requiring the caller to hold a read lock.
     *
     * Every branch below reads PSI (`psiMethod.name`, `psiClass.qualifiedName`, the lazy
     * resolution of `returnType`), and lifecycle event rules (`export.after`, `api.*.parse.*`)
     * are evaluated on the background pool **outside** any read action: the exporters that fire
     * them do not open one. Wrapping the whole branch in [readSync] is a no-op for callers that
     * already hold a read action, so this costs nothing on the hot path.
     */
    private fun resolvedCanonicalText(): String? = readSync {
        when (core) {
            is ResolvedMethod -> {
                val cls = core.psiMethod.containingClass?.qualifiedName ?: ""
                "$cls#${core.psiMethod.name}"
            }
            is ResolvedField -> {
                val cls = core.psiField.containingClass?.qualifiedName ?: ""
                "$cls#${core.psiField.name}"
            }
            is ResolvedParam -> {
                val method = core.psiParameter.declarationScope
                if (method is com.intellij.psi.PsiMethod) {
                    val cls = method.containingClass?.qualifiedName ?: ""
                    "$cls#${method.name}.${core.name}"
                } else core.name
            }
            is ResolvedType -> core.qualifiedName()
            else -> null
        }
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

    /**
     * Type text of the resolved [core], for regex filter matching.
     *
     * Same read-lock contract as [resolvedCanonicalText]: `qualifiedName()` walks into
     * `psiClass.qualifiedName` and lazily resolves type arguments, both PSI reads, and
     * `#regex:` filters run on event rules outside any read action.
     */
    private fun resolvedTypeText(): String? = readSync {
        when (core) {
            is ResolvedMethod -> core.returnType.qualifiedName()
            is ResolvedField -> core.type.qualifiedName()
            is ResolvedParam -> core.type.qualifiedName()
            is ResolvedType -> core.qualifiedName()
            else -> null
        }
    }

    override fun toString(): String = canonicalText ?: "anonymous"

    /** Captured regex groups from the most recent filter match. */
    var regexGroups: List<String>? = null

    val docHelper: DocHelper by lazy { UnifiedDocHelper.getInstance(project) }
    val annotationHelper: AnnotationHelper by lazy { UnifiedAnnotationHelper() }
    val session: SessionStorage get() = sessionStorageInstance
    val config: ConfigReader get() = ConfigReader.getInstance(project)
    val localStorage: LocalStorage by lazy { LocalStorage.getInstance(project) }
    val console: IdeaConsole get() = project.console

    fun getExt(name: String): Any? = extensions[name]
    fun setExt(name: String, value: Any?) { extensions[name] = value }
    fun exts(): Map<String, Any?> = extensions

    fun wrapExt(key: String, value: Any?): Any? {
        if (value == null) return null
        if (key == "fieldContext" && value is String) return ScriptFieldPathContext(value)
        if (value is PsiElement) return withElement(value).asScriptIt()
        if (value is ApiEndpoint) return ScriptApiEndpoint(value, project)
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

        /**
         * Creates a context for a member as viewed from [containingClass].
         *
         * For inherited fields and methods, script `containingClass()` resolves
         * to the class currently being processed while `defineClass()` resolves
         * to the PSI member's declaring class.
         */
        fun fromMember(
            project: Project,
            element: PsiElement,
            containingClass: com.intellij.psi.PsiClass,
            fieldContext: String? = null
        ): RuleContext {
            val core = readSync {
                when (element) {
                    is com.intellij.psi.PsiField -> ResolvedField(
                        name = element.name,
                        psiField = element,
                        annotations = element.annotations.toList(),
                        containClass = containingClass
                    )
                    is com.intellij.psi.PsiMethod -> ResolvedMethod(
                        name = element.name,
                        psiMethod = element,
                        containClass = containingClass
                    )
                    else -> element
                }
            }
            val ss = SessionStorage.getInstance(project)
            return RuleContext(project, element, core, fieldContext, ss)
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
