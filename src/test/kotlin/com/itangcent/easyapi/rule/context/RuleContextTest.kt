package com.itangcent.easyapi.rule.context

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
}
