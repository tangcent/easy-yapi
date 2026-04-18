package com.itangcent.easyapi.psi.helper

import org.junit.Assert.*
import org.junit.Test

class AnnotationHelperTest {

    @Test
    fun testAnnotationHelperInterfaceDefinesMethods() {
        val methods = AnnotationHelper::class.java.methods
        val methodNames = methods.map { it.name }.toSet()
        assertTrue("Should define hasAnn", methodNames.contains("hasAnn"))
        assertTrue("Should define findAnnMap", methodNames.contains("findAnnMap"))
        assertTrue("Should define findAnnMaps", methodNames.contains("findAnnMaps"))
        assertTrue("Should define findAttr", methodNames.contains("findAttr"))
        assertTrue("Should define findAttrAsString", methodNames.contains("findAttrAsString"))
    }
}
