package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.intellij.lang.jvm.JvmModifier
import com.intellij.psi.*
import com.itangcent.common.exporter.ClassExporter
import com.itangcent.common.exporter.ParseHandle
import com.itangcent.common.model.Request
import com.itangcent.common.model.RequestHandle
import com.itangcent.idea.constant.SpringAttrs
import com.itangcent.idea.plugin.StatusRecorder
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.WorkerStatus
import com.itangcent.idea.plugin.utils.SpringClassName
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.PsiAnnotationUtils
import com.itangcent.intellij.psi.PsiClassHelper
import com.itangcent.intellij.util.DocCommentUtils
import com.itangcent.intellij.util.traceError
import org.apache.commons.lang3.StringUtils

/**
 * only parse name
 */
class SimpleClassExporter : ClassExporter, Worker {

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
    private val docParseHelper: DefaultDocParseHelper? = null

    @Inject
    private val ruleComputer: RuleComputer? = null

    @Inject
    private var actionContext: ActionContext? = null

    override fun export(cls: Any, parseHandle: ParseHandle, requestHandle: RequestHandle) {
        if (cls !is PsiClass) return
        actionContext!!.checkStatus()
        statusRecorder.newWork()
        try {
            when {
                !isCtrl(cls) -> return
                shouldIgnore(cls) -> {
                    logger!!.info("ignore class:" + cls.qualifiedName)
                    return
                }
                else -> {
                    logger!!.info("search api from:${cls.qualifiedName}")

                    foreachMethod(cls) { method ->
                        exportMethodApi(method, parseHandle, requestHandle)
                    }
                }
            }
        } catch (e: Exception) {
            logger!!.traceError(e)
        } finally {
            statusRecorder.endWork()
        }
    }

    private fun isCtrl(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any {
            SpringAttrs.SPRING_CONTROLLER_ANNOTATION.contains(it.qualifiedName)
        }
    }

    private fun shouldIgnore(psiElement: PsiElement): Boolean {
        return ruleComputer!!.computer(ClassExportRuleKeys.IGNORE, psiElement) ?: false
    }

    private fun exportMethodApi(method: PsiMethod, parseHandle: ParseHandle, requestHandle: RequestHandle) {

        actionContext!!.checkStatus()
        //todo:support other web annotation
        findRequestMappingInAnn(method) ?: return
        val request = Request()
        request.resource = method

        val attr: String?
        val attrOfMethod = findAttrOfMethod(method, parseHandle)!!
        val lines = attrOfMethod.lines()
        attr = if (lines.size > 1) {//multi line
            lines.firstOrNull { it.isNotBlank() }
        } else {
            attrOfMethod
        }

        parseHandle.setName(request, attr ?: method.name)
        requestHandle(request)
    }

    private fun findRequestMappingInAnn(ele: PsiModifierListOwner): PsiAnnotation? {
        return SPRING_REQUEST_MAPPING_ANNOTATIONS
                .map { PsiAnnotationUtils.findAnn(ele, it) }
                .firstOrNull { it != null }
    }

    private fun findAttrOfMethod(method: PsiMethod, parseHandle: ParseHandle): String? {
        val docComment = method.docComment

        val docText = DocCommentUtils.getAttrOfDocComment(docComment)
        return when {
            StringUtils.isBlank(docText) -> method.name
            else -> docParseHelper!!.resolveLinkInAttr(docText, method, parseHandle)
        }
    }

    private fun foreachMethod(cls: PsiClass, handle: (PsiMethod) -> Unit) {
        cls.allMethods
                .filter { !PsiClassHelper.JAVA_OBJECT_METHODS.contains(it.name) }
                .filter { !it.hasModifier(JvmModifier.STATIC) }
                .filter { !shouldIgnore(it) }
                .forEach(handle)
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