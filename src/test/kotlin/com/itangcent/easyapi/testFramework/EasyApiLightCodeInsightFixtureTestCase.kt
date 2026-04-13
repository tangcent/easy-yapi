package com.itangcent.easyapi.testFramework

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.Project
import com.intellij.psi.JavaPsiFacade
import com.intellij.psi.PsiClass
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiMethod
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.testFramework.fixtures.LightJavaCodeInsightFixtureTestCase
import com.intellij.testFramework.registerServiceInstance
import com.itangcent.easyapi.config.ConfigReader
import com.itangcent.easyapi.core.event.ActionCompletedTopic
import com.itangcent.easyapi.core.event.ActionCompletedTopic.Companion.syncPublish
import com.itangcent.easyapi.core.threading.readSync
import com.itangcent.easyapi.rule.engine.RuleEngine
import com.itangcent.easyapi.settings.SettingBinder
import com.itangcent.easyapi.settings.Settings
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import java.io.InputStreamReader

abstract class EasyApiLightCodeInsightFixtureTestCase : LightJavaCodeInsightFixtureTestCase() {

    private val _testSettingBinder = ConstantSettingBinder(Settings())

    protected val testSettingBinder: ConstantSettingBinder
        get() = _testSettingBinder

    protected fun updateSettings(updater: Settings.() -> Unit) {
        _testSettingBinder.updateSettings(updater)
    }

    protected fun setSettings(settings: Settings) {
        _testSettingBinder.save(settings)
    }

    protected open fun createConfigReader(): ConfigReader? = null

    protected open fun createSettings(): Settings? = null

    @Before
    override fun setUp() {
        super.setUp()
        val settings = createSettings()
        if (settings != null) {
            project.registerServiceInstance(
                serviceInterface = SettingBinder::class.java,
                instance = ConstantSettingBinder(settings)
            )
        } else {
            project.registerServiceInstance(
                serviceInterface = SettingBinder::class.java,
                instance = _testSettingBinder
            )
        }
        val configReader = createConfigReader()
        if (configReader != null) {
            project.registerServiceInstance(
                serviceInterface = RuleEngine::class.java,
                instance = RuleEngine(project, configReader)
            )
        }
    }

    @After
    override fun tearDown() {
        project.syncPublish(ActionCompletedTopic.TOPIC)
        super.tearDown()
    }

    protected fun runTest(block: suspend () -> Unit) {
        runBlocking { block() }
    }

    protected fun findClass(qualifiedName: String): PsiClass? {
        return readSync {
            JavaPsiFacade.getInstance(project)
                .findClass(qualifiedName, GlobalSearchScope.allScope(project))
        }
    }

    protected fun findMethod(psiClass: PsiClass, name: String): PsiMethod? {
        return psiClass.findMethodsByName(name, false).firstOrNull() as? PsiMethod
    }

    protected fun loadFile(path: String): PsiFile {
        myFixture.findFileInTempDir(path)?.let { existing ->
            return myFixture.psiManager.findFile(existing)!!
        }
        val resourceStream = javaClass.getResourceAsStream("/$path")
            ?: throw AssertionError("Resource not found: $path")
        val reader = InputStreamReader(resourceStream, Charsets.UTF_8)
        val content = reader.readText()
        reader.close()
        return ApplicationManager.getApplication().runWriteAction<PsiFile> {
            myFixture.addFileToProject(path, content)
        }
    }

    protected fun loadFile(path: String, content: String): PsiFile {
        myFixture.findFileInTempDir(path)?.let { existing ->
            return myFixture.psiManager.findFile(existing)!!
        }
        return ApplicationManager.getApplication().runWriteAction<PsiFile> {
            myFixture.addFileToProject(path, content)
        }
    }

    protected fun loadSource(clazz: Class<*>): PsiClass {
        val className = clazz.simpleName
        val packageName = clazz.`package`.name
        val path = packageName.replace('.', '/') + "/" + className + ".java"
        val resourceStream = javaClass.getResourceAsStream("/jdk/$className.java")
        val content = if (resourceStream != null) {
            val reader = InputStreamReader(resourceStream, Charsets.UTF_8)
            val text = reader.readText()
            reader.close()
            text
        } else {
            generateStubClass(clazz)
        }
        ApplicationManager.getApplication().runWriteAction<PsiFile> {
            myFixture.addFileToProject(path, content)
        }
        return findClass(clazz.name)!!
    }

    private fun generateStubClass(clazz: Class<*>): String {
        val packageName = clazz.`package`.name
        val className = clazz.simpleName
        return when {
            clazz.isEnum -> "package $packageName; public enum $className {}"
            clazz.isInterface -> "package $packageName; public interface $className {}"
            clazz.isArray -> "package $packageName; public class $className {}"
            else -> "package $packageName; public class $className {}"
        }
    }
}
