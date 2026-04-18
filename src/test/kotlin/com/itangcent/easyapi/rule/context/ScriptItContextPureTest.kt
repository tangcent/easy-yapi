package com.itangcent.easyapi.rule.context

import org.junit.Assert.*
import org.junit.Test

class ScriptItContextPureTest {

    @Test
    fun testScriptItContextClassExists() {
        val clazz = Class.forName("com.itangcent.easyapi.rule.context.ScriptItContext")
        assertNotNull("ScriptItContext class should exist", clazz)
    }

    @Test
    fun testScriptItContextMethods() {
        val methods = ScriptItContext::class.java.methods.map { it.name }.toSet()
        assertTrue("Should have name() method", methods.contains("name"))
        assertTrue("Should hasAnn() method", methods.contains("hasAnn"))
        assertTrue("Should have doc() method", methods.contains("doc"))
        assertTrue("Should have ann() method", methods.contains("ann"))
        assertTrue("Should have sourceCode() method", methods.contains("sourceCode"))
        assertTrue("Should have getExt() method", methods.contains("getExt"))
        assertTrue("Should have setExt() method", methods.contains("setExt"))
    }
}
