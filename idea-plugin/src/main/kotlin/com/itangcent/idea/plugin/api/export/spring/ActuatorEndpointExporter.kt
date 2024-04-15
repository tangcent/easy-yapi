package com.itangcent.idea.plugin.api.export.spring

import com.google.inject.Singleton
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.common.constant.HttpMethod
import com.itangcent.common.kit.KVUtils
import com.itangcent.common.model.Request
import com.itangcent.common.model.URL
import com.itangcent.common.utils.cache
import com.itangcent.common.utils.notNullOrBlank
import com.itangcent.idea.condition.annotation.ConditionOnClass
import com.itangcent.idea.plugin.api.export.core.*
import com.itangcent.idea.plugin.condition.ConditionOnSetting


/**
 * Support export apis from an endpoint using Spring Boot Actuator
 *
 * @author tangcent
 */
@Singleton
@ConditionOnClass(SpringClassName.ENDPOINT_ANNOTATION)
@ConditionOnSetting("actuatorEnable")
class ActuatorEndpointExporter : SpringRequestClassExporter() {

    override fun processClass(cls: PsiClass, classExportContext: ClassExportContext) {
        val endPointAnnMap = SpringClassName.ENDPOINT_ANNOTATIONS.asSequence().mapNotNull {
            annotationHelper.findAnnMap(cls, it)
        }.firstOrNull()
        if (endPointAnnMap != null) {
            val endPointId = endPointAnnMap["id"] as? String
            var basePath: URL = URL.of(endPointId)
            val prefixPath = ruleComputer.computer(ClassExportRuleKeys.ENDPOINT_PREFIX_PATH, cls)
                .takeIf { it.notNullOrBlank() } ?: "/actuator"
            basePath = URL.of(prefixPath).concat(basePath)
            classExportContext.setExt("basePath", basePath)
            return
        }

        return super.processClass(cls, classExportContext)
    }

    override fun hasApi(psiClass: PsiClass): Boolean {
        return SpringClassName.ENDPOINT_ANNOTATIONS.any {
            annotationHelper.hasAnn(psiClass, it)
        }
    }

    override fun isApi(psiMethod: PsiMethod): Boolean {
        return getOperation(psiMethod) != null || super.isApi(psiMethod)
    }

    override fun processMethod(methodExportContext: MethodExportContext, request: Request) {
        val operation = getOperation(methodExportContext.psi())
        if (operation != null) {
            when (operation.second) {
                SpringClassName.READ_OPERATION_ANNOTATION -> {
                    requestBuilderListener.setMethodIfMissed(
                        methodExportContext,
                        request, HttpMethod.GET
                    )
                }

                SpringClassName.WRITE_OPERATION_ANNOTATION -> {
                    requestBuilderListener.setMethodIfMissed(
                        methodExportContext,
                        request, HttpMethod.POST
                    )
                    methodExportContext.setExt("hasWriteOrDeleteOperation", true)
                }

                SpringClassName.DELETE_OPERATION_ANNOTATION -> {
                    requestBuilderListener.setMethodIfMissed(
                        methodExportContext,
                        request, HttpMethod.DELETE
                    )
                    methodExportContext.setExt("hasWriteOrDeleteOperation", true)
                }
            }
            //todo: process produces in operation
        }

        super.processMethod(methodExportContext, request)
    }

    override fun processMethodParameter(
        request: Request,
        parameterExportContext: ParameterExportContext,
        paramDesc: String?
    ) {
        val psiParameter = parameterExportContext.psi()
        val methodExportContext = parameterExportContext.methodContext()!!
        val paramName = parameterExportContext.name()
        if (annotationHelper.hasAnn(psiParameter, SpringClassName.SELECTOR_ANNOTATION)) {
            request.path?.concat(URL.of("{$paramName}"))?.let {
                requestBuilderListener.setPath(parameterExportContext, request, it)
            }
            requestBuilderListener.addPathParam(
                parameterExportContext, request,
                paramName,
                getUltimateCommentOfParam(paramDesc, parameterExportContext)
            )
            return
        }
        if (methodExportContext.getExt<Boolean>("hasWriteOrDeleteOperation") == true) {
            val body = methodExportContext.cache("body") { hashMapOf<String, Any?>() }!!
            body[paramName] = psiClassHelper!!.getTypeObject(
                parameterExportContext.type(), psiParameter
            )
            KVUtils.addKeyComment(
                body, paramName,
                getUltimateCommentOfParam(paramDesc, parameterExportContext)
            )
            return
        }

        super.processMethodParameter(request, parameterExportContext, paramDesc)
    }

    override fun processCompleted(methodExportContext: MethodExportContext, request: Request) {
        super.processCompleted(methodExportContext, request)
        methodExportContext.getExt<Any?>("body")?.let {
            setRequestBody(methodExportContext, request, it, "")
        }
    }

    private fun getOperation(psiMethod: PsiMethod): Pair<Map<String, Any?>, String>? {
        return SpringClassName.ENDPOINT_OPERATION_ANNOTATIONS
            .asSequence()
            .map { ann -> annotationHelper.findAnnMap(psiMethod, ann)?.to(ann) }
            .firstOrNull { it != null }
    }
}
