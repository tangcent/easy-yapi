package com.itangcent.easyapi.rule.context

import com.itangcent.easyapi.exporter.model.ApiEndpoint
import com.itangcent.easyapi.exporter.model.HttpMetadata
import com.itangcent.easyapi.exporter.model.HttpMethod
import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase

class RuleContextTest : EasyApiLightCodeInsightFixtureTestCase() {

    fun testFromCreatesContextWithElement() {
        loadFile("rule/SimpleClass.java", """
            package com.test.rule;
            public class SimpleClass {
                private String name;
            }
        """.trimIndent())
        val psiClass = findClass("com.test.rule.SimpleClass")!!
        val context = RuleContext.from(project, psiClass)
        assertNotNull("Context should have project", context.project)
        assertEquals("Context element should be the class", psiClass, context.element)
    }

    fun testWithoutElementCreatesContext() {
        val context = RuleContext.withoutElement(project)
        assertNotNull("Context should have project", context.project)
        assertNull("Context element should be null", context.element)
    }

    fun testExtensions() {
        val context = RuleContext.withoutElement(project)
        assertNull("Extension should be null initially", context.getExt("test"))
        context.setExt("test", "value")
        assertEquals("Extension should be set", "value", context.getExt("test"))
    }

    fun testFieldContext() {
        loadFile("rule/FieldContextClass.java", """
            package com.test.rule;
            public class FieldContextClass {
                private String name;
            }
        """.trimIndent())
        val psiClass = findClass("com.test.rule.FieldContextClass")!!
        val context = RuleContext.from(project, psiClass, "user.name")
        assertEquals("Field context should be set", "user.name", context.fieldContext)
    }

    fun testExtsReturnsAllExtensions() {
        val context = RuleContext.withoutElement(project)
        assertTrue("Extensions should be empty initially", context.exts().isEmpty())
        context.setExt("key1", "value1")
        context.setExt("key2", "value2")
        assertEquals("Should have 2 extensions", 2, context.exts().size)
        assertEquals("value1", context.exts()["key1"])
        assertEquals("value2", context.exts()["key2"])
    }

    fun testWrapExtWithNullValue() {
        val context = RuleContext.withoutElement(project)
        val result = context.wrapExt("anyKey", null)
        assertNull("Should return null for null value", result)
    }

    fun testWrapExtWithFieldContext() {
        val context = RuleContext.withoutElement(project)
        val result = context.wrapExt("fieldContext", "user.name")
        assertTrue("Should return ScriptFieldPathContext", result is ScriptFieldPathContext)
        assertEquals("user.name", (result as ScriptFieldPathContext).path())
    }

    fun testWrapExtWithApiEndpoint() {
        val context = RuleContext.withoutElement(project)
        val endpoint = ApiEndpoint(
            name = "test",
            metadata = HttpMetadata(method = HttpMethod.GET, path = "/api/test")
        )
        val result = context.wrapExt("endpoint", endpoint)
        assertTrue("Should return ScriptApiEndpoint", result is ScriptApiEndpoint)
        assertEquals("test", (result as ScriptApiEndpoint).name())
    }

    fun testWrapExtWithNonSpecialValue() {
        val context = RuleContext.withoutElement(project)
        val result = context.wrapExt("custom", "plain string")
        assertEquals("Should return value as-is", "plain string", result)
    }

    fun testWrapExtWithIntegerValue() {
        val context = RuleContext.withoutElement(project)
        val result = context.wrapExt("count", 42)
        assertEquals("Should return integer as-is", 42, result)
    }

    fun testToStringWithoutElement() {
        val context = RuleContext.withoutElement(project)
        assertEquals("anonymous", context.toString())
    }

    fun testCanonicalTextWithClass() {
        loadFile("rule/CanonicalClass.java", """
            package com.test.rule;
            public class CanonicalClass {
            }
        """.trimIndent())
        val psiClass = findClass("com.test.rule.CanonicalClass")!!
        val context = RuleContext.from(project, psiClass)
        assertEquals("com.test.rule.CanonicalClass", context.canonicalText)
    }

    fun testRegexGroups() {
        val context = RuleContext.withoutElement(project)
        assertNull("Regex groups should be null initially", context.regexGroups)
        context.regexGroups = listOf("match1", "match2")
        assertEquals("Should have 2 groups", 2, context.regexGroups!!.size)
    }

    fun testWithElement() {
        loadFile("rule/WithElementClass.java", """
            package com.test.rule;
            public class WithElementClass {
                private String field;
            }
        """.trimIndent())
        val psiClass = findClass("com.test.rule.WithElementClass")!!
        val originalContext = RuleContext.from(project, psiClass)
        val psiField = psiClass.fields.first()
        val newContext = originalContext.withElement(psiField, "field")
        assertEquals("New context should have field as element", psiField, newContext.element)
        assertEquals("Field context should be set", "field", newContext.fieldContext)
    }
}
