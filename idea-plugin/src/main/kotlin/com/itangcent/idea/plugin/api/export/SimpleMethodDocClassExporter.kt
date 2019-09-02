package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.itangcent.common.model.MethodDoc
import com.itangcent.idea.plugin.StatusRecorder
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.WorkerStatus
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.plugin.utils.SpringClassName
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.PsiClassHelper
import com.itangcent.intellij.util.DocCommentUtils
import com.itangcent.intellij.util.KV
import com.itangcent.intellij.util.traceError
import kotlin.reflect.KClass

/**
 * only parse name
 */
open class SimpleMethodDocClassExporter : ClassExporter, Worker {

    override fun support(docType: KClass<*>): Boolean {
        return docType == MethodDoc::class && methodDocEnable()
    }

    private var statusRecorder: StatusRecorder = StatusRecorder()

    override fun status(): WorkerStatus {
        return statusRecorder.status()
    }

    override fun waitCompleted() {
        return statusRecorder.waitCompleted()
    }

    override fun cancel() {
        return statusRecorder.cancel()
    }

    @Inject
    private val logger: Logger? = null

    @Inject
    protected val docParseHelper: DocParseHelper? = null

    @Inject
    protected val methodDocHelper: MethodDocHelper? = null

    @Inject
    protected val settingBinder: SettingBinder? = null

    @Inject
    protected val ruleComputer: RuleComputer? = null

    @Inject(optional = true)
    protected val methodFilter: MethodFilter? = null

    @Inject
    protected var actionContext: ActionContext? = null

    override fun export(cls: Any, docHandle: DocHandle): Boolean {
        if (!methodDocEnable()) {
            return false
        }
        if (cls !is PsiClass) return false
        actionContext!!.checkStatus()
        statusRecorder.newWork()
        try {
            when {
                !hasApi(cls) -> return false
                shouldIgnore(cls) -> {
                    logger!!.info("ignore class:" + cls.qualifiedName)
                    return true
                }
                else -> {
                    logger!!.info("search api from:${cls.qualifiedName}")

                    val kv = KV.create<String, Any?>()

                    processClass(cls, kv)

                    foreachMethod(cls) { method ->
                        if (isApi(method) && methodFilter?.checkMethod(method) != false) {
                            exportMethodApi(method, kv, docHandle)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger!!.traceError(e)
        } finally {
            statusRecorder.endWork()
        }
        return true
    }

    @Suppress("UNUSED")
    protected fun processClass(cls: PsiClass, kv: KV<String, Any?>) {}

    @Suppress("UNUSED")
    protected fun hasApi(psiClass: PsiClass): Boolean {
        return true
    }

    @Suppress("UNUSED")
    protected fun isApi(psiMethod: PsiMethod): Boolean {
        return true
    }

    open protected fun shouldIgnore(psiElement: PsiElement): Boolean {

        if (ruleComputer!!.computer(ClassExportRuleKeys.IGNORE, psiElement) == true) {
            return true
        }

        if (psiElement is PsiClass) {
            if (ruleComputer.computer(ClassExportRuleKeys.CLASS_FILTER, psiElement) == false) {
                return true
            }
        } else {
            if (ruleComputer.computer(ClassExportRuleKeys.METHOD_FILTER, psiElement) == false) {
                return true
            }
        }

        return false
    }

    private fun exportMethodApi(method: PsiMethod, kv: KV<String, Any?>,
                                docHandle: DocHandle) {

        actionContext!!.checkStatus()

        val methodDoc = MethodDoc()

        methodDoc.resource = method

        processMethod(method, kv, methodDoc)

        docHandle(methodDoc)
    }

    protected open fun processMethod(method: PsiMethod, kv: KV<String, Any?>, methodDoc: MethodDoc) {

        val attr: String?
        var attrOfMethod = findAttrOfMethod(method)
        attrOfMethod = docParseHelper!!.resolveLinkInAttr(attrOfMethod, method)

        if (attrOfMethod.isNullOrBlank()) {
            methodDocHelper!!.setName(methodDoc, method.name)
        } else {
            val lines = attrOfMethod.lines()
            attr = if (lines.size > 1) {//multi line
                lines.firstOrNull { it.isNotBlank() }
            } else {
                attrOfMethod
            }

            methodDocHelper!!.appendDesc(methodDoc, attrOfMethod)
            methodDocHelper.setName(methodDoc, attr ?: method.name)
        }

        readMethodDoc(method)?.let {
            methodDocHelper.appendDesc(methodDoc, docParseHelper.resolveLinkInAttr(it, method))
        }

    }

    private fun foreachMethod(cls: PsiClass, handle: (PsiMethod) -> Unit) {
        cls.allMethods
                .filter { !PsiClassHelper.JAVA_OBJECT_METHODS.contains(it.name) }
                .filter { !it.hasModifier(JvmModifier.STATIC) }
                .filter { !it.isConstructor }
                .filter { !shouldIgnore(it) }
                .forEach(handle)
    }

    open protected fun findAttrOfMethod(method: PsiMethod): String? {
        return DocCommentUtils.getAttrOfDocComment(method.docComment)
    }

    protected open fun readMethodDoc(method: PsiMethod): String? {
        return ruleComputer!!.computer(ClassExportRuleKeys.METHOD_DOC, method)
    }

    private fun methodDocEnable(): Boolean {
        return settingBinder!!.read().methodDocEnable
    }

    companion object {
        val SPRING_REQUEST_MAPPING_ANNOTATIONS: Set<String> = setOf(SpringClassName.REQUESTMAPPING_ANNOTATION,
                SpringClassName.GET_MAPPING,
                SpringClassName.DELETE_MAPPING,
                SpringClassName.PATCH_MAPPING,
                SpringClassName.POST_MAPPING,
                SpringClassName.PUT_MAPPING)
    }
}