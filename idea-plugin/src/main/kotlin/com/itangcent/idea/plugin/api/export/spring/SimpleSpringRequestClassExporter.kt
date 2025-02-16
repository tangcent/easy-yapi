package com.itangcent.idea.plugin.api.export.spring

import com.google.inject.Inject
import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiMethod
import com.itangcent.common.logger.traceError
import com.itangcent.common.model.Request
import com.itangcent.idea.condition.annotation.ConditionOnClass
import com.itangcent.idea.plugin.api.ClassApiExporterHelper
import com.itangcent.idea.plugin.api.export.condition.ConditionOnDoc
import com.itangcent.idea.plugin.api.export.condition.ConditionOnSimple
import com.itangcent.idea.plugin.api.export.core.ApiHelper
import com.itangcent.idea.plugin.api.export.core.ClassExportRuleKeys
import com.itangcent.idea.plugin.api.export.core.ClassExporter
import com.itangcent.idea.plugin.api.export.core.DocHandle
import com.itangcent.idea.psi.PsiMethodResource
import com.itangcent.intellij.config.rule.RuleComputer
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.jvm.AnnotationHelper
import com.itangcent.intellij.jvm.JvmClassHelper
import com.itangcent.intellij.logger.Logger
import kotlin.reflect.KClass

/*
 * A simplified version of Spring request class exporter that focuses on basic request mapping information.
 * This exporter provides a lightweight alternative to the full SpringRequestClassExporter,
 * processing only essential information from Spring MVC controllers.
 * - Only processes basic request mapping information (paths, method names)
 * - Ignores complex parameter processing and type resolution
 */
@Singleton
@ConditionOnSimple
@ConditionOnClass(SpringClassName.REQUEST_MAPPING_ANNOTATION)
@ConditionOnDoc("request")
open class SimpleSpringRequestClassExporter : ClassExporter {

    @Inject
    protected val annotationHelper: AnnotationHelper? = null

    @Inject
    protected val jvmClassHelper: JvmClassHelper? = null

    @Inject
    protected lateinit var springRequestMappingResolver: SpringRequestMappingResolver

    @Inject
    protected lateinit var classApiExporterHelper: ClassApiExporterHelper

    @Inject
    private lateinit var springControllerAnnotationResolver: SpringControllerAnnotationResolver

    override fun support(docType: KClass<*>): Boolean {
        return docType == Request::class
    }

    @Inject
    private val logger: Logger? = null

    @Inject
    protected lateinit var ruleComputer: RuleComputer

    @Inject
    protected lateinit var actionContext: ActionContext

    @Inject
    protected var apiHelper: ApiHelper? = null

    override fun export(cls: Any, docHandle: DocHandle): Boolean {
        if (cls !is PsiClass) {
            return false
        }
        val clsQualifiedName = actionContext.callInReadUI { cls.qualifiedName }
        try {
            when {
                !isCtrl(cls) -> {

                    return false
                }

                shouldIgnore(cls) -> {
                    logger!!.info("ignore class: $clsQualifiedName")
                    return true
                }

                else -> {
                    logger!!.info("search api from: $clsQualifiedName")


                    classApiExporterHelper.foreachPsiMethod(cls) { method ->
                        exportMethodApi(cls, method, docHandle)
                    }
                }
            }
        } catch (e: Exception) {
            logger!!.traceError(e)
        }
        return true
    }

    protected open fun isCtrl(psiClass: PsiClass): Boolean {
        return springControllerAnnotationResolver.hasControllerAnnotation(psiClass)
                || (ruleComputer.computer(ClassExportRuleKeys.IS_SPRING_CTRL, psiClass) ?: false)
    }

    private fun shouldIgnore(psiElement: PsiElement): Boolean {
        return ruleComputer.computer(ClassExportRuleKeys.IGNORE, psiElement) ?: false
    }

    private fun exportMethodApi(psiClass: PsiClass, method: PsiMethod, docHandle: DocHandle) {

        actionContext.checkStatus()
        //todo:support other web annotation
        findRequestMappingInAnn(method) ?: return

        val request = Request()
        request.resource = PsiMethodResource(method, psiClass)
        request.name = apiHelper!!.nameOfApi(method)
        docHandle(request)
    }

    private fun findRequestMappingInAnn(ele: PsiElement): Map<String, Any?>? {
        return springRequestMappingResolver.resolveRequestMapping(ele)
    }
}