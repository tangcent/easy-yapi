package com.itangcent.idea.plugin.api.export.generic

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.Request
import com.itangcent.idea.plugin.api.ClassApiExporterHelper
import com.itangcent.idea.plugin.api.export.Orders
import com.itangcent.idea.plugin.api.export.condition.ConditionOnDoc
import com.itangcent.idea.plugin.api.export.condition.ConditionOnSimple
import com.itangcent.idea.plugin.api.export.core.*
import com.itangcent.idea.plugin.condition.ConditionOnSetting
import com.itangcent.idea.psi.PsiMethodResource
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.JvmClassHelper
import com.itangcent.intellij.logger.Logger
import com.itangcent.order.Order
import kotlin.reflect.KClass

/**
 * Simple implement of [RequestClassExporter] that exports [Request]
 * from any ordinary method
 * Depends on [GenericClassExportRuleKeys]
 */
@Singleton
@ConditionOnSimple
@Order(Orders.GENERIC)
@ConditionOnDoc("request")
@ConditionOnSetting("genericEnable")
open class SimpleGenericRequestClassExporter : ClassExporter {

    @Inject
    protected val jvmClassHelper: JvmClassHelper? = null

    @Inject
    protected lateinit var classApiExporterHelper: ClassApiExporterHelper

    override fun support(docType: KClass<*>): Boolean {
        return docType == Request::class
    }

    @Inject
    private lateinit var logger: Logger

    @Inject
    private lateinit var ruleComputer: RuleComputer

    @Inject
    private lateinit var actionContext: ActionContext

    @Inject
    protected var apiHelper: ApiHelper? = null

    override fun export(cls: Any, docHandle: DocHandle): Boolean {
        if (cls !is PsiClass) {
            return false
        }
        actionContext.checkStatus()

        val clsQualifiedName = actionContext.callInReadUI { cls.qualifiedName }
        try {
            when {
                !hasApi(cls) -> {
                    return false
                }
                shouldIgnore(cls) -> {
                    logger.debug("ignore class: $clsQualifiedName")
                    return true
                }
                else -> {
                    logger.info("search api from: $clsQualifiedName")

                    classApiExporterHelper.foreachPsiMethod(cls) { method ->
                        exportMethodApi(cls, method, docHandle)
                    }
                }
            }
        } catch (e: Exception) {
            logger.traceError(e)
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

        actionContext.checkStatus()
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
}
