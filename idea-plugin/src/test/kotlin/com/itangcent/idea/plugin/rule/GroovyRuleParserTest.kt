package com.itangcent.idea.plugin.rule

import com.itangcent.debug.LoggerCollector
import com.itangcent.intellij.config.rule.*
import com.itangcent.intellij.context.ActionContextBuilder
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.logger.Logger
import com.itangcent.mock.toUnixString
import org.junit.jupiter.api.assertThrows

/**
 * Test case of [GroovyRuleParser]
 */
internal class GroovyRuleParserTest : RuleParserBaseTest() {

    override fun bind(builder: ActionContextBuilder) {
        super.bind(builder)
        builder.bind(Logger::class) { it.with(LoggerCollector::class) }
    }

    override fun ruleParserClass() = GroovyRuleParser::class

    fun testContextOf() {
        val context = ruleParser.contextOf(userCtrlPsiClass, userCtrlPsiClass)
        assertThrows<IllegalArgumentException> {
            ruleParser.contextOf(context, userCtrlPsiClass)
        }
    }

    fun testParseStringRule() {
        val ruleReadRequestMapping: StringRule =
                ruleParser.parseStringRule("groovy:it.ann(\"org.springframework.web.bind.annotation.RequestMapping\")")!!
        assertEquals(
                "/greeting",
                ruleReadRequestMapping(ruleParser.contextOf(greetingPsiMethod, greetingPsiMethod))
        )
        assertEquals(
                null,
                ruleReadRequestMapping(ruleParser.contextOf(getUserInfoPsiMethod, getUserInfoPsiMethod))
        )


        val ruleReadGetMapping: StringRule = ruleParser.parseStringRule("groovy:it.ann(\"org.springframework.web.bind.annotation.GetMapping\")")!!
        assertEquals(null, ruleReadGetMapping(ruleParser.contextOf(greetingPsiMethod, greetingPsiMethod)))
        assertEquals(
                "/get/{id}",
                ruleReadGetMapping(ruleParser.contextOf(getUserInfoPsiMethod, getUserInfoPsiMethod))
        )

        val ruleReadTagFolder: StringRule = ruleParser.parseStringRule("groovy:it.doc(\"folder\")")!!
        assertEquals(null, ruleReadTagFolder(ruleParser.contextOf(greetingPsiMethod, greetingPsiMethod)))
        assertEquals(
                "update-apis",
                ruleReadTagFolder(ruleParser.contextOf(getUserInfoPsiMethod, getUserInfoPsiMethod))
        )
    }

    fun testParseBooleanRule() {

        val ruleCheckPublic: BooleanRule = ruleParser.parseBooleanRule("groovy:it.hasAnn(\"org.springframework.web.bind.annotation.RequestMapping\")")!!
        assertEquals(true, ruleCheckPublic(ruleParser.contextOf(greetingPsiMethod, greetingPsiMethod)))
        assertEquals(false, ruleCheckPublic(ruleParser.contextOf(getUserInfoPsiMethod, getUserInfoPsiMethod)))

        val ruleCheckNotPublic: BooleanRule = ruleParser.parseBooleanRule("groovy:!it.hasAnn(\"org.springframework.web.bind.annotation.RequestMapping\")")!!
        assertEquals(false, ruleCheckNotPublic(ruleParser.contextOf(greetingPsiMethod, greetingPsiMethod)))
        assertEquals(true, ruleCheckNotPublic(ruleParser.contextOf(getUserInfoPsiMethod, getUserInfoPsiMethod)))

        val ruleCheckDeprecated: BooleanRule = ruleParser.parseBooleanRule("groovy:it.hasAnn(\"java.lang.Deprecated\")")!!
        assertEquals(false, ruleCheckDeprecated(ruleParser.contextOf(greetingPsiMethod, greetingPsiMethod)))
        assertEquals(
                true,
                ruleCheckDeprecated(ruleParser.contextOf(getUserInfoPsiMethod, getUserInfoPsiMethod))
        )

        val ruleCheckUndone: BooleanRule = ruleParser.parseBooleanRule("groovy:it.hasDoc(\"undone\")")!!
        assertEquals(false, ruleCheckUndone(ruleParser.contextOf(greetingPsiMethod, greetingPsiMethod)))
        assertEquals(
                true,
                ruleCheckUndone(ruleParser.contextOf(getUserInfoPsiMethod, getUserInfoPsiMethod))
        )

        val ruleCheckDone: BooleanRule = ruleParser.parseBooleanRule("groovy:!it.hasDoc(\"undone\")")!!
        assertEquals(true, ruleCheckDone(ruleParser.contextOf(greetingPsiMethod, greetingPsiMethod)))
        assertEquals(
                false,
                ruleCheckDone(ruleParser.contextOf(getUserInfoPsiMethod, getUserInfoPsiMethod))
        )

        for (script in arrayOf(
                "groovy:it.isExtend(\"java.util.Collection\")",
                "groovy:it.isCollection()"
        )) {
            val ruleCheckIsCollection = ruleParser.parseBooleanRule(script)!!
            assertEquals(false, ruleCheckIsCollection(ruleParser.contextOf(modelPsiClass, modelPsiClass)))
            assertEquals(
                    true,
                    ruleCheckIsCollection(ruleParser.contextOf(listPsiClass, listPsiClass))
            )
        }
    }

    fun testParseEventRule() {
        LoggerCollector.getLog()
        ruleParser.parseEventRule("groovy:logger.info(\"hello world\")")!!(ruleParser.contextOf(listPsiClass, listPsiClass))
        assertEquals("[INFO]\thello world\n", LoggerCollector.getLog().toUnixString())
    }
}