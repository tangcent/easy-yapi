package com.itangcent.idea.plugin.rule

import com.google.inject.Inject
import com.itangcent.common.utils.FileUtils
import com.itangcent.debug.LoggerCollector
import com.itangcent.idea.plugin.api.export.ExportChannel
import com.itangcent.idea.plugin.utils.LocalStorage
import com.itangcent.idea.plugin.utils.SessionStorage
import com.itangcent.idea.utils.FileSaveHelper
import com.itangcent.intellij.config.rule.RuleContext
import com.itangcent.intellij.config.rule.parseEventRule
import com.itangcent.intellij.config.rule.parseStringRule
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.extend.withBoundary
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.ContextSwitchListener
import com.itangcent.mock.FileSaveHelperAdaptor
import com.itangcent.mock.toUnixString
import com.itangcent.test.ResultLoader
import com.itangcent.test.assertLinesContain
import com.itangcent.testFramework.escapeBackslash
import com.itangcent.testFramework.sub
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

    @Inject
    private lateinit var contextSwitchListener: ContextSwitchListener

    protected val ruleContext: RuleContext
        get() = SuvRuleContext()

    @Inject
    private lateinit var fileSaveHelper: FileSaveHelper

    override fun ruleParserClass() = SuvRuleParser::class

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bind(Logger::class) { it.with(LoggerCollector::class) }
        builder.bind(FileSaveHelper::class) { it.with(FileSaveHelperAdaptor::class) }
        builder.bindInstance(ExportChannel::class, ExportChannel.of("markdown"))
    }

    override fun customConfig(): String {
        return "x=123\nx=456\ny=666"
    }

    fun testLogger() {
        arrayOf("logger", "LOG").forEach { logger ->
            LoggerCollector.getLog()//clear
            ruleParser.parseEventRule("groovy:$logger.info(\"hello world\")")!!(ruleContext)
            assertEquals("[INFO]\thello world\n", LoggerCollector.getLog().toUnixString())
        }
    }

    fun testLocalStorage() {
        ruleParser.parseEventRule("groovy:localStorage.set(\"demo\",1)")!!(ruleContext)
        assertEquals(1, localStorage.get("demo"))
    }

    fun testSessionStorage() {
        arrayOf("session", "S").forEach { session ->
            ruleParser.parseEventRule("groovy:$session.set(\"demo\",1)")!!(ruleContext)
            assertEquals(1, sessionStorage.get("demo"))
            ruleParser.parseEventRule("groovy:$session.set(\"demo\",2)")!!(ruleContext)
            assertEquals(2, sessionStorage.get("demo"))
        }
    }

    fun testHttpClient() {
        assertEquals(
            "200",
            ruleParser.parseStringRule("groovy:httpClient.get(\"https://www.apache.org/licenses/LICENSE-1.1\").call().code()")!!(
                ruleContext
            )
        )
    }

    /**
     * Test case of [com.itangcent.idea.plugin.rule.StandardJdkRuleParser.Helper]
     */
    fun testHelper() {
        arrayOf("helper", "H").forEach { helper ->
            assertEquals(
                "com.itangcent.model.Model",
                ruleParser.parseStringRule("groovy:$helper.findClass(\"com.itangcent.model.Model\")")!!
                    (ruleParser.contextOf(userCtrlPsiClass, userCtrlPsiClass))
            )
            assertEquals(
                "com.itangcent.model.Model#str",
                ruleParser.parseStringRule("groovy:$helper.resolveLink(\"{@link com.itangcent.model.Model#str}\")")!!
                    (ruleParser.contextOf(userCtrlPsiClass, userCtrlPsiClass))
            )
            assertEquals(
                "[com.itangcent.model.Model, com.itangcent.model.Model#str]",
                ruleParser.parseStringRule("groovy:$helper.resolveLinks(\"{@link com.itangcent.model.Model},{@link com.itangcent.model.Model#str}\")")!!
                    (ruleParser.contextOf(userCtrlPsiClass, userCtrlPsiClass))
            )
        }
    }

    /**
     * Test case of [com.itangcent.idea.plugin.rule.StandardJdkRuleParser.Config]
     */
    fun testConfig() {
        arrayOf("config", "C").forEach { config ->
            assertEquals(
                "123",
                ruleParser.parseStringRule("groovy:$config.get(\"x\")")!!
                    (ruleContext)
            )
            assertEquals(
                "123456",
                ruleParser.parseStringRule("groovy:$config.getValues(\"x\").join()")!!
                    (ruleContext)
            )
            assertEquals(
                "666",
                ruleParser.parseStringRule("groovy:$config.resolveProperty(\"\\\${y}\")")!!
                    (ruleContext)
            )
            assertNull(
                ruleParser.parseStringRule("groovy:$config.resolvePropertyWith(null,\"#\\\$\",[z:888])")!!
                    (ruleContext)
            )
            assertEquals(
                "#{x},888",
                ruleParser.parseStringRule("groovy:$config.resolvePropertyWith(\"#{x},\\\${z}\",null,[z:888])")!!
                    (ruleContext)
            )
            assertEquals(
                "123,",
                ruleParser.parseStringRule("groovy:$config.resolvePropertyWith(\"#{x},\\\${z}\",\"#\\\$\",null)")!!
                    (ruleContext)
            )
            assertEquals(
                "123,888",
                ruleParser.parseStringRule("groovy:$config.resolvePropertyWith(\"#{x},\\\${z}\",\"#\\\$\",[z:888])")!!
                    (ruleContext)
            )
        }
    }

    /**
     * Test case of [com.itangcent.idea.plugin.rule.StandardJdkRuleParser.Files]
     */
    fun testFiles() {
        arrayOf("files", "F").forEach { files ->
            val demoPath = tempDir.sub("demo.txt").escapeBackslash()
            ruleParser.parseEventRule("groovy:$files.save(\"hello world\",\"$demoPath\")")!!(ruleContext)
            assertEquals("hello world", FileUtils.read(File(demoPath)))
            ruleParser.parseEventRule("groovy:$files.saveWithUI({\"hello world!\"},\"demo2.txt\",{},{},{})")!!(ruleContext)
            assertEquals("hello world!",
                (fileSaveHelper as FileSaveHelperAdaptor).bytes()?.let { String(it, Charsets.UTF_8) })
        }
    }

    /**
     * Test case of [com.itangcent.idea.plugin.rule.StandardJdkRuleParser.Runtime]
     */
    fun testRuntime() {
        arrayOf("runtime", "R").forEach { runtime ->
            contextSwitchListener.switchTo(userCtrlPsiClass)
            LoggerCollector.getLog()//clear

            assertEquals(
                "markdown", ruleParser.parseStringRule("groovy:$runtime.channel()")!!
                    (ruleContext)
            )
            assertEquals(
                project.name, ruleParser.parseStringRule("groovy:$runtime.projectName()")!!
                    (ruleContext)
            )
            assertEquals(
                project.basePath, ruleParser.parseStringRule("groovy:$runtime.projectPath()")!!
                    (ruleContext)
            )
            assertEquals(
                "test_default", ruleParser.parseStringRule("groovy:$runtime.module()")!!
                    (ruleParser.contextOf("userCtrlPsiClass", userCtrlPsiClass))
            )
            assertEquals(
                "light_idea_test_case", ruleParser.parseStringRule("groovy:$runtime.moduleName()")!!
                    (ruleParser.contextOf("userCtrlPsiClass", userCtrlPsiClass))
            )
            assertEquals(
                "/src", ruleParser.parseStringRule("groovy:$runtime.modulePath()")!!
                    (ruleParser.contextOf("userCtrlPsiClass", userCtrlPsiClass))
            )
            assertEquals(
                "/src/api/UserCtrl.java", ruleParser.parseStringRule("groovy:$runtime.filePath()")!!
                    (ruleParser.contextOf("userCtrlPsiClass", userCtrlPsiClass))
            )
            assertEquals(
                fileSaveHelper.toString(),
                ruleParser.parseStringRule("groovy:$runtime.getBean(\"com.itangcent.idea.utils.FileSaveHelper\")")!!
                    (ruleParser.contextOf("userCtrlPsiClass", userCtrlPsiClass))
            )
            assertNull(
                ruleParser.parseStringRule("groovy:$runtime.getBean(\"java.lang.String\")")!!
                    (ruleParser.contextOf("userCtrlPsiClass", userCtrlPsiClass))
            )
            assertNull(
                ruleParser.parseStringRule("groovy:$runtime.getBean(\"com.itangcent.Unknown\")")!!
                    (ruleParser.contextOf("userCtrlPsiClass", userCtrlPsiClass))
            )

            actionContext.withBoundary {
                ruleParser.parseStringRule(
                    "groovy:$runtime.async{" +
                            "logger.info(\"log in async\")" +
                            "}"
                )!!(ruleParser.contextOf("userCtrlPsiClass", userCtrlPsiClass))
            }

            assertLinesContain(
                ResultLoader.load("runtime.log"), LoggerCollector.getLog().toUnixString()
            )
        }
    }
}