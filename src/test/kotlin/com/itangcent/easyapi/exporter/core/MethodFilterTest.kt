package com.itangcent.easyapi.exporter.core

import com.intellij.psi.PsiMethod
import org.junit.Assert.*
import org.junit.Test
import org.mockito.kotlin.mock

class MethodFilterTest {

    @Test
    fun testEmptyMethodFilter() {
        val filter = EmptyMethodFilter()
        val mockMethod: PsiMethod = mock()
        assertTrue("EmptyMethodFilter should always return true", filter.checkMethod(mockMethod))
    }
}
