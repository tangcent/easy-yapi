package com.itangcent.idea.plugin.rule

import com.itangcent.debug.LoggerCollector
import com.itangcent.intellij.config.rule.BooleanRule
import com.itangcent.intellij.config.rule.StringRule
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.logger.Logger
import com.itangcent.mock.toUnixString

/**
 * Test case of [SuvRuleParser]
 */
internal class SuvRuleParserTest : RuleParserBaseTest() {

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(Logger::class) { it.with(LoggerCollector::class) }
    }

    override fun ruleParserClass() = SuvRuleParser::class

    fun testContextOf() {
        val context = ruleParser.contextOf(userCtrlPsiClass, userCtrlPsiClass)
        assertEquals(context, ruleParser.contextOf(context, userCtrlPsiClass))
    }

    fun testParseStringRule() {
        assertNull(ruleParser.parseStringRule(""))
        assertNull(ruleParser.parseStringRule("\t\n"))
        for (script in arrayOf(
                "@org.springframework.web.bind.annotation.RequestMapping",
                "js:it.ann(\"org.springframework.web.bind.annotation.RequestMapping\")",
                "groovy:it.ann(\"org.springframework.web.bind.annotation.RequestMapping\")"
        )) {
            val ruleReadRequestMapping: StringRule =
                    ruleParser.parseStringRule(script)!!
            assertEquals(
                    "/greeting",
                    ruleReadRequestMapping.compute(ruleParser.contextOf(greetingPsiMethod, greetingPsiMethod))
            )
            assertEquals(
                    null,
                    ruleReadRequestMapping.compute(ruleParser.contextOf(getUserInfoPsiMethod, getUserInfoPsiMethod))
            )
        }

        for (script in arrayOf(
                "@org.springframework.web.bind.annotation.GetMapping",
                "js:it.ann(\"org.springframework.web.bind.annotation.GetMapping\")",
                "groovy:it.ann(\"org.springframework.web.bind.annotation.GetMapping\")"
        )) {
            val ruleReadGetMapping: StringRule = ruleParser.parseStringRule(script)!!
            assertEquals(null, ruleReadGetMapping.compute(ruleParser.contextOf(greetingPsiMethod, greetingPsiMethod)))
            assertEquals(
                    "/get/{id}",
                    ruleReadGetMapping.compute(ruleParser.contextOf(getUserInfoPsiMethod, getUserInfoPsiMethod))
            )
        }

        for (script in arrayOf(
                "#folder",
                "js:it.doc(\"folder\")",
                "groovy:it.doc(\"folder\")"
        )) {
            val ruleReadTagFolder: StringRule = ruleParser.parseStringRule(script)!!
            assertEquals(null, ruleReadTagFolder.compute(ruleParser.contextOf(greetingPsiMethod, greetingPsiMethod)))
            assertEquals(
                    "update-apis",
                    ruleReadTagFolder.compute(ruleParser.contextOf(getUserInfoPsiMethod, getUserInfoPsiMethod))
            )
        }

    }

    fun testParseBooleanRule() {

        assertNull(ruleParser.parseBooleanRule(""))
        assertNull(ruleParser.parseBooleanRule("\t\n"))
        for (script in arrayOf(
                "@com.itangcent.common.annotation.Public",
                "js:it.hasAnn(\"org.springframework.web.bind.annotation.RequestMapping\")",
                "groovy:it.hasAnn(\"org.springframework.web.bind.annotation.RequestMapping\")"
        )) {
            val ruleCheckPublic: BooleanRule = ruleParser.parseBooleanRule(script)!!
            assertEquals(true, ruleCheckPublic.compute(ruleParser.contextOf(greetingPsiMethod, greetingPsiMethod)))
            assertEquals(false, ruleCheckPublic.compute(ruleParser.contextOf(getUserInfoPsiMethod, getUserInfoPsiMethod)))
        }

        for (script in arrayOf(
                "!@com.itangcent.common.annotation.Public",
                "js:!it.hasAnn(\"org.springframework.web.bind.annotation.RequestMapping\")",
                "groovy:!it.hasAnn(\"org.springframework.web.bind.annotation.RequestMapping\")"
        )) {
            val ruleCheckNotPublic: BooleanRule = ruleParser.parseBooleanRule(script)!!
            assertEquals(false, ruleCheckNotPublic.compute(ruleParser.contextOf(greetingPsiMethod, greetingPsiMethod)))
            assertEquals(true, ruleCheckNotPublic.compute(ruleParser.contextOf(getUserInfoPsiMethod, getUserInfoPsiMethod)))
        }

        for (script in arrayOf(
                "@java.lang.Deprecated",
                "js:it.hasAnn(\"java.lang.Deprecated\")",
                "groovy:it.hasAnn(\"java.lang.Deprecated\")"
        )) {
            val ruleCheckDeprecated: BooleanRule = ruleParser.parseBooleanRule(script)!!
            assertEquals(false, ruleCheckDeprecated.compute(ruleParser.contextOf(greetingPsiMethod, greetingPsiMethod)))
            assertEquals(
                    true,
                    ruleCheckDeprecated.compute(ruleParser.contextOf(getUserInfoPsiMethod, getUserInfoPsiMethod))
            )
        }

        for (script in arrayOf(
                "#undone",
                "js:it.hasDoc(\"undone\")",
                "groovy:it.hasDoc(\"undone\")"
        )) {
            val ruleCheckUndone: BooleanRule = ruleParser.parseBooleanRule(script)!!
            assertEquals(false, ruleCheckUndone.compute(ruleParser.contextOf(greetingPsiMethod, greetingPsiMethod)))
            assertEquals(
                    true,
                    ruleCheckUndone.compute(ruleParser.contextOf(getUserInfoPsiMethod, getUserInfoPsiMethod))
            )
        }

        for (script in arrayOf(
                "!#undone",
                "js:!it.hasDoc(\"undone\")",
                "groovy:!it.hasDoc(\"undone\")"
        )) {
            val ruleCheckDone: BooleanRule = ruleParser.parseBooleanRule(script)!!
            assertEquals(true, ruleCheckDone.compute(ruleParser.contextOf(greetingPsiMethod, greetingPsiMethod)))
            assertEquals(
                    false,
                    ruleCheckDone.compute(ruleParser.contextOf(getUserInfoPsiMethod, getUserInfoPsiMethod))
            )
        }

        for (script in arrayOf(
                "\$class:? extend java.util.Collection",
                "js:it.isExtend(\"java.util.Collection\")",
                "js:it.isCollection()",
                "groovy:it.isExtend(\"java.util.Collection\")",
                "groovy:it.isCollection()"
        )) {
            val ruleCheckIsCollection = ruleParser.parseBooleanRule(script)!!
            assertEquals(false, ruleCheckIsCollection.compute(ruleParser.contextOf(modelPsiClass, modelPsiClass)))
            assertEquals(
                    true,
                    ruleCheckIsCollection.compute(ruleParser.contextOf(listPsiClass, listPsiClass))
            )
        }
    }

    fun testParseEventRule() {
        assertNull(ruleParser.parseEventRule(""))
        assertNull(ruleParser.parseEventRule("\t\n"))
        assertNull(ruleParser.parseEventRule("any"))
        assertEquals("[WARN]\tevent rule not be supported for simple rule.\n", LoggerCollector.getLog().toUnixString())
        ruleParser.parseEventRule("groovy:logger.info(\"hello world\")")!!.compute(ruleParser.contextOf(listPsiClass, listPsiClass))
        assertEquals("[INFO]\thello world\n", LoggerCollector.getLog().toUnixString())
        ruleParser.parseEventRule("js:logger.debug(\"that's the future\")")!!.compute(ruleParser.contextOf(listPsiClass, listPsiClass))
        assertEquals("[DEBUG]\tthat's the future\n", LoggerCollector.getLog().toUnixString())
    }
}