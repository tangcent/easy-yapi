package com.itangcent.easyapi.exporter.swagger

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter

object SwaggerAnnotationProcessor {
    
    fun processClassAnnotation(psiClass: PsiClass, annotation: PsiAnnotation): SwaggerClassInfo? {
        val qualifiedName = annotation.qualifiedName ?: return null
        
        return when {
            qualifiedName == "io.swagger.annotations.Api" -> processApiAnnotation(annotation)
            qualifiedName == "io.swagger.annotations.ApiModel" -> processApiModelAnnotation(annotation)
            qualifiedName == "io.swagger.v3.oas.annotations.tags.Tag" -> processTagAnnotation(annotation)
            qualifiedName == "io.swagger.v3.oas.annotations.info.Info" -> processInfoAnnotation(annotation)
            else -> null
        }
    }
    
    fun processMethodAnnotation(psiMethod: PsiMethod, annotation: PsiAnnotation): SwaggerMethodInfo? {
        val qualifiedName = annotation.qualifiedName ?: return null
        
        return when {
            qualifiedName == "io.swagger.annotations.ApiOperation" -> processApiOperationAnnotation(annotation)
            qualifiedName == "io.swagger.v3.oas.annotations.Operation" -> processOperationAnnotation(annotation)
            else -> null
        }
    }
    
    fun processParameterAnnotation(psiParameter: PsiParameter, annotation: PsiAnnotation): SwaggerParamInfo? {
        val qualifiedName = annotation.qualifiedName ?: return null
        
        return when {
            qualifiedName == "io.swagger.annotations.ApiParam" -> processApiParamAnnotation(annotation)
            qualifiedName == "io.swagger.v3.oas.annotations.Parameter" -> processParameterAnnotation(annotation)
            else -> null
        }
    }
    
    fun processFieldAnnotation(psiField: PsiField, annotation: PsiAnnotation): SwaggerFieldInfo? {
        val qualifiedName = annotation.qualifiedName ?: return null
        
        return when {
            qualifiedName == "io.swagger.annotations.ApiModelProperty" -> processApiModelPropertyAnnotation(annotation)
            qualifiedName == "io.swagger.v3.oas.annotations.media.Schema" -> processSchemaAnnotation(annotation)
            else -> null
        }
    }
    
    private fun processApiAnnotation(annotation: PsiAnnotation): SwaggerClassInfo {
        val value = annotation.findAttributeValue("value")?.text?.removeSurrounding("\"")
        val tags = annotation.findAttributeValue("tags")?.text?.removeSurrounding("\"")
        val hidden = annotation.findAttributeValue("hidden")?.text?.toBoolean() ?: false
        
        return SwaggerClassInfo(
            description = value ?: tags,
            tags = tags?.split(",")?.map { it.trim() } ?: emptyList(),
            hidden = hidden
        )
    }
    
    private fun processApiModelAnnotation(annotation: PsiAnnotation): SwaggerClassInfo {
        val value = annotation.findAttributeValue("value")?.text?.removeSurrounding("\"")
        val description = annotation.findAttributeValue("description")?.text?.removeSurrounding("\"")
        
        return SwaggerClassInfo(
            description = description ?: value,
            tags = emptyList(),
            hidden = false
        )
    }
    
    private fun processTagAnnotation(annotation: PsiAnnotation): SwaggerClassInfo {
        val name = annotation.findAttributeValue("name")?.text?.removeSurrounding("\"")
        val description = annotation.findAttributeValue("description")?.text?.removeSurrounding("\"")
        
        return SwaggerClassInfo(
            description = description ?: name,
            tags = listOfNotNull(name),
            hidden = false
        )
    }
    
    private fun processInfoAnnotation(annotation: PsiAnnotation): SwaggerClassInfo {
        val title = annotation.findAttributeValue("title")?.text?.removeSurrounding("\"")
        val description = annotation.findAttributeValue("description")?.text?.removeSurrounding("\"")
        
        return SwaggerClassInfo(
            description = description ?: title,
            tags = emptyList(),
            hidden = false
        )
    }
    
    private fun processApiOperationAnnotation(annotation: PsiAnnotation): SwaggerMethodInfo {
        val value = annotation.findAttributeValue("value")?.text?.removeSurrounding("\"")
        val notes = annotation.findAttributeValue("notes")?.text?.removeSurrounding("\"")
        val tags = annotation.findAttributeValue("tags")?.text?.removeSurrounding("\"")
        val httpMethod = annotation.findAttributeValue("httpMethod")?.text?.removeSurrounding("\"")
        
        return SwaggerMethodInfo(
            summary = value,
            description = notes,
            tags = tags?.split(",")?.map { it.trim() } ?: emptyList(),
            httpMethod = httpMethod
        )
    }
    
    private fun processOperationAnnotation(annotation: PsiAnnotation): SwaggerMethodInfo {
        val summary = annotation.findAttributeValue("summary")?.text?.removeSurrounding("\"")
        val description = annotation.findAttributeValue("description")?.text?.removeSurrounding("\"")
        val tags = annotation.findAttributeValue("tags")?.text?.removeSurrounding("\"")
        val method = annotation.findAttributeValue("method")?.text?.removeSurrounding("\"")
        
        return SwaggerMethodInfo(
            summary = summary,
            description = description,
            tags = tags?.split(",")?.map { it.trim() } ?: emptyList(),
            httpMethod = method
        )
    }
    
    private fun processApiParamAnnotation(annotation: PsiAnnotation): SwaggerParamInfo {
        val value = annotation.findAttributeValue("value")?.text?.removeSurrounding("\"")
        val defaultValue = annotation.findAttributeValue("defaultValue")?.text?.removeSurrounding("\"")
        val required = annotation.findAttributeValue("required")?.text?.toBoolean() ?: false
        val hidden = annotation.findAttributeValue("hidden")?.text?.toBoolean() ?: false
        
        return SwaggerParamInfo(
            description = value,
            defaultValue = defaultValue,
            required = required,
            hidden = hidden
        )
    }
    
    private fun processParameterAnnotation(annotation: PsiAnnotation): SwaggerParamInfo {
        val description = annotation.findAttributeValue("description")?.text?.removeSurrounding("\"")
        val required = annotation.findAttributeValue("required")?.text?.toBoolean() ?: false
        val deprecated = annotation.findAttributeValue("deprecated")?.text?.toBoolean() ?: false
        
        return SwaggerParamInfo(
            description = description,
            defaultValue = null,
            required = required,
            hidden = deprecated
        )
    }
    
    private fun processApiModelPropertyAnnotation(annotation: PsiAnnotation): SwaggerFieldInfo {
        val value = annotation.findAttributeValue("value")?.text?.removeSurrounding("\"")
        val notes = annotation.findAttributeValue("notes")?.text?.removeSurrounding("\"")
        val name = annotation.findAttributeValue("name")?.text?.removeSurrounding("\"")
        val required = annotation.findAttributeValue("required")?.text?.toBoolean() ?: false
        val hidden = annotation.findAttributeValue("hidden")?.text?.toBoolean() ?: false
        
        return SwaggerFieldInfo(
            description = notes ?: value,
            name = name,
            required = required,
            hidden = hidden
        )
    }
    
    private fun processSchemaAnnotation(annotation: PsiAnnotation): SwaggerFieldInfo {
        val description = annotation.findAttributeValue("description")?.text?.removeSurrounding("\"")
        val name = annotation.findAttributeValue("name")?.text?.removeSurrounding("\"")
        val requiredMode = annotation.findAttributeValue("requiredMode")?.text
        val hidden = annotation.findAttributeValue("hidden")?.text?.toBoolean() ?: false
        
        return SwaggerFieldInfo(
            description = description,
            name = name,
            required = requiredMode?.contains("REQUIRED") == true,
            hidden = hidden
        )
    }
}

data class SwaggerClassInfo(
    val description: String?,
    val tags: List<String>,
    val hidden: Boolean
)

data class SwaggerMethodInfo(
    val summary: String?,
    val description: String?,
    val tags: List<String>,
    val httpMethod: String?
)

data class SwaggerParamInfo(
    val description: String?,
    val defaultValue: String?,
    val required: Boolean,
    val hidden: Boolean
)

data class SwaggerFieldInfo(
    val description: String?,
    val name: String?,
    val required: Boolean,
    val hidden: Boolean
)
