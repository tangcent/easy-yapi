package com.itangcent.easyapi.ide.script

import org.junit.Assert.*
import org.junit.Test

class GeneralScriptSupportTest {

    @Test
    fun testBuildScriptReturnsInputUnchanged() {
        val input = "some.annotation.RequestMapping"
        assertEquals("GeneralScriptSupport should pass through script unchanged",
            input, GeneralScriptSupport.buildScript(input))
    }

    @Test
    fun testBuildPropertyReturnsInputUnchanged() {
        val input = "property.expression"
        assertEquals("GeneralScriptSupport should pass through property unchanged",
            input, GeneralScriptSupport.buildProperty(input))
    }

    @Test
    fun testCheckSupportAlwaysReturnsTrue() {
        assertTrue("GeneralScriptSupport should always be supported",
            GeneralScriptSupport.checkSupport())
    }

    @Test
    fun testSuffixReturnsTxt() {
        assertEquals("txt", GeneralScriptSupport.suffix())
    }

    @Test
    fun testDemoCodeReturnsAnnotation() {
        assertTrue("Demo code should contain Spring annotation",
            GeneralScriptSupport.demoCode().contains("RequestMapping"))
    }

    @Test
    fun testToStringReturnsGeneral() {
        assertEquals("General", GeneralScriptSupport.toString())
    }

    @Test
    fun testEqualsIsReferenceEquality() {
        assertSame("GeneralScriptSupport is a singleton", GeneralScriptSupport, GeneralScriptSupport)
        assertEquals("Same reference should be equal", GeneralScriptSupport, GeneralScriptSupport)
        assertNotEquals("Different type should not be equal", GeneralScriptSupport, "General")
    }

    @Test
    fun testBuildScriptWithEmptyString() {
        assertEquals("", GeneralScriptSupport.buildScript(""))
    }

    @Test
    fun testBuildPropertyWithEmptyString() {
        assertEquals("", GeneralScriptSupport.buildProperty(""))
    }
}

class GroovyScriptSupportTest {

    @Test
    fun testBuildScriptAddsPrefix() {
        val result = GroovyScriptSupport.buildScript("println 'hello'")
        assertEquals("groovy:println 'hello'", result)
    }

    @Test
    fun testBuildPropertyWrapsInCodeBlock() {
        val result = GroovyScriptSupport.buildProperty("it.name")
        assertEquals("groovy:```\nit.name\n```", result)
    }

    @Test
    fun testSuffixReturnsGroovy() {
        assertEquals("groovy", GroovyScriptSupport.suffix())
    }

    @Test
    fun testScriptTypeReturnsGroovy() {
        assertEquals("groovy", GroovyScriptSupport.scriptType())
    }

    @Test
    fun testPrefixReturnsScriptType() {
        assertEquals("groovy", GroovyScriptSupport.prefix())
    }

    @Test
    fun testToStringReturnsGroovy() {
        assertEquals("Groovy", GroovyScriptSupport.toString())
    }

    @Test
    fun testEqualsIsReferenceEquality() {
        assertSame("GroovyScriptSupport is a singleton", GroovyScriptSupport, GroovyScriptSupport)
        assertEquals("Same reference should be equal", GroovyScriptSupport, GroovyScriptSupport)
        assertNotEquals("Different type should not be equal", GroovyScriptSupport, "Groovy")
    }

    @Test
    fun testDemoCodeIsNotBlank() {
        assertTrue("Demo code should not be blank", GroovyScriptSupport.demoCode().isNotBlank())
    }

    @Test
    fun testDemoCodeContainsVariables() {
        assertTrue("Demo code should reference tool variable",
            GroovyScriptSupport.demoCode().contains("tool"))
    }

    @Test
    fun testBuildScriptWithEmptyString() {
        assertEquals("groovy:", GroovyScriptSupport.buildScript(""))
    }

    @Test
    fun testBuildPropertyWithEmptyString() {
        assertEquals("groovy:```\n\n```", GroovyScriptSupport.buildProperty(""))
    }
}

class AbstractScriptSupportConcreteTest : AbstractScriptSupport() {

    override fun suffix(): String = "test"

    override fun scriptType(): String = "testengine"

    override fun demoCode(): String = "test demo"

    @Test
    fun testBuildScriptAddsCustomPrefix() {
        val concrete = AbstractScriptSupportConcreteTest()
        assertEquals("testengine:script content", concrete.buildScript("script content"))
    }

    @Test
    fun testBuildPropertyWrapsInCodeBlock() {
        val concrete = AbstractScriptSupportConcreteTest()
        assertEquals("testengine:```\nprop\n```", concrete.buildProperty("prop"))
    }

    @Test
    fun testPrefixDefaultsToScriptType() {
        val concrete = AbstractScriptSupportConcreteTest()
        assertEquals("testengine", concrete.prefix())
    }

    @Test
    fun testCheckSupportReturnsFalseForUnavailableEngine() {
        val concrete = AbstractScriptSupportConcreteTest()
        assertFalse("Non-existent engine should not be supported",
            concrete.checkSupport())
    }
}

class ScriptSupportsListTest {

    @Test
    fun testScriptSupportsContainsGeneral() {
        assertTrue("scriptSupports should contain GeneralScriptSupport",
            scriptSupports.contains(GeneralScriptSupport))
    }

    @Test
    fun testScriptSupportsContainsGroovy() {
        assertTrue("scriptSupports should contain GroovyScriptSupport",
            scriptSupports.contains(GroovyScriptSupport))
    }

    @Test
    fun testScriptSupportsSize() {
        assertEquals("Should have 2 script support implementations", 2, scriptSupports.size)
    }

    @Test
    fun testScriptSupportsGeneralIsFirst() {
        assertSame("GeneralScriptSupport should be first", GeneralScriptSupport, scriptSupports[0])
    }

    @Test
    fun testScriptSupportsGroovyIsSecond() {
        assertSame("GroovyScriptSupport should be second", GroovyScriptSupport, scriptSupports[1])
    }
}
