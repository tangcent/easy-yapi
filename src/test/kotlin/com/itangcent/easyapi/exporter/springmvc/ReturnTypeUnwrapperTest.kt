package com.itangcent.easyapi.exporter.springmvc

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class ReturnTypeUnwrapperTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testUnwrapNullType() {
        val result = ReturnTypeUnwrapper.unwrapPsiType(null)
        assertNull("Should return null for null input", result)
    }

    fun testUnwrapPlainType() = runBlocking {
        loadFile("springmvc/PlainDto.java", """
            package com.test.springmvc;
            public class PlainDto {
                private String name;
            }
        """.trimIndent())
        val psiClass = findClass("com.test.springmvc.PlainDto")!!
        val classType = com.intellij.psi.util.PsiTypesUtil.getClassType(psiClass)
        val result = ReturnTypeUnwrapper.unwrapPsiType(classType)
        assertNotNull("Should return a type for plain class", result)
    }
}
