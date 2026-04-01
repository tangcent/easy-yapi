package com.itangcent.easyapi.exporter.swagger

import com.intellij.psi.PsiAnnotation
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiField
import com.intellij.psi.PsiMethod
import com.intellij.psi.PsiParameter
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SwaggerAnnotationProcessorTest {
    
    @Test
    fun testProcessClassAnnotation() {
        val annotation = mock<PsiAnnotation>()
        whenever(annotation.qualifiedName).thenReturn("io.swagger.annotations.Api")
        
        val result = SwaggerAnnotationProcessor.processClassAnnotation(
            mock<PsiClass>(),
            annotation
        )
        
        assertNotNull("Result should not be null", result)
    }
    
    @Test
    fun testProcessMethodAnnotation() {
        val annotation = mock<PsiAnnotation>()
        whenever(annotation.qualifiedName).thenReturn("io.swagger.annotations.ApiOperation")
        
        val result = SwaggerAnnotationProcessor.processMethodAnnotation(
            mock<PsiMethod>(),
            annotation
        )
        
        assertNotNull("Result should not be null", result)
    }
    
    @Test
    fun testProcessParameterAnnotation() {
        val annotation = mock<PsiAnnotation>()
        whenever(annotation.qualifiedName).thenReturn("io.swagger.annotations.ApiParam")
        
        val result = SwaggerAnnotationProcessor.processParameterAnnotation(
            mock<PsiParameter>(),
            annotation
        )
        
        assertNotNull("Result should not be null", result)
    }
    
    @Test
    fun testProcessFieldAnnotation() {
        val annotation = mock<PsiAnnotation>()
        whenever(annotation.qualifiedName).thenReturn("io.swagger.annotations.ApiModelProperty")
        
        val result = SwaggerAnnotationProcessor.processFieldAnnotation(
            mock<PsiField>(),
            annotation
        )
        
        assertNotNull("Result should not be null", result)
    }
    
    @Test
    fun testSwaggerConfigLoader() {
        val swagger2Config = SwaggerConfigLoader.getSwagger2Config()
        assertNotNull("Swagger2 config should not be null", swagger2Config)
        assertTrue("Swagger2 config should not be empty", swagger2Config.isNotEmpty())
        
        val swagger3Config = SwaggerConfigLoader.getSwagger3Config()
        assertNotNull("Swagger3 config should not be null", swagger3Config)
        assertTrue("Swagger3 config should not be empty", swagger3Config.isNotEmpty())
        
        val allConfig = SwaggerConfigLoader.getAllConfig()
        assertNotNull("All config should not be null", allConfig)
        assertTrue("All config should not be empty", allConfig.isNotEmpty())
    }
}
