package com.itangcent.easyapi.exporter.springmvc

import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.ApiParameter
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.exporter.model.ParameterBinding
import com.itangcent.easyapi.psi.model.FieldModel
import com.itangcent.easyapi.psi.model.ObjectModel
import com.itangcent.easyapi.psi.type.JsonType

/**
 * Constants for Spring Boot Actuator annotations.
 */
object SpringActuatorConstants {
    const val ENDPOINT_ANNOTATION = "org.springframework.boot.actuate.endpoint.annotation.Endpoint"
    const val WEB_ENDPOINT_ANNOTATION = "org.springframework.boot.actuate.endpoint.web.annotation.WebEndpoint"
    const val CONTROLLER_ENDPOINT_ANNOTATION = "org.springframework.boot.actuate.endpoint.web.annotation.ControllerEndpoint"
    const val REST_CONTROLLER_ENDPOINT_ANNOTATION = "org.springframework.boot.actuate.endpoint.web.annotation.RestControllerEndpoint"

    const val READ_OPERATION_ANNOTATION = "org.springframework.boot.actuate.endpoint.annotation.ReadOperation"
    const val WRITE_OPERATION_ANNOTATION = "org.springframework.boot.actuate.endpoint.annotation.WriteOperation"
    const val DELETE_OPERATION_ANNOTATION = "org.springframework.boot.actuate.endpoint.annotation.DeleteOperation"
    const val SELECTOR_ANNOTATION = "org.springframework.boot.actuate.endpoint.annotation.Selector"

    val ENDPOINT_ANNOTATIONS = setOf(
        ENDPOINT_ANNOTATION,
        WEB_ENDPOINT_ANNOTATION,
        CONTROLLER_ENDPOINT_ANNOTATION,
        REST_CONTROLLER_ENDPOINT_ANNOTATION
    )

    val ENDPOINT_OPERATION_ANNOTATIONS = setOf(
        READ_OPERATION_ANNOTATION,
        WRITE_OPERATION_ANNOTATION,
        DELETE_OPERATION_ANNOTATION
    )
}

/**
 * Scans Spring Boot Actuator endpoint classes and extracts API information.
 *
 * Actuator endpoints use a different annotation model than standard MVC:
 * - @ReadOperation → GET
 * - @WriteOperation → POST
 * - @DeleteOperation → DELETE
 * - @Selector → Path variable
 *
 * All endpoints are mapped under `/actuator/{endpointId}`.
 */
class ActuatorEndpointScanner {

    suspend fun scan(psiClass: PsiClass): List<ApiEndpoint> {
        val endpointId = findEndpointId(psiClass) ?: return emptyList()
        val basePath = "/actuator/$endpointId"

        val endpoints = ArrayList<ApiEndpoint>()
        for (method in psiClass.methods) {
            val endpoint = processMethod(psiClass, method, basePath)
            if (endpoint != null) {
                endpoints.add(endpoint)
            }
        }
        return endpoints
    }

    private fun findEndpointId(psiClass: PsiClass): String? {
        for (annotation in psiClass.annotations) {
            val qualifiedName = annotation.qualifiedName ?: continue
            if (qualifiedName in SpringActuatorConstants.ENDPOINT_ANNOTATIONS) {
                val idAttr = annotation.findAttributeValue("id")
                return idAttr?.text?.trim('"') ?: psiClass.name?.lowercase()
            }
        }
        return null
    }

    private fun processMethod(psiClass: PsiClass, method: PsiMethod, basePath: String): ApiEndpoint? {
        val operation = findOperation(method) ?: return null

        val (httpMethod, hasBody) = when (operation) {
            SpringActuatorConstants.READ_OPERATION_ANNOTATION -> HttpMethod.GET to false
            SpringActuatorConstants.WRITE_OPERATION_ANNOTATION -> HttpMethod.POST to true
            SpringActuatorConstants.DELETE_OPERATION_ANNOTATION -> HttpMethod.DELETE to true
            else -> return null
        }

        val apiName = method.name
        val path = StringBuilder(basePath)
        val pathParams = ArrayList<ApiParameter>()
        val bodyFields = HashMap<String, FieldModel>()

        for (parameter in method.parameterList.parameters) {
            val paramName = parameter.name ?: continue
            val paramType = parameter.type
            val paramComment = extractParamComment(method, paramName)

            if (hasSelectorAnnotation(parameter)) {
                path.append("/{").append(paramName).append("}")
                pathParams.add(
                    ApiParameter(
                        name = paramName,
                        type = paramType.presentableText,
                        description = paramComment,
                        binding = ParameterBinding.Path,
                        required = true
                    )
                )
            } else if (hasBody) {
                val jsonType = JsonType.fromPsiType(paramType)
                val objectModel = ObjectModel.Single(jsonType)
                bodyFields[paramName] = FieldModel(objectModel, paramComment)
            }
        }

        val body = if (bodyFields.isNotEmpty()) {
            ObjectModel.Object(bodyFields)
        } else {
            null
        }

        return ApiEndpoint(
            name = apiName,
            path = path.toString(),
            method = httpMethod,
            parameters = pathParams,
            body = body,
            sourceClass = psiClass,
            sourceMethod = method,
            className = psiClass.qualifiedName ?: psiClass.name ?: ""
        )
    }

    private fun findOperation(method: PsiMethod): String? {
        for (annotation in method.annotations) {
            val qualifiedName = annotation.qualifiedName ?: continue
            if (qualifiedName in SpringActuatorConstants.ENDPOINT_OPERATION_ANNOTATIONS) {
                return qualifiedName
            }
        }
        return null
    }

    private fun hasSelectorAnnotation(parameter: PsiParameter): Boolean {
        return parameter.annotations.any { 
            it.qualifiedName == SpringActuatorConstants.SELECTOR_ANNOTATION 
        }
    }

    private fun extractParamComment(method: PsiMethod, paramName: String): String? {
        val docComment = method.docComment ?: return null
        for (paramTag in docComment.findTagsByName("param")) {
            val nameElement = paramTag.nameElement?.text
            if (nameElement == paramName) {
                return paramTag.text.removePrefix("@param $paramName ").trim()
            }
        }
        return null
    }
}
