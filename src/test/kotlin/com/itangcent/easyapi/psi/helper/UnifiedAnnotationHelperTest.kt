package com.itangcent.easyapi.psi.helper

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class UnifiedAnnotationHelperTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var helper: UnifiedAnnotationHelper

    override fun setUp() {
        super.setUp()
        helper = UnifiedAnnotationHelper()
    }

    fun testHelperCreation() {
        assertNotNull("UnifiedAnnotationHelper should be created", helper)
    }

    fun testHasAnnReturnsFalseForMissingAnnotation() = runBlocking {
        loadFile("helper/NoAnnoTest.java", """
            package com.test.helper;
            public class NoAnnoTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.NoAnnoTest")!!
        assertFalse("Should not find @Override annotation", helper.hasAnn(psiClass, "java.lang.Override"))
    }

    fun testFindAnnMapReturnsNullForMissingAnnotation() = runBlocking {
        loadFile("helper/MissingAnnoTest.java", """
            package com.test.helper;
            public class MissingAnnoTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.MissingAnnoTest")!!
        val map = helper.findAnnMap(psiClass, "java.lang.SuppressWarnings")
        assertNull("Should return null for missing annotation", map)
    }

    fun testFindAttrReturnsNullForMissingAnnotation() = runBlocking {
        loadFile("helper/MissingAttrTest.java", """
            package com.test.helper;
            public class MissingAttrTest {}
        """.trimIndent())
        val psiClass = findClass("com.test.helper.MissingAttrTest")!!
        val value = helper.findAttr(psiClass, "java.lang.Deprecated", "value")
        assertNull("Should return null for missing annotation attribute", value)
    }
}
