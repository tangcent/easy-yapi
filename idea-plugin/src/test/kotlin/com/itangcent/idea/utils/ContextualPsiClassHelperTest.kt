package com.itangcent.idea.utils

import com.itangcent.common.kit.toJson
import com.itangcent.debug.LoggerCollector
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.intellij.logger.Logger
import com.itangcent.mock.toUnixString
import com.itangcent.test.ResultLoader

/**
 * Test case of [ContextualPsiClassHelper]
 */
internal abstract class ContextualPsiClassHelperTest : ContextualPsiClassHelperBaseTest() {

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(PsiClassHelper::class) { it.with(ContextualPsiClassHelper::class) }
        builder.bind(Logger::class) { it.with(LoggerCollector::class) }
    }

    override fun customConfig(): String {
        return "#The ObjectId and Date will be parsed as strings\n" +
                "json.rule.convert[org.bson.types.ObjectId]=java.lang.String\n" +
                "json.rule.convert[java.util.Date]=java.lang.String\n" +
                "json.rule.convert[java.sql.Timestamp]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDateTime]=java.lang.String\n" +
                "json.rule.convert[java.time.LocalDate]=java.lang.String\n" +
                "field.parse.before=groovy:```\n" +
                "    logger.info(\"before:\"+it)\n" +
                "```\n" +
                "field.parse.after=groovy:```\n" +
                "    logger.info(\"after:\"+it)\n" +
                "```\n"
    }

    fun testParseUserInfo() {
        LoggerCollector.getLog()
        val fields = psiClassHelper.getFields(userInfoPsiClass)
        assertEquals(
            "{\"id\":0,\"type\":0,\"name\":\"\",\"age\":0,\"sex\":0,\"birthDay\":\"\",\"regtime\":\"\"}",
            fields.toJson()
        )
        assertEquals(ResultLoader.load(this::class, "1"), LoggerCollector.getLog().toUnixString())
        psiClassHelper.getFields(userInfoPsiClass)
        assertEquals(ResultLoader.load(this::class, "2"), LoggerCollector.getLog().toUnixString())
    }

    internal class CachedContextualPsiClassHelperTest : ContextualPsiClassHelperTest()

    internal class NoCachedContextualPsiClassHelperTest : ContextualPsiClassHelperTest() {
        override fun customConfig(): String {
            return "json.cache.disable=true\n" +
                    super.customConfig()
        }
    }

}