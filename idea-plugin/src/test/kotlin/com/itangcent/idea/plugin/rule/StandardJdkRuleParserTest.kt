package com.itangcent.idea.plugin.rule

import com.google.inject.Inject
import com.itangcent.common.utils.FileUtils
import com.itangcent.debug.LoggerCollector
import com.itangcent.idea.plugin.utils.LocalStorage
import com.itangcent.idea.plugin.utils.SessionStorage
import com.itangcent.idea.utils.FileSaveHelper
import com.itangcent.intellij.config.rule.RuleContext
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.logger.Logger
import com.itangcent.mock.FileSaveHelperAdaptor
import com.itangcent.mock.toUnixString
import com.itangcent.testFramework.escapeBackslash
import com.itangcent.testFramework.sub
import junit.framework.Assert
import java.io.File

/**
 * Test case of [StandardJdkRuleParser]
 * 1.test engineBindings in [StandardJdkRuleParser.initScriptContext]
 */
internal class StandardJdkRuleParserTest : RuleParserBaseTest() {

    @Inject
    protected lateinit var localStorage: LocalStorage

    @Inject
    protected lateinit var sessionStorage: SessionStorage

    protected val ruleContext: RuleContext = SuvRuleContext()

    @Inject
    private lateinit var fileSaveHelper: FileSaveHelper

    override fun ruleParserClass() = SuvRuleParser::class

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(Logger::class) { it.with(LoggerCollector::class) }
        builder.bind(FileSaveHelper::class) { it.with(FileSaveHelperAdaptor::class) }
    }

    override fun customConfig(): String? {
        return "x=123\nx=456\ny=666"
    }

    fun testLogger() {
        LoggerCollector.getLog()//clear
        ruleParser.parseEventRule("groovy:logger.info(\"hello world\")")!!.compute(ruleContext)
        assertEquals("[INFO]\thello world\n", LoggerCollector.getLog().toUnixString())
    }

    fun testLocalStorage() {
        ruleParser.parseEventRule("groovy:localStorage.set(\"demo\",1)")!!.compute(ruleContext)
        assertEquals(1, localStorage.get("demo"))
    }

    fun testSessionStorage() {
        ruleParser.parseEventRule("groovy:session.set(\"demo\",1)")!!.compute(ruleContext)
        assertEquals(1, sessionStorage.get("demo"))
    }

    fun testHttpClient() {
        assertEquals("200",
                ruleParser.parseStringRule("groovy:httpClient.get(\"https://www.apache.org/licenses/LICENSE-1.1\").call().code()")!!.compute(ruleContext))
    }

    /**
     * Test case of [com.itangcent.idea.plugin.rule.StandardJdkRuleParser.Helper]
     */
    fun testHelper() {
        assertEquals("com.itangcent.model.Model",
                ruleParser.parseStringRule("groovy:helper.findClass(\"com.itangcent.model.Model\")")!!
                        .compute(ruleParser.contextOf(userCtrlPsiClass, userCtrlPsiClass)))
        assertEquals("com.itangcent.model.Model#str",
                ruleParser.parseStringRule("groovy:helper.resolveLink(\"{@link com.itangcent.model.Model#str}\")")!!
                        .compute(ruleParser.contextOf(userCtrlPsiClass, userCtrlPsiClass)))
        assertEquals("[com.itangcent.model.Model, com.itangcent.model.Model#str]",
                ruleParser.parseStringRule("groovy:helper.resolveLinks(\"{@link com.itangcent.model.Model},{@link com.itangcent.model.Model#str}\")")!!
                        .compute(ruleParser.contextOf(userCtrlPsiClass, userCtrlPsiClass)))
    }

    /**
     * Test case of [com.itangcent.idea.plugin.rule.StandardJdkRuleParser.Config]
     */
    fun testConfig() {
        assertEquals("123",
                ruleParser.parseStringRule("groovy:config.get(\"x\")")!!
                        .compute(ruleContext))
        assertEquals("123456",
                ruleParser.parseStringRule("groovy:config.getValues(\"x\").join()")!!
                        .compute(ruleContext))
        assertEquals("666",
                ruleParser.parseStringRule("groovy:config.resolveProperty(\"\\\${y}\")")!!
                        .compute(ruleContext))
        assertNull(
                ruleParser.parseStringRule("groovy:config.resolvePropertyWith(null,\"#\\\$\",[z:888])")!!
                        .compute(ruleContext))
        assertEquals("#{x},888",
                ruleParser.parseStringRule("groovy:config.resolvePropertyWith(\"#{x},\\\${z}\",null,[z:888])")!!
                        .compute(ruleContext))
        assertEquals("123,",
                ruleParser.parseStringRule("groovy:config.resolvePropertyWith(\"#{x},\\\${z}\",\"#\\\$\",null)")!!
                        .compute(ruleContext))
        assertEquals("123,888",
                ruleParser.parseStringRule("groovy:config.resolvePropertyWith(\"#{x},\\\${z}\",\"#\\\$\",[z:888])")!!
                        .compute(ruleContext))
    }

    /**
     * Test case of [com.itangcent.idea.plugin.rule.StandardJdkRuleParser.Files]
     */
    fun testFiles() {
        val demoPath = tempDir.sub("demo.txt").escapeBackslash()
        ruleParser.parseEventRule("groovy:files.save(\"hello world\",\"$demoPath\")")!!
                .compute(ruleContext)
        Assert.assertEquals("hello world", FileUtils.read(File(demoPath)))
        ruleParser.parseEventRule("groovy:files.saveWithUI({\"hello world!\"},\"demo2.txt\",{},{},{})")!!
                .compute(ruleContext)
        Assert.assertEquals("hello world!",
                (fileSaveHelper as FileSaveHelperAdaptor).bytes()?.let { String(it, Charsets.UTF_8) })
    }
}