package com.itangcent.easyapi.core.rule.context

import com.itangcent.easyapi.core.export.ApiEndpoint
import com.itangcent.easyapi.core.export.HttpMetadata
import com.itangcent.easyapi.core.export.HttpMethod
import com.itangcent.easyapi.core.internal.threading.readSync
import com.itangcent.easyapi.core.psi.type.ResolvedType
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

    /**
     * `canonicalText` / `matchText` for a [com.itangcent.easyapi.core.psi.type.ResolvedMethod]
     * context: `class#method` and the resolved return type.
     *
     * Both getters wrap their resolved branch in `readSync` so that lifecycle event rules —
     * which run on the background pool without a read action — cannot hit the PSI read-lock
     * assertion. That contract is **not** assertable here: locating the class with
     * `findClass` already warms `psiClass.qualifiedName`, so a bare-thread probe stays green
     * with or without the `readSync` (a vacuous test). The lock contract is enforced by
     * AGENTS.md "boundary classes self-protect unconditionally" instead; this test pins the
     * values so the wrappers cannot silently change them.
     */
    fun testResolvedContextText() {
        loadFile("rule/ResolvedHolder.java", """
            package com.test.rule;
            public class ResolvedHolder {
                public ResolvedHolder self() {
                    return this;
                }
            }
        """.trimIndent())
        val psiClass = findClass("com.test.rule.ResolvedHolder")!!
        val resolvedMethod = readSync {
            ResolvedType.ClassType(psiClass, emptyList())
                .suitableMethods()
                .single { it.name == "self" }
        }
        val context = RuleContext.from(project, resolvedMethod)

        assertEquals("com.test.rule.ResolvedHolder#self", context.canonicalText)
        assertEquals("com.test.rule.ResolvedHolder", context.matchText)
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
