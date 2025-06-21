package com.itangcent.idea.plugin.rule

import com.itangcent.debug.LoggerCollector
import com.itangcent.idea.psi.ParseScriptContext
import com.itangcent.intellij.config.rule.parseBooleanRule
import com.itangcent.intellij.config.rule.parseEventRule
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.logger.Logger

class FieldPatternRuleParserTest : RuleParserBaseTest() {

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(Logger::class) { it.with(LoggerCollector::class) }
    }

    override fun ruleParserClass() = FieldPatternRuleParser::class


    fun testContextOf() {
        val context = ruleParser.contextOf(userCtrlPsiClass, userCtrlPsiClass)
        assertEquals(userCtrlPsiClass, context.getCore())
    }


    fun testParseBooleanRule() {
        run {
            val context = ruleParser.contextOf(modelPsiClass.fields[0], modelPsiClass.fields[0])
            context.setExt("fieldContext", FakeParseScriptContext("str"))
            assertEquals(true, ruleParser.parseBooleanRule("*")!!(context))
            assertEquals(true, ruleParser.parseBooleanRule("str")!!(context))
            assertEquals(true, ruleParser.parseBooleanRule("*.str")!!(context))
            assertEquals(false, ruleParser.parseBooleanRule("none")!!(context))
            assertEquals(false, ruleParser.parseBooleanRule("*.none")!!(context))
            assertEquals(true, ruleParser.parseBooleanRule("*.str|string")!!(context))
            assertEquals(false, ruleParser.parseBooleanRule("*.none|string")!!(context))
            assertEquals(true, ruleParser.parseBooleanRule("*.str|*")!!(context))
            assertEquals(false, ruleParser.parseBooleanRule("*.str|int")!!(context))
        }
        run {
            val context = ruleParser.contextOf(modelPsiClass.fields[1], modelPsiClass.fields[1])
            context.setExt("fieldContext", FakeParseScriptContext("integer"))
            assertEquals(true, ruleParser.parseBooleanRule("*")!!(context))
            assertEquals(true, ruleParser.parseBooleanRule("integer")!!(context))
            assertEquals(true, ruleParser.parseBooleanRule("*.integer")!!(context))
            assertEquals(false, ruleParser.parseBooleanRule("none")!!(context))
            assertEquals(false, ruleParser.parseBooleanRule("*.none")!!(context))
            assertEquals(true, ruleParser.parseBooleanRule("*.integer|int")!!(context))
            assertEquals(false, ruleParser.parseBooleanRule("*.none|int")!!(context))
            assertEquals(true, ruleParser.parseBooleanRule("*.integer|*")!!(context))
            assertEquals(false, ruleParser.parseBooleanRule("*.integer|string")!!(context))
        }
    }


    fun testParseEventRule() {
        org.junit.jupiter.api.assertThrows<NotImplementedError> {
            ruleParser.parseEventRule("str")
        }
    }


    fun testParseStringRule() {
        org.junit.jupiter.api.assertThrows<NotImplementedError> {
            ruleParser.parseEventRule("str")
        }
    }
}


class FakeParseScriptContext(private val path: String) : ParseScriptContext {
    override fun path(): String {
        return path
    }

    override fun property(property: String): String {
        return if (path.isEmpty()) {
            property
        } else {
            "$path.$property"
        }
    }

}