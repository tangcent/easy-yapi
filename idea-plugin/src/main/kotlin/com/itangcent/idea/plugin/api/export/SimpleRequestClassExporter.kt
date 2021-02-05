package com.itangcent.idea.plugin.api.export

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.Request
import com.itangcent.common.utils.firstOrNull
import com.itangcent.common.utils.stream
import com.itangcent.idea.plugin.StatusRecorder
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.WorkerStatus
import com.itangcent.idea.plugin.utils.SpringClassName
import com.itangcent.idea.psi.PsiMethodResource
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.AnnotationHelper
import com.itangcent.intellij.jvm.JvmClassHelper
import com.itangcent.intellij.logger.Logger
import kotlin.reflect.KClass

/**
 * only parse name
 */
open class SimpleRequestClassExporter : ClassExporter, Worker {

    @Inject
    private val annotationHelper: AnnotationHelper? = null

    @Inject
    protected val jvmClassHelper: JvmClassHelper? = null

    override fun support(docType: KClass<*>): Boolean {
        return docType == Request::class
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
    private val docParseHelper: DocParseHelper? = null

    @Inject
    protected val requestHelper: RequestHelper? = null

    @Inject
    private val ruleComputer: RuleComputer? = null

    @Inject
    private var actionContext: ActionContext? = null

    @Inject
    protected var apiHelper: ApiHelper? = null

    override fun export(cls: Any, docHandle: DocHandle, completedHandle: CompletedHandle): Boolean {
        if (cls !is PsiClass) {
            completedHandle(cls)
            return false
        }
        actionContext!!.checkStatus()
        statusRecorder.newWork()
        try {
            when {
                !isCtrl(cls) -> {
                    completedHandle(cls)
                    return false
                }
                shouldIgnore(cls) -> {
                    logger!!.info("ignore class:" + cls.qualifiedName)
                    completedHandle(cls)
                    return true
                }
                else -> {
                    logger!!.info("search api from:${cls.qualifiedName}")
                    completedHandle(cls)

                    foreachMethod(cls) { method ->
                        exportMethodApi(cls, method, docHandle)
                    }
                }
            }
        } catch (e: Exception) {
            logger!!.traceError(e)
        } finally {
            statusRecorder.endWork()
        }
        completedHandle(cls)
        return true
    }

    private fun isCtrl(psiClass: PsiClass): Boolean {
        return psiClass.annotations.any {
            SpringClassName.SPRING_CONTROLLER_ANNOTATION.contains(it.qualifiedName)
        } || (ruleComputer!!.computer(ClassExportRuleKeys.IS_CTRL, psiClass) ?: false)
    }

    private fun shouldIgnore(psiElement: PsiElement): Boolean {
        return ruleComputer!!.computer(ClassExportRuleKeys.IGNORE, psiElement) ?: false
    }

    private fun exportMethodApi(psiClass: PsiClass, method: PsiMethod, docHandle: DocHandle) {

        actionContext!!.checkStatus()
        //todo:support other web annotation
        findRequestMappingInAnn(method) ?: return

        val request = Request()

        request.resource = PsiMethodResource(method, psiClass)

        requestHelper!!.setName(request, apiHelper!!.nameOfApi(method))

        docHandle(request)
    }

    private fun findRequestMappingInAnn(ele: PsiElement): Map<String, Any?>? {
        return SPRING_REQUEST_MAPPING_ANNOTATIONS
                .stream()
                .map { annotationHelper!!.findAnnMap(ele, it) }
                .firstOrNull { it != null }
    }

    private fun foreachMethod(cls: PsiClass, handle: (PsiMethod) -> Unit) {
        jvmClassHelper!!.getAllMethods(cls)
                .stream()
                .filter { !jvmClassHelper.isBasicMethod(it.name) }
                .filter { !it.hasModifierProperty("static") }
                .filter { !it.isConstructor }
                .filter { !shouldIgnore(it) }
                .forEach(handle)
    }

    companion object {
        val SPRING_REQUEST_MAPPING_ANNOTATIONS: Set<String> = setOf(SpringClassName.REQUEST_MAPPING_ANNOTATION,
                SpringClassName.GET_MAPPING,
                SpringClassName.DELETE_MAPPING,
                SpringClassName.PATCH_MAPPING,
                SpringClassName.POST_MAPPING,
                SpringClassName.PUT_MAPPING)
    }
}