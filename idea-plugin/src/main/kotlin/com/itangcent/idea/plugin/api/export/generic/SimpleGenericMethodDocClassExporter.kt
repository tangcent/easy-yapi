package com.itangcent.idea.plugin.api.export.generic

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.MethodDoc
import com.itangcent.common.utils.KV
import com.itangcent.idea.plugin.api.ClassApiExporterHelper
import com.itangcent.idea.plugin.api.export.Orders
import com.itangcent.idea.plugin.api.export.condition.ConditionOnDoc
import com.itangcent.idea.plugin.api.export.condition.ConditionOnSimple
import com.itangcent.idea.plugin.api.export.core.*
import com.itangcent.idea.plugin.condition.ConditionOnSetting
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.psi.PsiMethodResource
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.JvmClassHelper
import com.itangcent.intellij.logger.Logger
import com.itangcent.order.Order
import kotlin.reflect.KClass

/**
 * only parse name
 */
@Order(Orders.GENERIC + Orders.METHOD_DOC)
@ConditionOnSimple
@ConditionOnDoc("methodDoc")
@ConditionOnSetting("genericEnable", "methodDocEnable")
open class SimpleGenericMethodDocClassExporter : ClassExporter {

    @Inject
    protected val jvmClassHelper: JvmClassHelper? = null

    @Inject
    protected lateinit var classApiExporterHelper: ClassApiExporterHelper

    override fun support(docType: KClass<*>): Boolean {
        return docType == MethodDoc::class
    }

    @Inject
    private val logger: Logger? = null

    @Inject
    protected val settingBinder: SettingBinder? = null

    @Inject
    protected val ruleComputer: RuleComputer? = null

    @Inject(optional = true)
    protected val methodFilter: MethodFilter? = null

    @Inject
    protected lateinit var actionContext: ActionContext

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
                    logger!!.info("ignore class: $clsQualifiedName")
                    return true
                }

                else -> {
                    logger!!.info("search api from: $clsQualifiedName")

                    val kv = KV.create<String, Any?>()

                    processClass(cls, kv)

                    classApiExporterHelper.foreachPsiMethod(cls) { method ->
                        if (isApi(method) && methodFilter?.checkMethod(method) != false) {
                            exportMethodApi(cls, method, kv, docHandle)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            logger!!.traceError(e)
        }

        return true
    }

    @Suppress("UNUSED")
    protected fun processClass(cls: PsiClass, kv: KV<String, Any?>) {
    }

    @Suppress("UNUSED")
    protected fun hasApi(psiClass: PsiClass): Boolean {
        return true
    }

    @Suppress("UNUSED")
    protected fun isApi(psiMethod: PsiMethod): Boolean {
        return true
    }

    protected open fun shouldIgnore(psiElement: PsiElement): Boolean {

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

    private fun exportMethodApi(
        psiClass: PsiClass, method: PsiMethod, kv: KV<String, Any?>,
        docHandle: DocHandle,
    ) {

        actionContext.checkStatus()

        val methodDoc = MethodDoc()

        methodDoc.resource = PsiMethodResource(method, psiClass)

        processMethod(method, kv, methodDoc)

        docHandle(methodDoc)
    }

    protected open fun processMethod(method: PsiMethod, kv: KV<String, Any?>, methodDoc: MethodDoc) {
        methodDoc.name = apiHelper!!.nameOfApi(method)
    }
}