package com.itangcent.idea.plugin.api.export.generic

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.Request
import com.itangcent.common.utils.stream
import com.itangcent.idea.plugin.StatusRecorder
import com.itangcent.idea.plugin.Worker
import com.itangcent.idea.plugin.WorkerStatus
import com.itangcent.idea.plugin.api.export.core.*
import com.itangcent.idea.plugin.settings.helper.SupportSettingsHelper
import com.itangcent.idea.psi.PsiMethodResource
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.AnnotationHelper
import com.itangcent.intellij.jvm.JvmClassHelper
import com.itangcent.intellij.logger.Logger
import kotlin.reflect.KClass

/**
 * Simple implement of [RequestClassExporter] that exports [Request]
 * from any ordinary method
 * Depends on [GenericClassExportRuleKeys]
 */
@Singleton
open class SimpleGenericRequestClassExporter : ClassExporter, Worker {

    @Inject
    private val annotationHelper: AnnotationHelper? = null

    @Inject
    protected val jvmClassHelper: JvmClassHelper? = null

    @Inject
    protected lateinit var supportSettingsHelper: SupportSettingsHelper

    override fun support(docType: KClass<*>): Boolean {
        return supportSettingsHelper.genericEnable() && docType == Request::class
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
    private lateinit var ruleComputer: RuleComputer

    @Inject
    private var actionContext: ActionContext? = null

    @Inject
    protected var apiHelper: ApiHelper? = null

    override fun export(cls: Any, docHandle: DocHandle, completedHandle: CompletedHandle): Boolean {
        if (!supportSettingsHelper.genericEnable() || cls !is PsiClass) {
            completedHandle(cls)
            return false
        }
        actionContext!!.checkStatus()
        statusRecorder.newWork()
        try {
            when {
                !hasApi(cls) -> {
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
            completedHandle(cls)
        }
        return true
    }

    fun hasApi(psiClass: PsiClass): Boolean {
        return (ruleComputer.computer(GenericClassExportRuleKeys.CLASS_HAS_API, psiClass) ?: false)
    }

    private fun shouldIgnore(psiElement: PsiElement): Boolean {
        return ruleComputer.computer(ClassExportRuleKeys.IGNORE, psiElement) ?: false
    }

    private fun exportMethodApi(psiClass: PsiClass, method: PsiMethod, docHandle: DocHandle) {

        actionContext!!.checkStatus()
        if (!isApi(method)) {
            return
        }

        val request = Request()
        request.resource = PsiMethodResource(method, psiClass)
        request.name = apiHelper!!.nameOfApi(method)
        docHandle(request)
    }

    fun isApi(psiMethod: PsiMethod): Boolean {
        return (ruleComputer.computer(GenericClassExportRuleKeys.METHOD_HAS_API, psiMethod) ?: false)
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
}
