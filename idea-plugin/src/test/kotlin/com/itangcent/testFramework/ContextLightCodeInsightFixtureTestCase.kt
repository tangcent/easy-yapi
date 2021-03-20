package com.itangcent.testFramework

import com.google.inject.Inject
import com.intellij.lang.Language
import com.intellij.lang.java.JavaLanguage
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiClassOwner
import com.intellij.psi.PsiFile
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.fixtures.LightCodeInsightFixtureTestCase
import com.itangcent.common.spi.Setup
import com.itangcent.idea.plugin.rule.SuvRuleParser
import com.itangcent.idea.plugin.settings.SettingBinder
import com.itangcent.idea.utils.ModuleHelper
import com.itangcent.intellij.config.AbstractConfigReader
import com.itangcent.intellij.config.ConfigReader
import com.itangcent.intellij.config.rule.RuleParser
import com.itangcent.intellij.context.ActionContext
import com.itangcent.intellij.extend.guice.PostConstruct
import com.itangcent.intellij.extend.guice.singleton
import com.itangcent.intellij.extend.guice.with
import com.itangcent.intellij.file.AbstractLocalFileRepository
import com.itangcent.intellij.file.LocalFileRepository
import com.itangcent.intellij.jvm.PsiClassHelper
import com.itangcent.intellij.jvm.dev.DevEnv
import com.itangcent.intellij.logger.Logger
import com.itangcent.intellij.psi.DefaultPsiClassHelper
import com.itangcent.mock.BaseContextTest
import com.itangcent.mock.ConstantModuleHelper
import com.itangcent.mock.PrintLogger
import com.itangcent.mock.SettingBinderAdaptor
import com.itangcent.utils.ResourceUtils
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path

abstract class ContextLightCodeInsightFixtureTestCase : LightCodeInsightFixtureTestCase() {

    @JvmField
    @TempDir
    var tempDir: Path? = null

    @Inject
    protected lateinit var actionContext: ActionContext

    @Inject
    protected lateinit var logger: Logger

    override fun getProjectDescriptor(): LightProjectDescriptor {
        //use java8
        return JAVA_8
    }

    open fun customConfig(): String? {
        return null
    }

    override fun setUp() {
        super.setUp()

        val builder = ActionContext.builder()
        builder.bind(SettingBinder::class) { it.with(SettingBinderAdaptor::class) }
        builder.bind(Logger::class) { it.with(PrintLogger::class) }
        builder.bind(Project::class) { it.toInstance(this.project) }
        builder.bind(ConfigReader::class) { it.toInstance(BaseContextTest.mockConfigReader) }
        builder.bind(DevEnv::class) { it.toInstance(BaseContextTest.mockDevEnv) }
        builder.bind(ModuleHelper::class) { it.toInstance(ConstantModuleHelper.INSTANCE) }
        builder.bindInstance("plugin.name", "easy_api")
        builder.bind(LocalFileRepository::class) {
            it.toInstance(TempFileRepository())
        }
        builder.bind(LocalFileRepository::class, "projectCacheRepository") {
            it.toInstance(TempFileRepository())
        }

        customConfig()?.takeIf { it.isNotBlank() }
                ?.let { config ->
                    builder.bind(ConfigReader::class) {
                        it.toInstance(ConfigReaderAdaptor(config))
                    }
                }

        builder.bind(RuleParser::class) { it.with(SuvRuleParser::class).singleton() }
        builder.bind(PsiClassHelper::class) { it.with(DefaultPsiClassHelper::class).singleton() }

        bind(builder)
        builder.build().init(this)
    }

    override fun tearDown() {
        actionContext.waitComplete()
        actionContext.stop(true)

        super.tearDown()
    }

    protected open fun bind(builder: ActionContext.ActionContextBuilder) {

    }

    /**
     * create psiClass but not configure to project
     * more lighter than [loadClass]
     */
    protected fun createClass(file: String, language: Language = JavaLanguage.INSTANCE): PsiClass? {
        return (createFile(file, language) as? PsiClassOwner)?.classes?.firstOrNull()
    }

    /**
     * create file but not configure to project
     * more lighter than [loadFile]
     */
    protected fun createFile(file: String, language: Language = JavaLanguage.INSTANCE): PsiFile? {
        val content = ResourceUtils.readResource(file)
        return createLightFile(file.substringAfterLast("/"), language, content)
    }

    /**
     * load psiClass from file to project
     */
    protected fun loadClass(file: String, language: Language = JavaLanguage.INSTANCE): PsiClass? {
        return (loadFile(file, language) as? PsiClassOwner)?.classes?.firstOrNull()
    }

    /**
     * load file to project
     */
    protected fun loadFile(file: String, language: Language = JavaLanguage.INSTANCE): PsiFile? {
        val content = ResourceUtils.readResource(file)
        myFixture.tempDirFixture.createFile(file, content)
        return myFixture.configureFromTempProjectFile(file)
    }

    private inner class TempFileRepository : AbstractLocalFileRepository() {
        override fun basePath(): String {
            return tempDir.toString()
        }
    }

    private inner class ConfigReaderAdaptor(val config: String) : AbstractConfigReader() {

        @PostConstruct
        fun init() {
            loadConfigInfoContent(config, "properties")
        }

        override fun findConfigFiles(): List<String>? {
            return null
        }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun load() {
            Setup.load()
        }
    }

    protected val n = System.getProperty("line.separator")
    protected val s = File.separator
}