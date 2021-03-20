package com.itangcent.mock

import com.itangcent.common.spi.Setup
import com.itangcent.idea.plugin.rule.SuvRuleParser
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
import com.itangcent.intellij.psi.DefaultPsiClassHelper
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.io.TempDir
import java.io.File
import java.nio.file.Path


/**
 * BaseContextTest with [tempDir]
 */
abstract class AdvancedContextTest : BaseContextTest() {

    @JvmField
    @TempDir
    var tempDir: Path? = null

    @BeforeAll
    fun load() {
        Setup.load()
    }

    open fun initConfig(file: File) {
    }

    override fun bind(builder: ActionContext.ActionContextBuilder) {
        super.bind(builder)
        builder.bindInstance("plugin.name", "easy_api")
        builder.bind(LocalFileRepository::class) {
            it.toInstance(TempFileRepository())
        }
        builder.bind(LocalFileRepository::class, "projectCacheRepository") {
            it.toInstance(TempFileRepository())
        }
        builder.bind(ConfigReader::class) {
            it.toInstance(ConfigReaderAdaptor())
        }
        builder.bind(RuleParser::class) { it.with(SuvRuleParser::class).singleton() }
        builder.bind(PsiClassHelper::class) { it.with(DefaultPsiClassHelper::class).singleton() }
        File("$tempDir${File.separator}temp_config.properties")
                .also { it.createNewFile() }
                .let { initConfig(it) }
    }

    private inner class TempFileRepository : AbstractLocalFileRepository() {
        override fun basePath(): String {
            return tempDir.toString()
        }
    }

    private inner class ConfigReaderAdaptor : AbstractConfigReader() {

        @PostConstruct
        fun init() {
            loadConfigInfo()
        }

        override fun findConfigFiles(): List<String>? {
            return listOf("$tempDir${File.separator}temp_config.properties")
        }
    }

    protected val n = System.getProperty("line.separator")
    protected val s = File.separator
}