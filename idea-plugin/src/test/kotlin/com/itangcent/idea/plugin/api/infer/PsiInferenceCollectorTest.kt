package com.itangcent.idea.plugin.api.infer

import com.google.inject.Inject
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiMethod
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.testFramework.PluginContextLightCodeInsightFixtureTestCase

/**
 * Test case for [PsiInferenceCollector]
 */
class PsiInferenceCollectorTest : PluginContextLightCodeInsightFixtureTestCase() {

    @Inject
    private lateinit var psiInferenceCollector: PsiInferenceCollector

    private lateinit var userInfoClass: PsiClass
    private lateinit var resultClass: PsiClass
    private lateinit var modelClass: PsiClass
    private lateinit var testMethod: PsiMethod

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
    }

    override fun setUp() {
        super.setUp()
        
        // Load necessary classes for testing
        loadSource(Object::class)
        loadSource(Collection::class)
        loadSource(List::class)
        
        // Load test classes
        userInfoClass = loadClass("model/UserInfo.java")!!
        resultClass = loadClass("model/Result.java")!!
        modelClass = loadClass("model/Model.java")!!
        
        // Get a method to use as context
        testMethod = loadClass("api/TestCtrl.java")!!.methods[0]
    }

    fun testCollectAdditionalClassInfo_ExistingClasses() {
        // Test collecting information for classes that exist
        val classNames = listOf(
            "com.itangcent.model.UserInfo",
            "com.itangcent.model.Result"
        )
        
        val result = psiInferenceCollector.collectAdditionalClassInfo(classNames, testMethod)
        
        // Verify the result contains information for both classes
        assertEquals(2, result.size)
        
        // Verify UserInfo class info
        val userInfo = result.find { it.name.endsWith("UserInfo") }
        assertNotNull(userInfo)
        userInfo?.let {
            // Check for expected fields in UserInfo
            assertTrue(it.fields.any { field -> field.name == "id" })
            assertTrue(it.fields.any { field -> field.name == "name" })
            assertTrue(it.fields.any { field -> field.name == "age" })
            
            // Check for expected methods in UserInfo
            assertTrue(it.methods.any { method -> method.name == "getId" })
            assertTrue(it.methods.any { method -> method.name == "getName" })
            assertTrue(it.methods.any { method -> method.name == "getAge" })
        }
        
        // Verify Result class info
        val resultInfo = result.find { it.name.endsWith("Result") }
        assertNotNull(resultInfo)
        resultInfo?.let {
            // Check for expected fields in Result
            assertTrue(it.fields.any { field -> field.name == "code" })
            assertTrue(it.fields.any { field -> field.name == "msg" })
            assertTrue(it.fields.any { field -> field.name == "data" })
            
            // Check for expected methods in Result
            assertTrue(it.methods.any { method -> method.name == "getCode" })
            assertTrue(it.methods.any { method -> method.name == "getMsg" })
            assertTrue(it.methods.any { method -> method.name == "getData" })
        }
    }

    fun testCollectAdditionalClassInfo_NonExistentClass() {
        // Test collecting information for a class that doesn't exist
        val classNames = listOf("com.itangcent.model.NonExistentClass")
        
        val result = psiInferenceCollector.collectAdditionalClassInfo(classNames, testMethod)
        
        // Verify the result is empty since the class doesn't exist
        assertTrue(result.isEmpty())
    }

    fun testCollectAdditionalClassInfo_MixedClasses() {
        // Test collecting information for a mix of existing and non-existent classes
        val classNames = listOf(
            "com.itangcent.model.UserInfo",
            "com.itangcent.model.NonExistentClass",
            "com.itangcent.model.Model"
        )
        
        val result = psiInferenceCollector.collectAdditionalClassInfo(classNames, testMethod)
        
        // Verify the result contains information only for the existing classes
        assertEquals(2, result.size)
        
        // Verify the class names in the result
        val classNamesInResult = result.map { it.name.substringAfterLast('.') }.toSet()
        assertTrue(classNamesInResult.contains("UserInfo"))
        assertTrue(classNamesInResult.contains("Model"))
        assertFalse(classNamesInResult.contains("NonExistentClass"))
    }

    fun testCollectAdditionalClassInfo_SimpleClassName() {
        // Test collecting information using simple class names instead of fully qualified names
        val classNames = listOf("UserInfo", "Model")
        
        val result = psiInferenceCollector.collectAdditionalClassInfo(classNames, testMethod)
        
        // Verify the result contains information for both classes
        assertEquals(1, result.size)
        
        // Verify the class names in the result
        val classNamesInResult = result.map { it.name.substringAfterLast('.') }.toSet()
        assertTrue(classNamesInResult.contains("UserInfo"))
        assertFalse(classNamesInResult.contains("Model"))
    }

    fun testCollectAdditionalClassInfo_FieldsAndMethods() {
        // Test that fields and methods are correctly collected
        val classNames = listOf("com.itangcent.model.Result")
        
        val result = psiInferenceCollector.collectAdditionalClassInfo(classNames, testMethod)
        
        // Verify the result contains the Result class
        assertEquals(1, result.size)
        val resultInfo = result[0]
        
        // Verify fields
        assertTrue(resultInfo.fields.any { it.name == "code" })
        assertTrue(resultInfo.fields.any { it.name == "msg" })
        assertTrue(resultInfo.fields.any { it.name == "data" })
        
        // Verify methods
        assertTrue(resultInfo.methods.any { it.name == "getCode" })
        assertTrue(resultInfo.methods.any { it.name == "getMsg" })
        assertTrue(resultInfo.methods.any { it.name == "getData" })
        
        // Verify field types
        val codeField = resultInfo.fields.find { it.name == "code" }
        assertNotNull(codeField)
        assertEquals("Integer", codeField?.type)
        
        // Verify method return types
        val getCodeMethod = resultInfo.methods.find { it.name == "getCode" }
        assertNotNull(getCodeMethod)
        assertEquals("Integer", getCodeMethod?.returnType)
    }

    fun testCollectAdditionalClassInfo_Annotations() {
        // Test that annotations are correctly collected
        val classNames = listOf("com.itangcent.model.UserInfo")
        
        val result = psiInferenceCollector.collectAdditionalClassInfo(classNames, testMethod)
        
        // Verify the result contains the UserInfo class
        assertEquals(1, result.size)
        val userInfo = result[0]
        
        // Verify field annotations
        val nameField = userInfo.fields.find { it.name == "name" }
        assertNotNull(nameField)
        assertTrue(nameField!!.annotations.any { it.contains("NotBlank") })
        
        val ageField = userInfo.fields.find { it.name == "age" }
        assertNotNull(ageField)
        assertTrue(ageField!!.annotations.any { it.contains("NotNull") })
    }
} 