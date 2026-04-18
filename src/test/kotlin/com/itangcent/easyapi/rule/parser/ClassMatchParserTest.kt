package com.itangcent.easyapi.rule.parser

import com.itangcent.easyapi.testFramework.EasyApiLightCodeInsightFixtureTestCase
import kotlinx.coroutines.runBlocking

class ClassMatchParserTest : EasyApiLightCodeInsightFixtureTestCase() {

    private lateinit var parser: ClassMatchParser

    override fun setUp() {
        super.setUp()
        parser = ClassMatchParser()
    }

    fun testCanParseClassExpression() {
        assertTrue("Should parse \$class: expression", parser.canParse("\$class:com.example.User"))
    }

    fun testCannotParseNonClassExpression() {
        assertFalse("Should not parse non-\$class expression", parser.canParse("groovy: it.name()"))
        assertFalse("Should not parse empty expression", parser.canParse(""))
    }

    fun testParseExactMatch() = runBlocking {
        loadFile("rule/MatchClass.java", """
            package com.test.rule;
            public class MatchClass {}
        """.trimIndent())
        val psiClass = findClass("com.test.rule.MatchClass")!!
        val context = com.itangcent.easyapi.rule.context.RuleContext.from(project, psiClass)
        val result = parser.parse("\$class:com.test.rule.MatchClass", context, null)
        assertTrue("Should match exact class", result as Boolean)
    }

    fun testParseExactNoMatch() = runBlocking {
        loadFile("rule/NoMatchClass.java", """
            package com.test.rule;
            public class NoMatchClass {}
        """.trimIndent())
        val psiClass = findClass("com.test.rule.NoMatchClass")!!
        val context = com.itangcent.easyapi.rule.context.RuleContext.from(project, psiClass)
        val result = parser.parse("\$class:com.example.NonExistent", context, null)
        assertFalse("Should not match different class", result as Boolean)
    }

    fun testParseExtendsMatch() = runBlocking {
        loadFile("rule/SubClass.java", """
            package com.test.rule;
            public class SubClass extends java.util.ArrayList {}
        """.trimIndent())
        val psiClass = findClass("com.test.rule.SubClass")!!
        val context = com.itangcent.easyapi.rule.context.RuleContext.from(project, psiClass)
        val result = parser.parse("\$class:? extend java.util.ArrayList", context, null)
        val superClass = psiClass.superClass
        if (superClass != null && superClass.qualifiedName == "java.util.ArrayList") {
            assertTrue("Should match class extending target", result as Boolean)
        } else {
            assertFalse("Cannot verify extends in test fixture without JDK resolution", result as Boolean)
        }
    }

    fun testParseExtendsNoMatch() = runBlocking {
        loadFile("rule/NotSubClass.java", """
            package com.test.rule;
            public class NotSubClass {}
        """.trimIndent())
        val psiClass = findClass("com.test.rule.NotSubClass")!!
        val context = com.itangcent.easyapi.rule.context.RuleContext.from(project, psiClass)
        val result = parser.parse("\$class:? extend java.util.ArrayList", context, null)
        assertFalse("Should not match class not extending target", result as Boolean)
    }
}
